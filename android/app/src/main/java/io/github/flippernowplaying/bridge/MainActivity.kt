package io.github.flippernowplaying.bridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.*
import io.github.flippernowplaying.core.Protocol
import java.util.UUID

/**
 * One-click setup: a single primary Connect button walks the three
 * prerequisites in order (Bluetooth permission → media access →
 * Flipper selection) and then starts the foreground bridge.
 *
 * Spec mapping (FR01/FR10): the three facts are still shown as three
 * separate checklist rows with separate explanations, the Flipper is
 * still chosen from an exact-UUID scan, and pairing confirmation is
 * still explicit. Player mode, elapsed/remaining, and diagnostics stay
 * available under Advanced (collapsed by default) so the main flow is
 * one button + one list.
 *
 * POST_NOTIFICATIONS is opportunistic and never blocks Connect.
 */
class MainActivity: Activity() {
 private val h=Handler(Looper.getMainLooper())
 private lateinit var checkRow: TextView
 private lateinit var hint: TextView
 private lateinit var primary: Button
 private lateinit var deviceText: TextView
 private lateinit var trackText: TextView
 private lateinit var list: LinearLayout
 private lateinit var listHeader: TextView
 private lateinit var advancedBox: LinearLayout
 private lateinit var playerButton: Button
 private var scanning=false
 private var scanner: BluetoothLeScanner?=null
 private val found=mutableSetOf<String>()
 private var pendingAuto=false
 private val prefs by lazy { getSharedPreferences("settings",MODE_PRIVATE) }

 private fun needs(): Array<String> = PermissionPolicy.bluetooth(Build.VERSION.SDK_INT)
  .filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }.toTypedArray()

 private fun access(): Boolean {
  val component=ComponentName(this,MediaAccessService::class.java)
  return if(Build.VERSION.SDK_INT>=27) getSystemService(NotificationManager::class.java)
   .isNotificationListenerAccessGranted(component)
  else Settings.Secure.getString(contentResolver,"enabled_notification_listeners")
   .orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString).contains(component)
 }

 private fun serviceRunning(): Boolean = BridgeService.status!="STOPPED"
 private fun device(): String? = prefs.getString("device",null)
 private fun playerLabel(): String = when(prefs.getString("player","com.apple.android.music")) {
  "auto"->"Auto"; "com.apple.android.music"->"Apple Music"; else->"Custom"
 }

 override fun onCreate(saved: Bundle?) {
  super.onCreate(saved)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(32,32,32,32) }
  setContentView(ScrollView(this).apply { addView(root) })

  root.addView(TextView(this).apply { text=getString(R.string.ui_1);textSize=26f })
  root.addView(TextView(this).apply { text=getString(R.string.ui_2) })
  checkRow=TextView(this).apply { textSize=15f;setPadding(0,16,0,0) };root.addView(checkRow)

  primary=Button(this).apply { textSize=18f;setOnClickListener { onPrimary() } }
  root.addView(primary)
  hint=TextView(this);root.addView(hint)

  deviceText=TextView(this);root.addView(deviceText)
  trackText=TextView(this);root.addView(trackText)

  listHeader=TextView(this).apply { visibility=View.GONE };root.addView(listHeader)
  list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };root.addView(list)

  root.addView(TextView(this).apply { text=getString(R.string.ui_3);textSize=12f;setPadding(0,16,0,0) })

  // Advanced (collapsed): everything FR10 requires beyond Start/Stop.
  val advToggle=Button(this).apply { text=getString(R.string.btn_advanced);setOnClickListener {
   advancedBox.visibility=if(advancedBox.visibility==View.GONE)View.VISIBLE else View.GONE } }
  root.addView(advToggle)
  advancedBox=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=View.GONE }
  root.addView(advancedBox)
  playerButton=Button(this).apply { setOnClickListener { pickPlayer() } }
  advancedBox.addView(playerButton)
  advancedBox.addView(Switch(this).apply {
   text=getString(R.string.ui_4);isChecked=prefs.getBoolean("remaining",false)
   setOnCheckedChangeListener { _,v->prefs.edit().putBoolean("remaining",v).apply();BridgeService.settingsChanged() } })
  advancedBox.addView(Button(this).apply { text=getString(R.string.btn_change);setOnClickListener {
   stopBridge();prefs.edit().remove("device").apply();pendingAuto=true;onPrimary() } })
  advancedBox.addView(Button(this).apply { text=getString(R.string.btn_forget);setOnClickListener {
   stopBridge();prefs.edit().remove("device").apply();pendingAuto=false;stopScan() } })
  advancedBox.addView(Button(this).apply { text=getString(R.string.btn_export);setOnClickListener {
   startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"now-playing-diagnostics.txt"),10) } })
 }

 /** Single entry point: perform the next missing step, otherwise start. */
 private fun onPrimary() {
  if(serviceRunning()) { stopBridge();return }
  if(needs().isNotEmpty()) { pendingAuto=true;requestPermissions(needs(),1);return }
  if(!access()) {
   pendingAuto=true
   hint.text=getString(R.string.hint_media)
   try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch(_:Exception) { }
   return
  }
  if(device()==null) {
   pendingAuto=true
   scan()
   return
  }
  startBridge()
 }

 private fun startBridge() {
  pendingAuto=false;stopScan()
  startForegroundService(Intent(this,BridgeService::class.java))
  // Opportunistic, non-blocking: connection notifications are nice but
  // denial must not block media access (spec 3.2).
  if(PermissionPolicy.notificationRuntime(Build.VERSION.SDK_INT) &&
     checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED &&
     !prefs.getBoolean("asked_notif",false)) {
   prefs.edit().putBoolean("asked_notif",true).apply()
   requestPermissions(PermissionPolicy.notifications(Build.VERSION.SDK_INT).toTypedArray(),2)
  }
 }

 private fun stopBridge() { stopScan();stopService(Intent(this,BridgeService::class.java)) }

 private fun pickPlayer() {
  val packages=listOf("com.apple.android.music","auto")+BridgeService.players
   .filter { it!="com.apple.android.music" }.distinct()
  val labels=packages.map { when(it) {
   "auto"->"Auto (active player)";"com.apple.android.music"->"Apple Music only";else->it } }
  AlertDialog.Builder(this).setTitle("Player").setItems(labels.toTypedArray()) { _,i->
   prefs.edit().putString("player",packages[i]).apply();BridgeService.settingsChanged() }.show()
 }

 @SuppressLint("MissingPermission")
 private fun scan() {
  if(needs().isNotEmpty()) { hint.text=getString(R.string.hint_bt);return }
  stopScan();list.removeAllViews();found.clear()
  try {
   scanner=getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner
   if(scanner==null) { hint.text=getString(R.string.ui_9);pendingAuto=false;return }
   scanning=true
   scanner!!.startScan(
    listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(Protocol.SERVICE))).build()),
    ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback)
   listHeader.visibility=View.VISIBLE;listHeader.text=getString(R.string.scanning)
   hint.text=getString(R.string.hint_scan)
   h.postDelayed({ stopScan() },15000)
  } catch(_:SecurityException) { scanning=false;hint.text=getString(R.string.ui_11) }
 }

 private val callback=object: ScanCallback() {
  @SuppressLint("MissingPermission") override fun onScanResult(type: Int,result: ScanResult) { h.post {
   if(!scanning||needs().isNotEmpty())return@post
   try {
    val address=result.device.address
    if(found.add(address)) list.addView(Button(this@MainActivity).apply {
     text="${result.scanRecord?.deviceName?:"Now Playing"} · ${address.takeLast(5)}"
     setOnClickListener {
      prefs.edit().putString("device",address).apply()
      hint.text=getString(R.string.selected_pair,address.takeLast(5))
      // Selecting IS connecting: no second Start tap.
      startBridge()
     }
    })
   } catch(_:SecurityException) { hint.text=getString(R.string.ui_12) }
  } }
  override fun onScanFailed(code: Int) { h.post { scanning=false;hint.text=getString(R.string.scan_error,code) } }
 }

 @SuppressLint("MissingPermission") private fun stopScan() {
  if(scanning)try { scanner?.stopScan(callback) } catch(_:SecurityException) { }
  scanning=false
  if(found.isEmpty())listHeader.visibility=View.GONE
 }

 override fun onRequestPermissionsResult(code: Int,perms: Array<String>,results: IntArray) {
  super.onRequestPermissionsResult(code,perms,results)
  if(code==2)return // notification permission never blocks
  if(pendingAuto&&needs().isEmpty())onPrimary()
  else if(needs().isNotEmpty())hint.text=getString(R.string.hint_bt)
 }

 override fun onResume() {
  super.onResume();h.post(refresh)
  // Auto-continue after the user returns from system settings.
  if(pendingAuto&&!serviceRunning()&&needs().isEmpty()&&access()) {
   if(device()!=null)startBridge()
   else if(!scanning)scan()
  }
 }
 override fun onPause() { stopScan();h.removeCallbacks(refresh);super.onPause() }

 private val refresh=object: Runnable { override fun run() {
  val btOk=needs().isEmpty();val mediaOk=access();val dev=device()
  val ok=getString(R.string.row_ok);val miss=getString(R.string.row_missing)
  checkRow.text="${if(btOk)ok else miss} ${getString(R.string.check_bt)}   "+
   "${if(mediaOk)ok else miss} ${getString(R.string.check_media)}   "+
   "${if(dev!=null)ok else miss} ${getString(R.string.check_flipper)}"
  deviceText.text=getString(R.string.device_line,dev?.takeLast(5)?:getString(R.string.none))
  val preview=BridgeService.preview
  trackText.text=if(preview.isNotEmpty())preview else getString(R.string.track_hint)
  playerButton.text=getString(R.string.btn_player,playerLabel())
  if(serviceRunning()) {
   primary.text=getString(R.string.btn_stop)
   if(hint.text.isNullOrEmpty()||hint.text.toString().startsWith("Tap Connect"))
    hint.text=getString(R.string.hint_running)+" "+BridgeService.status
  } else {
   primary.text=getString(R.string.btn_connect)
   // Only overwrite the hint when it is not showing scan/selection progress.
   if(!scanning&&!hint.text.toString().startsWith("Flipper selected")) {
    hint.text=when {
     !btOk->getString(R.string.hint_bt)
     !mediaOk->getString(R.string.hint_media)
     dev==null->getString(R.string.hint_scan)
     else->getString(R.string.hint_ready)
    }
   }
  }
  h.postDelayed(this,1000)
 } }

 @Deprecated("Platform activity result compatibility") override fun onActivityResult(request: Int,result: Int,data: Intent?) {
  super.onActivityResult(request,result,data)
  if(request==10&&result==RESULT_OK)data?.data?.let { uri->
   try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(BridgeService.report()) } }
   catch(_:Exception) { hint.text=getString(R.string.ui_13) } }
 }
}

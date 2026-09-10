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

class MainActivity: Activity() {
 private val h=Handler(Looper.getMainLooper());private lateinit var text: TextView;private lateinit var deviceText: TextView
 private lateinit var list: LinearLayout;private var scanning=false;private var scanner: BluetoothLeScanner?=null
 private val found=mutableSetOf<String>();private val prefs by lazy { getSharedPreferences("settings",MODE_PRIVATE) }
 private fun needs(): Array<String> = PermissionPolicy.bluetooth(Build.VERSION.SDK_INT).filter { checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED }.toTypedArray()
 private fun access(): Boolean {
  val component=ComponentName(this,MediaAccessService::class.java)
  return if(Build.VERSION.SDK_INT>=27) getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
  else Settings.Secure.getString(contentResolver,"enabled_notification_listeners").orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString).contains(component)
 }
 private fun button(parent: LinearLayout,label: String,click: ()->Unit) { parent.addView(Button(this).apply { text=label;setOnClickListener { click() } }) }
 override fun onCreate(saved: Bundle?) {
  super.onCreate(saved)
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(24,24,24,24) }
  setContentView(ScrollView(this).apply { addView(root) })
  root.addView(TextView(this).apply { text=getString(R.string.ui_1);textSize=24f })
  root.addView(TextView(this).apply { text=getString(R.string.ui_2) })
  deviceText=TextView(this);root.addView(deviceText)
  text=TextView(this);root.addView(text)
  button(root,"Grant Bluetooth access") { val permissions=needs();if(permissions.isNotEmpty())requestPermissions(permissions,1) }
  button(root,"Enable notification access") { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
  root.addView(TextView(this).apply { text=getString(R.string.ui_3) })
  if(PermissionPolicy.notificationRuntime(Build.VERSION.SDK_INT))button(root,"Allow connection notifications") { requestPermissions(PermissionPolicy.notifications(Build.VERSION.SDK_INT).toTypedArray(),2) }
  button(root,"Find Now Playing devices") { scan() }
  list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };root.addView(list)
  button(root,"Choose player (default: Apple Music)") {
   val packages=listOf("com.apple.android.music","auto")+BridgeService.players.filter { it!="com.apple.android.music" }
   val labels=packages.map { when(it) { "auto"->"Auto (active player)";"com.apple.android.music"->"Apple Music only";else->it } }
   AlertDialog.Builder(this).setTitle("Player").setItems(labels.toTypedArray()) { _,i->prefs.edit().putString("player",packages[i]).apply();BridgeService.settingsChanged() }.show()
  }
  root.addView(Switch(this).apply { text=getString(R.string.ui_4);isChecked=prefs.getBoolean("remaining",false);setOnCheckedChangeListener { _,v->prefs.edit().putBoolean("remaining",v).apply();BridgeService.settingsChanged() } })
  button(root,"Start") {
   if(needs().isNotEmpty()) { text.text=getString(R.string.ui_5);return@button }
   if(!access()) { text.text=getString(R.string.ui_6);return@button }
   if(prefs.getString("device",null)==null) { text.text=getString(R.string.ui_7);return@button }
   stopScan();startForegroundService(Intent(this,BridgeService::class.java))
  }
  button(root,"Stop") { val stopIntent=Intent(this,BridgeService::class.java);stopService(stopIntent) }
  button(root,"Export redacted diagnostics") { startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"now-playing-diagnostics.txt"),10) }
 }
 @SuppressLint("MissingPermission")
 private fun scan() {
  if(needs().isNotEmpty()) { text.text=getString(R.string.ui_8);return }
  stopScan();list.removeAllViews();found.clear()
  try {
   scanner=getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner
   if(scanner==null) { text.text=getString(R.string.ui_9);return }
   scanning=true;scanner!!.startScan(listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID.fromString(Protocol.SERVICE))).build()),ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback)
   text.text=getString(R.string.ui_10);h.postDelayed({ stopScan() },15000)
  } catch(_:SecurityException) { scanning=false;text.text=getString(R.string.ui_11) }
 }
 private val callback=object: ScanCallback() {
  @SuppressLint("MissingPermission") override fun onScanResult(type: Int,result: ScanResult) { h.post {
   if(!scanning||needs().isNotEmpty())return@post
   try { val address=result.device.address
    if(found.add(address))button(list,"${result.scanRecord?.deviceName?:"Now Playing"} · ${address.takeLast(5)}") {
     stopScan();prefs.edit().putString("device",address).apply();deviceText.text=getString(R.string.selected_pair,address.takeLast(5))
    }
   } catch(_:SecurityException) { text.text=getString(R.string.ui_12) }
  } }
  override fun onScanFailed(code: Int) { h.post { scanning=false;text.text=getString(R.string.scan_error,code) } }
 }
 @SuppressLint("MissingPermission") private fun stopScan() { if(scanning)try { scanner?.stopScan(callback) } catch(_:SecurityException) { };scanning=false }
 private val refresh=object: Runnable { override fun run() {
  text.text=getString(R.string.status,needs().isEmpty().toString(),access().toString(),BridgeService.status,prefs.getString("player","com.apple.android.music"),BridgeService.preview)
  deviceText.text=getString(R.string.device,prefs.getString("device",null)?.takeLast(5)?:getString(R.string.none))
  h.postDelayed(this,1000)
 } }
 override fun onResume() { super.onResume();h.post(refresh) }
 override fun onPause() { stopScan();h.removeCallbacks(refresh);super.onPause() }
 @Deprecated("Platform activity result compatibility") override fun onActivityResult(request: Int,result: Int,data: Intent?) { super.onActivityResult(request,result,data)
  if(request==10&&result==RESULT_OK)data?.data?.let { uri->try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(BridgeService.report()) } } catch(_:Exception) { text.text=getString(R.string.ui_13) } }
 }
}

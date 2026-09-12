package io.github.flippernowplaying.bridge

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Standard listening mode refreshes after process death without persisting a false On state. */
class NowPlayingTileService : TileService() {
 private val main = Handler(Looper.getMainLooper())
 private var listening = false
 private val changed: () -> Unit = { main.post { if(listening) render() } }
 override fun onStartListening() {
  super.onStartListening()
  listening = true
  BridgeService.observers.add(changed)
  render()
 }
 override fun onStopListening() {
  listening = false
  BridgeService.observers.remove(changed)
  main.removeCallbacksAndMessages(null)
  super.onStopListening()
 }
 override fun onDestroy() {
  listening = false
  BridgeService.observers.remove(changed)
  main.removeCallbacksAndMessages(null)
  super.onDestroy()
 }
 override fun onClick() {
  super.onClick()
  when(TilePolicy.action(BridgeService.enabled, BridgeSetup.configured(this))) {
   TilePolicy.Action.STOP -> {
    val stopIntent=Intent(this, BridgeService::class.java)
    stopService(stopIntent)
   }
   else -> if(isLocked) unlockAndRun { startOrSetup() } else startOrSetup()
  }
 }
 private fun startOrSetup() {
  // Re-evaluate after unlocking: another entry point may have started the helper.
  if(BridgeService.enabled) return
  val destination = if(BridgeSetup.configured(this)) TileStartActivity::class.java else MainActivity::class.java
  val intent = Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
  if(destination == MainActivity::class.java) intent.putExtra(MainActivity.EXTRA_TILE_SETUP, true)
  if(Build.VERSION.SDK_INT >= 34) {
   startActivityAndCollapse(PendingIntent.getActivity(this, 20, intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
  } else {
   launchLegacy(intent)
  }
 }
 // AGP 8.10 lint does not account for the API guard. PendingIntent overload exists only on API34+.
 @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
 @Suppress("DEPRECATION")
 private fun launchLegacy(intent: Intent) {
  check(Build.VERSION.SDK_INT < 34)
  startActivityAndCollapse(intent)
 }
 private fun render() {
  val tile = qsTile ?: return
  val enabled = BridgeService.enabled
  val status = TilePolicy.status(enabled, BridgeSetup.configured(this), BridgeService.status)
  val subtitle = getString(when(status) {
   TilePolicy.Status.OFF -> R.string.tile_off
   TilePolicy.Status.SETUP -> R.string.tile_setup
   TilePolicy.Status.CONNECTING -> R.string.tile_connecting
   TilePolicy.Status.WAITING -> R.string.tile_waiting
   TilePolicy.Status.CONNECTED -> R.string.tile_connected
   TilePolicy.Status.BLUETOOTH_OFF -> R.string.tile_bluetooth_off
   TilePolicy.Status.ATTENTION -> R.string.tile_attention
  })
  tile.label = getString(R.string.ui_1)
  tile.state = if(enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
  tile.contentDescription = getString(R.string.tile_description, tile.label, subtitle)
  if(Build.VERSION.SDK_INT >= 29) tile.subtitle = subtitle
  if(Build.VERSION.SDK_INT >= 30) tile.stateDescription = subtitle
  tile.updateTile()
 }
}

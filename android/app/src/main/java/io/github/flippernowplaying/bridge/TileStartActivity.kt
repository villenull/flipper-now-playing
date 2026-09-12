package io.github.flippernowplaying.bridge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView

/** A visible user-initiated start, including on platforms restricting tile-started FGS. */
class TileStartActivity : Activity() {
 private var started = false
 private var resumed = false
 private val main = Handler(Looper.getMainLooper())
 private val changed: () -> Unit = { main.post {
  if(started && !isFinishing) {
   if(BridgeService.enabled) finish()
   else { openSetup();finish() }
  }
 } }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  BridgeService.observers.add(changed)
  setContentView(TextView(this).apply {
   text = getString(R.string.tile_starting)
   textSize = 20f
   setPadding(32, 32, 32, 32)
  })
 }
 override fun onPostResume() {
  super.onPostResume()
  resumed = true
  if(hasWindowFocus()) begin()
 }
 override fun onWindowFocusChanged(hasFocus: Boolean) {
  super.onWindowFocusChanged(hasFocus)
  if(hasFocus && resumed) begin()
 }
 override fun onPause() { resumed=false;super.onPause() }
 private fun begin() {
  if(started) return
  started = true
  if(!BridgeSetup.configured(this)) {
   openSetup();finish()
  } else if(!BridgeService.enabled) {
   try {
    startForegroundService(Intent(this, BridgeService::class.java))
    // Remain visible until the service actually enters foreground, not just until the request returns.
    main.postDelayed({ if(!isFinishing) { openSetup();finish() } }, 5000)
   }
   catch(_: IllegalStateException) { openSetup();finish() }
   catch(_: SecurityException) { openSetup();finish() }
  } else finish()
 }
 override fun onDestroy() {
  BridgeService.observers.remove(changed)
  main.removeCallbacksAndMessages(null)
  super.onDestroy()
 }
 private fun openSetup() {
  startActivity(Intent(this, MainActivity::class.java))
 }
}

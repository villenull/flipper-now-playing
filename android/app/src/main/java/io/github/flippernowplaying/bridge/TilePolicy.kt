package io.github.flippernowplaying.bridge

/** Enabled means the listening session is armed, not that a radio link is ready. */
object TilePolicy {
 enum class Action { STOP, SETUP, START }
 enum class Status { OFF, SETUP, CONNECTING, WAITING, CONNECTED, BLUETOOTH_OFF, ATTENTION }
 fun action(enabled: Boolean, configured: Boolean) = when {
  enabled -> Action.STOP
  !configured -> Action.SETUP
  else -> Action.START
 }
 fun status(enabled: Boolean, configured: Boolean, connection: String) = when {
  !enabled -> if(configured) Status.OFF else Status.SETUP
  !configured -> Status.ATTENTION
  connection == "READY" -> Status.CONNECTED
  connection == "ARMED_WAITING" -> Status.WAITING
  connection == "BLUETOOTH_OFF" -> Status.BLUETOOTH_OFF
  connection in setOf("STARTING", "CONNECTING", "SECURING", "DISCOVERING", "SUBSCRIBING", "HANDSHAKING", "SYNCING") || connection.startsWith("SECURING:") -> Status.CONNECTING
  else -> Status.ATTENTION
 }
}

package io.github.flippernowplaying.bridge

import kotlin.test.*

class TilePolicyTest {
 @Test fun stopStillWorksWhenPermissionWasRevoked() {
  assertEquals(TilePolicy.Action.STOP, TilePolicy.action(true, false))
  assertEquals(TilePolicy.Action.STOP, TilePolicy.action(true, true))
 }
 @Test fun missingSetupCannotStartTheRadio() {
  assertEquals(TilePolicy.Action.SETUP, TilePolicy.action(false, false))
  assertEquals(TilePolicy.Action.START, TilePolicy.action(false, true))
 }
 @Test fun stoppedProcessCannotDisplayStaleConnectedState() {
  assertEquals(TilePolicy.Status.OFF, TilePolicy.status(false, true, "READY"))
  assertEquals(TilePolicy.Status.SETUP, TilePolicy.status(false, false, "READY"))
 }
 @Test fun enabledSessionDistinguishesWaitingFromConnected() {
  assertEquals(TilePolicy.Status.WAITING, TilePolicy.status(true, true, "ARMED_WAITING"))
  assertEquals(TilePolicy.Status.CONNECTED, TilePolicy.status(true, true, "READY"))
  for(stage in listOf("STARTING", "SECURING: confirm the matching code on both devices", "HANDSHAKING", "SYNCING"))
   assertEquals(TilePolicy.Status.CONNECTING, TilePolicy.status(true, true, stage))
 }
 @Test fun armedFailuresRemainStoppableAndVisible() {
  assertEquals(TilePolicy.Status.BLUETOOTH_OFF, TilePolicy.status(true, true, "BLUETOOTH_OFF"))
  for(stage in listOf("ERROR: timeout; waiting to reconnect", "NEEDS_PERMISSION: repair Bluetooth access", "STOPPED", "READY")) {
   assertEquals(TilePolicy.Status.ATTENTION, TilePolicy.status(true, false, stage))
   assertEquals(TilePolicy.Action.STOP, TilePolicy.action(true, false))
  }
 }
}

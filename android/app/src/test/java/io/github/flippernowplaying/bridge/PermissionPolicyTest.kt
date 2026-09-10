package io.github.flippernowplaying.bridge
import kotlin.test.*
class PermissionPolicyTest {
 @Test fun legacyDiscoveryIsForegroundLocationOnly() { for(api in listOf(26,30))assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"),PermissionPolicy.bluetooth(api)) }
 @Test fun modernBluetoothNeverRequestsLocationOrAdvertise() { for(api in listOf(31,33,34,36,37))assertEquals(listOf("android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT"),PermissionPolicy.bluetooth(api)) }
 @Test fun notificationIsSeparate() { assertFalse(PermissionPolicy.notificationRuntime(31));assertTrue(PermissionPolicy.notificationRuntime(33));assertTrue(PermissionPolicy.notifications(31).isEmpty());assertEquals(listOf("android.permission.POST_NOTIFICATIONS"),PermissionPolicy.notifications(33)) }
}

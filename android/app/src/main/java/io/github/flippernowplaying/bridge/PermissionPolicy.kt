package io.github.flippernowplaying.bridge
import android.Manifest
import android.annotation.SuppressLint
// These are inlined permission strings, guarded by the injected API level and tested at each boundary.
@SuppressLint("InlinedApi")
object PermissionPolicy {
 fun bluetooth(api: Int): List<String> = if(api>=31) listOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
 fun notificationRuntime(api: Int)=api>=33
 fun notifications(api: Int): List<String> = if(notificationRuntime(api))listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
}

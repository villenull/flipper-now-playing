package io.github.flippernowplaying.bridge

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

object BridgeSetup {
 fun missingBluetooth(context: Context): Array<String> = PermissionPolicy.bluetooth(Build.VERSION.SDK_INT)
  .filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
 fun mediaAccess(context: Context): Boolean {
  val component = ComponentName(context, MediaAccessService::class.java)
  return if(Build.VERSION.SDK_INT >= 27) context.getSystemService(NotificationManager::class.java)
   .isNotificationListenerAccessGranted(component)
  else Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
   .orEmpty().split(':').mapNotNull(ComponentName::unflattenFromString).contains(component)
 }
 fun configured(context: Context) = missingBluetooth(context).isEmpty() && mediaAccess(context) &&
  context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("device", null) != null
}

package io.github.flippernowplaying.bridge
import android.service.notification.NotificationListenerService
class MediaAccessService: NotificationListenerService() {
 override fun onListenerConnected() { BridgeService.accessChanged(true) }
 override fun onListenerDisconnected() { BridgeService.accessChanged(false) }
 // No notification body callbacks: active media sessions are the only data source.
}

package io.github.flippernowplaying.bridge

import android.content.*
import android.media.*
import android.media.session.*
import android.os.*
import android.view.KeyEvent
import io.github.flippernowplaying.core.MediaState
import io.github.flippernowplaying.core.PlayerSelector

class MediaSessionRepository(private val context: Context,private val h: Handler,private val changed: (Boolean)->Unit,private val problem: (String)->Unit) {
 private val artworkReader=ArtworkReader(context,h)
 private var artworkDirty=true
 var artwork: ByteArray?=null;private set
 fun close() { stop();artworkReader.close() }
 private fun invalidateArtwork() { artworkReader.invalidate();artwork=null;artworkDirty=true }
 private val manager=context.getSystemService(MediaSessionManager::class.java)
 private val component=ComponentName(context,MediaAccessService::class.java)
 private val prefs=context.getSharedPreferences("settings",Context.MODE_PRIVATE)
 private var controller: MediaController?=null;private var callback: MediaController.Callback?=null
 private var controllers=listOf<MediaController>();private var registered=false
 private var epochCounter=0L;private var epoch=0L;private var revision=0L;private var identity: List<Any?>?=null
 private val sessions=MediaSessionManager.OnActiveSessionsChangedListener { list -> controllers=list.orEmpty();select() }
 fun start() { try { if(!registered) { manager.addOnActiveSessionsChangedListener(sessions,component,h);registered=true };controllers=manager.getActiveSessions(component);select() } catch(_:SecurityException) { revoke() } }
 fun stop() { if(registered) { try { manager.removeOnActiveSessionsChangedListener(sessions) } catch(_:SecurityException) { };registered=false };detach();controllers=emptyList() }
 private fun detach() { invalidateArtwork(); callback?.let { controller?.unregisterCallback(it) };callback=null;controller=null }
 fun revoke() { stop();epoch=0;revision=0;identity=null;problem("Phone needs notification access");changed(true) }
 fun resetConnection() { invalidateArtwork(); epochCounter=0;epoch=if(controller==null)0 else ++epochCounter;revision=if(controller==null)0 else 1;identity=null }
 fun refresh() { start() }
 private fun select() {
  val mode=prefs.getString("player","com.apple.android.music")!!
  val candidates=controllers.filter { if(mode=="auto") it.packageName!=context.packageName&&!it.packageName.startsWith("io.github.flippernowplaying.testplayer") else it.packageName==mode }
  fun playing(c: MediaController)=c.playbackState?.state in listOf(PlaybackState.STATE_PLAYING,PlaybackState.STATE_BUFFERING)
  val old=controller
  val tokens=candidates.map { it.sessionToken }
  val selectedToken=PlayerSelector.choose(tokens,old?.sessionToken) { token->playing(candidates.first { it.sessionToken==token }) }
  val selected=candidates.firstOrNull { it.sessionToken==selectedToken }
  if(selected?.sessionToken==old?.sessionToken) { changed(false);return }
  detach();controller=selected;identity=null
  if(selected==null) { epoch=0;revision=0;changed(true);return }
  if(epochCounter==0xffffffffL) { detach();epoch=0;revision=0;problem("Counter exhausted");return };epoch=++epochCounter;revision=1
  val token=selected.sessionToken
  callback=object: MediaController.Callback() {
   private fun update(full: Boolean) { if(controller?.sessionToken==token)changed(full) }
   override fun onMetadataChanged(metadata: MediaMetadata?) { if(controller?.sessionToken==token) { invalidateArtwork();update(true) } }
   override fun onPlaybackStateChanged(state: PlaybackState?) { if(controller?.sessionToken==token) { select();update(false) } }
   override fun onAudioInfoChanged(info: MediaController.PlaybackInfo)=update(false)
   override fun onSessionDestroyed() { if(controller?.sessionToken==token) { controllers=controllers.filter { it.sessionToken!=token };select() } }
  }
  selected.registerCallback(callback!!,h);changed(true)
 }
 fun observed(): List<String> = controllers.map { it.packageName }.distinct()
 fun sample(): MediaState {
  val c=controller?:return MediaState(remaining=prefs.getBoolean("remaining",false))
  val m=c.metadata;val p=c.playbackState
  val rawState=p?.state?:PlaybackState.STATE_NONE
  val state=when(rawState) {
   PlaybackState.STATE_PLAYING,PlaybackState.STATE_FAST_FORWARDING,PlaybackState.STATE_REWINDING->3
   PlaybackState.STATE_PAUSED->2
   PlaybackState.STATE_STOPPED->1
   PlaybackState.STATE_BUFFERING,PlaybackState.STATE_CONNECTING->4
   PlaybackState.STATE_ERROR->5
   else->6
  }
  val title=m?.getString(MediaMetadata.METADATA_KEY_TITLE)?:m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
  val artist=m?.getString(MediaMetadata.METADATA_KEY_ARTIST)?:m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
  val album=m?.getString(MediaMetadata.METADATA_KEY_ALBUM)
  val key=listOf(m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),p?.activeQueueItemId,title,artist,album)
  if(identity!=null&&identity!=key) { if(revision==0xffffffffL) { problem("Counter exhausted");return MediaState() };revision++;invalidateArtwork() };identity=key
  if(artworkDirty) {
   artworkDirty=false
   val requestedEpoch=epoch;val requestedRevision=revision;val requestedToken=c.sessionToken
   artworkReader.read(m) { bytes ->
    if(controller?.sessionToken==requestedToken&&epoch==requestedEpoch&&revision==requestedRevision) {
     if(!(artwork?.contentEquals(bytes?:byteArrayOf())?: (bytes==null))) { artwork=bytes;changed(true) }
    }
   }
  }
  val actions=p?.actions?:0
  val toggle=if(state==3) PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
  var caps=0
  if(actions and (toggle or PlaybackState.ACTION_PLAY_PAUSE)!=0L)caps=caps or 1
  if(actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS!=0L)caps=caps or 2
  if(actions and PlaybackState.ACTION_SKIP_TO_NEXT!=0L)caps=caps or 4
  val info=c.playbackInfo
  if(if(info.playbackType==MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL) !context.getSystemService(AudioManager::class.java).isVolumeFixed else info.volumeControl!=VolumeProvider.VOLUME_CONTROL_FIXED)caps=caps or 24
  return MediaState(epoch,revision,state,p?.position?.takeIf { it>=0 },m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it>0 },p?.playbackSpeed?:0f,p?.lastPositionUpdateTime?:0,caps,title,artist,album,
   runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(c.packageName,0)).toString() }.getOrDefault(c.packageName),prefs.getBoolean("remaining",false))
 }
 fun dispatch(command: Int): Int {
  val c=controller?:return 1
  return try {
   val state=sample();if(state.capabilities and (1 shl (command-1))==0)return 3
   val actions=c.playbackState?.actions?:0
   when(command) {
    1->{ val pause=state.state==3;val direct=if(pause)PlaybackState.ACTION_PAUSE else PlaybackState.ACTION_PLAY
     if(actions and direct!=0L) { if(pause)c.transportControls.pause() else c.transportControls.play() }
     else { val down=SystemClock.uptimeMillis();c.dispatchMediaButtonEvent(KeyEvent(down,down,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0));c.dispatchMediaButtonEvent(KeyEvent(down,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,0)) }
    }
    2->c.transportControls.skipToPrevious()
    3->c.transportControls.skipToNext()
    4,5->{ val direction=if(command==4)AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
     val info=c.playbackInfo?:return 3
     if(info.playbackType==MediaController.PlaybackInfo.PLAYBACK_TYPE_LOCAL)context.getSystemService(AudioManager::class.java).adjustStreamVolume(AudioManager.STREAM_MUSIC,direction,0)
     else if(info.volumeControl==VolumeProvider.VOLUME_CONTROL_FIXED)return 3 else c.adjustVolume(direction,0)
    }
    else->return 3
   };h.postDelayed({ changed(false) },300);0
  } catch(_:SecurityException) { revoke();5 } catch(_:RuntimeException) { changed(true);6 }
 }
}

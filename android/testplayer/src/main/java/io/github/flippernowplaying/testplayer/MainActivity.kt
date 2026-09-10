package io.github.flippernowplaying.testplayer
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.AudioAttributes
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.SystemClock
import android.widget.*
class MainActivity: Activity() {
 private lateinit var session: MediaSession;private var artEnabled=true;private var playing=false;private var index=1;private var pos=0L;private var missing=false;private var supported=true;private var stateOverride: Int?=null;private var speed=1f;private var remoteMode=0;private var actions=0;private lateinit var status: TextView
 private fun publish() {
  val metadata=MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,if(missing) "Crème — 日本語" else "Fixture track $index").putString(MediaMetadata.METADATA_KEY_ARTIST,"Test Artist")
  if(!missing)metadata.putString(MediaMetadata.METADATA_KEY_ALBUM,"A long fixture album that scrolls on the Flipper display").putLong(MediaMetadata.METADATA_KEY_DURATION,248000)
  if(artEnabled&&!missing) {
   val bitmap=Bitmap.createBitmap(90,90,Bitmap.Config.ARGB_8888)
   for(y in 0 until 90)for(x in 0 until 90)bitmap.setPixel(x,y,Color.rgb((x*255/89+index*17)%256,y*255/89,128))
   metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,bitmap)
  }
  session.setMetadata(metadata.build())
  if(::status.isInitialized)status.text="Dispatched actions: $actions; track $index; volume mode $remoteMode"
  session.setPlaybackState(PlaybackState.Builder().setActions(if(supported) PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS else 0).setState(stateOverride?:(if(playing)PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED),pos,if(playing)speed else 0f,SystemClock.elapsedRealtime()).build())
 }
 private fun create() {
  session=MediaSession(this,"NowPlayingFixture");session.setCallback(object: MediaSession.Callback() {
   override fun onPlay() { actions++;stateOverride=null;playing=true;publish() };override fun onPause() { actions++;stateOverride=null;playing=false;publish() }
   override fun onSkipToNext() { actions++;index++;pos=0;publish() };override fun onSkipToPrevious() { actions++;index--;pos=0;publish() }
  });session.isActive=true;publish()
 }
 override fun onCreate(saved: Bundle?) { super.onCreate(saved);create()
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL };setContentView(ScrollView(this).apply { addView(root) });status=TextView(this);root.addView(status)
  fun button(name: String,f: ()->Unit) { root.addView(Button(this).apply { text=name;setOnClickListener { f() } }) }
  button("Play / pause") { playing=!playing;publish() };button("Seek +60 seconds") { pos=(pos+60000).coerceAtMost(248000);publish() }
  button("Artwork / no artwork") { artEnabled=!artEnabled;publish() }
  button("Missing fields / Unicode") { missing=!missing;publish() };button("Toggle supported actions") { supported=!supported;publish() }
  button("Replace session") { session.release();create() };button("Destroy session") { session.release() }
  button("Buffering / normal") { stateOverride=if(stateOverride==null)PlaybackState.STATE_BUFFERING else null;publish() }
  button("Speed 2x / reverse / normal") { speed=when(speed) { 1f->2f;2f->-1f;else->1f };playing=true;publish() }
  button("Unknown position / reset") { pos=if(pos<0)0 else PlaybackState.PLAYBACK_POSITION_UNKNOWN;publish() }
  button("Local / adjustable remote / fixed remote volume") {
   remoteMode=(remoteMode+1)%3
   if(remoteMode==0)session.setPlaybackToLocal(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
   else session.setPlaybackToRemote(object: VolumeProvider(if(remoteMode==1)VOLUME_CONTROL_RELATIVE else VOLUME_CONTROL_FIXED,20,10) {
    override fun onAdjustVolume(direction: Int) { actions++;setCurrentVolume((currentVolume+direction).coerceIn(0,20));publish() }
   });publish()
  }
 }
 override fun onDestroy() { session.release();super.onDestroy() }
}

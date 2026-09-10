package io.github.flippernowplaying.bridge

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import io.github.flippernowplaying.core.*

class BridgeService: Service() {
 companion object {
  @Volatile var status="STOPPED";private set
  @Volatile var preview="";private set
  @Volatile var players=listOf<String>();private set
  @Volatile private var instance: BridgeService?=null
  private val diagnostics=ArrayDeque<String>()
  @Synchronized fun report(): String = "Now Playing 1.1\nAndroid API ${Build.VERSION.SDK_INT}\n"+diagnostics.joinToString("\n")
  fun accessChanged(available: Boolean) { instance?.let { s -> s.h.post { if(available)s.media.start() else s.media.revoke() } } }
  fun settingsChanged() { instance?.let { s->s.h.post { s.media.refresh();s.publish(true) } } }
 }
 private lateinit var thread: HandlerThread;private lateinit var h: Handler;private lateinit var media: MediaSessionRepository
 private var ble: BleConnectionManager?=null;private val commands=CommandRouter();private val seq=ConnectionCounter()
 private var lastEpoch=-1L;private var lastRevision=-1L;private var armed=false
 private fun now()=SystemClock.elapsedRealtime()
 private fun notification(text: String): Notification {
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
  val stop=PendingIntent.getService(this,1,Intent(this,BridgeService::class.java).setAction("STOP"),PendingIntent.FLAG_IMMUTABLE)
  return Notification.Builder(this,"bridge").setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Now Playing").setContentText(text).setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).addAction(Notification.Action.Builder(null,"Stop",stop).build()).build()
 }
 private fun update(s: String) { status=s;synchronized(Companion) { diagnostics.addLast("${now()} $s");while(diagnostics.size>100)diagnostics.removeFirst() }
  if(armed) { try { getSystemService(NotificationManager::class.java).notify(1,notification(s)) } catch(_:SecurityException) { } }
 }
 override fun onCreate() {
  super.onCreate();thread=HandlerThread("NowPlayingActor").also { it.start() };h=Handler(thread.looper)
  media=MediaSessionRepository(this,h,{ full->publish(full) },{ update(it);if(it=="Counter exhausted")ble?.restart(it) });instance=this
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("bridge","Connection",NotificationManager.IMPORTANCE_LOW))
 }
 override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
  if(intent?.action=="STOP") { stopSelf();return START_NOT_STICKY }
  if(armed)return START_NOT_STICKY
  try {
   if(Build.VERSION.SDK_INT>=29)startForeground(1,notification("Starting"),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(1,notification("Starting"))
  } catch(_:SecurityException) { update("NEEDS_PERMISSION: open setup and grant Bluetooth");stopSelf();return START_NOT_STICKY }
  armed=true
  h.post {
   val address=getSharedPreferences("settings",MODE_PRIVATE).getString("device",null)
   if(address==null) { update("Select a Now Playing device in setup");stopSelf();return@post }
   media.start()
   ble=BleConnectionManager(this,h,address,{ update(it) },{
    commands.reset();seq.reset();lastEpoch=-1;lastRevision=-1;media.resetConnection();publish(true)
   },{ frame,received ->
    when(frame.type) {
     32->{ val epoch=media.sample().epoch
      val result=runCatching { commands.dispatch(frame,ble?.ready==true,epoch,received,now(),media::dispatch) }
      if(result.isFailure) { ble?.stop();update("Protocol error: reused or old command");return@BleConnectionManager }
      ble?.enqueue(33,{ Protocol.buffer(12).putInt(frame.id.toInt()).put(result.getOrThrow().toByte()).put(0).putShort(0).putInt(epoch.toInt()).array() })
     }
     48->if(now()-lastRequest>=1000) { lastRequest=now();publish(true) }
    }
   }).also { it.start() }
   h.postDelayed(periodic,10000);h.postDelayed(heartbeat,15000)
  }
  return START_NOT_STICKY
 }
 private var lastRequest=0L
 private fun nextSeq(): Long = seq.next()
 private fun publish(full: Boolean) {
  if(!armed)return
  val sample=try { media.sample() } catch(_:SecurityException) { media.revoke();return }
  preview=listOf(sample.title,sample.artist,sample.album).filterNotNull().joinToString("\n")
  players=media.observed()
  val snapshot=full||sample.epoch!=lastEpoch||sample.revision!=lastRevision
  lastEpoch=sample.epoch;lastRevision=sample.revision
  if(snapshot) {
   ble?.snapshot { sample.payload(nextSeq(),now(),true) }
   ble?.enqueue(17,{ sample.payload(nextSeq(),now(),false) })
   if(ble?.supportsArtwork==true&&sample.epoch>0) {
    val art=Artwork.payload(sample.epoch,sample.revision,media.artwork)
    ble?.enqueue(19,{ art })
   }
  } else ble?.enqueue(17,{ sample.payload(nextSeq(),now(),false) })
 }
 private val periodic=object: Runnable { override fun run() { if(!armed)return;media.refresh();publish(false);h.postDelayed(this,10000) } }
 private val heartbeat=object: Runnable { override fun run() { if(!armed)return;ble?.enqueue(64,{ Protocol.buffer(4).putInt(now().toInt()).array() });h.postDelayed(this,15000) } }
 override fun onDestroy() {
  instance=null;armed=false
  h.post { h.removeCallbacksAndMessages(null);ble?.stop();media.close();commands.reset();thread.quitSafely() }
  stopForeground(STOP_FOREGROUND_REMOVE);status="STOPPED";preview="";super.onDestroy()
 }
 override fun onBind(intent: Intent?): IBinder?=null
}

package io.github.flippernowplaying.bridge

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Dependency-free tests of the actual Android bitmap reader and callback lifecycle. */
class ArtworkInstrumentation: Instrumentation() {
 override fun onCreate(arguments: Bundle?) { super.onCreate(arguments);start() }
 override fun onStart() {
  val thread=HandlerThread("ArtworkInstrumentation").also { it.start() }
  val h=Handler(thread.looper);val reader=ArtworkReader(targetContext,h)
  var checks=0
  try {
   fun read(m: MediaMetadata?): ByteArray? {
    val latch=CountDownLatch(1);val result=AtomicReference<ByteArray?>()
    h.post { reader.read(m) { result.set(it);latch.countDown() } }
    check(latch.await(10,TimeUnit.SECONDS)) { "Artwork callback timed out" }
    return result.get()
   }
   val bitmap=Bitmap.createBitmap(90,90,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }
   val metadata=MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,bitmap).build()
   val bytes=read(metadata)!!
   check(bytes.size==270 && bytes.indices.all { (bytes[it].toInt() and 255)==if(it%6==5)31 else 255 })
   check(!bitmap.isRecycled);checks++
   val wide=Bitmap.createBitmap(90,45,Bitmap.Config.ARGB_8888).apply {
    eraseColor(Color.WHITE)
    for(y in 0 until 45)for(x in 22 until 67)setPixel(x,y,Color.BLACK)
   }
   val crop=read(MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_ART,wide).build())!!
   check(crop.contentEquals(bytes));check(!wide.isRecycled);checks++
   check(read(null)==null);checks++
   check(read(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI,"https://example.invalid/never-fetch.jpg").build())==null);checks++
   val latch=CountDownLatch(1);var obsolete=false
   h.post {
    reader.read(metadata) { obsolete=true }
    reader.read(null) { check(it==null);latch.countDown() }
   }
   check(latch.await(10,TimeUnit.SECONDS));check(!obsolete);checks++
   finish(Activity.RESULT_OK,Bundle().apply { putString("stream","PASS: $checks Android artwork checks (bitmap, crop, missing, no HTTP, stale callback)\n") })
  } catch(t:Throwable) {
   finish(Activity.RESULT_CANCELED,Bundle().apply { putString("stream","FAIL: ${t.stackTraceToString()}\n") })
  } finally {
   h.post { reader.close();thread.quitSafely() }
  }
 }
}

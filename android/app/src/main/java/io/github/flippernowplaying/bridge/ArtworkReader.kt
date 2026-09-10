package io.github.flippernowplaying.bridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaMetadata
import android.net.Uri
import android.os.Handler
import io.github.flippernowplaying.core.Artwork
import java.io.ByteArrayOutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** At most one running and one replacement request; results return on the media actor. */
class ArtworkReader(private val context: Context,private val handler: Handler) {
 private val worker=ThreadPoolExecutor(1,1,10,TimeUnit.SECONDS,ArrayBlockingQueue(1),
  { task -> Thread(task,"NowPlayingArtwork").apply { isDaemon=true } },ThreadPoolExecutor.DiscardOldestPolicy()).apply { allowCoreThreadTimeOut(true) }
 private var generation=0L
 fun invalidate() { generation++;worker.queue.clear() }
 fun close() { invalidate();worker.shutdownNow() }
 fun read(metadata: MediaMetadata?,result: (ByteArray?)->Unit) {
  invalidate();val token=generation
  worker.execute {
   val pixels=try { load(metadata) } catch(_:Exception) { null } catch(_:OutOfMemoryError) { null }
   handler.post { if(token==generation)result(pixels) }
  }
 }
 private fun load(m: MediaMetadata?): ByteArray? {
  if(m==null)return null
  for(key in listOf(MediaMetadata.METADATA_KEY_ALBUM_ART,MediaMetadata.METADATA_KEY_ART,MediaMetadata.METADATA_KEY_DISPLAY_ICON)) {
   val b=runCatching { m.getBitmap(key) }.getOrNull()?:continue
   val result=runCatching { encode(b) }.getOrNull()
   if(result!=null)return result
  }
  for(key in listOf(MediaMetadata.METADATA_KEY_ALBUM_ART_URI,MediaMetadata.METADATA_KEY_ART_URI,MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)) {
   val raw=m.getString(key)?:continue
   val uri=runCatching { Uri.parse(raw) }.getOrNull()?:continue
   // Never fetch HTTP(S), file paths or credentials; only a provider already granting access.
   if(uri.scheme!="content")continue
   val result=runCatching {
    val bytes=context.contentResolver.openInputStream(uri)?.use { stream ->
     val out=ByteArrayOutputStream();val chunk=ByteArray(8192)
     while(true) { val n=stream.read(chunk);if(n<0)break;require(out.size()+n<=4*1024*1024);out.write(chunk,0,n) }
     out.toByteArray()
    }?:return@runCatching null
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    val options=BitmapFactory.Options().apply { inPreferredConfig=Bitmap.Config.ARGB_8888;inSampleSize=Artwork.decodeSampleSize(bounds.outWidth,bounds.outHeight) }
    val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)?:return@runCatching null
    try { encode(bitmap) } finally { bitmap.recycle() }
   }.getOrNull()
   if(result!=null)return result
  }
  return null
 }
 private fun encode(source: Bitmap): ByteArray {
  require(!source.isRecycled&&source.width>0&&source.height>0)
  // Square album covers are normal; center crop other artwork without distorting it.
  val side=minOf(source.width,source.height)
  val scaled=Bitmap.createBitmap(Artwork.SIZE,Artwork.SIZE,Bitmap.Config.ARGB_8888)
  try {
   val left=(source.width-side)/2;val top=(source.height-side)/2
   Canvas(scaled).drawBitmap(source,Rect(left,top,left+side,top+side),Rect(0,0,Artwork.SIZE,Artwork.SIZE),Paint(Paint.FILTER_BITMAP_FLAG))
   val colors=IntArray(Artwork.SIZE*Artwork.SIZE)
   scaled.getPixels(colors,0,Artwork.SIZE,0,0,Artwork.SIZE,Artwork.SIZE)
   return Artwork.monochrome(colors)
  } finally { scaled.recycle() }
 }
}

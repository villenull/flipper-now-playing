package io.github.flippernowplaying.core

/** Fixed-size one-bit artwork. No Android dependencies or unbounded image buffers. */
object Artwork {
 const val SIZE=45
 const val STRIDE=6
 const val BYTES=270
 // Bayer ordered dithering is deterministic and preserves coarse midtone detail.
 private val bayer=intArrayOf(0,8,2,10,12,4,14,6,3,11,1,9,15,7,13,5)
 fun decodeSampleSize(width: Int,height: Int): Int {
  require(width in 1..16384&&height in 1..16384&&width.toLong()*height<=64000000)
  var sample=1
  while(maxOf(width,height)/sample>128)sample*=2
  return sample
 }
 fun monochrome(argb: IntArray): ByteArray {
  require(argb.size==SIZE*SIZE)
  val out=ByteArray(BYTES)
  for(y in 0 until SIZE)for(x in 0 until SIZE) {
   val c=argb[y*SIZE+x];val alpha=c ushr 24
   val gray=((c ushr 16 and 255)*77+(c ushr 8 and 255)*150+(c and 255)*29) ushr 8
   val whiteComposite=(gray*alpha+255*(255-alpha)+127)/255
   val threshold=bayer[(y%4)*4+x%4]*16+8
   if(whiteComposite<threshold)out[y*STRIDE+x/8]=(out[y*STRIDE+x/8].toInt() or (1 shl (x%8))).toByte()
  }
  return out
 }
 fun payload(epoch: Long,revision: Long,pixels: ByteArray?): ByteArray {
  require(epoch in 1..0xffffffffL&&revision in 1..0xffffffffL)
  require(pixels==null||pixels.size==BYTES)
  return Protocol.buffer(12+(pixels?.size?:0)).putInt(epoch.toInt()).putInt(revision.toInt())
   .put(if(pixels==null)0 else SIZE.toByte()).put(if(pixels==null)0 else SIZE.toByte())
   .put(if(pixels==null)0 else 1).put(0).apply { pixels?.let { put(it) } }.array().also { Protocol.validate(19,it) }
 }
}

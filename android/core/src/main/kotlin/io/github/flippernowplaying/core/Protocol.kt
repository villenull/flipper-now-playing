package io.github.flippernowplaying.core

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

object Protocol {
 const val SERVICE = "8f9a1000-6d8a-4f1b-a6e6-3b4c5d6e7f80"
 const val RX = "8f9a1001-6d8a-4f1b-a6e6-3b4c5d6e7f80"
 const val TX = "8f9a1002-6d8a-4f1b-a6e6-3b4c5d6e7f80"
 const val MAX_TIME = 315360000000L
 fun buffer(n: Int): ByteBuffer = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
 fun wrap(p: ByteArray): ByteBuffer = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN)
 fun u32(p: ByteArray, o: Int): Long = wrap(p).getInt(o).toLong() and 0xffffffffL
 fun u16(p: ByteArray, o: Int): Int = wrap(p).getShort(o).toInt() and 65535
 fun crc(p: ByteArray): Long = CRC32().also { it.update(p) }.value
 fun validate(t: Int, p: ByteArray) {
  require(p.size <= 768)
  fun size(n: Int) = require(p.size == n)
  fun byte(i: Int) = p[i].toInt() and 255
  when(t) {
   1,2 -> { size(12); require(u16(p,2)==768 && u16(p,4) in 20..128 && u16(p,6)==0 && u32(p,8)==1L)
    require(if(t==1) byte(0) in 1..byte(1) else (byte(0)==1&&byte(1)==0)||(byte(0)==0&&byte(1)==1)) }
   16,17 -> {
    require(if(t==16) p.size in 44..604 else p.size==36)
    val state=byte(12); val flags=byte(13); val speed=wrap(p).getShort(14).toInt()
    val pos=wrap(p).getLong(16); val dur=wrap(p).getLong(24); val caps=u16(p,32)
    require(u32(p,8)>0 && state in 0..6 && flags and 7==flags && caps and 31==caps && byte(34) in 0..1 && byte(35)==0)
    require(speed in -8000..8000 && (state==3||speed==0))
    require(pos in 0..MAX_TIME && dur in 0..MAX_TIME && (flags and 1!=0||pos==0L) && (flags and 2!=0||dur==0L) && (flags and 2==0||dur>0) && (flags and 3!=3||pos<=dur))
    require(if(state==0) u32(p,0)==0L&&u32(p,4)==0L&&flags==0&&caps==0 else u32(p,0)>0&&u32(p,4)>0)
    if(t==16) {
     val lengths=(0..3).map { u16(p,36+2*it) }; val max=listOf(192,128,192,48)
     require(lengths.sum()+44==p.size && lengths.indices.all { lengths[it]<=max[it] })
     require(p.drop(44).all { it.toInt() in 32..126 })
     require(state!=0||lengths.take(3).all { it==0 })
    }
   }
   18 -> { size(8); require(u32(p,0)>0&&u32(p,4)>0) }
   32 -> { size(12); require(u32(p,0)>0&&u32(p,4)>0&&byte(8) in 1..5&&byte(9) in 0..1&&(byte(9)==0||byte(8)>=4)&&u16(p,10) in 1..750) }
   33 -> { size(12); require(u32(p,0)>0&&byte(4) in 0..7&&byte(5)==0&&u16(p,6)==0) }
   48 -> size(0)
   64,65 -> size(4)
   126 -> { size(8); require(u16(p,4) in 1..6&&u16(p,6)==0) }
   127 -> { size(1); require(byte(0) in 1..3) }
   else -> error("Unknown type")
  }
 }
}
data class Frame(val type: Int,val session: Long,val id: Long,val payload: ByteArray) {
 fun encode(): ByteArray {
  require(session in 1..0xffffffffL && id in 1..0xffffffffL); Protocol.validate(type,payload)
  val b=Protocol.buffer(20+payload.size).put("FNP1".toByteArray()).put(1).put(type.toByte()).putShort(payload.size.toShort()).putInt(session.toInt()).putInt(id.toInt()).put(payload)
  b.putInt(Protocol.crc(b.array().copyOfRange(4,16+payload.size)).toInt()); return b.array()
 }
 companion object {
  fun decode(bytes: ByteArray): Frame {
   require(bytes.size in 20..788 && bytes.copyOfRange(0,4).contentEquals("FNP1".toByteArray()) && bytes[4]==1.toByte())
   val n=Protocol.u16(bytes,6); require(n<=768&&bytes.size==20+n)
   require(Protocol.crc(bytes.copyOfRange(4,16+n))==Protocol.u32(bytes,16+n))
   val f=Frame(bytes[5].toInt() and 255,Protocol.u32(bytes,8),Protocol.u32(bytes,12),bytes.copyOfRange(16,16+n))
   require(f.session>0&&f.id>0); Protocol.validate(f.type,f.payload); return f
  }
 }
}
class StreamDecoder {
 private val bytes=ByteArray(788); var used=0; private set
 var errors=0; private set
 private var first=0L; private var last=0L
 fun reset() { used=0 }
 fun expire(now: Long): Boolean {
  if(used>0&&(now-last>=5000||now-first>=15000)) { reset(); errors++; return true }; return false
 }
 private fun drop(n: Int) { bytes.copyInto(bytes,0,n,used); used-=n }
 fun feed(input: ByteArray,now: Long,receive: (Frame)->Unit) {
  expire(now)
  for(byte in input) {
   if(used==0) first=now
   last=now
   if(used==788) { drop(1); errors++ }
   bytes[used++]=byte
   while(used>=4) {
    if(!(bytes[0]==70.toByte()&&bytes[1]==78.toByte()&&bytes[2]==80.toByte()&&bytes[3]==49.toByte())) { drop(1); continue }
    if(used<16) break
    val n=Protocol.u16(bytes,6)
    if(bytes[4]!=1.toByte()||n>768) { drop(1); errors++; continue }
    if(used<20+n) break
    val f=runCatching { Frame.decode(bytes.copyOf(20+n)) }.getOrNull()
    if(f==null) { drop(1); errors++ } else { drop(20+n); receive(f) }
   }
  }
 }
}

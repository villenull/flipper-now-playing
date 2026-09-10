package io.github.flippernowplaying.core

import java.text.Normalizer
import kotlin.math.roundToInt

data class DisplayText(val text: String,val lossy: Boolean)
object TextNormalizer {
 fun normalize(raw: String?,limit: Int): DisplayText {
  val original=raw.orEmpty()
  var s=original.replace("ß","ss").replace("æ","ae").replace("Æ","AE").replace("œ","oe").replace("Œ","OE")
  s=s.replace('‘','\'').replace('’','\'').replace('“','"').replace('”','"').replace('–','-').replace('—','-').replace("…","...")
  s=Normalizer.normalize(s,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"")
  val b=StringBuilder()
  s.codePoints().forEach { cp -> b.append(if(Character.isWhitespace(cp)||Character.isISOControl(cp)) " " else if(cp in 32..126) cp.toChar().toString() else "?") }
  s=b.toString().trim().replace(Regex(" +")," ")
  if(s.length>limit) s=s.take(limit-3)+"..."
  return DisplayText(s,s!=original)
 }
}
data class MediaState(
 val epoch: Long=0,val revision: Long=0,val state: Int=0,val position: Long?=null,
 val duration: Long?=null,val speed: Float=0f,val anchor: Long=0,val capabilities: Int=0,
 val title: String?=null,val artist: String?=null,val album: String?=null,val app: String="Apple Music",val remaining: Boolean=false
) {
 fun projected(now: Long): Long? {
  val pos=position?.takeIf { it in 0..Protocol.MAX_TIME }?:return null
  val delta=if(state==3&&speed.isFinite()&&anchor>0) (now-anchor).coerceAtLeast(0)*speed.coerceIn(-8f,8f).toDouble() else 0.0
  return (pos+delta).coerceIn(0.0,(duration?.takeIf { it in 1..Protocol.MAX_TIME }?:Protocol.MAX_TIME).toDouble()).toLong()
 }
 fun payload(seq: Long,now: Long,snapshot: Boolean): ByteArray {
  require(seq in 1..0xffffffffL)
  val texts=listOf(title,artist,album,app).zip(listOf(192,128,192,48)).map { TextNormalizer.normalize(it.first,it.second) }
  val pos=if(state!=0) projected(now) else null
  val dur=if(state!=0) duration?.takeIf { it in 1..Protocol.MAX_TIME } else null
  val flags=(if(pos!=null) 1 else 0) or (if(dur!=null) 2 else 0) or (if(state!=0&&texts.any { it.lossy }) 4 else 0)
  val safeTexts=if(state==0) listOf("","","",texts[3].text) else texts.map { it.text }
  val n=if(snapshot) 44+safeTexts.sumOf { it.length } else 36
  val b=Protocol.buffer(n).putInt(epoch.toInt()).putInt(revision.toInt()).putInt(seq.toInt()).put(state.toByte()).put(flags.toByte())
  b.putShort((if(state==3&&speed.isFinite()) (speed.coerceIn(-8f,8f)*1000).roundToInt() else 0).toShort())
  b.putLong(pos?:0).putLong(dur?:0).putShort(capabilities.toShort()).put(if(remaining) 1 else 0).put(0)
  if(snapshot) { safeTexts.forEach { b.putShort(it.length.toShort()) }; safeTexts.forEach { b.put(it.toByteArray(Charsets.US_ASCII)) } }
  return b.array().also { Protocol.validate(if(snapshot) 16 else 17,it) }
 }
}
class CommandRouter {
 private data class Result(val bytes: ByteArray,var status: Int,val at: Long)
 private val cache=linkedMapOf<Long,Result>()
 var highWater=0L; private set
 fun reset() { cache.clear(); highWater=0 }
 fun dispatch(frame: Frame,ready: Boolean,epoch: Long,received: Long,now: Long,action: (Int)->Int): Int {
  Protocol.validate(32,frame.payload)
  cache.entries.removeAll { now-it.value.at>30000 }
  cache[frame.id]?.let { require(it.bytes.contentEquals(frame.payload)) { "Reused command ID" }; return it.status }
  require(frame.id>highWater) { "Old command ID" }; highWater=frame.id
  val p=frame.payload
  val result=Result(p.copyOf(),6,now); cache[frame.id]=result
  while(cache.size>32) cache.remove(cache.keys.first())
  result.status=when {
   !ready->7
   epoch==0L->1
   Protocol.u32(p,0)!=epoch->2
   now-received>=Protocol.u16(p,10)->4
   else->action(p[8].toInt())
  }
  return result.status
 }
}
/** Frames are selected and IDs allocated only at frame boundaries. */
class Publisher {
 data class Pending(val type: Int,val payload: ()->ByteArray,val sent: (Long)->Unit={})
 private val control=ArrayDeque<Pending>()
 private var snapshot: Pending?=null; private var state: Pending?=null; private var heartbeat: Pending?=null; private var artwork: Pending?=null
 fun clear() { control.clear(); snapshot=null; state=null; heartbeat=null; artwork=null }
 fun enqueue(p: Pending): Boolean {
  when(p.type) {
   16->{snapshot=p;state=null;artwork=null}
   17->state=p
   64->heartbeat=p
   19->artwork=p
   else->{ if(control.size==4) return false; control.addLast(p) }
  }; return true
 }
 fun next(): Pending? {
  if(control.isNotEmpty()) return control.removeFirst()
  snapshot?.let { snapshot=null; return it }
  state?.let { state=null; return it }
  heartbeat?.let { heartbeat=null; return it }
  artwork?.let { artwork=null; return it }
  return null
 }
}

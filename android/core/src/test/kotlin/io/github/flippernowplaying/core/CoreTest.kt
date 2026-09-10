package io.github.flippernowplaying.core
import kotlin.test.*
import java.io.File
class CoreTest {
 private fun golden(): List<ByteArray> = Regex("\\\"frame_hex\\\": \\\"([0-9a-f]+)\\\"").findAll(File("../../protocol/golden_vectors.json").readText()).map { m->m.groupValues[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray() }.toList()
 @Test fun goldenAndEverySplit() {
  val vectors=golden();assertEquals(16,vectors.size)
  for(bytes in vectors) {
   assertContentEquals(bytes,Frame.decode(bytes).encode())
   for(split in 0..bytes.size) { val d=StreamDecoder();val out=mutableListOf<Frame>();d.feed(bytes.copyOfRange(0,split),0,out::add);d.feed(bytes.copyOfRange(split,bytes.size),1,out::add);assertEquals(1,out.size);assertContentEquals(bytes,out.single().encode()) }
   val d=StreamDecoder();var count=0;bytes.forEach { d.feed(byteArrayOf(it),0) { count++ } };assertEquals(1,count)
  }
 }
 @Test fun crcAndMalformed() {
  assertEquals(0xcbf43926L,Protocol.crc("123456789".toByteArray()))
  val bytes=golden()[2]
  for(i in bytes.indices)for(bit in 0..7) { val bad=bytes.copyOf();bad[i]=(bad[i].toInt() xor (1 shl bit)).toByte();assertFails { Frame.decode(bad) } }
  val d=StreamDecoder();var count=0;d.feed(ByteArray(100000){0x7f},0) { count++ };assertTrue(d.used<=3)
  d.feed(bytes+bytes,1) {count++};assertEquals(2,count)
  d.feed(bytes.copyOf(19),2) {};assertTrue(d.expire(5002));assertEquals(0,d.used)
  val s=MediaState(1,1,3,1000,2000,1f,1,31).payload(1,1,false)
  s[35]=1;assertFails { Protocol.validate(17,s) }
 }
 @Test fun normalizeAndClock() {
  assertEquals("Creme - ss ae oe ?",TextNormalizer.normalize("Crème — ß æ œ 日",192).text)
  assertEquals("a b",TextNormalizer.normalize("a\n\tb",192).text)
  assertEquals("TTTTT...",TextNormalizer.normalize("T".repeat(20),8).text)
  val s=MediaState(1,1,3,1000,10000,2f,100,31)
  assertEquals(3000,s.projected(1100));assertEquals(1000,s.copy(state=2).projected(5000));assertEquals(0,s.copy(speed=-2f).projected(1100))
  assertEquals(10000,s.projected(90000));assertEquals(1000,s.copy(anchor=0).projected(90000));assertNull(s.copy(position=-1).projected(5))
  assertEquals(1000,s.copy(speed=Float.NaN).projected(5000))
 }
 private fun command(id: Long=1,cmd: Int=1,epoch: Long=1,ttl: Int=750)=Frame(32,99,id,Protocol.buffer(12).putInt(epoch.toInt()).putInt(1).put(cmd.toByte()).put(0).putShort(ttl.toShort()).array())
 @Test fun duplicateExpiryAndEpoch() {
  val r=CommandRouter();var calls=0
  fun invoke(f: Frame,ready: Boolean=true,epoch: Long=1,at: Long=0,now: Long=1)=r.dispatch(f,ready,epoch,at,now) { calls++;0 }
  assertEquals(0,invoke(command()));assertEquals(0,invoke(command()));assertEquals(1,calls)
  assertFails { invoke(command(cmd=3)) };assertEquals(1,calls)
  assertEquals(2,invoke(command(2,epoch=2)));assertEquals(4,invoke(command(3),now=751));assertEquals(7,invoke(command(4),ready=false));assertEquals(1,invoke(command(5),epoch=0))
  for(i in 6L..40L)invoke(command(i))
  assertFails { invoke(command(1)) }
  r.reset();assertEquals(0,invoke(command()))
 }
 @Test fun publisherCoalescingAndPriority() {
  val p=Publisher();p.enqueue(Publisher.Pending(17,{byteArrayOf(1)}));p.enqueue(Publisher.Pending(16,{byteArrayOf(2)}));p.enqueue(Publisher.Pending(16,{byteArrayOf(3)}))
  p.enqueue(Publisher.Pending(33,{byteArrayOf(4)}));assertEquals(33,p.next()?.type);assertContentEquals(byteArrayOf(3),p.next()?.payload?.invoke());assertNull(p.next())
  repeat(4) { assertTrue(p.enqueue(Publisher.Pending(33,{byteArrayOf()}))) };assertFalse(p.enqueue(Publisher.Pending(33,{byteArrayOf()})))
  p.clear();assertNull(p.next())
 }
 @Test fun operationGenerationTimeoutAndOrdering() {
  val gate=GattOperationGate();val first=gate.begin(1,"discover",100,10000)
  assertFails { gate.begin(1,"write",101,5000) }
  assertFalse(gate.complete(0,"discover"));assertFalse(gate.complete(1,"write"));assertFalse(gate.expired(first,10099))
  assertTrue(gate.expired(first,10100));gate.reset();assertFalse(gate.complete(1,"discover"))
  gate.begin(2,"cccd",20000,5000);assertFalse(gate.expired(first,99999));assertFalse(gate.complete(1,"cccd"));assertTrue(gate.complete(2,"cccd"))
  assertFalse(gate.complete(2,"cccd"));gate.begin(2,"write",20001,5000);assertTrue(gate.complete(2,"write"))
 }
 @Test fun stablePlayerSelection() {
  val list=listOf("apple","other")
  assertEquals("apple",PlayerSelector.choose(list,"apple") { true })
  assertEquals("other",PlayerSelector.choose(list,"apple") { it=="other" })
  assertEquals("apple",PlayerSelector.choose(list,"apple") { false })
  assertNull(PlayerSelector.choose(emptyList<String>(),"apple") { true })
  assertEquals("apple",PlayerSelector.choose(listOf("apple"),"other") { true })
 }
 @Test fun countersNeverWrap() {
  val c=ConnectionCounter(0xfffffffeL);assertEquals(0xffffffffL,c.next());assertFailsWith<CounterExhausted> { c.next() };c.reset();assertEquals(1,c.next())
 }
 @Test fun productionStateSerializerMatchesApprovedFixture() {
  val state=MediaState(1,1,3,102000,248000,1f,100,31,"Get Lucky","Daft Punk","Random Access Memories","Apple Music")
  assertContentEquals(golden()[2],Frame(16,0x12345678,2,state.payload(1,100,true)).encode())
  assertContentEquals(golden()[3],Frame(17,0x12345678,3,state.copy(state=2,speed=0f,remaining=true).payload(2,100,false)).encode())
  assertContentEquals(golden()[5],Frame(16,0x12345678,5,MediaState().payload(4,100,true)).encode())
 }
}

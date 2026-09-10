package io.github.flippernowplaying.core
import java.io.File
import kotlin.test.*
class ArtworkTest {
 private fun golden()=Regex("\"frame_hex\": \"([0-9a-f]+)\"").findAll(File("../../protocol/artwork_golden_vectors.json").readText()).map { it.groupValues[1].chunked(2).map { n->n.toInt(16).toByte() }.toByteArray() }.toList()
 @Test fun extensionGoldenEverySplit() {
  for(b in golden()) {
   assertContentEquals(b,Frame.decode(b).encode())
   for(i in 0..b.size) { val decoder=StreamDecoder();val frames=mutableListOf<Frame>();decoder.feed(b.copyOfRange(0,i),0,frames::add);decoder.feed(b.copyOfRange(i,b.size),1,frames::add);assertEquals(1,frames.size);assertContentEquals(b,frames.single().encode()) }
  }
 }
 @Test fun whiteBlackTransparencyAndPadding() {
  val black=Artwork.monochrome(IntArray(2025){0xff000000.toInt()})
  val white=Artwork.monochrome(IntArray(2025){0xffffffff.toInt()})
  assertContentEquals(ByteArray(270),white)
  assertContentEquals(white,Artwork.monochrome(IntArray(2025)))
  assertContentEquals(Frame.decode(golden()[4]).payload,Artwork.payload(1,1,black))
  assertContentEquals(Frame.decode(golden()[5]).payload,Artwork.payload(1,1,white))
  assertContentEquals(Frame.decode(golden()[3]).payload,Artwork.payload(1,1,null))
  assertFails { Artwork.monochrome(IntArray(2024)) }
  assertFails { Artwork.payload(0,1,white) }
  assertFails { Artwork.payload(1,1,ByteArray(269)) }
 }
 @Test fun boundedDecodeSamplingAndDither() {
  assertEquals(1,Artwork.decodeSampleSize(45,45));assertEquals(16,Artwork.decodeSampleSize(2048,1024))
  assertEquals(128,Artwork.decodeSampleSize(16384,1))
  assertFails { Artwork.decodeSampleSize(0,45) };assertFails { Artwork.decodeSampleSize(16384,16384) }
  val gray=Artwork.monochrome(IntArray(2025){0xff808080.toInt()})
  assertEquals(1012,gray.sumOf { Integer.bitCount(it.toInt() and 255) })
 }
 @Test fun malformedArtCannotDecode() {
  val p=Frame.decode(golden()[2]).payload
  for((offset,value) in listOf(8 to 44,9 to 46,10 to 2,11 to 1,17 to 128)) {
   val bad=p.copyOf();bad[offset]=value.toByte();assertFails { Protocol.validate(19,bad) }
  }
 }
 @Test fun artworkIsCoalescedAndBelowControls() {
  val p=Publisher()
  p.enqueue(Publisher.Pending(19,{byteArrayOf(1)}));p.enqueue(Publisher.Pending(19,{byteArrayOf(2)}))
  p.enqueue(Publisher.Pending(33,{byteArrayOf()}));p.enqueue(Publisher.Pending(17,{byteArrayOf()}))
  assertEquals(33,p.next()?.type);assertEquals(17,p.next()?.type);assertContentEquals(byteArrayOf(2),p.next()?.payload?.invoke());assertNull(p.next())
  p.enqueue(Publisher.Pending(19,{byteArrayOf(3)}));p.enqueue(Publisher.Pending(16,{byteArrayOf()}))
  assertEquals(16,p.next()?.type);assertNull(p.next())
  p.enqueue(Publisher.Pending(19,{byteArrayOf(3)}));p.clear();assertNull(p.next())
 }
}

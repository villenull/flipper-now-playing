import binascii
import json
from pathlib import Path
import random
import struct
import unittest

from reference_codec import (
    CHAR_MAX, Frame, MAGIC, MAX_FRAME, MAX_PAYLOAD, ProtocolError, StreamDecoder,
    TYPES, crc32, fragments, golden_vectors, parse_snapshot, snapshot_payload,
    state_payload, validate_payload,
)


class ReferenceCodecTests(unittest.TestCase):
    def setUp(self):
        self.raw = Frame(TYPES["SNAPSHOT"], 0x12345678, 2, snapshot_payload()).encode()

    def test_crc_standard_check(self):
        self.assertEqual(crc32(b"123456789"), 0xCBF43926)
        self.assertEqual(crc32(b""), 0)

    def test_header_layout(self):
        raw = Frame(TYPES["REQUEST_SNAPSHOT"], 0x12345678, 5, b"").encode()
        self.assertEqual(raw[:16].hex(), "464e5031013000007856341205000000")
        self.assertEqual(len(raw), 20)

    def test_fixed_state_offsets(self):
        p = state_payload()
        self.assertEqual(len(p), 36)
        self.assertEqual(struct.unpack_from("<Q", p, 16)[0], 102000)
        self.assertEqual(struct.unpack_from("<Q", p, 24)[0], 248000)
        self.assertEqual(struct.unpack_from("<H", p, 32)[0], 31)

    def test_snapshot_roundtrip(self):
        f = Frame.decode(self.raw)
        s = parse_snapshot(f.payload)
        self.assertEqual(s["title"], "Get Lucky")
        self.assertEqual(s["album"], "Random Access Memories")
        self.assertEqual(f.encode(), self.raw)

    def test_all_golden_roundtrips(self):
        for v in golden_vectors():
            with self.subTest(v=v["name"]):
                raw = bytes.fromhex(v["frame_hex"])
                self.assertEqual(Frame.decode(raw).encode(), raw)
                self.assertEqual(b"".join(bytes.fromhex(x) for x in v["fragments_20_hex"]), raw)

    def test_committed_vectors_unchanged(self):
        p = Path(__file__).with_name("golden_vectors.json")
        self.assertEqual(json.loads(p.read_text())["vectors"], golden_vectors())

    def test_every_split_point(self):
        for i in range(len(self.raw)+1):
            dec = StreamDecoder()
            out = dec.feed(self.raw[:i]) + dec.feed(self.raw[i:])
            self.assertEqual([x.encode() for x in out], [self.raw])

    def test_single_byte_fragments(self):
        dec = StreamDecoder()
        out=[]
        for part in fragments(self.raw, 1):
            out.extend(dec.feed(part))
        self.assertEqual(len(out), 1)

    def test_multiple_frames_one_input(self):
        dec = StreamDecoder()
        self.assertEqual(len(dec.feed(self.raw*10)), 10)

    def test_random_chunking(self):
        rng = random.Random(20260909)
        data = b"".join(bytes.fromhex(v["frame_hex"]) for v in golden_vectors())
        expected = [v["frame_hex"] for v in golden_vectors()]
        for _ in range(300):
            dec, out, i = StreamDecoder(), [], 0
            while i < len(data):
                n = rng.randint(1, 128)
                out.extend(dec.feed(data[i:i+n]))
                i += n
            self.assertEqual([f.encode().hex() for f in out], expected)
            self.assertLessEqual(dec.high_water, MAX_FRAME)

    def test_noise_resynchronization(self):
        dec = StreamDecoder()
        out = dec.feed(b"junkFNFNPxx" + self.raw)
        self.assertEqual(len(out), 1)

    def test_corrupt_crc_then_valid(self):
        bad = bytearray(self.raw); bad[-1] ^= 1
        dec = StreamDecoder()
        self.assertEqual(len(dec.feed(bytes(bad)+self.raw)), 1)
        self.assertGreater(dec.errors, 0)

    def test_oversize_length_then_valid(self):
        head = struct.pack("<4sBBHII", MAGIC, 1, TYPES["STATE"], 65535, 1, 1)
        dec = StreamDecoder()
        self.assertEqual(len(dec.feed(head+self.raw)), 1)

    def test_all_single_bit_corruptions(self):
        # Independent corruption check: every bit in a valid frame must be detected.
        for i in range(len(self.raw)):
            for bit in range(8):
                bad = bytearray(self.raw); bad[i] ^= 1 << bit
                with self.assertRaises(ProtocolError): Frame.decode(bytes(bad))

    def test_truncated_frame_waits_and_reset(self):
        dec = StreamDecoder()
        self.assertFalse(dec.feed(self.raw[:-1]))
        dec.reset()
        self.assertEqual(len(dec.feed(self.raw)), 1)

    def test_unknown_type_rejected(self):
        with self.assertRaises(ProtocolError): Frame(0x55,1,1,b"").encode()

    def test_zero_session_or_message_rejected(self):
        for s,m in ((0,1),(1,0)):
            with self.assertRaises(ProtocolError): Frame(TYPES["PING"],s,m,b"0000").encode()

    def test_text_character_limits(self):
        for t in ("é", "bad\nline", "bad\0text", "x"*193):
            with self.assertRaises(ProtocolError): snapshot_payload(title=t)

    def test_maximum_text(self):
        p = snapshot_payload(title="T"*192,artist="A"*128,album="B"*192,app="P"*48)
        self.assertEqual(len(p),604)
        self.assertLessEqual(len(p),MAX_PAYLOAD)

    def test_state_value_validation(self):
        invalid = [dict(flags=128),dict(state=2,speed_milli=1000),dict(speed_milli=8001),
                   dict(duration_ms=0),dict(position_ms=248001),dict(display_mode=2),
                   dict(state_seq=0),dict(player_epoch=0),dict(capabilities=32)]
        for kw in invalid:
            with self.subTest(kw=kw), self.assertRaises(ProtocolError): state_payload(**kw)

    def test_no_session(self):
        kw=dict(player_epoch=0,track_revision=0,state_seq=1,state=0,flags=0,
                speed_milli=0,position_ms=0,duration_ms=0,capabilities=0)
        p = snapshot_payload(title="",artist="",album="",**kw)
        self.assertEqual(parse_snapshot(p)["state"],0)
        with self.assertRaises(ProtocolError): snapshot_payload(**kw)

    def test_bad_command_repeat(self):
        for cmd,origin,ttl in ((1,1,300),(6,0,300),(4,0,0),(4,0,751)):
            with self.assertRaises(ProtocolError):
                validate_payload(TYPES["COMMAND"],struct.pack("<IIBBH",1,1,cmd,origin,ttl))

    def test_unknown_position_duration(self):
        p=state_payload(flags=0,position_ms=0,duration_ms=0)
        self.assertEqual(len(p),36)

    def test_fragment_bounds(self):
        for n in (0,129):
            with self.assertRaises(ProtocolError): fragments(self.raw,n)
        self.assertTrue(all(len(x)<=20 for x in fragments(self.raw,20)))

    def test_magic_in_text_not_delimiter(self):
        raw=Frame(TYPES["SNAPSHOT"],1,1,snapshot_payload(title="FNP1 in the title")).encode()
        self.assertEqual(len(StreamDecoder().feed(raw)),1)

    def test_reserved_state_byte_rejected_with_valid_crc(self):
        p=bytearray(state_payload()); p[35]=1
        head=struct.pack("<4sBBHII",MAGIC,1,TYPES["STATE"],len(p),1,1)
        raw=head+bytes(p)+struct.pack("<I",binascii.crc32(head[4:]+p)&0xffffffff)
        with self.assertRaises(ProtocolError): Frame.decode(raw)

    def test_large_noise_buffer_bound(self):
        dec=StreamDecoder()
        self.assertFalse(dec.feed(b"z"*100000))
        self.assertLessEqual(dec.high_water,MAX_FRAME)


if __name__ == "__main__":
    unittest.main(verbosity=2)

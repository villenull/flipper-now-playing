#!/usr/bin/env python3
"""FNP/1 reference syntax codec. No BLE, Android APIs, or production session state.

The normative contract is docs/05_PROTOCOL.md. Production C/Kotlin implementations
must match the committed golden vectors independently, not execute this module.
Only the stream assembly buffer is bounded here; callers should consume returned
frames promptly. The embedded implementation should emit frames via a callback.
"""
from __future__ import annotations

from dataclasses import dataclass
import binascii
import json
from pathlib import Path
import struct
from typing import Any

MAGIC = b"FNP1"
VERSION = 1
MAX_PAYLOAD = 768
MAX_FRAME = 16 + MAX_PAYLOAD + 4
CHAR_MAX = 128
MAX_MEDIA_MS = 315_360_000_000
SERVICE_UUID = "8f9a1000-6d8a-4f1b-a6e6-3b4c5d6e7f80"
RX_UUID = "8f9a1001-6d8a-4f1b-a6e6-3b4c5d6e7f80"
TX_UUID = "8f9a1002-6d8a-4f1b-a6e6-3b4c5d6e7f80"
TYPES = {
    "HELLO": 0x01, "HELLO_ACK": 0x02,
    "SNAPSHOT": 0x10, "STATE": 0x11, "SNAPSHOT_APPLIED": 0x12,
    "COMMAND": 0x20, "COMMAND_RESULT": 0x21,
    "REQUEST_SNAPSHOT": 0x30, "PING": 0x40, "PONG": 0x41,
    "ERROR": 0x7E, "BYE": 0x7F,
}
NAMES = {v: k for k, v in TYPES.items()}
HEADER = struct.Struct("<4sBBHII")
STATE_FIELDS = struct.Struct("<IIIBBhQQHBB")
LENGTHS = struct.Struct("<HHHH")
HELLO_FIELDS = struct.Struct("<BBHHHI")
TEXT_FIELDS = (("title", 192), ("artist", 128), ("album", 192), ("app", 48))


class ProtocolError(ValueError):
    """Input violates the FNP/1 syntactic or field-value contract."""


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ProtocolError(message)


def _uint(value: int, bits: int, name: str, nonzero: bool = False) -> int:
    _require(isinstance(value, int) and not isinstance(value, bool), f"{name}: integer required")
    _require((1 if nonzero else 0) <= value < 1 << bits, f"{name}: out of range")
    return value


def crc32(data: bytes) -> int:
    """CRC-32/ISO-HDLC, result in the conventional unsigned representation."""
    return binascii.crc32(data) & 0xFFFFFFFF


def state_payload(*, player_epoch: int = 1, track_revision: int = 1,
                  state_seq: int = 1, state: int = 3, flags: int = 3,
                  speed_milli: int = 1000, position_ms: int = 102000,
                  duration_ms: int = 248000, capabilities: int = 31,
                  display_mode: int = 0) -> bytes:
    vals = (player_epoch, track_revision, state_seq, state, flags, speed_milli,
            position_ms, duration_ms, capabilities, display_mode, 0)
    try:
        payload = STATE_FIELDS.pack(*vals)
    except (struct.error, OverflowError) as exc:
        raise ProtocolError("state field not representable") from exc
    validate_payload(TYPES["STATE"], payload)
    return payload


def snapshot_payload(*, title: str = "Get Lucky", artist: str = "Daft Punk",
                     album: str = "Random Access Memories", app: str = "Apple Music",
                     **state: Any) -> bytes:
    texts = []
    for (name, maximum), text in zip(TEXT_FIELDS, (title, artist, album, app)):
        try:
            value = text.encode("ascii", errors="strict")
        except (UnicodeError, AttributeError) as exc:
            raise ProtocolError(f"{name}: printable display ASCII required") from exc
        _require(len(value) <= maximum, f"{name}: over byte limit")
        _require(all(32 <= b <= 126 for b in value), f"{name}: control character")
        texts.append(value)
    payload = state_payload(**state) + LENGTHS.pack(*(len(t) for t in texts)) + b"".join(texts)
    validate_payload(TYPES["SNAPSHOT"], payload)
    return payload


def parse_state(payload: bytes) -> dict[str, int]:
    _require(len(payload) >= STATE_FIELDS.size, "state too short")
    keys = ("player_epoch", "track_revision", "state_seq", "state", "flags",
            "speed_milli", "position_ms", "duration_ms", "capabilities", "display_mode", "reserved")
    out = dict(zip(keys, STATE_FIELDS.unpack_from(payload)))
    _require(out["state_seq"] > 0, "state_seq must be nonzero")
    _require(0 <= out["state"] <= 6, "unknown playback state")
    _require(out["flags"] & ~7 == 0, "unknown state flags")
    _require(out["capabilities"] & ~31 == 0, "unknown capabilities")
    _require(out["display_mode"] in (0, 1) and out["reserved"] == 0, "state reserved/mode")
    _require(-8000 <= out["speed_milli"] <= 8000, "speed range")
    _require(out["state"] == 3 or out["speed_milli"] == 0, "nonplaying speed must be zero")
    for name, flag in (("position_ms", 1), ("duration_ms", 2)):
        _require(out[name] <= MAX_MEDIA_MS, f"{name}: exceeds safety bound")
        _require(bool(out["flags"] & flag) or out[name] == 0, f"{name}: invalid must encode zero")
    _require(not(out["flags"] & 2) or out["duration_ms"] > 0, "valid duration must be positive")
    _require((out["flags"] & 3) != 3 or out["position_ms"] <= out["duration_ms"], "position exceeds duration")
    if out["state"] == 0:
        _require(out["player_epoch"] == out["track_revision"] == out["flags"] == out["capabilities"] == 0,
                 "NO_SESSION must clear epoch, revision, flags, capabilities")
    else:
        _require(out["player_epoch"] > 0 and out["track_revision"] > 0, "live session needs epoch/revision")
    return out


def parse_snapshot(payload: bytes) -> dict[str, Any]:
    _require(len(payload) >= 44, "snapshot too short")
    out: dict[str, Any] = parse_state(payload)
    lengths = LENGTHS.unpack_from(payload, 36)
    _require(44 + sum(lengths) == len(payload), "snapshot length mismatch")
    pos = 44
    for (name, maximum), length in zip(TEXT_FIELDS, lengths):
        _require(length <= maximum, f"{name}: exceeds byte limit")
        raw = payload[pos:pos + length]
        _require(all(32 <= b <= 126 for b in raw), f"{name}: nonprintable ASCII")
        out[name] = raw.decode("ascii")
        pos += length
    if out["state"] == 0:
        _require(not any(out[n] for n in ("title", "artist", "album")), "NO_SESSION has track text")
    return out


def validate_payload(kind: int, payload: bytes) -> None:
    _require(kind in NAMES, "unknown message type")
    _require(len(payload) <= MAX_PAYLOAD, "payload too large")
    if kind == TYPES["SNAPSHOT"]:
        parse_snapshot(payload)
    elif kind == TYPES["STATE"]:
        _require(len(payload) == 36, "STATE length")
        parse_state(payload)
    elif kind in (TYPES["HELLO"], TYPES["HELLO_ACK"]):
        _require(len(payload) == 12, "HELLO length")
        a, b, maximum, rx_cap, reserved, features = HELLO_FIELDS.unpack(payload)
        _require(maximum == MAX_PAYLOAD and 20 <= rx_cap <= CHAR_MAX, "HELLO capacity")
        _require(reserved == 0 and features == 1, "HELLO reserved/features")
        if kind == TYPES["HELLO"]:
            _require(1 <= a <= b <= 255, "HELLO version range")
        else:
            _require((b == 0 and a == 1) or (b == 1 and a == 0), "HELLO_ACK version/status")
    elif kind == TYPES["SNAPSHOT_APPLIED"]:
        _require(len(payload) == 8, "SNAPSHOT_APPLIED length")
        ref, seq = struct.unpack("<II", payload)
        _require(ref > 0 and seq > 0, "SNAPSHOT_APPLIED IDs")
    elif kind == TYPES["COMMAND"]:
        _require(len(payload) == 12, "COMMAND length")
        epoch, rev, cmd, origin, ttl = struct.unpack("<IIBBH", payload)
        _require(epoch > 0 and rev > 0, "COMMAND player/revision")
        _require(1 <= cmd <= 5 and origin in (0, 1), "COMMAND enum")
        _require(origin == 0 or cmd in (4, 5), "repeat is volume-only")
        _require(1 <= ttl <= 750, "COMMAND ttl")
    elif kind == TYPES["COMMAND_RESULT"]:
        _require(len(payload) == 12, "COMMAND_RESULT length")
        ref, status, detail, reserved, _epoch = struct.unpack("<IBBHI", payload)
        _require(ref > 0 and status <= 7 and detail == 0 and reserved == 0, "COMMAND_RESULT fields")
    elif kind == TYPES["REQUEST_SNAPSHOT"]:
        _require(len(payload) == 0, "REQUEST_SNAPSHOT length")
    elif kind in (TYPES["PING"], TYPES["PONG"]):
        _require(len(payload) == 4, "PING/PONG length")
    elif kind == TYPES["ERROR"]:
        _require(len(payload) == 8, "ERROR length")
        _ref, code, reserved = struct.unpack("<IHH", payload)
        _require(1 <= code <= 6 and reserved == 0, "ERROR fields")
    elif kind == TYPES["BYE"]:
        _require(len(payload) == 1 and payload[0] in (1, 2, 3), "BYE fields")


@dataclass(frozen=True)
class Frame:
    kind: int
    session: int
    message_id: int
    payload: bytes

    def encode(self) -> bytes:
        _uint(self.session, 32, "session", True)
        _uint(self.message_id, 32, "message_id", True)
        validate_payload(self.kind, self.payload)
        head = HEADER.pack(MAGIC, VERSION, self.kind, len(self.payload), self.session, self.message_id)
        covered = head[4:] + self.payload
        return head + self.payload + struct.pack("<I", crc32(covered))

    @classmethod
    def decode(cls, data: bytes) -> "Frame":
        _require(20 <= len(data) <= MAX_FRAME, "frame size")
        magic, version, kind, length, session, message_id = HEADER.unpack_from(data)
        _require(magic == MAGIC and version == VERSION, "magic/version")
        _require(length <= MAX_PAYLOAD and len(data) == 20 + length, "frame length")
        expected = struct.unpack_from("<I", data, 16 + length)[0]
        _require(crc32(data[4:16 + length]) == expected, "CRC mismatch")
        _uint(session, 32, "session", True)
        _uint(message_id, 32, "message_id", True)
        payload = bytes(data[16:16 + length])
        validate_payload(kind, payload)
        return cls(kind, session, message_id, payload)


def fragments(frame: bytes, value_size: int = 20) -> list[bytes]:
    _require(1 <= value_size <= CHAR_MAX, "fragment value size")
    return [frame[i:i + value_size] for i in range(0, len(frame), value_size)]


class StreamDecoder:
    """Single-direction bounded byte-stream reassembly; semantic session checks are external.

    Call reset() on disconnect, overflow, or the protocol's incomplete-frame timeout.
    errors is diagnostic only; malformed bytes never cause a returned partial frame.
    """
    def __init__(self) -> None:
        self.buffer = bytearray()
        self.errors = 0
        self.high_water = 0

    def reset(self) -> None:
        self.buffer.clear()

    def feed(self, incoming: bytes) -> list[Frame]:
        output = []
        for b in incoming:
            self.buffer.append(b)
            self.high_water = max(self.high_water, len(self.buffer))
            while True:
                start = self.buffer.find(MAGIC)
                if start < 0:
                    if len(self.buffer) > 3:
                        del self.buffer[:-3]
                    break
                if start:
                    del self.buffer[:start]
                if len(self.buffer) < 16:
                    break
                _magic, version, _kind, length, _session, _msg = HEADER.unpack_from(self.buffer)
                if version != VERSION or length > MAX_PAYLOAD:
                    self.errors += 1
                    del self.buffer[0]
                    continue
                total = 20 + length
                if len(self.buffer) < total:
                    break
                candidate = bytes(self.buffer[:total])
                try:
                    decoded = Frame.decode(candidate)
                except ProtocolError:
                    self.errors += 1
                    del self.buffer[0]
                else:
                    del self.buffer[:total]
                    output.append(decoded)
            assert len(self.buffer) <= MAX_FRAME
        return output


def golden_vectors() -> list[dict[str, Any]]:
    session = 0x12345678
    no_session = dict(player_epoch=0, track_revision=0, state_seq=4, state=0,
                      flags=0, speed_milli=0, position_ms=0, duration_ms=0, capabilities=0)
    definitions = [
        ("hello", "HELLO", 1, HELLO_FIELDS.pack(1, 1, 768, 128, 0, 1)),
        ("hello_ack", "HELLO_ACK", 1, HELLO_FIELDS.pack(1, 0, 768, 128, 0, 1)),
        ("approved_demo_snapshot", "SNAPSHOT", 2, snapshot_payload()),
        ("paused_remaining", "STATE", 3, state_payload(state_seq=2, state=2, speed_milli=0, display_mode=1)),
        ("unknown_duration", "STATE", 4, state_payload(state_seq=3, flags=1, duration_ms=0)),
        ("no_player", "SNAPSHOT", 5, snapshot_payload(title="", artist="", album="", **no_session)),
        ("snapshot_applied", "SNAPSHOT_APPLIED", 2, struct.pack("<II", 2, 1)),
        ("play_pause", "COMMAND", 3, struct.pack("<IIBBH", 1, 1, 1, 0, 750)),
        ("held_volume_up", "COMMAND", 4, struct.pack("<IIBBH", 1, 1, 4, 1, 300)),
        ("command_dispatched", "COMMAND_RESULT", 6, struct.pack("<IBBHI", 3, 0, 0, 0, 1)),
        ("request_snapshot", "REQUEST_SNAPSHOT", 5, b""),
        ("ping", "PING", 7, struct.pack("<I", 0xA1B2C3D4)),
        ("pong", "PONG", 6, struct.pack("<I", 0xA1B2C3D4)),
        ("semantic_error", "ERROR", 8, struct.pack("<IHH", 99, 2, 0)),
        ("bye_flipper_exit", "BYE", 7, b"\x02"),
        ("maximum_text_snapshot", "SNAPSHOT", 9,
         snapshot_payload(title="T"*192, artist="A"*128, album="B"*192, app="P"*48,
                          state_seq=5, flags=7)),
    ]
    out = []
    for name, type_name, mid, payload in definitions:
        frame = Frame(TYPES[type_name], session, mid, payload).encode()
        out.append(dict(name=name, type=type_name, session=session, message_id=mid,
                        payload_length=len(payload), frame_length=len(frame),
                        payload_hex=payload.hex(), frame_hex=frame.hex(),
                        fragments_20_hex=[part.hex() for part in fragments(frame, 20)],
                        fragments_128_hex=[part.hex() for part in fragments(frame, 128)]))
    return out


def write_vectors(path: Path) -> None:
    path.write_text(json.dumps({"protocol": "FNP/1", "vectors": golden_vectors()}, indent=2)+"\n")


if __name__ == "__main__":
    import argparse
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--write-vectors", type=Path, help="Regenerate deterministic reference fixtures")
    args = ap.parse_args()
    if args.write_vectors:
        write_vectors(args.write_vectors)
    else:
        print(json.dumps({"protocol": "FNP/1", "vectors": golden_vectors()}, indent=2))

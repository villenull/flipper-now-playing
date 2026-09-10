# 05 — FNP/1 normative protocol

**This document is the interoperability contract.** Values in `protocol/constants.json` mirror it. The reference codec and golden vectors exercise syntax; production C and Kotlin must implement the contract independently. State-machine, permissions, and hardware behavior are additional requirements beyond the codec.

## 5.1 GATT identifiers and direction
| Identifier | Canonical UUID / behavior |
|---|---|
| Primary service | `8f9a1000-6d8a-4f1b-a6e6-3b4c5d6e7f80` |
| RX, phone → Flipper | `8f9a1001-6d8a-4f1b-a6e6-3b4c5d6e7f80`; WRITE with response; max value 128 bytes. |
| TX, Flipper → phone | `8f9a1002-6d8a-4f1b-a6e6-3b4c5d6e7f80`; INDICATE; max value 128 bytes. |
| Standard CCCD | `00002902-0000-1000-8000-00805f9b34fb`; write bytes `02 00` to enable indications. |

These UUIDs are project-defined, not adopted Bluetooth standard services. Secure the link and require authenticated RX access. The helper must finish subscription before sending HELLO. Flipper sends only 20 bytes per indication in v1. Phone writes start at 20 bytes and may increase to `min(observed_mtu-3, 128, peer_rx_value_max)` after successful negotiation/handshake. A negotiated large MTU is an optimization, not a requirement.

Treat characteristic values as **chunks of a byte stream**, not whole application messages. A message can span many values. Multiple complete messages can arrive in one parser feed. There is one independent stream in each direction; logical frames cannot interleave within a direction. Application frames have their own lengths and checksum.

## 5.2 Frame format
All multibyte integers use little-endian. CRC and payload lengths are not native C structure layouts.

| Byte offset | Size | Field |
|---:|---:|---|
| 0 | 4 | Magic ASCII `FNP1`: `46 4E 50 31`. |
| 4 | 1 | Frame version, `1`. |
| 5 | 1 | Message type. |
| 6 | 2 | Payload length, 0…768. |
| 8 | 4 | Connection session ID, nonzero. |
| 12 | 4 | Sender message ID, nonzero. |
| 16 | payload length | Message payload. |
| 16 + payload length | 4 | CRC-32/ISO-HDLC over bytes **4 through 15 + payload length**, inclusive. |

The magic is excluded from CRC; version, type, length, session, message ID, and payload are included. CRC uses reflected polynomial `0xEDB88320`, initial register `0xFFFFFFFF`, final XOR `0xFFFFFFFF`, reflected input/output. Standard check: ASCII `123456789` produces `0xCBF43926`. Transmit the final CRC little-endian. Python `binascii.crc32(covered_bytes) & 0xffffffff` produces the same conventional result.

Minimum frame is 20 bytes. Maximum frame is 788 bytes. Do not allocate from a claimed peer length before validating the 768-byte maximum. CRC detects corruption/parser mistakes; **it is not authentication or encryption**.

## 5.3 Connection session and message IDs
The Android central creates a fresh random nonzero 32-bit connection session ID for every new GATT connection and sends it in HELLO. Do not persist/reuse it across reconnects. The Flipper accepts a new HELLO only in its handshake-wait state; after acceptance all frames must match the bound session. Android likewise rejects mismatched sessions. The ID is a freshness token inside an authenticated connection, not a cryptographic identity or secret.

Each sender has its own increasing nonzero 32-bit message-ID counter, starting at 1. Counters are independent by direction, so both HELLO and HELLO_ACK can have ID 1. Allocate IDs when a frame is selected for serialization/transmission, not when a pending item first enters a priority queue; later priority changes must not reorder already allocated new IDs. GATT delivery is ordered; an already seen ID is a duplicate. The COMMAND duplicate rule is explicit below. Never wrap IDs while a connection remains active; close and start a new handshake before exhaustion.

The golden file contains isolated examples, not a requirement to transmit all its rows consecutively as a valid live session. Live ordering and allowed directions still apply.

## 5.4 Message table
| Type | Hex | Direction | Payload bytes |
|---|---:|---|---:|
| HELLO | 01 | Phone → Flipper | 12 |
| HELLO_ACK | 02 | Flipper → Phone | 12 |
| SNAPSHOT | 10 | Phone → Flipper | 44…604 |
| STATE | 11 | Phone → Flipper | 36 |
| SNAPSHOT_APPLIED | 12 | Flipper → Phone | 8 |
| COMMAND | 20 | Flipper → Phone | 12 |
| COMMAND_RESULT | 21 | Phone → Flipper | 12 |
| REQUEST_SNAPSHOT | 30 | Flipper → Phone | 0 |
| PING | 40 | Phone → Flipper | 4 |
| PONG | 41 | Flipper → Phone | 4 |
| ERROR | 7E | Either | 8 |
| BYE | 7F | Either | 1 |

Unknown types or wrong directions cannot perform side effects. Reserved bytes/bits must be zero. An unsupported **frame** version is rejected at framing level. A HELLO in frame version 1 can propose an unsupported application version range and receive a version-rejection ACK.

## 5.5 HELLO and HELLO_ACK
HELLO:
| Payload offset | Type | Value |
|---:|---|---|
| 0 | u8 | Minimum supported protocol version, v1 sender uses 1. |
| 1 | u8 | Maximum supported protocol version, v1 sender uses 1. |
| 2 | u16 | Maximum complete payload capacity, exactly 768 for v1. |
| 4 | u16 | Sender's accepted GATT value size, 20…128; Android normally advertises 128. |
| 6 | u16 | Reserved, 0. |
| 8 | u32 | Feature bits, exactly `1` for the required base-v1 feature. |

HELLO_ACK uses the same field sizes. Offset 0 is selected version; offset 1 is status. Successful ACK uses selected version 1/status 0. Unsupported range uses selected version 0/status 1. Other fields describe the Flipper's receive capacity and base feature. There is no silent version downgrade to a different packet format.

Handshake flow: encrypted link → services discovered → indication subscription confirmed → HELLO → matching HELLO_ACK → SNAPSHOT → SNAPSHOT_APPLIED → Android READY. Flipper can mark its display ready immediately after valid snapshot application, but it must enqueue SNAPSHOT_APPLIED before any user command generated from that display. Initial no-session snapshots complete connection setup while leaving media controls disabled.

A duplicate identical HELLO while still completing handshake can return the same ACK without resetting state. An unexpected new session on an already-ready connection is a protocol error, not an instruction to erase the current session and replay commands.

## 5.6 Shared state prefix: exactly 36 bytes
SNAPSHOT and STATE begin with this prefix:

| Offset | Type | Field |
|---:|---|---|
| 0 | u32 | `player_epoch`: 0 means no selected live controller; otherwise nonzero. |
| 4 | u32 | `track_revision`: 0 with no controller; otherwise nonzero. |
| 8 | u32 | `state_seq`: increasing publication sequence within this connection, starting at 1. |
| 12 | u8 | `playback_state`: enum below. |
| 13 | u8 | `flags`: bit 0 position valid; bit 1 duration valid; bit 2 display text lossy/truncated. |
| 14 | i16 | `speed_milli`: signed thousandths, −8000…8000; 1000 means normal speed. |
| 16 | u64 | `position_ms`, projected at serialization; encode 0 when invalid. |
| 24 | u64 | `duration_ms`; encode 0 when invalid. |
| 32 | u16 | `capabilities` bits: 0 play/pause, 1 previous, 2 next, 3 volume up, 4 volume down. |
| 34 | u8 | `display_mode`: 0 elapsed/total, 1 elapsed/remaining. |
| 35 | u8 | Reserved, 0. |

Playback-state values: `0 NO_SESSION`, `1 STOPPED`, `2 PAUSED`, `3 PLAYING`, `4 BUFFERING`, `5 ERROR`, `6 UNKNOWN`.

Progression is permitted only for PLAYING with valid position and a useful speed; other states must encode speed 0. A valid duration is strictly positive. When position and duration are both valid, position must not exceed duration. For defensive 64-bit arithmetic, each time value is at most **315,360,000,000 ms**; the Android normalizer marks unsupported larger values unknown rather than wrapping them. This is a project safety bound, not a platform limit.

NO_SESSION requires player epoch, track revision, flags, capabilities, speed, position, and duration all zero. It still has a nonzero state sequence. All other playback states require nonzero player epoch and track revision. All unknown flag/capability bits are invalid in v1.

`player_epoch` changes when the selected Android controller/token changes, not on every song. Allocate it monotonically within the connection; reconnect resets the namespace. `track_revision` changes on new track/display metadata; it cannot identify a controller by itself. `state_seq` increases for every emitted SNAPSHOT/STATE, including otherwise identical periodic refreshes. Never wrap any of these active-session counters: reconnect before exhaustion.

## 5.7 SNAPSHOT payload
After the 36-byte state prefix:
| Offset | Type | Field |
|---:|---|---|
| 36 | u16 | Title byte length, max 192. |
| 38 | u16 | Artist byte length, max 128. |
| 40 | u16 | Album byte length, max 192. |
| 42 | u16 | Player app-label byte length, max 48. |
| 44 | bytes | Title, then artist, then album, then app label, concatenated without terminators. |

All text is normalized **printable ASCII bytes 0x20…0x7E** in v1, not arbitrary unvalidated UTF-8. Empty length means absent. The receiver adds its own terminator after copying into a bounded local buffer. Sum of lengths must match payload length exactly. Maximum payload is 44+192+128+192+48 = **604 bytes**. The frame limit remains 768 to bound generic parsing and allow future types only after an explicit protocol change.

A NO_SESSION snapshot must have empty title, artist, and album; app label may identify the selected target, e.g. Apple Music. Do not put demo metadata there.

Decode and validate the entire message into a temporary model, then apply it atomically. Never combine the new title with the old song's duration. A successfully applied new snapshot invalidates old text-scroll positions and establishes that epoch/revision for subsequent STATE messages.

SNAPSHOT_APPLIED contains `snapshot_message_id:u32` at offset 0 and `state_seq:u32` at offset 4. This means the receiver accepted and committed that snapshot; it is not an acknowledgement that audio played. Do not recursively acknowledge this acknowledgement.

## 5.8 STATE payload and publication order
STATE is exactly the shared 36-byte prefix. Apply it only when player epoch and track revision match the last applied snapshot, and its state sequence is newer. A mismatched state cannot overwrite metadata; request a full snapshot, at most once per second, and wait. Preserve the previous valid screen with a syncing indicator rather than inventing a match.

SNAPSHOT establishes a new epoch/revision or refreshes the current one. Discard older state sequences. A syntactically valid duplicate snapshot may be acknowledged again without resetting the clock to an older anchor or re-running animations.

The publisher orders a snapshot before any state referring to its new revision. If an unsent snapshot is replaced by a newer snapshot, drop obsolete dependent states. If a snapshot has begun transmission, finish its bytes and then send the newest pending snapshot/state; never splice a newer payload into its remaining chunks.

STATE requires no application ACK because writes are acknowledged by GATT and heartbeat detects an unresponsive application. Android sends a fresh STATE right after a snapshot and every 10 seconds, plus playback callbacks. Snapshot application ACKs exist to make startup readiness explicit, not to create an ACK for every transport byte.

## 5.9 COMMAND and COMMAND_RESULT
COMMAND:
| Offset | Type | Field |
|---:|---|---|
| 0 | u32 | Target player epoch, nonzero. |
| 4 | u32 | Observed track revision, nonzero; advisory, not strict equality. |
| 8 | u8 | Command: 1 play/pause, 2 previous, 3 next, 4 volume up, 5 volume down. |
| 9 | u8 | Origin: 0 physical initial press, 1 held-volume repeat. |
| 10 | u16 | Remaining receiver queue TTL in ms, 1…750. |

Held-repeat origin is valid only for volume commands. A command's frame message ID is its command ID. It is not necessary to add a second redundant command counter. Generate commands only while ready; never queue them across reconnections.

The Flipper initially assigns a 750-ms lifetime to a normal press, 300 ms to a repeat. Before serialization, subtract locally queued age and drop an expired command. Android timestamps receipt and dispatches before the encoded remaining TTL elapses. **This is bounded local queue aging, not a synchronized end-to-end deadline**: the two clocks are not synchronized and BLE transit time is not encoded. Keep sends short, do not retry commands automatically, and never replay a command on a new connection. If realistic tests show excessive transit delay, disconnect/drop rather than pretending the TTL gives stronger timing guarantees.

COMMAND_RESULT:
| Offset | Type | Field |
|---:|---|---|
| 0 | u32 | Referenced COMMAND message ID. |
| 4 | u8 | Status enum. |
| 5 | u8 | Detail, 0 in v1. |
| 6 | u16 | Reserved, 0. |
| 8 | u32 | Android's current player epoch, possibly 0. |

Statuses: `0 DISPATCHED`, `1 NO_PLAYER`, `2 STALE_EPOCH`, `3 UNSUPPORTED`, `4 EXPIRED`, `5 PERMISSION_DENIED`, `6 INTERNAL_ERROR`, `7 NOT_READY`.

DISPATCHED means one controller/volume API attempt was made; player confirmation comes through later state. Do not label it “song changed successfully” merely because IPC was submitted. Missing COMMAND_RESULT after 2 seconds shows a brief “No response” indication; do not resend a toggle or volume press.

Deduplicate the latest 32 command results for up to 30 seconds within the connection. Duplicate identical command ID/payload returns the prior result without dispatch. A reused ID with different payload is an error. The receiver must also reject an old command ID outside its retained cache rather than treating it as new. IDs are strictly increasing for new frames in a given direction; out-of-order old commands are stale. Track revision is not a dispatch lock: several deliberate Next presses can target the same player while metadata is catching up.

## 5.10 Other messages
REQUEST_SNAPSHOT has no payload. The phone responds by sampling and sending the current complete snapshot; rate limit repeated requests to one per second.

PING and PONG each contain one arbitrary `cookie:u32`; PONG echoes it. The phone emits a heartbeat every 15 seconds while ready. Valid incoming frames refresh peer activity. After 35 seconds without a valid peer frame, both sides invalidate readiness and reconnect/reset the session. An unmatched PONG does not prove the latest heartbeat succeeded, although its valid same-session frame still shows some peer activity.

ERROR is `related_message_id:u32`, `error_code:u16`, `reserved:u16=0`. Codes: 1 unexpected type/direction, 2 invalid payload, 3 session/state violation, 4 unsupported feature/version, 5 resource limit, 6 internal transport problem. Related ID can be 0 when none can be safely identified. Only respond to a complete CRC-valid frame where it makes sense; do not generate endless errors in response to malformed noise, an ERROR, or an unsupported frame version.

BYE is one reason byte: 1 Android user Stop, 2 Flipper exit, 3 protocol failure. It is best effort and needs no reply. Shutdown must not wait for it indefinitely.

## 5.11 Parser recovery and memory limits
Maintain one assembly buffer per receive direction, bounded to 788 bytes. Search for magic; while searching preserve at most the final three possible prefix bytes. Once a header is complete, reject oversize payloads and unsupported frame versions. Once the full candidate is present, verify CRC before parsing payload fields or touching domain state.

On a bad length/version/CRC candidate, discard one leading byte and rescan so a later valid magic can be recovered. A literal `FNP1` inside a validated payload is ordinary data, not a delimiter. On a valid frame with invalid semantics, drop its side effects and optionally report an error; do not crash or allocate larger buffers.

Reset incomplete assembly after 5 seconds without new bytes or 15 seconds total assembly time, on connection change, on queue overflow, or on transport shutdown. On timeout, clear readiness if synchronization may have been lost, and request snapshot/reconnect as appropriate. Three framing/resource errors within a 10-second window trigger a controlled reconnect rather than unlimited noise processing. The reference stream decoder's caller supplies timeout/session policy; those policies are mandatory in production.

A single Android GATT write-with-response or Flipper indication acknowledgement confirms only that ATT handled that chunk. It does not make a multi-chunk payload atomically visible. The whole-frame CRC and application reducer provide that atomicity.

## 5.12 Scheduling and prioritization
No frame-byte interleaving. At frame boundaries prioritize shutdown/protocol repair, then handshake/command results, then physical commands, then snapshots, then periodic state/heartbeat. Each side only sends the types allowed for its direction. A started frame is completed or the link is closed; it is never abandoned halfway to send a different frame on the same stream.

Queue bounds: one active TX frame per direction; up to 4 pending control/command frames; one replaceable latest snapshot; one replaceable latest state; one pending heartbeat. Expired repeated-volume commands are discarded. If the command queue fills, reject/drop with visible local feedback; never execute a historical burst once the phone recovers. Coalescing metadata must not coalesce distinct deliberate Next presses into one unless the older press expired.

A frame-level ACK/retransmission protocol is intentionally not added on top of GATT. Only HELLO_ACK, SNAPSHOT_APPLIED, COMMAND_RESULT, and PONG have defined application semantics. Never add ACK-of-ACK loops or retransmit a play/pause operation to “make it reliable.”

## 5.13 Interoperability evidence
Run `python -m unittest discover -s protocol -v` from the package root. Fixtures include the approved example, paused/remaining state, unknown duration, no player, maximum-length text, media commands, and control messages. C and Kotlin must decode the committed bytes and re-encode exactly; also compare their outputs against each other with randomized valid models.

Do not generate expected bytes using the same production encoder inside the test asserting those bytes. Golden fixtures are fixed reviewable expectations. Tests must cover minimum MTU, arbitrary splitting, concatenated frames, invalid lengths, reserved bits, stale epochs/sequences, duplicate commands, mid-frame disconnect, and expired queue entries.

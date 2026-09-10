# 08 — Acceptance criteria, testing, and evidence

## 8.1 Evidence rules
Every matrix row has one of `PASS`, `FAIL`, `NOT_RUN`, or `BLOCKED`, with an exact command/log/device observation. The included CSV starts at NOT_RUN because the applications do not exist yet. Do not change that status just because this handoff's Python reference tests pass.

Host tests prove codec/domain behavior. An Android emulator can prove permissions/UI/session logic but does not substitute for a real Flipper Bluetooth connection. A synthetic MediaSession can prove missing-field behavior but does not prove the installed Apple Music version publishes album or accurate position. A successful FAP build proves compilation/API linkage, not safe repeated device cleanup.

A requirement marked MUST remains a release-validation requirement. Where the environment lacks the physical device, mark it NOT_RUN or BLOCKED with the exact prerequisite, finish independent software work, and report software-complete separately from device-validated.

## 8.2 Required test layers
**Reference checks:** The handoff supplies Python syntax tests and fixed vectors. Run them before and after protocol-related changes.

**Pure Kotlin:** Test normalization, selection, epoch allocation, position projection, dispatch choice, command dedup/expiry, publication coalescing, GATT queue ordering, and reconnect state machine using injected clocks and fake ports.

**Pure C:** Compile production codec/reducers/input/clock on a host. Run address/undefined sanitizers where supported and a bounded fuzz corpus covering header lengths, CRC, text lengths, enum values, flags, and timestamp limits. Fuzzing must not invoke radio APIs or allocate from uncontrolled lengths.

**Cross-language:** Kotlin→C and C→Kotlin round trips using committed golden bytes and randomly generated bounded valid states; malformed corpus rejected without side effects. Do not use the reference Python encoder as the implementation for either production app.

**Android instrumentation:** Real service lifecycle and permissions with a test-only MediaSession application; its interface can change metadata, playback state, supported actions, route/volume capability, and kill/recreate its session. Secure fixture-control endpoints so they are test-only; never expose a production remote-command broadcast.

**Renderer:** Exercise production layout/clock/input logic through a Canvas-compatible test adapter using the same measured font/rendering behavior where feasible. Record native-resolution screenshots and inspect them. Label simulation accurately.

**Physical integration:** Pair the actual custom service, send real Apple Music metadata, operate every key, keep the phone locked, reconnect, and return to normal official-app operation. Record phone model/API, Apple Music version, Flipper firmware/API, and which signed APK/FAP hashes were tested.

## 8.3 Platform matrix
Minimum code-path tests: API 26/30 for legacy discovery permissions; API 31 for modern Bluetooth grants; API 33 for notification permission/value-based GATT overloads; API 34 for connectedDevice FGS rules/MTU behavior; API 36 as baseline target; API 37 for background-audio hardening. Use representative emulators/unit configuration for code paths and clearly distinguish them from real radio tests.

The initial FAP build baseline is official 1.4.3 / API 87.1. Additional firmware support requires a separate import audit/build and documented hardware result; no blanket “all firmware supported” claim. [S03]

## 8.4 Acceptance matrix
The same rows are available in `docs/acceptance_matrix.csv`. Add concrete evidence in that CSV during development.

| ID | Area | Criterion | Environment |
|---|---|---|---|
| P01 | Protocol | C and Kotlin match all committed valid golden frames | Host |
| P02 | Protocol | CRC check value and little-endian offsets match fixed expectations | Host |
| P03 | Protocol | Every split position and one-byte fragments reassemble | Host |
| P04 | Protocol | Multiple concatenated frames and magic inside text parse correctly | Host |
| P05 | Protocol | Oversized lengths, malformed lengths, invalid CRC and noise recover boundedly | Host |
| P06 | Protocol | Unknown version/type/direction and nonzero reserved fields have no effects | Host |
| P07 | Protocol | Maximum 604-byte snapshot at 20-byte chunks applies atomically | Host + device |
| P08 | Protocol | Partial-frame timeout, RX overflow and disconnect clear assembly | Host + device |
| P09 | Protocol | Session mismatch and old state sequences cannot change current state | Host |
| P10 | Protocol | STATE with wrong epoch/revision requests snapshot and does not mix metadata | Host |
| P11 | Protocol | Duplicate command with same ID dispatches once; different payload rejected | Host |
| P12 | Protocol | Expired commands, old IDs outside dedup cache and reconnect replay rejected | Host |
| P13 | Protocol | Counters near wrap trigger controlled new session, not ambiguous rollover | Host |
| P14 | Protocol | Frame priorities never interleave bytes or reorder allocated IDs | Host |
| P15 | Protocol | C fuzz/sanitizers show no out-of-bounds, use-after-free or overflow | Host |
| A01 | Android | Permission grants, refusals and settings cancellation all leave repair path | Emulator + phone |
| A02 | Android | Notification permission and notification-listener access are distinct | Emulator + phone |
| A03 | Android | Already-playing Apple Music discovered immediately on helper startup | Phone |
| A04 | Android | Title, artist, album, duration and position fields inspected on actual Apple Music | Phone |
| A05 | Android | Apple Music-only mode never controls another concurrently active player | Fixture + phone |
| A06 | Android | Auto selection is stable; callbacks from replaced controller ignored | Host + fixture |
| A07 | Android | Missing metadata, zero/unknown duration, unknown position shown honestly | Host + fixture |
| A08 | Android | Latin accents, smart punctuation, control chars and unsupported scripts normalize safely | Host |
| A09 | Android | Play/pause, previous/next execute once through one action path | Fixture + phone |
| A10 | Android | Local music volume changes without affecting ringtone or audio route | Phone |
| A11 | Android | Unsupported/fixed remote volume returns truthful unsupported result | Host + fixture |
| A12 | Android | Rapid Next twice does not get lost due to track-revision change | Fixture + phone |
| A13 | Android | No audio focus request, media-session creation or output-route takeover | Review + phone |
| A14 | Android | Single GATT operation outstanding; stale callbacks and timeouts handled | Host + instrumentation |
| A15 | Android | CCCD indication subscription completes before HELLO | Host + phone |
| A16 | Android | MTU refusal and Android 14 MTU behavior do not break default 20-byte path | Host + phone |
| A17 | Android | FGS started from visible Start action and remains correctly typed | Emulator + phone |
| A18 | Android | Android 17 locked-screen volume obeys audio-hardening requirements | API 37 phone/emulator |
| A19 | Android | Stop cancels service, callbacks, scans, GATT and reconnect work | Emulator + phone |
| A20 | Android | Process kill/force-stop/reboot yields honest stopped/restart-required state | Phone |
| A21 | Android | Permission revocation and Bluetooth-off do not crash or loop prompts | Phone |
| A22 | Android | Manifest has no Internet/analytics/privilege workaround permissions | Build audit |
| A23 | Android | Diagnostics and notification redact song data and unrelated notifications by default | Host + phone |
| F01 | Flipper | FAP builds against pinned official API and every import is exported | Build |
| F02 | Flipper | Custom UUID discoverable; stock RPC service not hijacked | Phone + Flipper |
| F03 | Flipper | Pairing confirmation works; unauthenticated control path unavailable | Phone + Flipper |
| F04 | Flipper | GATT adapter normalizes false-on-success behavior of pinned wrapper | Host + source audit |
| F05 | Flipper | TX indication confirmation gates next chunk; callback stays nonblocking | Host + Flipper |
| F06 | Flipper | Long Back exits during waiting, playback, disconnect and TX timeout | Flipper |
| F07 | Flipper | Short Back does not exit or change media action | Host + Flipper |
| F08 | Flipper | Press/Short/Release do not duplicate play/pause or track commands | Host + Flipper |
| F09 | Flipper | Held volume bounded; release/opposite key/disconnect/10s cap stop repeat | Host + Flipper |
| F10 | Flipper | No queued commands execute after reconnection | Phone + Flipper |
| F11 | Flipper | Partial init failures restore key path and clean only acquired resources | Host + Flipper |
| F12 | Flipper | Default profile restored before unload; no freed callback context remains | Review + Flipper |
| F13 | Flipper | Existing official-app bond survives custom app launch/exit | Phone + Flipper |
| F14 | Flipper | App-specific pair survives ordinary FAP restart | Phone + Flipper |
| F15 | Flipper | Missing SD card/path/Bluetooth-off produces clear nonfatal error | Flipper |
| U01 | UI/time | Native 128x64 production-render screenshots exist for required states | Host + optional Flipper |
| U02 | UI/time | Title/artist/album stay inside boxes and do not collide with icon/status | Pixel + visual |
| U03 | UI/time | Long metadata scrolls; ordinary periodic state does not reset marquee | Host + Flipper |
| U04 | UI/time | Play pause seek buffering speed0/2x/negative speed and unknown clock tested | Host + fixture |
| U05 | UI/time | Tick wrap, clock skew, wall-clock change and delayed timer do not corrupt elapsed | Host |
| U06 | UI/time | Elapsed total remaining and hours format without overlap/divide-by-zero | Host + visual |
| U07 | UI/time | Known disconnect or 35s stale peer freezes progress and disables commands | Host + devices |
| U08 | UI/time | Demo values only in explicit test/demo sources, not default disconnected UI | Review + devices |
| H01 | End-to-end | Two hours of Apple Music playback with phone locked and headphones connected | Phone + Flipper |
| H02 | End-to-end | 50 disconnect/reconnect cycles without duplicate commands or pair resets | Phone + Flipper |
| H03 | End-to-end | 30 FAP start/exit cycles without progressive memory loss or profile failure | Phone + Flipper |
| H04 | End-to-end | Measure first-screen, command, metadata and reconnect latency percentiles | Phone + Flipper |
| H05 | End-to-end | Measure loaded FAP size, heap and stack high-water marks under worst metadata | Flipper |
| H06 | End-to-end | Bluetooth toggles, range loss, player termination and permission repair recover | Phone + Flipper |
| H07 | End-to-end | Album/duration source omissions documented from observed app version | Phone |
| R01 | Release | Fresh-checkout scripts build both artifacts with recorded versions | Clean build |
| R02 | Release | APK package/signature/version and FAP API verified from actual artifacts | Build audit |
| R03 | Release | No signing key, bond file, private media metadata or font files in bundle | Package audit |
| R04 | Release | Install pairing Stop recovery and uninstall guide verified against artifacts | Review + devices |
| R05 | Release | Manifest hashes match files; tests not run are not marked passed | Package audit |
| R06 | Release | Source license/third-party notices preserved; no unlicensed copied artwork/fonts | Review |

## 8.5 Critical scenario scripts
**Already-playing startup:** Start Apple Music first with a known track. Start helper/FAP afterward. Verify the first screen is not blank until the next song. Compare title, artist, album, state, and time to the phone.

**Seek/pause synchronization:** Play for 20 seconds, seek forward about 60 seconds on the phone, pause, wait 15 seconds, resume, then seek backward. Confirm time jumps promptly on seek, stays frozen during pause, and uses the new anchor after resume. Move the phone's wall clock where an isolated test environment permits; the playback clock must not move with it.

**Duplicate and stale commands:** In a fixture, deliver identical COMMAND bytes twice and verify one API attempt. Deliver the same ID with a different command and verify no second action. Disconnect with an unsent command queued, reconnect, and prove it never plays later. Fill the queue with held-volume events and stall delivery; recovery must not produce a burst of expired volume changes.

**Media-session replacement:** While a command is queued, replace the fixture's controller token with a new session from the same package. The stale epoch must be rejected, then the new snapshot enables commands. Separately skip a track without replacing the controller and verify a second rapid Next still works.

**Radio teardown under pressure:** With a large snapshot being transmitted, hold Back. Repeat during outstanding TX indication, pending phone write, and timeout recovery. No callback may execute against freed state; the stock profile must be restored. This is a physical test, not just an allocation-counter unit test.

**Screen-off endurance:** Start the helper from visible UI, pair, play Apple Music through Bluetooth headphones, lock the phone for two hours, periodically exercise pause/resume, Next, volume, and reconnect. Record whether Android suppresses service/volume activity. Use ordinary power settings first; do not preemptively grant a battery exemption and claim default behavior passed.

**Android 17 audio-hardening diagnostic:** In an authorized test environment, use the documented hardening diagnostic mode to detect invalid background volume calls, then restore the test device's prior setting. The goal is correct lifecycle, not leaving hardening disabled. Record whether the app's connectedDevice service retains the required user-initiated capability. [S17]

## 8.6 Measurements
Record monotonic times for physical input receipt, FAP frame transmit start/end, Android receive/dispatch, metadata callback, and Flipper application. Do not subtract unsynchronized phone and Flipper clocks to claim an end-to-end latency. Use one instrumentation clock, a loopback/correlated measurement, or synchronized external video for true cross-device timing. Report method and exclusions.

Collect median/95th percentile command dispatch and first-screen timings over at least 30 ordinary trials. Separate cold pairing, already bonded reconnect, MTU 23, and optimized write-size cases. Log packet counts/bytes for a paused minute and a playing minute, without recording private payloads. Measure memory across start/exit cycles and after worst-case metadata; a small persistent step must be investigated, not waved away as “probably firmware”.

## 8.7 Release decision
Software-complete requires all build/host/review tests that the environment supports to pass and both production apps to be fully implemented. Device-validated requires the real-device core rows, especially Apple Music fields, volume with screen locked, reconnection, and default bond restoration. Any failed core behavior blocks a claim that the product is ready for everyday use.

A final report should say, for example, “Both artifacts built; host and Android fixture tests passed; physical Flipper pairing and Apple Music lock-screen tests not run because no devices were attached.” It must not say “fully tested” or “works perfectly” in that situation.

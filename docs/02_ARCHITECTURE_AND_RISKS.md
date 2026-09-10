# 02 — Architecture, state machines, and risk gates

## 2.1 Data flow and boundaries
```text
Apple Music / selected Android media session
            │ MediaController metadata + playback callbacks
            ▼
MediaSessionRepository → Normalizer → SnapshotPublisher
                                     │
                             Android GATT client
                                     ⇅
                    dedicated encrypted BLE service
                                     ⇅
                             Flipper GATT server
                                     │
                              Protocol decoder
                                     │
                              NowPlayingModel
                                     │
                           128×64 view + clock

Flipper physical keys → InputReducer → COMMAND
       → Android CommandRouter → selected MediaController / media volume
```

Transport is bidirectional, audio is not. Android is the authority for track metadata and playback state. The Flipper extrapolates time locally only while state is playing and the connection is fresh. The phone never sends a timestamp that the Flipper mistakes for its own clock.

Pure domain components accept injected clocks, transports, and media-controller ports. The production wrappers use Android/Furi; unit tests use deterministic fakes. There must be no second set of “demo” application logic that is the only code exercised by tests.

## 2.2 Why a dedicated BLE profile
The firmware's normal serial profile is also its RPC management transport; the Bluetooth service recognizes that profile and attaches RPC callbacks on connection. Replacing serial callbacks from an external app is not a clean independent channel and can conflict with the firmware. Use a new profile template containing this project's service instead. The official exported API includes profile switching and GATT wrappers; that is the implementation route to verify, not a promise that every firmware variant exposes the same symbols. [S03][S05][S06][S10]

Do not impersonate the official app service UUID, use stock RPC framing for raw music messages, or require the official Android application to relay data. The official app and the helper can coexist on the phone; active concurrent ownership of the Flipper radio is not part of the design.

## 2.3 The integration risk gate
Before building the polished screen, implement a small version of the **actual production custom service** that compiles as an external FAP, advertises the dedicated UUID, pairs, receives HELLO, and returns HELLO_ACK. The phone side should be a minimal real GATT client, not only a fake.

The first evidence is a symbol/import audit and successful FAP build against official 1.4.3. Where hardware is present, then prove real bidirectional traffic and default-profile restoration. Without hardware, complete the compile audit and test harness, mark the radio proof pending, and continue all independent software work. Do not falsely convert the risk into a “passed” gate because the codec tests pass.

Inspect the exact source for API semantics. In official 1.4.3, `ble_gatt_characteristic_update()` returns `result != BLE_STATUS_SUCCESS`, whereas service-add returns `result == BLE_STATUS_SUCCESS`. Its retry loop can also delay under resource pressure. Encapsulate this discrepancy in a version-aware adapter so `np_ble_tx_submit()` has normal success/error meaning. Never block inside the BLE event callback while invoking that wrapper. [S04]

If a required function is genuinely unavailable in the selected official SDK, investigate equivalent **exported** APIs and complete the other modules. Record the exact missing symbol and alternatives. Do not quietly switch to custom firmware, a raw RPC callback takeover, or hard-coded internal function addresses.

## 2.4 Android service state machine
Use explicit states; do not infer everything from a single Boolean called `connected`.

| State | Entry / permitted work | Exit |
|---|---|---|
| STOPPED | No GATT object or scan. No reconnect job. | User starts from visible UI after required grants. |
| NEEDS_PERMISSION | Explain the missing permission/access; no illegal Bluetooth calls. | User fixes grant and starts/resumes setup. |
| ARMED_WAITING | Foreground service alive; selected identity retained; one legal connect attempt/auto-connect registration. | Device connects, Bluetooth turns off, user stops, or process dies. |
| CONNECTING | Exactly one current GATT connection generation. | Connected callback or bounded direct-connect timeout. |
| SECURING | Wait for bond/encrypted access; display system pairing instructions. | Bond success/failure; no retry loop after explicit rejection. |
| DISCOVERING | Discover expected service/characteristics, validate properties. | Success or close/retry. |
| SUBSCRIBING | Enable local callback then write indication CCCD; wait for success. | Success or connection error. |
| HANDSHAKING | Send HELLO and validate matching HELLO_ACK session/version. | Ready to send initial snapshot, or fail clearly. |
| SYNCING | Send full snapshot and await SNAPSHOT_APPLIED. | READY. |
| READY | Publish state; execute fresh authorized commands; maintain heartbeat. | Link loss, Stop, permissions revoked, or protocol failure. |
| BLUETOOTH_OFF | No scans/connects; keep an accurate status notification while armed. | Bluetooth enabled and permissions still valid. |
| ERROR | Human-readable reason plus recoverable action; no tight retry. | User repair or bounded reconnect policy. |

A generation counter changes on each GATT-object replacement. Every callback carries the generation/object identity into the connection actor; stale callbacks are ignored. STOPPED wins over pending reconnect callbacks. A disconnected peripheral is not the same as a stopped service.

## 2.5 Flipper state machine
```text
STARTING → WAITING_FOR_PHONE → LINK_CONNECTED → HANDSHAKING
           → WAITING_FOR_STATE → READY
READY → NO_PLAYER / MEDIA_ERROR  (link remains usable)
READY → STALE → RECONNECTING     (on heartbeat/link failure)
ANY ACTIVE STATE → STOPPING → RESTORING_DEFAULT → EXIT
```

Use separate link-state and media-state fields so “connected, nothing playing” is representable. Input media commands require a completed handshake and nonzero current player epoch. Before the first snapshot, controls are disabled, not buffered. Long Back remains available in every app-controlled state. System pairing dialogs may have their own navigation behavior; do not override firmware confirmation controls.

No state change may leave a callback pointing at freed application memory. The profile template, service instance, queues, and callback context outlive profile restoration. Radio callbacks post short events; the application worker performs profile operations and transport sends.

## 2.6 Ownership and concurrency
**Android:** One actor/serial execution context owns the active media selection, session epoch, pending publication, command deduplication, and logical connection state. The GATT operation queue is serialized and uses callbacks for completion. Main-thread UI observes immutable state. OS callbacks copy values and hand them off; do not read mutable `BluetoothGattCharacteristic.value` later on another thread.

**Flipper:** One application owner/worker updates domain state. The BLE callback copies bounded bytes into a fixed queue and immediately returns. GUI draw reads a short-lived snapshot protected by a lock or view model. Timers post nonblocking tick events; they do not draw or manipulate Bluetooth. A transport TX worker may be separate because the stable wrapper can sleep; the GUI/input path must remain responsive. The owner is responsible for shutdown order and joining workers.

Avoid nested GUI→BLE or BLE→GUI locks. Do not call synchronous `bt_profile_start`, `bt_disconnect`, or restoration from the Bluetooth service callback thread: these APIs queue messages to that service and wait. [S07]

## 2.7 Pairing and identity
The custom service requires encrypted, authenticated access with bonding and explicit numeric confirmation when supported by the profile. Follow the official HID profile's pairing/GAP conventions, but give this project a distinct stable identity and key file. The displayed Bluetooth device name is not sufficient authentication. [S05][S11]

Use a profile-specific key-storage path under the FAP app-data directory. The phone persists the chosen custom-profile device, not the stock management device. Do not change identities on every launch. Do not store bonding keys in exports or release bundles. Remember that an SD-card key file is not a secure enclave; do not claim encryption at rest that the platform does not provide.

For the initial official-firmware adapter, derive the profile address in the same byte-array convention as the official HID implementation, using factory address byte 2 plus **2** rather than the HID plus 1. Preserve all other bytes; test the resulting identity on the selected stack. This is a practical profile-separation convention, not a global Bluetooth address assignment. Detect/report a conflicting existing custom identity rather than forgetting unrelated bonds.

## 2.8 Known risks and required mitigations
| Risk | Mitigation / evidence |
|---|---|
| FAP compiles against incompatible dev ABI | Pin stable SDK and audit every import. [S02][S03] |
| Custom GATT behaves differently on device | Early real handshake/echo, minimum MTU, repeat profile restore tests. |
| GATT wrapper success Boolean inverted | Dedicated adapter plus source-version test/review. [S04] |
| RPC callback conflict | Do not use stock serial profile for raw music traffic. [S10] |
| Android background startup denied | Start connectedDevice FGS while activity visible; no boot/start-from-listener shortcut. [S12][S13] |
| Android 17 volume silently ignored | Preserve user-initiated foreground-service lifecycle; test locked-screen volume under audio hardening. [S17] |
| Apple Music omits album/duration | Inspect actual published keys; show missing fields honestly. No Apple API dependency. |
| Playback timer drifts | Paired position/timestamp projection, periodic resync, local monotonic clock, stale freeze. [S16] |
| Double play/pause or runaway volume | Unique command IDs, deduplication, no reconnect replay, bounded repeat, one action path. |
| Bluetooth headphones disrupted | No audio profile/focus/routing APIs; test while headphones remain connected. |
| Unicode text appears corrupt | Normalize before transmitting display text; strict receiver validation and visible fallback. |
| Cleanup race crashes firmware | Owner-thread shutdown, in-flight callback fencing, worker joins, restore before unload. |
| One-shot agent reports mocks as real | Separate software-complete and hardware-validated gates with explicit evidence. |

## 2.9 Failure policy
A recoverable metadata problem must not disconnect an otherwise healthy BLE link. A malformed but bounded payload must not crash either application. Repeated framing failures, an illegal session transition, or an operation timeout may close and recreate the connection. A denied permission, rejected pairing, or unsupported firmware requires an actionable explanation, not repeated dialogs.

The user can always return to the stock app. This product must not hold the default profile hostage, erase existing pairings, or leave the Flipper with an executable callback into an unloaded FAP.

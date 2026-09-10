# 03 — Android implementation

## 3.1 Components and responsibilities
Use a small app module plus a pure Kotlin core module. Avoid a playback library: the helper is a controller, not a music player.

| Component | Responsibility |
|---|---|
| `MainActivity` + setup/status ViewModel | Permission explanations, device discovery/selection, Start/Stop, settings, current state, and diagnostics. |
| `MediaAccessService : NotificationListenerService` | Establish the user-approved access path; signal listener connection/revocation; do not archive notifications. |
| `BridgeService : Service` | Own the foreground lifecycle, domain actor, active GATT client, repositories, and cancellation scope. |
| `MediaSessionRepository` | Observe active sessions, attach/detach callbacks, expose immutable session snapshots and supported actions. |
| `PlayerSelector` | Implement Apple Music-only, chosen-package, and Auto selection deterministically. |
| `MetadataNormalizer` | Safe title/artist/album fallbacks; display-text normalization and length bounds. |
| `PositionEstimator` | Project one published position/timestamp pair using the injected monotonic clock. |
| `CommandRouter` | Check readiness, player epoch, age, deduplication, and capabilities; dispatch exactly one action. |
| `BleConnectionManager` | Own connection generations, bond flow, discovery, subscription, handshake, reconnection, and close. |
| `GattOperationQueue` | Serialize asynchronous operations; correlate completions; time out and recover without parallel writes. |
| `ProtocolCodec` / `StreamDecoder` | FNP/1 encoding, decoding, validation, and bounded recovery. |
| `SnapshotPublisher` | Coalesce pending metadata; serialize snapshot/state at send time; periodic state and heartbeat. |
| `BridgeSettings` | Persist selected custom-profile device, player selection, and display mode only. |
| `DiagnosticRecorder` | Bounded, redacted event history and explicit export; never collect unrelated notifications. |

Public domain interfaces should be narrow: `MonotonicClock.nowMs()`, `MediaPort.currentSnapshot()`, `MediaPort.dispatch(command)`, and `Transport.enqueue(frame, priority)`. Separate fixture/instrumentation player code into a test-only app or test source set.

## 3.2 Manifest and permission matrix
Declare the BLE hardware feature as required. For API 31+, request `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` at runtime. Do not request `BLUETOOTH_ADVERTISE`: the phone is not a peripheral. Use `neverForLocation` on the scan declaration when the app truly does not derive location. For older Android, use legacy Bluetooth permissions and the fine-location grant needed for foreground discovery, limited with `maxSdkVersion=30`. Do not request background location just for reconnecting a previously selected fixed-identity device. [S14]

Declare `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE`. Declare the bridge service non-exported with `android:foregroundServiceType="connectedDevice"`. Enter the foreground promptly after the explicit Start action; perform slow BLE setup afterward. Request `POST_NOTIFICATIONS` on API 33+ for normal status visibility, but do not confuse denial of that runtime permission with denial of media-session access. An FGS still needs its notification object; notification permission itself is not a prerequisite to starting that service. [S13][S15]

Declare the normal manifest permission `android.permission.MODIFY_AUDIO_SETTINGS` for the local AudioManager volume-control path; it is not a runtime permission dialog. Limit usage to the selected music-volume operation. Do not change audio mode, routing, ringer policy, or audio focus. [S20]

The system-bound notification-listener service is exported with the protective service permission `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` and the standard notification-listener intent filter. The user enables notification access in Android settings. Do not request `MEDIA_CONTENT_CONTROL` as an ordinary runtime permission: the enabled notification-listener component is the supported authorization path for reading active media sessions. [S18][S19]

The launcher activity is exported with the launcher intent filter. Other activities/receivers/providers are non-exported unless a documented platform contract requires otherwise. Use immutable explicit PendingIntents for notification actions. Include a scoped `<queries>` entry for the Apple Music package if package existence/label lookup needs it; do not request `QUERY_ALL_PACKAGES`.

Do not declare Internet, accessibility, microphone, contacts, storage-all-files, battery-optimization exemption, exact-alarm, or boot-start permissions. Use app-private storage; use a FileProvider and temporary read URI permission only for a user-requested diagnostic export. Review the merged manifest for transitive permissions added by libraries.

## 3.3 Setup and permission lifecycle
The setup page must distinguish three facts: Bluetooth permission is granted; notification **access** is enabled; and the helper foreground service is running. Also show whether the chosen Flipper is bonded and whether this app's protocol handshake completed.

Request grants from visible UI, one logical step at a time. Notification-access enablement is not returned as a normal runtime grant; recheck the listener component and wait for `onListenerConnected()`. Handle `SecurityException` even after a previous successful grant. Listen for revocation and `onListenerDisconnected()`; unregister media callbacks and invalidate the active player epoch when access disappears.

A sideloaded APK can encounter Android's restricted-settings confirmation for notification access. Explain the official user-controlled settings route and keep a repair button. Do not automate around the protection or instruct the agent to bypass it. Test cancellation and repeated opening of settings without duplicate listeners.

After selecting a device, the user confirms system pairing. Display “Confirm the matching code on both devices” when appropriate. A rejected bond is a user decision: stop the attempt and show Retry, not repeated pairing prompts.

## 3.4 Foreground-service policy
**Baseline:** Start only from an explicit user action while the helper activity is visible. Keep one low-importance, ongoing notification while armed, including while waiting for the selected Flipper. Suggested text: “Waiting for Now Playing on your Flipper” / “Connected to Flipper” / “Bluetooth is off”. Actions: Open and Stop. Do not put song metadata on the lock-screen notification by default.

Use a service-owned `SupervisorJob` and serialized domain state. The activity may close or rotate without stopping the bridge. Use `START_NOT_STICKY` for the conservative v1 lifecycle: a killed process must not pretend to have a lawful, user-initiated foreground session after silently restarting. Do not use a notification-listener callback, WorkManager loop, boot receiver, or alarm as a hidden restart mechanism. A user can restart from the visible activity. [S12][S13]

This matters for volume on Android 17: the audio framework may silently ignore volume calls from an invalid background lifecycle, and targeting API 37 adds while-in-use capability requirements. Starting the FGS while the app is visible is part of the mitigation. Test the actual control path on API 37; do not assume the mere presence of any FGS grants the needed capability. Do not request a fake microphone/location permission or alarm privilege to work around the rule. [S17]

No permanent partial wake lock. No one-second background polling. Let callbacks and the small periodic state/heartbeat tasks do the work. If deep sleep delays a task, transmit a fresh state when execution resumes; never send a backlog of old timer events. Do not claim zero battery impact. Stop cancels timers, callbacks, connect attempts, scans, and GATT resources, removes the notification, and stops the service.

## 3.5 Discovering and selecting media sessions
Use `MediaSessionManager.getActiveSessions(listenerComponent)` and `addOnActiveSessionsChangedListener(...)`. Register a callback on the selected `MediaController` for metadata, playback state, audio info, and session destruction. Read initial values immediately after registering, then merge callbacks through the actor to avoid missing an already-playing session. [S18][S20]

The default Apple Music package candidate is `com.apple.android.music`; verify it against the installed package/controller during testing rather than requiring the label to be English. An app label can change or be localized. The setup UI should allow choosing the observed Apple Music controller/package when necessary.

Selection rules:

1. In Apple Music-only or chosen-package mode, consider only controllers from that package. Prefer one actively playing/buffering; otherwise retain the current live controller; otherwise use the highest-priority eligible controller. If none exists, publish NO_SESSION. Do not fall back to another app.
2. In Auto mode, prefer an eligible playing controller. If the current controller is still playing, keep it to prevent oscillation. Otherwise choose the highest-priority playing controller, then retain a live paused current controller, then the highest-priority remaining controller. Exclude this helper and any fixture package outside test mode.
3. A controller-token change allocates a new nonzero `player_epoch`, even if the package name is unchanged. Detach the previous callback before switching. Late callbacks carry their token/generation and cannot overwrite the replacement session.

Do not switch merely because a notification arrives. Do not parse arbitrary notification body text as album or time. An optional media-session token extracted from a notification must be from an explicitly eligible player and is only a discovery aid when necessary; the session remains the data source.

If no session exists, prompt the user to open Apple Music and play a track. Do not launch Apple Music or create playback automatically on connection.

## 3.6 Metadata mapping
Prefer `METADATA_KEY_TITLE`, then `METADATA_KEY_DISPLAY_TITLE`; artist, then album artist; album from its dedicated key. Read duration only when present and positive. Preserve a distinction between absent and empty values. Do not send a cover bitmap. [S21]

The internal model contains original metadata strings, normalized display strings, media ID when published, controller identity, track revision, playback state, capabilities, and a position anchor. Use media ID/queue-item information when reliable, plus metadata identity and transition cues. A new track increments `track_revision`. Metadata enrichment of the same item may also increment the revision because the display changed; it must not change `player_epoch`.

Do not detect a new track solely because playback position moved backward: the user may have sought. A repeated track can retain identical metadata but still needs its new playback anchor. Every published state has a fresh increasing `state_seq`, so progress is updated even if title and artist did not change.

Display normalization must be deterministic and tested. Replace CR/LF/tab and control characters with spaces; normalize whitespace; decompose Latin accents and strip combining marks; map common smart quotes/dashes and ligatures to printable equivalents. Use explicit replacements such as `ß→ss`, `æ→ae`, and `œ→oe`. Unsupported non-ASCII codepoints become `?`, not raw UTF-8 fragments. Bound the result by the field's wire byte limit, using a final `...` when it was truncated. Set the lossy-text flag when normalization loses meaningful characters or truncates. Original strings remain available in the phone's preview, not in a listening-history database.

For absent title/artist/album, transmit an empty string; the Flipper supplies “Unknown track”, “Unknown artist”, or “Album unavailable”. This avoids conflating an actual song named “Unknown track” with a missing field in internal tests.

## 3.7 Playback position and synchronization
`PlaybackState` supplies position, speed, state, and last-position-update time; that timestamp uses Android elapsed realtime rather than wall time. The helper must pair each position with its own timestamp, not the callback-arrival time. [S16]

For a valid anchor at `anchorMs`, at phone time `nowMs`:
```text
age = max(0, nowMs - lastPositionUpdateTime)
if timestamp is 0/invalid: age = 0
if state permits progression and speed is finite:
    projected = position + age * speed
else:
    projected = position
projected = max(0, projected)
if duration is known: projected = min(projected, duration)
```

Read one consistent playback-state object; do not project an already-updated position again using an unrelated old timestamp. Handle future timestamps, unknown positions, zero/negative/NaN/infinite speed, and duration inconsistencies. Convert wire speed to signed thousandths, clamped to the supported range. Map fast-forward/rewind states with a valid speed to the protocol's progressing state; buffering/connecting freeze until a usable playing state arrives.

Send a full SNAPSHOT after handshake, player change, track/metadata change, or display-setting change. Coalesce metadata callback bursts for up to 100 ms, but do not indefinitely delay a pause/seek update. Send STATE for playback-only changes and every 10 seconds while connected, including paused/no-player states so the display is refreshed. At a metadata transition, send a newly sampled STATE immediately after the SNAPSHOT finishes sending; this corrects the snapshot's transmission delay.

Projection is performed when a queued state is actually serialized, not when an old timer callback was enqueued. Periodic re-query uses the current media controller and does not blindly add 10 seconds to the last value. Do not send every missed interval after a suspension.

## 3.8 Command routing and exactly-once-attempt behavior
Route each valid COMMAND through one function on the domain actor. Check handshake readiness, matching connection session, nonzero and matching `player_epoch`, TTL, known command/origin, and deduplication before performing side effects. Track revision is advisory; two quick Next presses against the same player are legitimate and must not be rejected just because the first already changed tracks.

A duplicate `(connection_session, message_id)` with identical payload returns the stored result and never repeats the operation. A duplicate ID with a different payload is a protocol error. Reserve the ID in the deduplication table before calling out to a media API, so reentrancy cannot execute it twice. Cache the latest 32 command outcomes for up to 30 seconds. No automatic action retry, even on a missing acknowledgement.

For OK, choose one dispatch route per press. Prefer `TransportControls.pause()` while playing and `.play()` while paused/stopped when the corresponding action is supported. Otherwise, if the session supports play/pause media-button handling, send exactly one targeted KEYCODE_MEDIA_PLAY_PAUSE down/up pair through that controller. For previous/next prefer supported transport controls; a targeted media-key pair is an alternative for controllers that advertise media-button behavior. Never call transport controls and then optimistically send a media key as well because no callback arrived quickly. [S20]

Use `SystemClock.uptimeMillis()` for KeyEvent event/down times; use elapsed realtime for playback anchors. Key down and up share the press's downTime. Do not broadcast an untargeted key event that might control a different player. No privilege escalation or shell `input keyevent` runtime path.

For a local playback route, use an explicit music-stream volume adjustment with no audible feedback or route change. For remote/cast playback with adjustable session volume, use that controller's volume API; fixed-volume routes return UNSUPPORTED. Volume operations must run from the valid user-started foreground lifecycle. Do not modify ringtone or alarm volume. Do not pretend a void-returning call proves that the OEM actually changed the audio level. [S17][S20]

Return COMMAND_RESULT status DISPATCHED when the Android API was invoked once; this is not a claim that playback has completed. State callbacks (and a single fresh query roughly 300 ms after a dispatch when useful) provide the authoritative screen update. On SecurityException, stale controller, unsupported action, or explicit denial, return the appropriate error and refresh capabilities. Do not display a persistent fake pause state before the player confirms it.

## 3.9 BLE connection implementation
During visible setup, scan with an exact custom-service UUID filter and a finite window, show only matching devices, and let the user select. Stop scanning after selection/cancellation. Initial connection uses the selected device, LE transport, and a finite direct-connect attempt. Persist that fixed app-profile identity only after the user selects it.

During an armed session, reconnect to the already selected identity using the public `connectGatt(..., autoConnect=true, ...)` mechanism as appropriate; do not repeatedly scan in the background, particularly on legacy Android. Keep at most one GATT object/connection attempt. Use bounded backoff before recreating an abandoned object: 1, 2, 4, 8, 15, then 30 seconds, with small jitter. An auto-connect registration that is simply waiting is not a failed connection to recreate every 30 seconds. Only a stuck operation or explicit failure triggers teardown. Document OS-controlled reconnection latency. [S12]

After link establishment, secure the connection, discover and validate the exact two custom characteristics, enable local indication reception, and write the TX characteristic CCCD with the indication value. Wait for descriptor-write completion before HELLO. Bonded-device state alone does not establish protocol readiness or prove the current write is authenticated.

Use a single GATT operation queue for discover/write-descriptor/write-characteristic/optional MTU actions. Interpret an API call's immediate return as request acceptance, not operation completion. On API 33+, use immutable value-based write overloads; on older APIs copy the value before initiating the operation. Correlate callback with operation identity, characteristic, and generation. Ignore late/duplicate callbacks from closed objects. [S22]

Default write type is WRITE_TYPE_DEFAULT (with response). All initial handshake fragments are at most 20 bytes. An optional MTU request may improve Android→Flipper snapshot throughput, but it is not required to work. Android 14 may request MTU 517 regardless of the requested value; use the observed negotiated result, not the request argument. Limit outgoing characteristic values to `min(mtu-3, 128, peer_rx_value_max)`; absent a successful negotiation use 20. The Flipper→phone path remains 20 bytes per indication in v1. [S22]

Use sensible operation deadlines: approximately 10 seconds for service discovery/security after excluding an active human pairing dialog, 5 seconds for ordinary GATT operations, 5 seconds for HELLO, and 10 seconds for initial snapshot application under minimum MTU. Exceeding a deadline closes the GATT object and returns to a recoverable state. Never invoke hidden `refresh()` or programmatically erase system bonds as a generic error fix.

## 3.10 Publication, heartbeat, diagnostics
Logical frames may be queued by priority but their bytes may not interleave in one direction. Finish a started frame or close the connection. Prioritize COMMAND_RESULT and protocol control frames over new periodic states. Keep at most one unsent latest SNAPSHOT and one latest STATE; replace obsolete unsent updates rather than growing an unbounded backlog.

Every 15 seconds the phone sends a PING; the Flipper returns a PONG. Every valid incoming frame refreshes application-link activity. After 35 seconds without a valid peer frame, mark stale, stop accepting commands, and close/reconnect. A Bluetooth link existing in the OS is not proof that the other application is processing messages.

Diagnostics store bounded event names, connection generation, API/firmware versions, GATT statuses, protocol types/sizes, and timings. Redact titles, artist/album, addresses, session identifiers, bond material, notification contents, and other apps' data by default. Export only via explicit user action. Use separate debug logging flags; never log full packet payloads in a normal release.

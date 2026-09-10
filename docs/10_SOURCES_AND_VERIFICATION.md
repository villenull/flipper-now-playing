# 10 — Sources, verification, and remaining evidence

**Research date: 9 September 2026.** This catalog is part of the implementation contract. Bracketed source IDs elsewhere point here. References are official platform documentation or the platform maintainer’s source repository. No third-party application listing is used as proof that the proposed implementation works.

## 10.1 How to interpret this document
The product requirements, UUID allocation, packet formats, timing choices, queue sizes, screen coordinates, and test thresholds are **project decisions** defined by this handoff. Source references establish platform behavior and constrain those decisions; they do not imply that the platform authors endorsed this project.

The firmware source links intentionally target release **1.4.3**. Android documentation can change; the implementation agent must record actual SDK/toolchain versions and revisit relevant behavior changes before building. Resolve a full firmware commit from the official tag and retain the API-symbol checksum in the project lock. No full commit hash was fabricated during preparation.

## 10.2 Primary-source catalog

### [S01] Flipper external applications
External FAP packaging, SD-card deployment, exported-API compatibility, and documented fbt targets. Use it for build mechanics, not as proof that a custom radio service has passed a hardware test.

`https://developer.flipper.net/flipperzero/doxygen/apps_on_sd_card.html`

### [S02] Official firmware release baseline
The selected reproducible firmware baseline is official 1.4.3. The implementation agent must resolve the full commit and lock the SDK/toolchain. This handoff does not substitute a guessed full SHA or promise compatibility with every firmware fork.

`https://github.com/flipperdevices/flipperzero-firmware/releases/tag/1.4.3`

### [S03] Stable exported API manifest
The manifest reports API 87.1 and exports profile switching, key-path selection, GATT wrappers, event-dispatcher registration, and relevant GUI/Furi functions. Audit every actual import, including the +/− marker; a visible header alone is insufficient.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/targets/f7/api_symbols.csv`

### [S04] Stable GATT wrapper implementation
Important version-specific finding: characteristic-update returns false on success, while service-add returns true on success. Characteristic initialization returns void. The update wrapper may retry with sleeps. Normalize results and keep it off BLE callback and GUI threads.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/targets/f7/ble_glue/furi_ble/gatt.c`

### [S05] Profile template and GAP structures
Profile template/base layout and GAP service-UUID, identity, bonding, and pairing configuration. Inspect the selected headers for exact field names, lifetimes, and array representation; do not invent a desktop BLE API.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/targets/f7/ble_glue/furi_ble/profile_interface.h`
`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/targets/f7/ble_glue/gap.h`

### [S06] BLE dispatcher and firmware service reference
Examples of vendor-event interpretation, characteristic writes, indications, and completion handling. Reuse documented patterns, not the stock serial service instance or its RPC-owned callbacks. Import operations through exported wrappers.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/targets/f7/ble_glue/furi_ble/event_dispatcher.h`
`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/targets/f7/ble_glue/services/serial_service.c`

### [S07] Public Bluetooth service API implementation
Profile start/restore, disconnect, status callbacks, and key-storage path operations. Synchronous service APIs enqueue work and wait; calling them from the service callback thread can deadlock. Private settings helpers are not automatically FAP APIs.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/applications/services/bt/bt_service/bt_api.c`

### [S08] Existing media remote input behavior
The stock media view sends media actions on press, consumes short Back, and permits exit on long Back. This project preserves those user-level mappings while replacing the wire transport; its bounded volume-repeat policy is a project design decision.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/applications/system/hid_app/views/hid_media.c`

### [S09] Official HID application profile lifecycle
Reference ordering for disconnect, profile-specific key path, custom-profile startup, status callback removal, key-path restoration, and default-profile restoration. Recheck exact ownership and asynchronous callback fencing before implementing teardown.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/applications/system/hid_app/hid.c`

### [S10] Stock serial-profile RPC ownership
The Bluetooth service recognizes its serial profile and connects its byte path to an RPC session. This is the reason for choosing a dedicated project service rather than taking over serial callbacks.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/applications/services/bt/bt_service/bt.c`

### [S11] Official HID profile GAP and service example
Reference for profile base placement, standard supporting services, factory-derived separate address, name formatting, pairing mode, and initial connection parameters. The project’s byte-2 +2 convention is a proposed app identity, not an official globally reserved address.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/lib/ble_profile/extra_profiles/hid_profile.c`

### [S12] Android background BLE guidance
Background connection strategies, process-lifetime limits, autoConnect behavior, and foreground-service/companion options. The baseline intentionally uses a user-started connectedDevice service, visible discovery, and reconnect to a chosen device without indefinite scanning.

`https://developer.android.com/develop/connectivity/bluetooth/ble/background`

### [S13] Foreground-service types and startup restrictions
connectedDevice service type, corresponding declarations and runtime prerequisites, and restrictions on starting services from the background. This project does not treat a notification listener or boot receiver as a blanket exemption.

`https://developer.android.com/develop/background-work/services/fgs/service-types`
`https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start`

### [S14] Android Bluetooth permissions
Modern SCAN/CONNECT grants, neverForLocation constraints, and older Android Bluetooth/location declarations. The phone is a GATT client; it does not need an ADVERTISE grant for this design.

`https://developer.android.com/develop/connectivity/bluetooth/bt-permissions`

### [S15] Android notification runtime permission
Distinguishes notification posting permission from notification-listener access. A foreground service still supplies a notification even when ordinary notification visibility is denied; do not make the two grants one setup checkbox.

`https://developer.android.com/develop/ui/views/notifications/notification-permission`

### [S16] PlaybackState API
Playback states, position, playback speed, supported actions, and the elapsed-realtime update timestamp. The Android projection uses a matched position/timestamp pair. Receipt-time anchoring on the Flipper is a separate project clock contract.

`https://developer.android.com/reference/android/media/session/PlaybackState`

### [S17] Android 17 background audio hardening
The reviewed page, updated 1 September 2026, documents lifecycle restrictions affecting volume APIs on Android 17 and an additional while-in-use capability condition for target API 37. Invalid volume calls may fail silently. This motivates starting the helper service from visible, explicit user interaction and testing locked-screen controls.

`https://developer.android.com/about/versions/17/changes/bg-audio`

### [S18] MediaSessionManager authorization and controllers
Active-session enumeration and change listeners. An enabled notification-listener component provides an ordinary-app authorization path; this project does not request privileged MEDIA_CONTENT_CONTROL.

`https://developer.android.com/reference/android/media/session/MediaSessionManager`

### [S19] NotificationListenerService API
System binding, manifest permission/intent filter, listener lifecycle, and user-enabled access. Only media-session access is needed; unrelated notification content must not be stored or exported.

`https://developer.android.com/reference/android/service/notification/NotificationListenerService`

### [S20] Media commands and volume APIs
Transport controls, targeted media-key dispatch, controller volume adjustment, and local music-stream adjustment. Declare normal audio-settings permission for the local path. Choose one dispatch route per command; an API call returning normally is not proof that the audible result occurred.

`https://developer.android.com/reference/android/media/session/MediaController`
`https://developer.android.com/reference/android/media/AudioManager`
`https://developer.android.com/reference/android/Manifest.permission#MODIFY_AUDIO_SETTINGS`

### [S21] MediaMetadata API
Standard metadata keys including title, artist, album, and duration. The existence of a key does not prove that a specific Apple Music version populates it; missing-field handling and real-device inspection remain required.

`https://developer.android.com/reference/android/media/MediaMetadata`

### [S22] Android BluetoothGatt API
Connection operations, characteristic/descriptor writes, API 33 by-value methods, callbacks, and requestMtu behavior. Serialize operations and use the observed negotiated MTU; the requested size alone is not evidence of success.

`https://developer.android.com/reference/kotlin/android/bluetooth/BluetoothGatt`

### [S23] Flipper application manifest format
application.fam fields, app ID, entry point, FAP category, and icon requirements. Verify source paths and build target names against the pinned SDK; the app icon is separate from the music-note tile drawn in the view.

`https://developer.flipper.net/flipperzero/doxygen/app_manifests.html`

### [S24] Android API 37 setup and behavior review
References for the agent’s platform-compatibility audit. The handoff chooses compile/target 36 as a baseline, not a claim that API 37 cannot be targeted. Verify a consistent stable SDK/AGP/JDK matrix before promoting; do not blindly select a preview package because a setup page mentions one.

`https://developer.android.com/about/versions/17/setup-sdk`
`https://developer.android.com/about/versions/17/behavior-changes-17`
`https://developer.android.com/about/versions/17/behavior-changes-all`

### [S25] Native Flipper display and font implementation
GUI display constants are 128 by 64; Canvas provides native font metrics and drawing behavior. Source inspection is not permission to import GUI internals. Use exported Canvas functions and validate glyph bounds with the production renderer.

`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/applications/services/gui/gui_i.h`
`https://raw.githubusercontent.com/flipperdevices/flipperzero-firmware/1.4.3/applications/services/gui/canvas.c`

### [S26] Agent instruction-file guidance
Official guidance for repository instruction files. The supplied root AGENTS.md records enduring requirements and execution expectations. Environment configuration and available permissions still determine which commands the coding agent can execute.

`https://developers.openai.com/codex/guides/agents-md`
`https://learn.chatgpt.com/docs/agent-configuration/agents-md`

### [S27] Official uFBT repository
Optional lightweight FAP tooling. The canonical plan uses the pinned official firmware fbt target; an optional uFBT path must use a matching pinned SDK and pass the same import/build audit.

`https://github.com/flipperdevices/flipperzero-ufbt`

## 10.3 Verified during handoff preparation
Source-level inspection established the stable export/API baseline, dedicated profile/GATT building blocks, stock serial-to-RPC ownership, the counterintuitive GATT update return value, stock long-Back behavior, display dimensions/font constraints, and Android media-access/background-lifecycle requirements. The package also includes a tested Python reference codec and frozen valid-frame fixtures; see HANDOFF_VALIDATION.md for actual test execution evidence.

This is **not** a claim that the Android application or FAP has already been implemented, compiled, paired, or installed. The production Kotlin/C code, build scripts, screenshots, and hardware evidence are the development agent’s deliverables.

## 10.4 Must be verified by the implementation agent
| Item | Required evidence |
|---|---|
| Selected firmware SDK and exports | Full commit, API version, symbol checksum, compile result, import audit. |
| Custom service UUID and ATT properties | Real scan/service discovery; expected encrypted write and indication behavior. |
| Identity and bond isolation | Reconnect after FAP relaunch; existing management/HID bonds preserved. |
| Lifecycle cleanup | Repeated exit/restore without dangling callbacks, memory growth, or radio failure. |
| Apple Music metadata | Actual fields and playback timestamps from the user’s installed app version. |
| Locked-screen volume | Audible control on physical phone under its Android version and route; API 37 hardening coverage where available. |
| Pixel layout | Native framebuffer images from production rendering code, including long strings and missing fields. |
| End-to-end latency and battery | Measured results on real hardware, with conditions; not invented from mocked transport timing. |
| APK/FAP artifact compatibility | Install/load against recorded versions; actual checksums and signing status. |

## 10.5 Changes discovered during development
When a later SDK changes a fact, record the exact source and observed build/runtime behavior in docs/DECISIONS.md. Update the smallest affected adapter, version lock, tests, and documentation. Keep the product constraints. A version-specific incompatibility is not permission to switch to a firmware fork, root workaround, cloud relay, or modified official phone app.

Protocol changes require updating the normative specification, constants, Python fixtures, C/Kotlin implementations, and cross-language tests together. A breaking wire change must not continue advertising itself as the same supported FNP/1 contract without an explicit compatibility design. Never adjust a test merely to match a bug in one implementation.

# 04 — Flipper external application implementation

## 4.1 Application shape
Implement a regular external FAP with `application.fam`, app ID `now_playing`, entry point `int32_t now_playing_app(void* context)`, display name Now Playing, and Bluetooth category. Use a 10 × 10 one-bit application icon as required by the application manifest format. The on-screen music-note icon is a separate, larger asset. [S01][S23]

Build against a pinned checkout/SDK for official 1.4.3, exported API 87.1. Resolve the full release commit and record the SDK/API-symbol checksum. Do not build against the moving dev branch and name the result as though it were stable-compatible. FAP compatibility depends on the exported API, not just whether the C source builds. [S01][S02][S03]

Prefer clear modules:
```text
now_playing.c                 top-level ownership and lifecycle
np_app.[ch]                   app aggregate and event handling
np_ble_profile.[ch]           profile template and GAP configuration
np_ble_service.[ch]           service/characteristics and vendor-event adapter
np_ble_adapter.[ch]           SDK-version-specific wrappers and return normalization
np_transport.[ch]             bounded RX, serialized TX, parser handoff
core/np_protocol.[ch]         platform-independent frame and message codec
core/np_model.[ch]            state updates, epochs, revisions, readiness
core/np_clock.[ch]            elapsed/remaining and monotonic projection
core/np_input.[ch]            press/release/repeat reducer
ui/np_view.[ch]               native Canvas implementation
ui/np_layout.h                coordinate constants
np_settings.[ch]              app-local preferences if needed
```

## 4.2 Export audit before implementation
Check the selected `targets/f7/api_symbols.csv`, including the `+`/`-` export marker. A header being present does not imply its functions are callable from a FAP. The initial audit must include:
```text
bt_profile_start
bt_profile_restore_default
bt_disconnect
bt_keys_storage_set_storage_path
bt_keys_storage_set_default_path
bt_set_status_changed_callback
ble_event_dispatcher_register_svc_handler
ble_event_dispatcher_unregister_svc_handler
ble_gatt_service_add
ble_gatt_service_delete
ble_gatt_characteristic_init
ble_gatt_characteristic_delete
ble_gatt_characteristic_update
```
Also audit any Furi queues, threads, timers, GUI functions, advertising calls, battery/device-info service helpers, version/name helpers, and storage utilities actually imported. Do not directly import private `aci_*`/ST functions just because firmware-internal service code uses them. Vendor event structures/constants can be used from exported headers, while operations go through exported wrappers. [S03][S04][S06]

## 4.3 Custom profile and GAP
Create a `FuriHalBleProfileTemplate` with start, stop, and GAP-config functions. The profile instance begins with `FuriHalBleProfileBase`; assert offset zero. Set its config pointer to the live template. Keep template and parameters valid until restoration completes. [S05]

Use the service UUID from the protocol document in the advertised service field; its 128-bit byte ordering must match Android's canonical UUID. Do not assume the ST array representation is the same as a language UUID's network-order byte array. Add a known-UUID byte-order test, and inspect an actual scan where hardware is available.

Start from the official HID GAP conventions for name formatting, device address, pairing, and connection parameters, not from an invented desktop BLE API. The official name helper may include GAP-specific prefix/format bytes; preserve its ABI convention when replacing the visible prefix with `NP`. Cap the advertised name to the actual destination buffer and available advertisement space. A service UUID must remain discoverable even if the display name is shortened. [S11]

Use bonding with numeric confirmation (`GapPairingPinCodeVerifyYesNo` in the pinned source). Set a distinct stable address as specified in the architecture document. Choose a conservative connection interval compatible with the pinned stack, initially following the official working profile rather than inventing a new optimization; measure and tune only after the radio path is reliable. Do not change global Bluetooth settings or other profiles' persistent data.

Include the official battery/device-info services when required by the stock service lifecycle or when safely supported, following the profile reference. They do not carry the music protocol. Keep the custom service's UUID distinct from any standard battery or device-information UUID.

## 4.4 GATT service
Create the custom primary service with enough attribute records for service declaration, RX declaration/value, and TX declaration/value/CCCD; reserve 8 records for the two-characteristic custom service. Add other services with their own counts. RX is WRITE with response; TX is INDICATE. Use variable values, maximum 128 bytes, and authenticated access permissions supported by the selected stack. Do not expose an unauthenticated raw command channel.

Use `BleGattCharacteristicParams` and `BleGattCharacteristicInstance`, not guessed handles. A callback-based data source must report its maximum size when the wrapper queries initialization size, and its actual byte length when updating. Do not return “free this buffer” for storage that is stack-backed, embedded in a long-lived struct, or retained for retry. Zero-initialize instances so partial-initialization cleanup is trackable. [S04][S06]

Validate allocated handles and error paths available through the wrapper. Note that characteristic initialization's public wrapper returns void; inspect how failures are surfaced in the pinned SDK and do not fabricate a Boolean result from it. Record this limitation in the adapter and exercise functional discovery in the hardware probe.

For `ble_gatt_characteristic_update`, the 1.4.3 implementation returns false on success and true on failure. Its insufficient-resource retry loop may block for up to roughly a second. Normalize the result and run sends outside BLE callbacks and GUI rendering. A successful submit is not yet an acknowledged indication: wait for the server-confirmation event before advancing the next TX chunk. [S04]

## 4.5 BLE event adapter
Register an event handler through the exported dispatcher. Decode the actual `hci_uart_pckt` → HCI event → vendor event layout from the pinned headers. Accept RX attribute writes only to this service's RX value handle. Observe CCCD modifications for the TX characteristic and indication-confirmation events. Do not claim events for unrelated handles; return the appropriate “not acknowledged” status for events not owned by this service. [S06]

Treat event bytes as borrowed memory that expires on callback return. Copy at most 128 bytes into a preallocated queue entry and return immediately. Reject oversize writes. No malloc based on peer length, no logging full data, no waits, no drawing, and no synchronous profile operations inside callbacks.

On queue full, do not silently discard an arbitrary middle stream chunk and continue as if the frame were intact. Mark RX overflow, disable media readiness, and request controlled transport reset on the worker. Partial packets must never mutate visible metadata.

Only one TX indication may be outstanding. Chunk size is 20 bytes in v1, independent of a larger MTU. Keep its source storage alive until the wrapper completes and the indication is confirmed or canceled. Apply a 5-second confirmation timeout; on failure signal reconnect rather than spinning indefinitely. STOPPING cancels pending unsent messages and prevents newly queued callbacks from starting work.

## 4.6 Application startup and shutdown
Implement lifecycle as a sequence of acquired resources plus a single cleanup path that handles partial initialization. Do not scatter unconditional frees across multiple exits.

Startup, on the application owner thread:
1. Allocate checked app state, queues, transport worker, view model, and required service records. Open the storage path and verify the SD/app-data directory is usable before changing key stores.
2. Check radio/profile availability through exported APIs only. If unavailable or advertising cannot start, show a clear repair message, including checking Bluetooth settings. Do not import private `bt_get_settings`/`bt_set_settings` helpers or infer the persisted enable setting from a disconnected status. Do not silently rewrite global settings. Long Back exits.
3. Disconnect the existing profile through the public BT API. Follow the official application's settling behavior so radio-side bond storage writes complete before changing storage paths.
4. Set the application-specific key file path, then start the custom profile. Handle a failed start without leaving that path selected.
5. Register the status callback and begin advertising using the supported exported APIs. Establish callback lifetimes before events can arrive. Display Waiting for phone.
6. Process HELLO and snapshots, then activate media input only after an applied snapshot establishes a current player.

Normal stop, also on the owner thread:
1. Set STOPPING atomically. Invalidate command readiness, stop held-key repeats and UI timers, and stop accepting new work.
2. Optionally send BYE only if it can be submitted immediately without delaying exit. Never wait indefinitely for the phone.
3. Clear the public status callback and fence already-running callbacks. Disconnect while the app's key-storage path is still selected. Follow the radio/NVM settle behavior in the official reference.
4. Cancel and join transport work before profile resources are freed. Ensure a TX submit is no longer running. Do not join while holding a lock required by that worker.
5. Reset the key-storage path to the default, then call and verify default-profile restoration. The previous profile's stop callback unregisters its event handler and frees its own service resources while its code is still loaded.
6. Only after successful restoration and callback quiescence, remove the GUI view, free queues/model/app allocations, and close records. Clear any LED/backlight override that this app actually set.

This order follows the official app's separation of disconnect, key-path restoration, and default profile. A sleep alone is not a general substitute for callback fencing; use source semantics, worker joins, and generation checks. [S07][S09]

If default-profile restoration fails, do not unload a FAP whose profile template/callbacks remain referenced. Show a bounded retry/error screen and keep the relevant allocations alive; retry restoration from the owner thread. Offer clear restart instructions as a last-resort recovery, not an automatic firmware reset. Record this condition prominently in hardware testing.

## 4.7 Bond persistence and repair
Store bonds in `APP_DATA_PATH("bt.keys")` or the correctly resolved equivalent for this application ID, not the global Flipper keys file and not the stock HID file. Persist only through supported firmware APIs. The profile identity must be the same across app restarts.

Ordinary cleanup restores the global key-store path but does not delete this app's bonds. Repairing a broken pair is a user-triggered operation affecting only the custom profile. In v1, document how to forget the custom `NP` device on Android and reset only this application's bond data while the profile is safely inactive. Do not invoke `bt_forget_bonded_devices` with the default key-storage path selected. Do not include a “reset all Bluetooth” button.

## 4.8 Memory and responsiveness budgets
Initial design budgets: one 788-byte protocol assembly buffer; fixed 128-byte RX entries, queue depth 8; bounded frame TX buffers; at most one pending snapshot and state; no unbounded strings; roughly 192+128+192+48 bytes plus terminators for display text. A 128 × 64 one-bit framebuffer is 1,024 bytes if a test adapter needs one. Prefer the native Canvas in production.

Aim for application-owned heap under 32 KiB and a conservative checked thread stack (initial FAP stack 4 KiB, adjusted only from measurements). These are engineering targets, not claims about total firmware RAM. Measure the linked FAP's loaded code/data and stack high-water marks as well as heap. Do not add a large font library or JSON runtime without demonstrating capacity.

Allocate large RX/TX/text buffers in the app heap, not on a small callback stack. Check every allocation and use integer bounds before copying or multiplying. Avoid formatting untrusted text as a format string. Use short bounded locks for model snapshots. GUI and input should not wait on BLE operations.

## 4.9 Core safety rules
Parser, time estimator, input reducer, and state reducer compile on a host with no Furi headers. Use explicit little-endian reads/writes and `memcpy` where required; do not cast unaligned peer bytes to a packed C struct. Use 64-bit intermediates for playback math. Reject unsupported flags/reserved fields. Limit every queue and give overflow a defined outcome.

Track ownership of each initialized characteristic, service, event handler, timer, thread, and record. The profile stop callback is idempotent with respect to its own initialization flags, but it must not free its instance twice. Verify from the selected HAL source whether the profile implementation or framework owns the allocation; the initial implementation should explicitly account for that ownership rather than copying a reference leak or assuming automatic free.

No successful acceptance result may be based on a screen that is simply a bitmap of the approved render. The display must be generated from the live production model and update from parsed phone data.

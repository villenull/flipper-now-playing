# Current device validation — v0.3 Quick Settings

2026-09-12: authorized read-only ADB inventory returned no attached devices. New Quick Settings behavior has no device PASS claim. Prior emulator/Flipper observations below are historical and apply only to the earlier artifacts. No device installation or firmware changes were performed for v0.3.

Pending checks on API26/30, API33, API34/36 and API37 when available:

1. Upgrade v0.2 in place; confirm retained device/player settings and original certificate.
2. Add via Android13+ system prompt; decline and add manually. On older Android use the pencil/Edit panel.
3. With setup missing, tap tile and complete permissions/device selection. Long-press only opens settings.
4. With saved setup, tap On: one short Starting screen, service notification appears, screen closes. Check Connecting, Waiting, Connected, Bluetooth off and repair statuses without exposing metadata.
5. Tap Off while connected, reconnecting, Bluetooth off, and after access revocation. Confirm service, notification and reconnect work stop.
6. Lock phone: starting requests unlock; cancel leaves helper off. Stopping works while locked. Validate audible volume after tile-start and relock, especially API37.
7. Rapid taps, rotation/Back during start, shade close/reopen, remove/re-add tile and process death must not duplicate starts, restart a stopped service or leave a stale On tile. After force-stop, reopen app as Android requires.
8. Run the existing Apple Music, headset, reconnect and default-bond acceptance below with recorded APK/FAP hashes.

## Historical hardware evidence (pre-v0.3)

# Initial environment — NOT_RUN / BLOCKED

No supported Android/Flipper pair was available. No APK or FAP was installed on any attached device, and no firmware, pairing, media, application data or settings were changed.

Read-only ADB inventory found an NVIDIA SHIELD (`product:thor`, `device:roth`) running Android 5.1/API 22. `pm list packages com.apple.android.music` returned no package. This device is below the helper's required API 26 minimum and cannot validate it. Its serial is intentionally omitted from distribution reports. No Flipper USB device was visible. `/dev/kvm` is unavailable; no accelerated Android emulator was available or started.

Commands (exit 0):

```sh
.cache/android-sdk/platform-tools/adb devices -l
.cache/android-sdk/platform-tools/adb -s SELECTED_SERIAL shell getprop ro.build.version.sdk
.cache/android-sdk/platform-tools/adb -s SELECTED_SERIAL shell getprop ro.build.version.release
.cache/android-sdk/platform-tools/adb -s SELECTED_SERIAL shell pm list packages com.apple.android.music
```

## Remaining acceptance work

- **BLOCKED: compatible hardware required.** Actual encrypted custom-service discovery, numeric pairing, minimum-MTU handshake, rejected pairing, app identity persistence and default-bond restoration.
- **BLOCKED: API 26+ phone with Apple Music required.** Already-playing discovery, actual album/duration/position keys, one-attempt media controls and local/remote audible volume with Bluetooth headphones.
- **BLOCKED: API 37 test platform required.** Android 17 locked-screen volume/background-audio hardening. No API 37 behavior is claimed.
- **NOT_RUN:** two-hour locked-screen endurance, 50 reconnects, 30 start/exit cycles, outstanding indication teardown, permission revocation/range loss recovery and SD failure on physical Flipper.
- **NOT_RUN:** first-screen/command/reconnect latency percentiles, loaded FAP memory, heap trend and stack high-water marks. File size is not a loaded-memory measurement.
- **NOT_RUN:** emulator UI, real service lifecycle and synthetic-session instrumentation. Host tests do not prove platform callback behavior.

Use `tests/integration/README.md`, its scripted scenario JSON and `docs/acceptance_matrix.csv`. Record tested artifact hashes and actual device/app/firmware versions before marking any hardware row PASS. The test-player APK is under `dist/developer-tools/`; it is separate from the production helper.

The seven production-render images are **host simulations at 128×64**, not device captures. They demonstrate layout and native rasterization, not physical display or radio behavior.

## Subsequent USB installation (2026-09-10)
A physical Flipper became available after the initial preview. User-authorized copy to `/ext/apps/Bluetooth/now_playing.fap` passed full 22,980-byte readback verification. Device runs Momentum mntm-012 / API 87.1, not the official-firmware acceptance baseline. No firmware/settings/bond changes were made. App launch, BLE pairing, controls and cleanup remain NOT_RUN; this installation does not change their acceptance status. Local evidence: `build/evidence/device-install.json`.

### Startup defect corrected
First launch failed at SD root stat; CLI reproduced invalid name/path. Corrected FAP uses storage_sd_status. Normal long-Back exit, replacement readback, loader running and app-directory creation observed. On-screen readiness/pairing remain pending. Evidence: build/evidence/device-startup-fix.json.

- User confirmed corrected app displays **Waiting for phone**. Startup screen PASS on Momentum mntm-012; Android BLE pairing and media controls still pending.

## 1.1 artwork update
The API28 AOSP emulator now passes 5 actual Android ArtworkReader checks: embedded bitmap, center crop/borrowed ownership, missing image, no HTTP fetch, stale callback. This does not validate BLE. Physical artwork, rapid-skip correspondence, content-provider availability, Android17 volume and full pairing/cleanup remain open. User must update the physical phone APK; it is not connected over ADB.

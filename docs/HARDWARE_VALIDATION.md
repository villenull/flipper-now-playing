# Hardware validation — NOT_RUN / BLOCKED

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

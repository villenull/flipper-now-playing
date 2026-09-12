# Now Playing v0.3 — build and validation report

Built 2026-09-12. Android versionCode **3**, versionName **0.3**, package `io.github.flippernowplaying.bridge`. Quick Settings is additive: setup and settings remain available. This is a preview with physical tile/phone/Flipper acceptance still pending.

## Artifacts

| File under dist/ | Bytes | SHA-256 |
|---|---:|---|
| `flipper-now-playing-debug.apk` | 980,866 | `7eae1472c0ebc81aa9d8a8e962a800968cc15eb30d354f1f187e4203e5a1ab6c` |
| `flipper-now-playing-release-unsigned.apk` | 692,960 | `dd5a3c4df81e7fa5ff846a6427ea76d914eed6da6516ad375a4287f34d2dba26` |
| `now_playing-fw1.4.3-api87.1.fap` | 23,580 | `e37870ca54414f26fe918fe9feb4f3493ebe7bb5acba019b872397b19cea4df5` |
| `developer-tools/np-testplayer-debug.apk` | 834,873 | `5dda313804a49b2047be6c3d701bfbf0965638b3982fd6c4454d060cffd06304` |

The debug APK is installable and signed by the original development certificate, SHA256 `84af0fed14f74dc3d177870628d375ce0577364150036348c72ee9e736c25251`. The release APK is unsigned. FAP sources are unchanged for this Android feature; the compatible FAP was rebuilt and audited. An existing artwork-capable FAP does not need replacement to use the tile.

## Implemented behavior

- Tap the tile to start or stop the armed connection session. Its highlight indicates enabled, including while waiting for the peripheral.
- Show Off, Set up, Connecting, Waiting for Flipper, Connected, Bluetooth off or Check setup. Never show song metadata in Quick Settings.
- Starting opens a short visible activity, starts only after resume/window focus, and waits for foreground-service entry before closing. Missing setup opens the existing permission/device flow; blocked starts return to setup.
- Starting from a locked screen requests unlock. Stop remains available while locked or permissions are missing. Long-press opens setup without starting.
- Standard tile listening and bounded state observers refresh the displayed state after process death and while the shade is visible. Removing the tile does not stop the service.
- Add to Quick Settings uses the Android13+ system prompt and provides manual Edit/pencil instructions on earlier platforms.

## Commands and results

- `./scripts/bootstrap.sh --accept-android-sdk-license`: exit0 with the project’s previously recorded license authorization; dependencies installed into .cache. `./scripts/doctor.sh`: exit0.
- `NP_DEBUG_KEYSTORE=/path/to/existing/debug.keystore ./scripts/test_all.sh`: final exit0. Initial sandboxed download failed; an authorized network run then identified lint findings, which were corrected before the final pass.
- PASS: 30 Python reference tests; production C ASan/UBSan; eleven native128x64 renderer states and pixel checks; 722 valid plus731 malformed independent C/Kotlin/reference codec comparisons.
- PASS: 14 Kotlin core plus8 Android-module unit tests (five new tile-policy tests), covering revoked permissions, stopped processes with stale READY status, enabled waiting/connected states and routing to setup rather than starting without prerequisites.
- PASS: Android debug lint: No issues found. Debug, unsigned release, test-player and artwork instrumentation APKs compiled; final Gradle run155 tasks (67 executed,88 up-to-date).
- PASS: FAP API87.1 metadata and all76 actual imports exported by the pinned official SDK. APK package/version/API/permissions/signature audited from actual artifacts. Packaged manifest inspection confirms the tile’s BIND_QUICK_SETTINGS_TILE protection, non-exported start activity and long-press preference action.
- PASS: original APK certificate verified; final source/artifact digest recorded in build/evidence/validation.json.
- NOT_RUN: Quick Settings/system-UI instrumentation, locked-screen start/stop, Android17 audio, physical BLE/Apple Music/volume/reconnection/bond restoration. Authorized `adb devices -l` returned no attached devices. Earlier five artwork checks on API28 are historical, not rerun or counted as a v0.3 device pass.
- NOT_RUN: LeakSanitizer under ptrace; AddressSanitizer/UndefinedBehaviorSanitizer use detect_leaks=0 as previously documented.

The legacy Intent overload below API34 has a narrow documented lint suppression because AGP8.10’s detector flags it despite the version guard. A runtime API<34 check protects that helper; API34+ always uses PendingIntent. No lint baseline, global suppression, new permission or runtime dependency was added.

## Build baseline

Official Flipper1.4.3, commit8622f1a2b83d8f4918dd5fa3f43de963f6d6f819, API87.1/f7; exported CSV SHA256 da9f564c06e20cdaa28070d2f9a2a3753774ba0f78b0a72bf4cbae726e5f8f77. Official toolchain39, ARM GCC12.3.1, fbt/SCons4.7.0. Android min26/compile36/target36, platform36 revision2/build-tools35.0.0. AGP8.10.1, Gradle8.11.1, Kotlin2.1.20, Temurin17.0.16+8. Host Python3.14.7/GCC16.2.1. Exact downloads are locked in .toolchains.lock.json.

## Evidence

Current logs, manifest inspection and validation stamp are bundled with the release. Production renderer images are host simulations using pinned native fonts/rasterization, not physical screenshots. Packaging verifies tested source/artifact digests and excludes generated build/toolchain/Kotlin caches and signing keys. Fresh toolchain setup was performed for this work; the final pipeline includes incremental Android tasks and is not claimed to be a byte-reproducible clean rebuild.

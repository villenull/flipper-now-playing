# Now Playing v0.1 — build and validation report

GitHub release **v0.1** packages application build 1.1 (Android versionCode 2); this keeps upgrade compatibility with the earlier test APK.

Both production apps are updated for 45×45 album artwork and five-second metadata scrolling. Control legends are removed; all physical mappings and long Back exit remain. This is a **preview**, not a completed physical phone/Flipper acceptance release.

## Actual artifacts

| File under dist/ | Bytes | SHA-256 |
|---|---:|---|
| `flipper-now-playing-debug.apk` | 958,501 | `e9f12be9dd107d895794b3e5c0bcbeae0af7cc2446280b32f5831e0080007618` |
| `flipper-now-playing-release-unsigned.apk` | 682,992 | `0e9adc4aab3d337950db04e4ec02306528c33b1054a8b6b2bf19ac8b9a0cde02` |
| `now_playing-fw1.4.3-api87.1.fap` | 23,580 | `bbfaa8a68718c1846cfc607f9e112e7cacee8ff332dab4d41994a8e7446fcfab` |
| `developer-tools/np-testplayer-debug.apk` | 839,665 | `06dcddb2136e29ce8988b6a55444583747c2c70f37667fda927606b9a9e6de67` |

The debug APK is installable, uses the original development signing identity and increments versionCode to 2/versionName 1.1. The release APK is unsigned. The FAP targets official firmware 1.4.3/API87.1; the connected device runs Momentum mntm-012 with matching API. No firmware or bonds are erased.

## Exact build baseline

| Tool / platform | Version |
|---|---|
| Official Flipper firmware | 1.4.3 |
| Firmware commit | `8622f1a2b83d8f4918dd5fa3f43de963f6d6f819` |
| Exported API / hardware target | 87.1 / f7 |
| ARM compiler | GCC 12.3.1 20230626, official Flipper toolchain 39 |
| fbt | Pinned firmware script; SCons 4.7.0, bundled Python 3.11 |
| Android min / compile / target | 26 / 36 / 36 |
| SDK platform / build tools / platform tools | 36 revision 2 / 35.0.0 / 37.0.1 |
| Android Gradle Plugin / Gradle | 8.10.1 / 8.11.1 |
| Kotlin | 2.1.20 |
| JDK | Eclipse Temurin 17.0.16+8 |
| Host tests | Python 3.14.7, GCC 16.2.1; Clang 22.1.8 available |

The selected AGP supports API 36 and Gradle 8.11.1: https://developer.android.com/build/releases/agp-8-10-0-release-notes . Downloaded archives and their verified SHA-256 values are in `.toolchains.lock.json`. Initial Android SDK archives were also checked against Google's repository SHA-1 values. The official fbt script pins toolchain 39; its bootstrap uses HTTPS but supplies no archive checksum, a remaining upstream provenance limitation.

API symbol CSV SHA-256: `da9f564c06e20cdaa28070d2f9a2a3753774ba0f78b0a72bf4cbae726e5f8f77`.

The packaged FAP has **76 actual undefined imports**, all marked exported (+) in that exact CSV. `audit_artifacts.py` reads `.fapmeta` and verifies API 87.1 rather than inferring it from the filename. The intermediate debug ELF contains an fbt-only symbol stripped from the final FAP; the audit correctly uses the packaged FAP.

## Commands and results

`./scripts/test_all.sh` completed with exit 0 after final source changes. It runs:

```sh
python3 -m unittest discover -s protocol -v
python3 scripts/test_host.py
./scripts/build_android.sh
./scripts/build_flipper.sh
python3 scripts/compare_codecs.py
python3 scripts/audit_artifacts.py
python3 scripts/validation_stamp.py
```

- PASS: 30 Python reference tests, retaining original 16 vectors plus 6 new extension fixtures.
- PASS: 14 Kotlin core tests and 3 Android module unit tests, including fixed artwork vectors/every split, black/white/transparent/dither conversion, bounded decode sampling, malformed artwork and coalesced priority.
- PASS: C ASan/UBSan, all 22 vectors/every split/every bit corruption, bounded noise, clock/input checks, stale/invalid artwork rejection, track-change clearing and 5s/12px-sec/1.5s scroll boundaries.
- PASS: 722 valid fixed/random and 731 malformed frames agree across independent reference, production C and Kotlin codecs.
- PASS: Android debug lint has no issues; debug, unsigned release, test-player and instrumentation APKs built (155 Gradle tasks).
- PASS: APK package/version/min/target/permissions/signature; FAP API metadata and all 76 imported symbols exported.
- PASS: Eleven native 128×64 renderer states including missing art and start/4.999s/6s scrolling. Pixel tests preserve artwork/gutters and confirm no displayed controls. Enlarged production renders visually inspected. The cover in host screenshots is an original synthetic test fixture, not downloaded album art or a device capture.
- PASS: Actual Android artwork-reader instrumentation on the available API28 AOSP emulator: embedded bitmap, center crop/source ownership, missing artwork, rejected HTTP URI and obsolete callback. Both APK installs and the five-check runner completed successfully. This is not a BLE or Apple Music test.
- NOT_RUN: LeakSanitizer under ptrace; ASan/UBSan use detect_leaks=0, with no other findings suppressed.
- NOT_RUN: Physical phone-to-Flipper artwork, URI-provider interoperability, screen-off/Android17 volume, latency, bond restoration and endurance. Use HARDWARE_VALIDATION.md.

Emulator command:

```sh
adb -s emulator-5584 install -r dist/flipper-now-playing-debug.apk
adb -s emulator-5584 install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5584 shell am instrument -w io.github.flippernowplaying.bridge.test/io.github.flippernowplaying.bridge.ArtworkInstrumentation
```

## Implementation and compatibility

Application version 2 is negotiated within the existing FNP1 envelope. New/old peers negotiate v1 without artwork. The optional 282-byte ARTWORK payload is identity-bound, atomically applied and below controls/state in publication priority. Android decodes on a bounded worker and uses fixed 45×45 Bayer dithering. It reads embedded metadata bitmaps or already-readable content URIs; it does not fetch HTTP artwork or request new permissions. No art blocks READY; missing art uses a note placeholder.

The first physical startup also exposed the original root-stat guard defect. The corrected app checks exported storage_sd_status, then probes its own directory; no storage protections were removed. The user observed Waiting for phone after this fix.

## Evidence and remaining limits

Current logs and validation stamp are in build/evidence and bundled in dist/evidence. Packaging rejects source/artifact changes after validation. The initial 1.0 fresh-source build had identical FAP/unsigned release outputs; retained clean-* evidence describes that older digest, **not** a fresh rebuild of 1.1. The original GitHub Actions run passed; the new release's CI is tracked separately.

USB installation/readback and loader status are recorded separately from actual phone artwork or control observations. Earlier blanket unavailable-device statements in historical logs refer to the initial environment. An API28 emulator and Momentum Flipper are now present; no physical Android phone is connected through ADB.

The supplied workspace has managed Git metadata; publication uses a separate Git checkout. Source archive/digest identify the local build; the GitHub tag identifies the published source commit. No signing keys, caches or device bond files are included.

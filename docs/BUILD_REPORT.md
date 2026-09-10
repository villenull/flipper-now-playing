# Now Playing — build and validation report

Both production applications are implemented and built. The software verification pipeline passes; this is **not a device-validated release**. Physical custom-GATT pairing, Apple Music behavior, screen-off volume and default-bond restoration remain blocked on a supported phone and Flipper. No device software/settings were changed.

## Actual artifacts

| File under dist/ | Bytes | SHA-256 |
|---|---:|---|
| `flipper-now-playing-debug.apk` | 938,152 | `197827e2ad523d32c1b03829d4b6ce46145107f6d09cbffe7d93a8f9e6f19fa1` |
| `flipper-now-playing-release-unsigned.apk` | 677,780 | `e2a13a7a0cedcfefc57cceef3a9eba0e53dc228f38e090e80893cb907984f33c` |
| `now_playing-fw1.4.3-api87.1.fap` | 22,980 | `12ef06bb2e8d41f136b74505b70fe896421bf5e59e8325085f5fcbffdb4a3102` |
| `developer-tools/np-testplayer-debug.apk` | 839,147 | `d57a84e6e1a08303b828d6d60224438c1d719e7909214d0723d5b3f4c034004d` |

`flipper-now-playing-debug.apk` is installable and development-signed. `flipper-now-playing-release-unsigned.apk` is deliberately unsigned; no release signing key was requested, generated or distributed. The developer-tools APK is a separate controllable synthetic MediaSession fixture. Source ZIP, screenshots, instructions, manifest and checksums are produced by the packaging script after verification.

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

The packaged FAP has **77 actual undefined imports**, all marked exported (+) in that exact CSV. `audit_artifacts.py` reads `.fapmeta` and verifies API 87.1 rather than inferring it from the filename. The intermediate debug ELF contains an fbt-only symbol stripped from the final FAP; the audit correctly uses the packaged FAP.

## Commands and outcomes

All commands below finished with exit 0 in the primary project workspace:

```sh
./scripts/bootstrap.sh --accept-android-sdk-license
./scripts/doctor.sh
./scripts/test_all.sh
```

The test/build pipeline executes:

```sh
python3 -m unittest discover -s protocol -v
python3 scripts/test_host.py
./android/gradlew -p android --no-daemon :core:test :core:codecJar :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :testplayer:assembleDebug
# In .cache/flipper-firmware, after staging the project's FAP sources:
./fbt fap_now_playing
python3 scripts/audit_fap_imports.py
python3 scripts/compare_codecs.py
python3 scripts/audit_artifacts.py
python3 scripts/validation_stamp.py
```

- **PASS:** 27 supplied reference tests.
- **PASS:** 9 Kotlin core tests, including every golden split, corruption, production state serialization, normalization/clocks, duplicate/expired commands, bounded publication, operation generations/timeouts, stable selection and counter exhaustion.
- **PASS:** 3 Android-module permission-policy tests for API 26/30/31/33/34/36/37 decision paths. These are unit tests, not emulator lifecycle tests.
- **PASS:** Android debug lint, **no issues found**; unsigned release lint-vital/build also succeeds.
- **PASS:** C AddressSanitizer and UndefinedBehaviorSanitizer tests: all 16 vectors/every split/every one-bit corruption, 200,000 noise bytes, parser bounds/timeouts, atomic state rejection, clock wrap/freeze and physical-key reducer/repeat limits.
- **PASS:** 616 fixed/random valid frames and 619 malformed frames agree between C, Kotlin and the independent reference.
- **PASS:** GATT return adapter tests both false-on-success and true-on-error against the pinned wrapper's semantics.
- **PASS:** Seven native 128×64 renderer states plus pixel assertions for icon/marquee gutters, marker and control geometry. Approved/long/reconnecting frames were visually inspected; images use production drawing code and exact pinned native font/rasterizer data.
- **PASS:** Actual APK package/min/target, absence of prohibited permissions, and debug APK v2 signature verified. Certificate fingerprint is retained in `evidence/apk-signature.txt`; no private key is bundled.
- **NOT_RUN:** LeakSanitizer (LSan aborts under the environment's ptrace tracing). ASan/UBSan ran with only `detect_leaks=0`; no sanitizer findings were suppressed.
- **NOT_RUN:** Emulator/platform lifecycle instrumentation and all physical radio/Apple Music tests. See HARDWARE_VALIDATION.md and the mixed-status acceptance matrix.

Full logs and the source/artifact validation stamp are under `build/evidence/` and copied into the release evidence directory. The stamp rejects stale source or binaries during packaging. Earlier compile/lint failures were fixed, not baselined away; the execution log records them.

## Implemented runtime behavior

Android uses its actual media-session APIs, strict Apple Music/selected-package filtering, original metadata preview, ASCII normalization, a user-started connectedDevice foreground service, explicit pairing, fixed 20-byte GATT writes, serialized callbacks/operation timeouts, fresh handshakes/snapshots, bounded coalescing, heartbeat and duplicate-command reservation. No Internet permission, notification-body collection, audio focus/routing API, playback engine, account or backend is present.

Flipper uses a distinct authenticated RX / indicated TX GATT service, factory address byte-2 +2 identity, app-specific bond storage, a confirmation-gated TX worker, bounded RX/command queues, atomic model application, native 128×64 Canvas UI, time projection, all specified key mappings and bounded held volume. Startup checks writable app-data storage; exit joins the worker, fences/unregisters callbacks, restores default keys/profile and only then unloads. Restoration failure retains the FAP with a retry/error screen rather than unloading referenced code.

## Evidence limits

The attached NVIDIA SHIELD is API 22 (Android 5.1), below minimum, without Apple Music. No compatible Flipper/phone pair or accelerated emulator was available. Real authenticated discovery, numeric pairing, indication timing, default-bond preservation, OEM background behavior, Android 17 volume, latency, loaded heap/stack and endurance remain unverified. No performance numbers are claimed. The code-level tests and reviews do not exhaustively model every Android/Furi callback interleaving; hardware acceptance remains a release gate.

The workspace's managed `.git` directory is not a usable Git checkout. The manifest therefore records a source digest and source ZIP rather than inventing a project commit. The upstream firmware commit is real and verified.

## Fresh-source rebuild before final display-name update

A fresh source copy under `/tmp/fnp-clean-m1o34dmm` completed `./scripts/test_all.sh` with exit 0: all **130 Android tasks executed**, all host tests ran, and the FAP was recompiled after `./fbt -c fap_now_playing` removed only generated outputs. Downloaded toolchains/dependencies were shared; this is not a claim of a second cold network bootstrap.

After the final disconnect-readiness correction, the primary pipeline passed again and the updated FAP was compiled again from a cleaned target in the fresh copy. Android sources were unchanged from the 130-task clean build. That source digest matched. The later display-name-only update is separately rebuilt and verified; the archived clean-build evidence identifies the earlier source digest. The **unsigned release APK and FAP were byte-identical** to the primary build. Debug APK hashes differed after clean vs incremental builds because archive entry ordering differed; all corresponding ZIP entry contents were byte-identical. Those development artifacts are individually signed/audited/hashed. `evidence/clean-rebuild.json` and `evidence/debug-rebuild-differences.json` record the comparison. Distribution checksums always identify the actual primary artifacts, not an assumed reproducible debug hash.

## Packaging

`python3 scripts/package_release.py` completed with exit 0. The source ZIP audit found 101 source/documentation files, including the real Gradle wrapper and both real BLE implementations, and excluded caches, generated build trees, bond files and keystores. Every manifest size/hash and all `SHA256SUMS` entries were verified. The UUID initializer and RX/TX byte-12 substitutions were independently checked against the canonical protocol UUIDs; `evidence/uuid-byte-order.json` records the PASS.

The user subsequently authorized GitHub distribution under `villenull/flipper-now-playing`. The first release is explicitly a hardware-unverified preview. Runtime source contains no core TODO/NotImplemented/mock transport paths. The acceptance matrix retains NOT_RUN/BLOCKED for incomplete physical/instrumentation evidence; passing software checks do not relabel those rows.

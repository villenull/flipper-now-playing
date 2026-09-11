# Engineering decisions

- Preserve FNP/1 exactly as specified. No discrepancy identified in the reference syntax during initial read.
- Official firmware baseline: 1.4.3, commit 8622f1a2b83d8f4918dd5fa3f43de963f6d6f819, API to be independently audited.
- Fetch dependencies only into project-local `.cache`; do not alter global SDKs, accept SDK licenses silently, or install on devices.
- Use a serial Android Handler actor and platform Views/APIs to keep runtime dependencies small. Pure Kotlin domain code remains independent of Android bindings.
- Android toolchain: AGP 8.10.1, Gradle 8.11.1, Kotlin 2.1.20, Temurin 17.0.16+8, SDK 36 r2 and build tools 35.0.0. AGP compatibility verified with https://developer.android.com/build/releases/agp-8-10-0-release-notes . Exact archives and checksums recorded in the lock/bootstrap.
- Keep GATT writes at 20 bytes in v1. MTU negotiation is optional in the contract; omitting it removes a setup failure path while preserving interoperability at minimum MTU. Measure worst-case metadata throughput on hardware.
- Diagnostics export uses the system document picker (ACTION_CREATE_DOCUMENT), writing only a user-selected URI. This avoids a provider and broad storage permission while retaining explicit redacted export.
- Import audit uses the packaged FAP, not the intermediate debug ELF: fbt removes linker-only `uxTopUsedPriority` from the final file. All 54 initial packaged imports are exported.
- ASan/UBSan work with `ASAN_OPTIONS=detect_leaks=0`. LSan aborts because this managed environment uses ptrace; report leak sanitizer NOT_RUN rather than treating its runtime limitation as a passing leak test.
- Renderer uses native FontPrimary (`u8g2_font_helvB08_tr`, height 8/descender 2) and FontSecondary (`u8g2_font_haxrcorp4089_tr`, height 7/descender 2). Baselines 8/18/27/36 match the handoff. Masks erase only each row's outside columns; note/status decorations are restored afterward. Host adapter links the exact pinned rasterizer/font data; no environment font is bundled.
- The production input owner ticks every 25 ms so the 400/125-ms repeat schedule is representable. View invalidation is at most 10 Hz for animation/held keys and about 1 Hz for ordinary progress; paused short static metadata is not continuously redrawn.
- The Flipper startup checks writable app-data storage with a disposable `.write-probe` before changing key paths. This file is the only temporary storage item the app removes. Bond data is never deleted by runtime repair.
- BLE callback fencing uses an active-callback counter and a short exported Furi scheduler lock around dispatcher unregistration. Dispatcher callbacks originate in BleEventWorker, not interrupts. The application joins its TX worker before profile restoration; waits/radio operations remain outside the scheduler lock. This is a source-level ownership argument, still requiring physical teardown tests.
- Host codec CLIs use line-delimited frame hex to avoid adding JSON parsing code to C/Kotlin production dependencies. The Python interoperability driver generates seeded valid models and malformed cases and invokes both independent production codecs. Direct production payload/model tests supplement opaque frame roundtrips.
- Headless physical probe is the actual Android helper and its explicit state display/diagnostic export, with the separate controllable MediaSession fixture. No fake transport is linked into either production application.
- Runtime counters never wrap: Android sequence exhaustion closes/reconnects; Flipper message exhaustion resets transport. Duplicate commands reserve a result before dispatch, retain at most 32 outcomes/30 seconds, and reject old IDs outside the cache. The transport independently tracks older IDs across non-command frames.
- Default-profile restoration retries while retaining executable resources and an error screen. No automatic reset or firmware flash is used. Source verification does not establish that the radio/profile cleanup passes on a physical device.

## Final disconnect readiness fence
Keep STOPPING and reset/disconnect flags asserted until the transport worker joins. Recheck stopping under the model lock before applying incoming state, guard snapshot readiness, invalidate readiness again after join, and reject controls while STOPPING. This closes the reviewed late-snapshot race without replaying commands. Physical callback timing remains in the hardware acceptance gate.

## User-selected product name and GitHub distribution
The user corrected earlier typos and selected **Now Playing**, and requested a new GitHub repository for easy APK installation. Publish as `villenull/flipper-now-playing`, with matching app display names. Preserve application IDs, BLE UUIDs, NP pairing identity, bond paths, artifact filenames and signing identity to maintain compatibility. First distribution is a clearly labeled hardware-unverified preview. Normative handoff documents retain their original project name.

## SD readiness at startup
Use exported `storage_sd_status`, not a file stat of `/ext/`: FatFs rejects stat of the volume root. Keep the app-data write probe; report each startup gate separately. This corrects the first physical startup failure without relaxing storage or BLE checks.

## Artwork layout approved by user (1.1)
45×45 monochrome album art, metadata in three rows to its right, time/progress below. Remove control indicators per explicit user direction; key mappings and long Back remain unchanged. Overflow rows independently pause 5 seconds, move 12px/sec, pause 1.5 seconds and repeat. No-art fallback is a music note. Application-v2 negotiation and fixed 270-byte art preserve legacy connectivity; docs/05_PROTOCOL.md is normative. Android API/package/signing unchanged; versionCode increments to 2. Artwork decoding uses a bounded background worker, current-identity checks and no network permission; URI providers must already grant access.

## Release naming and catalog-style GitHub presentation
User requested the next public release be **v0.1** and the README resemble the Anki Remote catalog entry. Keep already-tested application build1.1/versionCode2 and its signing identity for in-place upgrades; GitHub tag/release/manifest use v0.1. New icon SVG follows the existing Android vector. Four orange previews come from production128x64 code with unchanged pixel geometry and explicit synthetic-art/simulated labels. No catalog submission is implied.

- README presentation: user prefers one orange version of the approved concept render over the four-state gallery. Label it as a concept and retain a native 128×64 production-renderer link for accurate device-resolution evidence.

## One-click Android setup (lightweight Connect flow)
User asked for fewer steps and a one-tap feel in the helper APK. Reworked MainActivity only (no manifest/permission/service/protocol changes): single Connect/Stop primary button auto-advances Bluetooth permission → media-access settings (auto-continues on return) → exact-UUID scan → immediate start on device tap. POST_NOTIFICATIONS stays opportunistic/non-blocking. Player picker, elapsed/remaining, export, change/forget moved to collapsed Advanced. FR01/FR10 content (3 separate explanations, UUID-filtered choice, explicit pairing, Start/Stop, device info, player mode, time mode, privacy text, redacted export, launcher entry) is preserved, just progressive instead of 7 separate buttons. INSTALL.md steps 5–6 rewritten. No Java toolchain in this environment, so static string/API cross-check only; full Gradle build + device check still pending.

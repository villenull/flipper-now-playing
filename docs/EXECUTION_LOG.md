# Execution log

## M0 — 2026-09-09
- Read AGENTS.md, START_HERE.md, all ten numbered specifications, constants, reference codec and all golden vectors. Inspected approved concept.
- Repository contains handoff only; `.git` is an inaccessible/empty managed mount, not a usable Git checkout. Preserve supplied files.
- `python3 -m unittest discover -s protocol -v`: exit 0, 27 tests passed.
- Available: GCC 16.2.1, Clang 22.1.8, Python 3.14.7. No Java, Gradle, Kotlin, CMake, Android SDK/ADB or ARM compiler on PATH; no matching cached SDK found in initial scan.
- Sandboxed `git ls-remote` failed DNS (exit 128). Authorized network retry succeeded (exit 0): official tag 1.4.3 resolves to 8622f1a2b83d8f4918dd5fa3f43de963f6d6f819.
- Downloading pinned official source/submodules into `.cache/flipper-firmware`. No device writes authorized or performed.
- Current milestone: M0/M1. Next: inspect exact source/export semantics, bootstrap project-local build tools, compile real custom GATT FAP and Android handshake.

## Validation policy
Build and host results do not establish physical pairing, bond restoration, Apple Music metadata or locked-screen volume behavior. Those remain NOT_RUN until measured on authorized devices.

## M1 — compile gate
- Firmware clone/submodules completed, exit 0. All 13 initial required BT/GATT symbols exported (+).
- `./fbt --version` downloaded the official local toolchain, exit 0; SCons 4.7.0, bundled Python 3.11.
- First `./fbt fap_now_playing`: exit 2 (missing vendor packet header and signed MIN argument). Fixed the include and size type, without disabling warnings.
- Second `./fbt fap_now_playing`: exit 0, API 87.1 up to date, APPCHK passed. Artifact: `.cache/flipper-firmware/build/f7-firmware-D/.extapps/now_playing.fap`. This is the transport milestone, not the final product.
- Downloaded checksum-verified Temurin 17.0.16+8, Gradle 8.11.1. Android SDK download/use license authorization requested through execution approval and granted; platform 36 revision 2 and build-tools 35.0.0 downloaded and checked against Google's repository SHA-1. No device writes.
- Source confirms HAL does not free profile instance after stop; custom stop owns free. Avoided BT status callback to reduce teardown races; service dispatcher handles disconnect. HAL profile reinitialization locks core2, then calls stop/unregister before BLE stack teardown.

## M2–M4 — implementation and hardening in progress
- C/Kotlin production codecs independently match all 16 committed vectors; randomized comparison passes 616 valid and 619 malformed frames (`python3 scripts/compare_codecs.py`, exit 0).
- C host sanitizer tests pass every split, every one-bit corruption, 200k noise bytes, timeout, clock wrap/freeze, input press/short dedup and repeat bounds. LSan excluded for ptrace limitation only.
- Android debug APK and test-player APK compiled; first lint failed on API 27 notification-access API with minSdk 26. Added API 26 Settings.Secure component check. Five initial core tests passed, expanded tests being added.
- Subsequent resource build found an apostrophe escaping error in extracted strings; fixing it (not a toolchain blocker).
- Native Canvas production renderer compiled with pinned firmware u8g2 fonts in a host adapter; rendered seven 128x64 states. Inspected approved and maximum-text screenshots. Corrected reversed previous/next triangles. Images are simulations, not device captures.
- FAP including domain/input/renderer built again with APPCHK success. Latest source changes need restaging and rebuild.
- No /dev/kvm, /dev/bus/usb, or ttyACM devices visible in initial read-only check. No emulator/device test passed or claimed.
- Current milestone: M4/M5. Next: finish callback/cleanup review, strengthen domain/transport tests, rerun lint/builds, finish reproducible scripts/hardware harness and release packaging. No final release bundle yet.

## M5 — complete primary software verification
- `./scripts/test_all.sh`: exit 0. 27 reference tests; 9 Kotlin core tests; 3 Android-module tests; C ASan/UBSan; seven renderer/pixel checks; 616 valid + 619 malformed cross-language cases; APKs, FAP, 77-import audit, APK signature/permissions and FAP API audit; validation stamp all PASS.
- Final lint reports no issues. Explicit backup exclusion resources and narrow parameter-guarded permission-string annotation resolved the final warnings. No lint baseline introduced.
- `./scripts/bootstrap.sh --accept-android-sdk-license`: exit 0 using pinned cached archives; `doctor.sh`: exit 0.
- Read-only ADB inventory: SHIELD Android 5.1/API22, Apple Music absent; unsupported for minSdk26. No device writes. No Flipper product found in accessible USB inventory; /dev/kvm unavailable.
- Clean-source check started in `/tmp/fnp-clean-m1o34dmm` with empty app/host build outputs and shared downloaded dependency cache. Ran official `./fbt -c fap_now_playing` (exit 0) first, deleting only generated FAP/dependency build outputs. Clean source pipeline in progress.
- Current milestone: M6. Next: collect clean-source result, finalize reports/matrix, run package_release.py and inspect all hashes/source archive exclusions. Do not alter tested runtime source unless a failure requires it.
- Fresh-source pipeline: exit 0; 130 Android tasks executed; host tests, full FAP compilation, 77-export audit, artifact audit and stamp passed. Source digest matched; unsigned release APK and FAP byte-identical. Debug APKs differ between clean/incremental builds; comparison recorded without claiming identical hashes.
- All seven renderer states visually inspected at nearest-neighbor 6x; native PNGs retained unchanged.

## M6 — packaged
- `python3 scripts/package_release.py`: exit 0; APKs/FAP/source ZIP/native and 6x simulated screenshots/install/compatibility/build/hardware reports/evidence/manifest/SHA256SUMS produced under dist/.
- Independent source ZIP audit: 101 files, actual wrapper and BLE source present, no .cache/.git/.agents/.codex/build trees or *.keys/*.jks/*.keystore. Manifest audit: all 45 listed file sizes/hashes matched on first package pass. Subsequent documentation/evidence additions are rehashed by final packaging.
- UUID byte-order check: actual profile service initializer equals canonical UUID bytes reversed; RX/TX byte12 substitutions match both protocol characteristic UUIDs. PASS; evidence/uuid-byte-order.json.
- All seven native renderer states inspected; no bitmap mock substitutes the production view.
- Remaining external blockers: supported API26+ Android/Apple Music phone and physical Flipper for radio/volume/bond-restoration/endurance tests; API37 environment for audio hardening; no /dev/kvm for accelerated emulator. Attached API22 SHIELD was read only, never installed/changed.
- Current milestone: delivery complete for implemented/buildable software and local package. Device-validation gate remains explicitly open. Resume future device work from HARDWARE_VALIDATION.md and acceptance_matrix.csv; do not restart architecture or claim physical PASS without observations.

## Final teardown correction
- Final review found owner clearing reset/disconnect flags before worker join could allow a late worker snapshot to re-enable readiness. Corrected: STOPPING is set before invalidation; error/disconnect flags remain asserted through join; model apply rechecks STOPPING under its lock; snapshot ACK cannot enable readiness during stop/reset/disconnect; flags clear only after worker quiescence. Re-running software pipeline and fresh FAP compilation; earlier package is superseded until new validation/packaging.
- Final `./scripts/test_all.sh`: exit 0 after all teardown corrections; staged source compared byte-for-byte to production source. 27 reference tests, C ASan/UBSan, seven render checks, 12 Kotlin/module tests (unchanged and up-to-date), lint, 616/619 differential codec cases, 77 exported imports, artifact audits and validation stamp passed.
- Clean FAP target removed/rebuilt (exit 0). First independent artifact audit lacked sourced JAVA_HOME (java not found, exit 127); rerun uses `source scripts/env.sh`. Initial copy/compare used the SDK working directory (exit 1); corrected to project working directory and both succeeded. Firmware metadata fetch under sandbox failed; rerun with approved network access. No runtime change was required for these environment/command corrections.
- Final clean FAP compile/audit/stamp: exit 0 with network and environment configured. Latest source digest matches fresh copy; final FAP and unsigned release APK byte-identical. Clean FAP log and stamp refreshed. Teardown flags stay asserted through worker join, readiness is invalidated after join, and controls reject STOPPING.
- Current milestone: final software verified; packaging refreshed below. Physical acceptance remains explicitly unverified.

## Nowo Playing naming and GitHub release
- User authorized a new GitHub repository for APK distribution and chose Nowo Playing. Updating visible branding; stable IDs, protocol, keys and filenames unchanged. Rebuild/test/package before publishing a public preview under villenull/nowo-playing.
- User corrected both interim names as typos: final display name **Now Playing**, repository **villenull/flipper-now-playing**. No interim repository/release was created. Revalidating final naming after the in-progress build completes.
- Created public repository https://github.com/villenull/flipper-now-playing with `gh repo create`: exit 0. No interim typo repository was created. Final-name `./scripts/test_all.sh`: exit 0; full portable checks, APK/FAP builds and audits passed. Running a final incremental Android build to ensure all corrected display-name inputs are current before publishing. Git publication uses an isolated checkout because the supplied workspace metadata is managed; no global Git identity change.
- Final incremental Android build: exit 0, all changed-name build inputs current (128 tasks up-to-date, 2 executed); APK/FAP audit and source stamp passed. Publishing preview v1.0.0-preview.1 with original signing identity and explicit hardware NOT_RUN status.

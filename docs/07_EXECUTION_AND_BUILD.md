# 07 — Autonomous execution, repository, build, and CI

## 7.1 Repository structure to implement
The handoff files already exist. The agent creates the production paths below; their presence in this plan is not a claim that the apps are already implemented.

```text
AGENTS.md
START_HERE.md
CODEX_KICKOFF_PROMPT.md
assets/approved-concept.png
docs/01_...md through 10_...md
protocol/                         committed specification fixtures/reference
android/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradle/libs.versions.toml
  gradle/wrapper/...              real wrapper and checksum
  gradlew, gradlew.bat
  core/                          pure Kotlin protocol/domain module
  app/                           production helper Android module
  testplayer/                    test-only controllable MediaSession APK
flipper/
  now_playing/
    application.fam
    now_playing.c
    np_*.c, np_*.h
    core/
    ui/
    assets/
  host_tests/
    CMakeLists.txt
    test_*.c
scripts/
  doctor.sh
  bootstrap.sh
  test_all.sh
  build_android.sh
  build_flipper.sh
  audit_fap_imports.py
  compare_codecs.py
  package_release.py
  install_android.sh
  install_flipper.sh
tests/integration/
  scenarios/                     fixed scripted session/transport scenarios
  fake_phone.py                  host-side FNP peer, no production dependency
  fake_flipper/                  protocol endpoint for Android transport tests
  README.md
.github/workflows/ci.yml
.toolchains.lock.json
.gitignore
LICENSE / THIRD_PARTY_NOTICES.md
dist/                            generated, not committed unless policy requires
```

Keep production protocol/domain code separate from the handoff reference codec so tests can compare independent implementations. Production core code is part of each built application, not an unused “testable” duplicate.

## 7.2 M0 — Inventory and lock the environment
Read the complete handoff before changing architecture. Inspect the existing repository; preserve unrelated work. Create the execution log and decision log. Verify Python, C compiler/CMake, Java/JDK, Android SDK, Gradle wrapper availability, Git, and Flipper build prerequisites.

If connected and already authorized, use read-only device queries to record Android API/build, installed Apple Music package/version, and Flipper firmware/API. Do not flash firmware, unlock a bootloader, clear app data, forget bonds, or install debug software merely because a device is visible. Build work does not require modifying a device.

Resolve one compatible stable Android toolchain, initially targeting SDK 36. Prefer JDK 17 if compatible with the selected supported AGP; use the exact documented requirement if not. Pin Gradle distribution/checksum, AGP, Kotlin, AndroidX libraries, build tools, SDK platform, Java, Python requirement, and Flipper commit/API. Use the official source set in document 10. Record how these versions were selected. Do not silently depend on a globally installed moving uFBT SDK.

M0 exit: a working repository skeleton, `.toolchains.lock.json` with exact resolved versions, and a doctor script that clearly distinguishes missing prerequisites from compile/test failures. Dependencies can be fetched into a project-local cache; scripts must not rewrite global SDK, Python, or system-package installations without authorization.

## 7.3 M1 — Prove the risky transport boundary
Implement the real custom profile/service and an external-app manifest. Build a minimal FAP that registers the actual production service and can receive HELLO/respond HELLO_ACK. Inspect the produced FAP imports against the pinned exported API. Normalize GATT wrapper return semantics.

Implement the minimal Android lifecycle, pairing, discovery, subscription, and HELLO path using the final service UUIDs. Keep the rest of the app temporarily simple; this is a development milestone, not the finished deliverable.

Where hardware is available/authorized, run at least 10 launch/connect/exit/restore cycles before relying on the transport. Verify default official-app pairing afterward. Test authentication failure and minimum MTU. Without devices, deliver the real source and compiled FAP/client with a repeatable probe harness and record the physical gate pending; continue remaining work.

M1 must not be replaced with a stock serial/RPC proof, a desktop-only BLE mock, or firmware-internal application that cannot load as a FAP.

## 7.4 M2 — Protocol and domain core
Implement C and Kotlin codecs, bounded stream decoders, state reducers, clocks, and input reducers. Match every committed vector byte-for-byte. Build host command-line codecs that read model JSON and output frame hex, then decode that hex. Keep JSON in development tooling only; it is not the radio protocol.

Add randomized cross-language interoperability, malformed-input tests, sequence/session checks, deduplication tests, queue expiry tests, and wraparound tests. Use an independent monotonic fake clock. Freeze the protocol before adding significant UI code; any change requires editing the normative document, constants, golden vectors, and both encoders together.

M2 exit: deterministic host tests pass; C AddressSanitizer/UndefinedBehaviorSanitizer checks pass where supported; no unbounded allocations or unsupported pointer casts in the parser.

## 7.5 M3 — Complete Android media and lifecycle
Implement all components in document 03, including permission repair, initial media-session discovery, pinned-player selection, callbacks, periodic fresh state, commands, local/remote volume, and ongoing foreground notification. Include a controlled test MediaSession app so missing metadata, seeks, unsupported actions, and session death can be reproduced without an Apple account.

Implement the true production GATT queue and reconnect logic, not a test transport behind a release flag. Add screen-off/session-lifecycle tests where possible. Explicitly validate API 37 audio-hardening behavior when that platform is available; do not upgrade target and ignore new failures.

M3 exit: unit/lint tests pass, debug APK builds, fake-player scenarios execute, and permission denial/cancellation leaves a usable repair path.

## 7.6 M4 — Complete Flipper view and controls
Implement the native 128×64 renderer, exact input semantics, held-volume bounds, missing-data states, reconnect/stale states, and local clock. Supply native-resolution and enlarged nearest-neighbor screenshots generated by the production rendering logic. Complete startup/cleanup, partial-init error handling, and worker/callback ownership.

M4 exit: real FAP builds and import-audit passes; host pixel/bounds/input/clock tests pass; no fake track appears in an unconnected production launch. The user-approved hierarchy is recognizable despite necessary pixel-size adaptation.

## 7.7 M5 — Integrate and harden
Run the scenario matrix in document 08 against production reducers and transports where available. Exercise long metadata at MTU 23; rapid double Next; repeated OK; disconnect while holding volume; session switch during a command; permission revocation; BLE operation timeouts; and repeated profile restoration.

Review the code specifically for deadlocks, stale callbacks, double commands, timer catch-up floods, metadata leaking to logs, unbounded queues, and hidden private API calls. Fix defects rather than merely documenting them. A hardware-only issue is a known failure until retested, not a reason to mark the whole matrix green.

## 7.8 M6 — Package, document, and report
Produce the release layout in document 09, source archive, checksums, and manifest. Write real installation/pairing instructions based on actual artifact names. Include a compatibility table with firmware/API and Android versions actually built/tested.

Run a clean build from a fresh checkout/cache configuration where feasible. Re-run the package/test scripts. Final report must separate implemented features, tests passed, tests failed, tests not run, and environmental blockers. No core placeholders, throw-NotImplemented paths, fake callbacks, or TODO methods in the shipped feature path.

## 7.9 Build interface contract
Implement these project scripts with real error checking. These are **required script interfaces for the agent to create**, not scripts already supplied by this handoff:

```sh
./scripts/doctor.sh
./scripts/bootstrap.sh
./scripts/test_all.sh
./scripts/build_android.sh
./scripts/build_flipper.sh
python3 scripts/package_release.py
```

`doctor.sh` is read-only, prints versions/availability, and exits nonzero when build-critical prerequisites are missing. `bootstrap.sh` fetches only pinned dependencies and verifies available checksums; it must not silently accept agreements outside the environment's authorization policy. `test_all.sh` runs tests, lint, import audit, and contract comparisons and preserves meaningful nonzero exit status. Build scripts copy only successfully produced artifacts into dist. Packaging refuses to label missing/unbuilt artifacts as present.

Android baseline commands, executed from `android/` after the agent creates the project:
```sh
./gradlew --no-daemon :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --no-daemon :app:assembleRelease
```
Choose task names that actually exist in the selected plugin/module setup, and update scripts/docs together when needed. A release assemble without configured signing can legitimately produce an unsigned release APK. Debug APK uses development signing and is installable for testing. Never package a fabricated APK, an AAB renamed to APK, or a failed build's stale output.

For the canonical Flipper build, use a pinned official firmware checkout in `.cache/`, stage/copy the **project's** FAP sources under `applications_user/now_playing`, and run the documented external-app target:
```sh
./fbt fap_now_playing
```
Run that command from the firmware checkout, not the project root. Find the actual output from the build result and copy it to the versioned dist name. Record its exact source path and API. The SDK source tree is a build dependency, not a firmware fork to install. Do not run flash targets. The official docs also provide `./fbt launch APPSRC=...` for authorized development-device launch; do not invoke it without device-write authorization. [S01]

An optional uFBT shortcut must use the same pinned SDK and produce an equivalently audited FAP; it must not silently fetch dev/latest and replace the reproducible baseline. [S27]

## 7.10 CI and quality checks
CI must run reference fixture validation, C host tests/sanitizers, Kotlin core tests, Android lint/unit tests, Android debug build, Flipper FAP build/import audit, and release-package checks. Pin action versions/commits and dependencies. Separate hardware jobs from default hosted CI because a hosted runner's emulator is not a real Flipper radio.

Archive logs and native-render screenshots. Keep test fixture APKs out of the main user distribution unless clearly placed under a developer-tools directory. Treat warnings from the app's own C/Kotlin code as issues to resolve; do not globally suppress warning groups to make a build green. Use narrow documented suppressions for known upstream/toolchain warnings only.

Do not gate correctness on an arbitrary percentage alone. Coverage is useful to identify untested parser and lifecycle branches, but domain tests must demonstrate behavior. Suggested focus is all message variants and malformed branches, all input transitions, every reconnect stage, and each command denial path.

## 7.11 Agent autonomy and stopping conditions
Use the fixed defaults rather than asking which app name, protocol format, icon, language, or Bluetooth strategy to pick. Track unresolved implementation details in the decision log and choose the smallest compatible solution. Parallelize independent Android, Flipper, and test work only after agreeing on the same protocol contract; integration remains one owner's responsibility.

An unavailable phone/Flipper does not block creating production code, compiling, or running portable tests. An unavailable compiler/download/SDK is a real environment blocker for that build, but other implementation and tests should continue. Record the exact failed command and required next step. Never imply a hardware pass from a fixture, or leave only a speculative code outline because the final physical test cannot run.

If a discovered API incompatibility requires changing a non-negotiable user requirement, do not silently make the change. Complete all unaffected work, demonstrate the incompatibility with the precise exported symbol/source/build evidence, and explain the remaining decision. Do not use an unlimited retry loop to hide a deterministic incompatibility.

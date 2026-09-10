# Agent instructions — Flipper Now Playing

Build the complete product specified in this repository. Do not stop at a plan, scaffold, pseudocode, a demo-only UI, or protocol stubs. Work through implementation, testing, packaging, and documentation. Make ordinary engineering decisions autonomously and record them in `docs/DECISIONS.md`.

## Read first
Read `START_HERE.md`, then `docs/01_PRODUCT_AND_DECISIONS.md` through `docs/10_SOURCES_AND_VERIFICATION.md`. Inspect `assets/approved-concept.png`. Read `protocol/constants.json`, `protocol/reference_codec.py`, and `protocol/golden_vectors.json` before implementing either production codec. The Markdown protocol is normative; discrepancies require a documented reconciliation and updated cross-language tests, not silently choosing whichever implementation is easier.

## Preserve the user's requirements
- Apple Music and the official Flipper Android app remain unchanged.
- Deliver a new Android APK and a new external Flipper FAP. Do not require a firmware fork, root, Termux, Shizuku, accessibility automation, account credentials, a server, or an Internet connection for the helper itself.
- Target official Flipper firmware. Build against the pinned exported API, not private firmware internals. Do not silently flash or update a user's device.
- Preserve Up/Down volume, Left/Right previous/next, OK play/pause, and **long Back to exit**. No seeking or menus on these main-screen controls.
- Follow the approved render's hierarchy within an actual 128 × 64 framebuffer. Do not ship a high-resolution mock as though it were a device screenshot.
- Use a dedicated BLE GATT profile/service. Do not hijack the stock serial/RPC callback or assume simultaneous HID and custom BLE profiles.
- Restore default Bluetooth profile and key-storage path before the FAP can unload. Never wipe default Flipper bonds to repair this app's pairing.
- No unrelated notification collection, analytics, network permission, listening-history database, or secrets in the repository.

## Engineering rules
Establish a minimal compile-valid custom-GATT FAP and Android handshake before investing in UI polish. Verify every imported Flipper function against the selected `api_symbols.csv`. The stable 1.4.3 GATT update wrapper has counterintuitive return semantics; inspect implementation, normalize it behind an adapter, and test success and failure.

Separate pure protocol/state/clock/input logic from Android and Furi bindings. C and Kotlin implementations must independently match the fixed golden vectors. Bound all parser buffers and queues. Reject malformed packets without crashing. Never replay queued media commands after reconnection or execute a duplicate toggle twice.

Use a user-started `connectedDevice` foreground service. Do not claim that a notification listener or a periodic worker automatically provides unrestricted background startup or volume-control rights. Test Android 17 background-audio behavior when that platform is available.

Do not introduce broad dependencies, a DI framework, a media playback engine, or a cloud component merely to simplify a small piece of code. Do not weaken tests to conceal defects. Preserve existing unrelated repository work. Do not publish, upload, change signing identity, erase device data, or perform destructive actions without the appropriate authorization.

## Work tracking and evidence
Create and maintain `docs/EXECUTION_LOG.md`, `docs/DECISIONS.md`, `docs/BUILD_REPORT.md`, and `docs/HARDWARE_VALIDATION.md`. Record commands, exit status, toolchain versions, and artifacts. Before context compaction, record the current milestone, failing tests, and exact next step. Resume from that record instead of restarting architecture discussions.

Use the defaults in the specification rather than asking non-blocking questions. An unavailable physical device is not a reason to stop all software work. Complete portable tests, compile/package everything possible, supply the hardware harness/checklist, and clearly label the remaining evidence. An actual missing dependency, authorization, or incompatible exported API must be reported precisely; never fabricate a successful build.

## Definition of done
Both production applications implemented; no core TODO/stub methods; protocol cross-language tests passing; host C sanitizers passing where available; Android unit/lint checks passing; installable debug APK and compatible FAP built where toolchains are available; reproducible build/install scripts; source and license notices; release manifest and checksums; real screenshots from the production renderer; explicit device-test status. A polished screenshot alone is not completion.

# Flipper Now Playing — autonomous development handoff
**Prepared: 9 September 2026 · Handoff version: 1.0 · Wire protocol: FNP/1**

## What this package is
A complete implementation brief for an agent to build two new applications: an Android media-session/Bluetooth helper and a Flipper Zero external application. It includes the approved visual reference, normative behavior and protocol, implementation boundaries, build requirements, and a test/release plan. The Python code included here is a reference codec and fixture checker, **not the Android or Flipper application**.

## Hand it to the agent
Extract this package into the root of the repository where the agent should work. Give the agent access to that repository and paste the contents of `CODEX_KICKOFF_PROMPT.md`. The agent must read `AGENTS.md` and the specification before implementing. The root instruction file follows the documented agent-instruction convention. [S26] No additional conversation history is required.

For a text-only handoff, use the accompanying `FLIPPER_NOW_PLAYING_MASTER_SPEC.md`; also supply `assets/approved-concept.png` and the protocol fixtures when possible. The ZIP is the preferred handoff because it preserves executable fixtures and file organization.

The agent is instructed to finish implementation, execute available tests, build installable artifacts when the environment permits, and leave an explicit hardware-validation report. It must not stop after drafting another plan. It must not claim physical-device success from an emulator or mocked Bluetooth connection.

## Product in one paragraph
The user already has Apple Music and the official Flipper Android application. Leave both untouched. Install a separate, small **Flipper Now Playing** helper APK on the Android phone, and a separate **Now Playing** `.fap` on the Flipper Zero. The phone sends music metadata and playback state over a dedicated BLE GATT service; the Flipper sends media-button commands back. The Flipper screen follows the approved amber-and-black concept, adapted honestly to 128 × 64 pixels. No firmware fork, root, cloud service, Apple API, or API credentials.

## Reading map
| File | Purpose |
|---|---|
| `CODEX_KICKOFF_PROMPT.md` | The single instruction to paste into the development agent. |
| `AGENTS.md` | Persistent project instructions and non-negotiable constraints. |
| `docs/01_PRODUCT_AND_DECISIONS.md` | Scope, defaults, feature requirements, and decisions already made. |
| `docs/02_ARCHITECTURE_AND_RISKS.md` | Architecture, verified platform constraints, state machines, and technical risk gates. |
| `docs/03_ANDROID_IMPLEMENTATION.md` | Android components, permissions, media selection, background lifecycle, and commands. |
| `docs/04_FLIPPER_IMPLEMENTATION.md` | Official-firmware FAP, custom GATT profile, threading, memory, and cleanup. |
| `docs/05_PROTOCOL.md` | Normative UUIDs, framing, message payloads, ordering, and recovery. |
| `docs/06_DISPLAY_AND_INPUT.md` | Pixel coordinates, typography, scrolling, playback clock, and physical buttons. |
| `docs/07_EXECUTION_AND_BUILD.md` | Repository layout, phased autonomous execution, reproducible builds, and CI. |
| `docs/08_ACCEPTANCE_AND_TESTING.md` | Required automated tests, hardware tests, evidence, and acceptance gates. |
| `docs/09_INSTALL_RELEASE_AND_SUPPORT.md` | Packaging, installation, pairing, operational instructions, and troubleshooting. |
| `docs/10_SOURCES_AND_VERIFICATION.md` | Primary-source references and items that still require device verification. |
| `protocol/constants.json` | Machine-readable protocol constants. |
| `protocol/golden_vectors.json` | Deterministic valid frames and fragmentation fixtures. |
| `protocol/reference_codec.py` | Dependency-free reference encoder/decoder; not a production transport. |
| `protocol/test_reference_codec.py` | Executable checks for framing and reference fixtures. |
| `assets/approved-concept.png` | User-approved concept. Visual intent, not a literal pixel specification. |
| `tools/check_handoff.py` | Checks required package files, source references, fixture validity, local links, and integrity. |
| `HANDOFF_VALIDATION.md` | What was actually checked while preparing this package. |

## Important operational truth
A background helper is not an invisible, immortal script. The baseline is a user-started foreground service with a small ongoing connection notification. Once started, it reconnects to the selected Flipper while its process remains alive. After force-stop, reboot, or a process termination that cannot lawfully resume the service, the user opens the helper and taps Start again. This is preferable to promising unreliable automatic startup or adding unnecessary permissions. Android's foreground-service and background-audio requirements inform this design. [S12][S13][S17]

## Completion has two levels
**Software-complete:** both applications are implemented, all available automated checks pass, artifacts build, and remaining device checks are explicitly identified.

**Device-validated:** the real Android phone running Apple Music and the real Flipper pass the pairing, controls, screen-off, reconnection, and restoration checks. Physical availability is an environmental prerequisite, not something an agent can replace with a fabricated result.

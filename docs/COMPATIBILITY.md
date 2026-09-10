# Compatibility and evidence

| Component | Built target | Evidence / limitation |
|---|---|---|
| Android helper | min API 26, compile/target API 36 | Debug and unsigned release APKs; unit/lint evidence in BUILD_REPORT.md |
| Android fixture | API 26+ | Separate developer-tools APK; no production remote-control endpoint |
| Flipper FAP | Official 1.4.3, API 87.1 | Actual packaged imports audited against pinned exported API |
| Android 17/API 37 | Baseline APK may run, behavior unvalidated | No suitable phone/emulator; locked-screen audio hardening NOT_RUN |
| Attached NVIDIA SHIELD | Android 5.1/API 22 | Below minimum; Apple Music absent; not installed or modified |
| Physical Flipper | None available | Pairing, identity/bond isolation, restoration, memory and endurance NOT_RUN |
| Host renderer | Same production drawing source and pinned native rasterizer/fonts | Seven simulated 128×64 images; not device screenshots |

Do not infer compatibility with firmware forks, other API versions, every Android OEM, cast volume routes or background power policies from compilation. Performance targets in the handoff remain unmeasured. The helper transfers metadata/control only and makes no audio-focus or routing calls.

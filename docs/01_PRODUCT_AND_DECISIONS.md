# 01 — Product requirements and decisions

## 1.1 Objective
Build **Flipper Now Playing**, a local, two-way music display and controller. The phone continues playing Apple Music through whichever audio output the user already selected. The Flipper receives display information; it never receives or plays audio. The helper controls the selected Android media session and media volume, not Apple Music's account or library API.

The user approved the visual concept in `assets/approved-concept.png`. Preserve its music-note icon, prominent title, secondary artist and album, progress row, central play/pause icon, previous/next icons, and vertical +/− volume cues. The exact concept is illustrative: the actual screen must be implemented and tested at 128 × 64, not treated as a higher-resolution display. [S25]

## 1.2 Fixed product decisions
| Topic | Decision |
|---|---|
| Android application | New independent APK, visible name **Flipper Now Playing**. |
| Application ID | `io.github.flippernowplaying.bridge` unless that identifier already conflicts in the supplied repository; document any change. This is a project-local choice, not a claim of ownership of an existing organization. |
| Flipper application | External FAP, app ID `now_playing`, entry point `now_playing_app`, display name **Now Playing**, category **Bluetooth**. |
| Existing applications | Do not modify, fork, replace, inject into, or require changes to Apple Music or the official Flipper Android app. |
| Firmware | Official firmware; initial reproducible baseline 1.4.3 / exported API 87.1, verified against source. Record the full commit when checking out. Build additional official versions only as documented variants. [S02][S03] |
| Bluetooth transport | Dedicated custom GATT profile; phone is central/client, Flipper peripheral/server. No simultaneous stock HID connection. |
| First supported media app | Apple Music on Android. Actual field availability and behavior are hardware acceptance items, not presumed guarantees. |
| Player selection | Default **Apple Music only**. Offer an explicit Auto option and a picker of observed active players. Do not silently control an unrelated player. |
| Android minimum | API 26, with legacy Bluetooth discovery permissions limited to older systems. No unsupported claim that every OEM device has identical background behavior. |
| Android compile/target | Baseline compileSdk/targetSdk 36. Add API 37 validation, and promote target to 37 only after its SDK/toolchain and lifecycle tests are verified. Do not use a preview toolchain unintentionally or suppress target-specific failures. This is a sideloaded project, not a Play-store publishing commitment. [S17][S24] |
| Languages | Kotlin on Android; C on Flipper; Python only for development/test tooling. |
| Android UI | One small setup/status activity plus settings and diagnostics. Prefer standard Android/AndroidX views and ViewBinding; Compose is not required. |
| Runtime service | Explicitly started `connectedDevice` foreground service; small ongoing notification while armed. No automatic boot startup in v1. |
| Time display | Default elapsed / total to match approved render. Android setting enables elapsed / remaining. |
| Long text | Scroll the title and album; keep all three metadata fields represented. Do not replace the album row with another feature. |
| Character support | V1 sends safe display strings. Normalize Latin accents and punctuation; show `?` for unsupported characters. Android setup/preview retains the original text. Full arbitrary-script rendering is not a v1 promise. |
| Network | No network permission, backend, telemetry, or helper account. Apple Music's own connectivity is outside this app. |
| Distribution | Sideloadable debug APK for immediate development testing; unsigned release APK or signed release only with authorized signing material; FAP; source; manifest; hashes. |

The exact JDK, Android Gradle Plugin, Gradle, Kotlin, and build-tools patch versions are resolved once during environment setup using a compatible stable set and then committed to a lock/manifest. Do not guess version numbers in generated files or leave dynamic `+`/`latest` dependencies. This is a deliberate environment-resolution task, not a request to redesign the product.

## 1.3 Functional requirements
**FR01 — Setup:** Explain Bluetooth access, notification-listener access, and the foreground connection service separately. The user chooses a Flipper advertising this app's service. System pairing confirmation is explicit and not bypassed.

**FR02 — Connection:** After setup and Start, reconnect to the same selected app-profile identity when it becomes available, as long as the helper remains running. The official app's management connection is not required and should be disconnected before using this FAP.

**FR03 — Display:** Show current title, artist, album, elapsed time, total or remaining time, progress, and playback state. Never display the demo track as real data when the phone has not sent it.

**FR04 — Controls:** Up increases media volume; Down decreases it; Left requests previous track; Right requests next track; OK requests play/pause; long Back exits the FAP. Previous-track semantics are delegated to the player, which may restart the current track. Short Back is consumed and does not exit. [S08]

**FR05 — Held volume:** One immediate step on press; after a hold threshold, bounded repeat steps; stop on release, disconnect, loss of readiness, or exit. No accumulated flood of volume changes after a stall.

**FR06 — State accuracy:** Freeze elapsed time when paused, stopped, buffering, unknown, or disconnected. Follow seek changes and playback speed when published. Resynchronize periodically in addition to callbacks.

**FR07 — Missing data:** Distinguish no active session, missing title, missing artist, missing album, unknown duration, unknown position, and unavailable permissions. Show placeholders; do not infer metadata from filenames, audio analysis, a web search, or fabricated timestamps.

**FR08 — Recovery:** Recover from ordinary Bluetooth drops, profile exit/reentry, notification-listener reconnect, media-session replacement, and permission revocation without app crashes or stuck input states.

**FR09 — Restoring normal use:** On exit, restore the default Flipper Bluetooth profile and default key-storage path. Normal official-app pairing must remain usable without forgetting/re-pairing that default identity. [S07][S09]

**FR10 — Minimal operational UI:** Setup/status activity includes Start, Stop, selected-device information, player mode, total/remaining setting, a privacy explanation, and an optional redacted diagnostic export. Keep a launcher entry so the helper can be restarted and permissions repaired.

**FR11 — No audio interference:** The helper does not start its own media session, request audio focus, change audio output, connect as an audio sink, or force a speaker route. Headphones and Android Auto should continue operating independently; validate ordinary Bluetooth headphones in v1.

**FR12 — Build and support:** Deliver both source trees, scripts, automated tests, actual artifacts where buildable, and instructions sufficient for installation and later rebuild without this chat.

## 1.4 Explicitly excluded from v1
Album artwork transfer, lyrics, browsing playlists, searching Apple Music, login/OAuth, cloud sync, scrobbling, equalization, audio streaming to Flipper, iOS support, Linux MPRIS support, Wi-Fi/USB runtime transports, custom firmware, simultaneous stock HID operation, invisible boot persistence, notification scraping from arbitrary applications, and broad Unicode font distribution. These are not prerequisites for finishing the requested product.

Do not spend the implementation budget adding any of these instead of completing core controls and reliability. The music-note image in the concept is a built-in monochrome icon, not album art.

## 1.5 User workflow
First use: install the APK and FAP; enable Bluetooth on both devices; launch the FAP; open the helper; grant required permissions; select the advertised Now Playing device; confirm system pairing; tap Start; play a track in Apple Music. The helper should immediately publish the current session, including one that was already playing before the FAP opened.

Routine use: leave the helper armed during a listening session, open the FAP, use the buttons, and hold Back to return to normal Flipper operation. The helper waits for the next app-profile connection. Tap Stop in the helper notification/activity when finished. A reboot, force-stop, or service/process termination may require opening the helper and pressing Start again; no misleading “always running” promise.

## 1.6 Performance targets, not unmeasured claims
Target first meaningful screen within 3 seconds after encrypted GATT setup and subscription complete, excluding human pairing. Target an ordinary button-to-Android-dispatch latency below 300 ms at the median and below 750 ms at the 95th percentile on a stable link. Target metadata changes within 1 second for typical short strings; report worst-case long-string/MTU-23 behavior separately. Target elapsed-time error within about 1 second shortly after synchronization; do not claim subsecond precision from an unsynchronized low-bandwidth link. Target reconnection within 15 seconds when the existing background connection is healthy, but measure and document OS-controlled auto-connect delays.

These targets must be measured before being advertised. Under congestion, prefer dropping an expired command to performing it late or twice. A low-power device must remain responsive to long Back even if the phone is slow.

## 1.7 Defaults that avoid blocking questions
Use Apple Music-only selection, elapsed/total, no automatic launch of Apple Music, no boot receiver, ordinary screen backlight policy, no vibration on every command, metadata redaction in diagnostics, one paired Flipper selected at a time, and no listening history. The original render's song and `1:42 / 4:08` are test fixtures only and are not an assertion about a particular released recording.

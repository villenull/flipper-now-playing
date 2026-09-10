# 09 — Installation, packaging, release, and support

## 9.1 Expected distribution after development
```text
dist/
  flipper-now-playing-debug.apk
  flipper-now-playing-release-unsigned.apk   # or authorized signed-release name
  now_playing-fw1.4.3-api87.1.fap
  flipper-now-playing-source.zip
  INSTALL.md
  COMPATIBILITY.md
  BUILD_REPORT.md
  HARDWARE_VALIDATION.md
  THIRD_PARTY_NOTICES.md
  manifest.json
  SHA256SUMS
  screenshots/
```

Only include artifacts that actually built. Missing release signing is not a reason to withhold the installable development APK, but debug signing must be labeled accurately. Never distribute a signing keystore, Flipper bond file, private notification dump, credentials, or font files. Do not copy unrelated user photos into the product release. The approved concept can stay in source documentation and must not be used to imply a device capture.

`manifest.json` records project version/commit, build time, Android min/target/compile API, AGP/Gradle/Kotlin/JDK versions, APK application ID/version code/signing class, Flipper firmware tag/full commit/API, protocol version, artifact filename/size/SHA-256, and test-validation status. Use actual generated values, not placeholders presented as real identifiers.

Archive source and required licenses/notices. Keep original upstream notices for any code/assets incorporated. Audit licensing before copying firmware implementation blocks; referencing an API is different from copying an upstream module. Preserve an existing repository license, and document the chosen compatible license for new code.

## 9.2 Android install instructions to produce
Describe installation through Android's normal APK installer and an optional authorized ADB path. The guide must identify the actual app package and distinguish debug from signed release.

For a development device where installation is authorized:
```sh
adb install -r dist/flipper-now-playing-debug.apk
```

Do not automatically uninstall the existing helper to fix a signature mismatch, because that can remove its settings. Explain that a release build signed with a different key cannot simply replace a development build without a deliberate migration/uninstall decision. Apple Music and the official Flipper app must never be uninstalled or replaced by these scripts.

Explain first-run Bluetooth permission and notification-listener access. On API 33+, explain that the ordinary notification permission is separate. Keep a visible setup screen to repair grants. The helper must never request Apple Music login credentials.

## 9.3 Flipper install instructions to produce
Check the device firmware/API before choosing the FAP. A binary built for another API must produce a clear compatibility explanation, not an instruction to blindly flash different firmware.

Using qFlipper or another already-authorized file-transfer method, copy the built compatible FAP to the SD card under `apps/Bluetooth/now_playing.fap`. The versioned distribution filename may be renamed to this stable installed filename; document that the API compatibility still matters. External apps are stored on the SD card. [S01]

Do not overwrite the stock Bluetooth Remote application. The new application has its own icon/name. Before using it, disconnect the official Android app's active Flipper connection. Launch **Apps → Bluetooth → Now Playing**.

Automatic install/launch scripts, if implemented, must require an explicit device/path selection when multiple devices exist and must not perform firmware flashing or global data cleanup. They should print what they will copy and where, then follow the environment's device-write authorization policy.

## 9.4 Pairing and normal use
Open the FAP and the helper's setup page. Select the device advertising the custom Now Playing service, not just any device whose name contains “Flipper”. Confirm the system pairing code on both devices. After subscription/handshake, tap Start if not already armed and begin a song in Apple Music.

The normal screen should now show metadata. Up/Down adjust volume, Left/Right select previous/next, OK play/pause, and long Back exits. For remaining-time display, change the setting in the Android helper; do not assign a new behavior to the main-screen media keys.

The helper's foreground service can stay armed while the FAP is closed; its notification should clearly say it is waiting. It reconnects only to the selected custom-profile identity. Tap Stop to end that armed session. After force-stop/reboot or a terminated service, open the helper and Start again. Do not promise boot persistence that v1 does not implement.

When the FAP exits normally, the default Flipper profile and its original pairing return. The official Android app may then reconnect normally. Its app and data stay untouched.

## 9.5 Troubleshooting table
| Symptom | Diagnosis / repair |
|---|---|
| New FAP fails to load | Compare binary API/firmware manifest with device; rebuild for the actual official API. Do not assume a dev build is compatible. |
| Helper cannot see Flipper | Launch the new FAP, enable Bluetooth, grant correct scan access, stop other active management connection, scan from visible setup UI. |
| Pairing dialog repeats | Stop attempts, inspect whether user rejected or an app-profile bond is stale. Repair only this profile's pair; never clear all phone/Flipper bonds. |
| Link connected but no metadata | Check notification access, chosen player mode, active session, HELLO_ACK and SNAPSHOT_APPLIED; BLE Connected alone is insufficient. |
| Title appears but album/time is missing | Inspect actual Apple Music MediaMetadata/PlaybackState keys in redacted diagnostics; show honest unavailable fields. |
| Controls affect another app | Verify pinned-player mode and controller epoch; report as a bug, not expected behavior. |
| Volume works with helper open but not locked | Inspect foreground-service lifecycle and Android 17 audio-hardening logs; Start from visible UI and retest. Do not add root/privileged workarounds. |
| Music moves from headphones to speaker | The helper must not route audio or request focus; inspect and remove that behavior. |
| Time keeps running after pause | Check paired timestamp/state projection and stale callback filtering; do not solve by constant polling. |
| Reconnect never happens | Check whether helper is still armed/alive, Bluetooth is on, the selected fixed identity is correct, and no abandoned GATT object blocks retry. |
| Official Flipper app no longer reconnects after exit | Treat as a cleanup/bond-isolation defect. Preserve default bond files and inspect profile/key-path restoration. |
| Accents or non-Latin text change | V1 uses normalized display text; original metadata remains on the phone preview. Document unsupported-script fallback rather than pretending full Unicode. |
| “No response” after a press | No automatic replay; check peer liveness and command result. A new intentional press is a new command. |

## 9.6 Uninstall and reversibility
Exit the FAP normally first. Stop and uninstall only the new Android helper. Delete only `apps/Bluetooth/now_playing.fap` and optionally its clearly identified app-specific data directory. The user's Apple Music, official Flipper app, firmware, stock media remote, and default Bluetooth bonds remain unchanged.

App-specific Bluetooth pairing entries may remain in Android settings after uninstall; the user can explicitly forget the `NP` identity. Do not claim deletion of a FAP automatically erases every OS-level bond. Never delete a broad SD-card apps/data directory or unrelated Bluetooth files.

## 9.7 Final report format
The development agent's final report must include: exact built artifact paths; code/features implemented; tests and their commands; precise platform/version matrix; device tests not run; known actual failures; screenshots labeled device vs simulator; and install/pairing steps. Report performance only with measured evidence. Do not end with an offer to implement the remaining obvious parts of the requested app.

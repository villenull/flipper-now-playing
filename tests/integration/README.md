# Integration and hardware harness

`./scripts/test_all.sh` runs the independent protocol codecs, production C reducers/input/renderer and Kotlin publication, selection, command and operation-gate tests. These are portable tests, not a radio simulation pass.

The debug-only fixture is `dist/developer-tools/np-testplayer-debug.apk`. Install it only on an authorized API 26+ device, open it, then explicitly choose its observed package in the helper. Auto mode deliberately excludes fixture packages. Buttons provide play/pause, seek, missing/Unicode fields, unsupported transport actions and session replacement/destruction. It creates a synthetic MediaSession without playing audio; local audible-volume evidence still needs real Apple Music and headphones.

Use `scenarios/acceptance.json` and every row of `docs/acceptance_matrix.csv`. Start with 10 custom-profile launch/handshake/exit cycles before endurance testing. Capture redacted helper diagnostics using Export. Record APK/FAP SHA-256, Android model/API, Apple Music version, firmware/API, clock/measurement method and exact PASS/FAIL/NOT_RUN outcomes. Never promote host results to hardware results.

Read-only Android inventory:

```sh
.cache/android-sdk/platform-tools/adb devices -l
.cache/android-sdk/platform-tools/adb -s SERIAL shell getprop ro.build.version.sdk
.cache/android-sdk/platform-tools/adb -s SERIAL shell dumpsys package com.apple.android.music
```

Do not publish raw dumpsys output; it may include unrelated package data. Installation requires explicit device authorization and a selected serial. The installation script never uninstalls an application or clears data. Flipper install uses an explicit mounted SD root or manual qFlipper transfer; never run a flash target.

Device acceptance remains manual for matching numeric pairing codes, unauthenticated-access rejection, GATT confirmation timing, audible local/remote volume, radio teardown and preservation of the original bond. The production helper itself is the real phone-side GATT probe; its status distinguishes SECURING, DISCOVERING, SUBSCRIBING, HANDSHAKING, SYNCING and READY. No mock-transport flag exists in the production APK or FAP.

## Artwork acceptance (1.1)
- Install both 1.1 builds; confirm title/artist/album and artwork match after next/previous and rapid skips. Test artwork arriving after metadata, missing artwork and player replacement. No old artwork may be attached to a new revision.
- Use the synthetic test player’s Artwork / no artwork toggle and long album name. Check initial 5-second pause, 12px/sec motion, 1.5-second end pause, fitting rows remaining still, and artwork unaffected by scrolling.
- Connect either 1.0 peer with a 1.1 peer: READY and controls should still work, with no ARTWORK sent. Reconnect while art is decoding and verify the callback cannot publish into the new session.
- Android emulator reader tests: `adb -s SERIAL install -r android/app/build/outputs/apk/debug/app-debug.apk`; install `android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`, then `adb -s SERIAL shell am instrument -w io.github.flippernowplaying.bridge.test/io.github.flippernowplaying.bridge.ArtworkInstrumentation`. This exercises actual bitmap APIs and callback cancellation, not BLE.

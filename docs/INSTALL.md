# Install and use Now Playing

## Compatibility

Use `flipper-now-playing-debug.apk` on Android 8.0/API 26 or newer. It targets API 36. The release APK is **unsigned** and cannot be installed until signed with an authorized key. The debug APK is development-signed and ready for ordinary sideloading. Do not uninstall an existing helper automatically if Android reports a signature mismatch; decide how to preserve settings and migrate signing first.

Use `now_playing-fw1.4.3-api87.1.fap` with official Flipper firmware 1.4.3/exported API 87.1. Compatibility with other firmware has not been verified. No firmware update is required or performed by these scripts.

## Installation

1. Install the debug APK with Android's normal APK installer. For an authorized development phone, run `./scripts/install_android.sh --serial YOUR_SERIAL` from this source directory.
2. Use qFlipper's file browser to copy the versioned FAP to the Flipper SD card as `apps/Bluetooth/now_playing.fap`. Alternatively, with the SD card explicitly mounted, run `./scripts/install_flipper.sh --sd-root /path/to/that/card`.
3. Disconnect the official Flipper Android app's active management connection. Leave that app and Apple Music installed and unchanged.
4. On Flipper, open Apps → Bluetooth → Now Playing. The new app uses a dedicated NP Bluetooth identity and app-specific bond file.
5. Open the helper and tap Connect. It walks the three steps in order: Bluetooth access, then media (notification-listener) access in system settings, then Flipper selection. Returning from settings continues automatically — there is no separate Start tap. Android 13+ notification posting permission is asked once, optionally, after starting and never blocks connection. Restricted settings on sideloaded applications are controlled through Android's own App info/settings UI.
6. When Connect finds your Flipper, tap the device advertising the custom service in the list — selecting it starts the connection immediately. Confirm the matching system pairing code on both devices. Wait for READY, then play in Apple Music. Apple Music-only is the default; Auto or an explicitly observed player, elapsed/remaining, diagnostics export, and change/forget device live under Advanced (collapsed by default).

## Controls and lifecycle

- Up/Down: media volume. Hold for bounded repeating steps; release stops.
- Left/Right: previous/next. No seek behavior.
- OK: one play/pause attempt; the screen changes only when the player publishes state.
- Short Back: exit hint. **Hold Back:** close the FAP and restore the default Bluetooth profile and key path.

Choose elapsed/remaining in the helper; the default is elapsed/total. Missing metadata/time remains visibly unknown. V1 normalizes display text to printable ASCII while retaining original Unicode text in the phone preview.

Start must be pressed from the visible helper activity (now the single Connect button). The connectedDevice foreground service stays armed while that process lives, and reconnects to the chosen identity without background scanning. The ongoing notification includes Stop. After reboot, force-stop or process termination, open the helper and press Start again. No boot persistence or battery exemption is configured. Background volume, particularly Android 17, requires real-device validation.

## Recovery and removal

If pairing was rejected, open the helper and tap Connect explicitly to retry. If an app-specific bond becomes stale, exit the FAP first, stop the helper, forget **only the NP identity** in Android Bluetooth settings, and optionally remove **only** `/ext/apps_data/now_playing/bt.keys` using the authorized file browser while the FAP is inactive. Never delete the stock Bluetooth keys or unrelated apps_data directories.

If default-profile restoration fails, the FAP deliberately remains loaded and retries with an error screen to avoid dangling callbacks. Preserve default bonds and report the failure; do not flash firmware or erase Bluetooth settings as a repair shortcut.

To remove: exit the FAP normally, stop/uninstall only the helper, and delete only `apps/Bluetooth/now_playing.fap` (and optionally its own data directory). Apple Music, the official Flipper app and default bonds remain untouched. Android may retain the NP pairing until explicitly forgotten.

## Rebuild

On Linux x86_64 with Python 3.12+ (safe tar extraction), Git and a C compiler:

```sh
./scripts/bootstrap.sh --accept-android-sdk-license
./scripts/doctor.sh
./scripts/test_all.sh
python3 scripts/package_release.py
```

Review https://developer.android.com/studio/terms before supplying the license flag. Dependencies live in `.cache/`; no global SDK or system packages are rewritten. The firmware source checkout is a build dependency, not firmware to flash. Exact versions/archive checksums are in `.toolchains.lock.json`. The scripts never install onto a device as part of build/test/package.

## Update to 1.1 (album artwork)

Stop the Android helper, install the new debug APK over the existing one, then press Start. The application ID and development signing key are unchanged; versionCode is 2. Update the FAP while it is closed. Both 1.1 applications are needed for artwork; either can still negotiate application version 1 with an older peer and use the no-artwork layout.

Album artwork is a 45×45 one-bit image. Title, artist and album stay at the beginning for five seconds before independently scrolling if too wide; they pause 1.5 seconds at the end. The button legend is removed, but all keys and long Back exit work as before. Missing or inaccessible artwork shows a music note. Embedded metadata bitmaps and readable local content URIs are supported; the helper never downloads HTTP artwork.

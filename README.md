# Now Playing

A small independent Android media-session helper and an external Flipper Zero app. Dedicated authenticated BLE GATT; no Internet permission, server, Apple credentials, firmware fork, root or changes to Apple Music/the official Flipper app.

**[Download the Android APK](https://github.com/villenull/flipper-now-playing/releases/download/v1.0.0-preview.1/flipper-now-playing-debug.apk)** · **[Download the Flipper app](https://github.com/villenull/flipper-now-playing/releases/download/v1.0.0-preview.1/now_playing-fw1.4.3-api87.1.fap)**

Open the APK link on an Android 8.0+ phone, download it, then tap it to install. This preview is built and software-tested; physical BLE/Apple Music acceptance remains unverified. See the release notes before use.

Built artifacts and evidence live in `dist/` after running the build scripts. Start with [installation and pairing](docs/INSTALL.md), [build results](docs/BUILD_REPORT.md) and [hardware validation](docs/HARDWARE_VALIDATION.md). Physical radio, Apple Music and locked-screen acceptance is distinct from successful host tests/builds.

```sh
./scripts/bootstrap.sh --accept-android-sdk-license
./scripts/doctor.sh
./scripts/test_all.sh
python3 scripts/package_release.py
```

Review the Android SDK license before passing the explicit acceptance flag. Toolchains are pinned in `.toolchains.lock.json` and installed only under `.cache/`. Builds/tests never install onto a device or flash firmware. Runtime source is under `android/` and `flipper/now_playing/`; the supplied normative handoff remains in `docs/01_…` through `docs/10_…`.

On Flipper: Up/Down volume; Left/Right previous/next; OK play/pause; **hold Back to exit**. The 128×64 view includes title, artist, album, progress and elapsed/total or remaining time. App-specific bonds remain separate; default profile and key path are restored before unload.

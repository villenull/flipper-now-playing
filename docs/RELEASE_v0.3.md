Now Playing v0.3 adds a Quick Settings toggle for everyday use.

Download **flipper-now-playing-debug.apk** below and install over the earlier Now Playing helper. It keeps the original package and signing certificate; versionCode3/versionName0.3.

Open the helper once and tap **Add to Quick Settings**. On Android8–12, use the Quick Settings Edit/pencil button to add **Now Playing** manually.

- Tap to start or stop the helper. The tile stays highlighted while enabled, including while waiting for your Flipper.
- Starting briefly shows a Starting screen, then returns automatically. No second tap is needed.
- Long-press opens setup/settings. Missing permissions or device selection open setup.
- Starting while locked requests unlock; stopping works while locked.

Your existing artwork-capable Flipper app works with this update. The rebuilt FAP is included for fresh installations; no firmware update is needed.

Validation: full local pipeline passed, including22 Kotlin/Android tests,30 reference tests, C sanitizers,722 valid/731 malformed cross-language cases, Android lint and artifact audits. The installable APK retains the original development certificate. The release APK is unsigned and intended for developers.

**Preview:** physical Quick Settings interaction, phone/Flipper pairing, locked-screen volume and Android17 behavior remain pending. No phone or emulator was attached for this build. Software evidence and checksums are included.

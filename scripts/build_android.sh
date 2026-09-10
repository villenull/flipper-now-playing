#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$NP_ROOT"
mkdir -p build/evidence dist/developer-tools
./android/gradlew -p android --no-daemon :core:test :core:codecJar :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :testplayer:assembleDebug 2>&1 | tee build/evidence/android-build.log
cp android/app/build/outputs/apk/debug/app-debug.apk dist/flipper-now-playing-debug.apk
cp android/app/build/outputs/apk/release/app-release-unsigned.apk dist/flipper-now-playing-release-unsigned.apk
cp android/testplayer/build/outputs/apk/debug/testplayer-debug.apk dist/developer-tools/np-testplayer-debug.apk

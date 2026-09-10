#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
if test "$#" -ne 2 || test "$1" != "--serial";then echo "Usage: $0 --serial DEVICE_SERIAL (explicitly installs the helper debug APK)" >&2;exit 2;fi
exec "$ANDROID_HOME/platform-tools/adb" -s "$2" install -r "$NP_ROOT/dist/flipper-now-playing-debug.apk"

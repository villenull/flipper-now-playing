#!/usr/bin/env bash
set -uo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$NP_ROOT"
missing=0
for tool in python3 gcc git; do
 if command -v "$tool" >/dev/null; then "$tool" --version | head -1; else echo "MISSING: $tool";missing=1;fi
done
for file in "$JAVA_HOME/bin/java" "$ANDROID_HOME/platforms/android-36/android.jar" "$ANDROID_HOME/build-tools/35.0.0/aapt2" android/gradle/wrapper/gradle-wrapper.jar .cache/flipper-firmware/toolchain/current/bin/arm-none-eabi-gcc;do
 if test -f "$file";then echo "FOUND: $file";else echo "MISSING: $file";missing=1;fi
done
if test -x "$JAVA_HOME/bin/java";then "$JAVA_HOME/bin/java" -version;fi
if test -x .cache/flipper-firmware/toolchain/current/bin/arm-none-eabi-gcc;then .cache/flipper-firmware/toolchain/current/bin/arm-none-eabi-gcc --version | head -1;fi
if test -e /dev/kvm;then echo "KVM device present; emulator not yet validated";else echo "No /dev/kvm: accelerated emulator unavailable";fi
exit "$missing"

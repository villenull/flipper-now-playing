#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
if test "$#" -ne 2 || test "$1" != "--sd-root";then echo "Usage: $0 --sd-root /explicit/mounted/Flipper/SD (copy only; never flashes)" >&2;exit 2;fi
if ! test -d "$2/apps";then echo "Expected a mounted Flipper SD root containing apps/" >&2;exit 2;fi
mkdir -p "$2/apps/Bluetooth"
echo "Copying API 87.1 FAP to $2/apps/Bluetooth/now_playing.fap"
cp "$NP_ROOT/dist/now_playing-fw1.4.3-api87.1.fap" "$2/apps/Bluetooth/now_playing.fap"

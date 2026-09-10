#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$NP_ROOT"
python3 - <<'PY'
import json,subprocess,shutil
from pathlib import Path
root=Path.cwd();sdk=root/'.cache/flipper-firmware';lock=json.load(open('.toolchains.lock.json'))
assert subprocess.check_output(['git','-C',str(sdk),'rev-parse','HEAD'],text=True).strip()==lock['flipper']['commit']
shutil.copytree(root/'flipper/now_playing',sdk/'applications_user/now_playing',dirs_exist_ok=True)
PY
mkdir -p build/evidence dist
(cd .cache/flipper-firmware && ./fbt fap_now_playing) 2>&1 | tee build/evidence/fap-build.log
python3 scripts/audit_fap_imports.py | tee build/evidence/fap-audit.log
cp .cache/flipper-firmware/build/f7-firmware-D/.extapps/now_playing.fap dist/now_playing-fw1.4.3-api87.1.fap

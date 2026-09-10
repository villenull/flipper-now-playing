#!/usr/bin/env bash
set -euo pipefail
source "$(dirname -- "$0")/env.sh"
cd "$NP_ROOT"
mkdir -p build/evidence
python3 -m unittest discover -s protocol -v 2>&1 | tee build/evidence/reference-tests.log
python3 scripts/test_host.py 2>&1 | tee build/evidence/host-tests.log
./scripts/build_android.sh
./scripts/build_flipper.sh
python3 scripts/compare_codecs.py 2>&1 | tee build/evidence/cross-language.log
python3 scripts/audit_artifacts.py 2>&1 | tee build/evidence/artifact-audit.log
python3 scripts/validation_stamp.py

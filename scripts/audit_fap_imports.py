#!/usr/bin/env python3
"""Audit actual ELF undefined imports against the pinned official (+) exports."""
import csv, hashlib, json, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
sdk=root/'.cache/flipper-firmware'
api=sdk/'targets/f7/api_symbols.csv'
lock=json.loads((root/'.toolchains.lock.json').read_text())
assert hashlib.sha256(api.read_bytes()).hexdigest()==lock['flipper']['api_symbols_sha256']
exports={r['name'] for r in csv.DictReader(api.open()) if r['status']=='+'}
nm=sdk/'toolchain/current/bin/arm-none-eabi-nm'
elf=sdk/'build/f7-firmware-D/.extapps/now_playing.fap'
lines=subprocess.check_output([str(nm),'-u',str(elf)],text=True).splitlines()
imports={line.split()[-1] for line in lines}
missing=imports-exports
assert not missing, f'Non-exported imports: {sorted(missing)}'
out=root/'build/evidence'; out.mkdir(parents=True,exist_ok=True)
(out/'fap-imports.txt').write_text('\n'.join(sorted(imports))+'\n')
print(f'PASS: {len(imports)} actual imports exported by official 1.4.3 API 87.1')

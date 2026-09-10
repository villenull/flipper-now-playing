#!/usr/bin/env python3
"""Package only successfully tested, audited artifacts; reject stale source/binaries."""
import hashlib,json,os,shutil,subprocess,zipfile
from datetime import datetime,timezone
from pathlib import Path
from validation_stamp import source_digest
root=Path(__file__).resolve().parents[1];os.chdir(root)
dist=root/'dist';validation=json.load(open('build/evidence/validation.json'))
assert validation['status']=='PASS' and validation['source_digest']==source_digest(),'Sources changed since validation; run test_all.sh'
for name,sha in validation['artifacts'].items():assert hashlib.sha256((dist/name).read_bytes()).hexdigest()==sha,f'Stale artifact: {name}'
for name in ['INSTALL','COMPATIBILITY','BUILD_REPORT','HARDWARE_VALIDATION']:
 shutil.copyfile(root/'docs'/f'{name}.md',dist/f'{name}.md')
for name in ['LICENSE','THIRD_PARTY_NOTICES.md']:shutil.copyfile(root/name,dist/name)
screens=dist/'screenshots';screens.mkdir(exist_ok=True)
for p in (root/'build/screenshots').glob('*.png'):shutil.copyfile(p,screens/p.name)
(screens/'README.txt').write_text('SIMULATED: same production Canvas drawing source with pinned firmware rasterizer/native fonts. Native 128x64 PNGs and nearest-neighbor 6x previews. Not device captures.\n')
evidence=dist/'evidence';evidence.mkdir(exist_ok=True)
for p in (root/'build/evidence').iterdir():
 if p.is_file() and p.suffix in ['.log','.json','.txt']:shutil.copyfile(p,evidence/p.name)
archive=dist/'flipper-now-playing-source.zip'
exclude={'.cache','.git','.agents','.codex','.gradle','__pycache__','dist','build'}
files=[p for p in root.rglob('*') if p.is_file() and not any(part in exclude for part in p.relative_to(root).parts) and p.name!='local.properties']
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED) as z:
 for p in sorted(files):
  assert p.suffix not in ['.jks','.keystore','.keys'],f'Forbidden source secret: {p}'
  info=zipfile.ZipInfo(str(p.relative_to(root)),date_time=(2026,9,9,0,0,0));info.external_attr=(p.stat().st_mode&0xffff)<<16
  z.writestr(info,p.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
lock=json.load(open('.toolchains.lock.json'))
manifest={'project':'Now Playing','version':'1.1','source_commit':None,'source_identity':validation['source_digest'],'source_commit_note':'Managed supplied workspace is not a usable Git checkout; source archive and digest identify this build.','build_utc':datetime.now(timezone.utc).isoformat(),'protocol':'FNP/1 envelope; application versions 1 and 2','toolchains':lock,'android':{'application_id':'io.github.flippernowplaying.bridge','version_code':2,'debug_signing':'development debug certificate (verified)','release_signing':'unsigned'},'validation':validation,'artifacts':[]}
for p in sorted(dist.rglob('*')):
 if p.is_file() and p.name not in ['manifest.json','SHA256SUMS']:
  assert p.suffix not in ['.jks','.keystore','.keys']
  manifest['artifacts'].append({'file':str(p.relative_to(dist)),'size':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
(dist/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
files=sorted(p for p in dist.rglob('*') if p.is_file() and p.name!='SHA256SUMS')
(dist/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+str(p.relative_to(dist))+'\n' for p in files))
subprocess.run(['sha256sum','--check','SHA256SUMS'],cwd=dist,check=True)
print('Packaged audited APKs, FAP, source, screenshots, instructions, evidence and checksums in dist/')

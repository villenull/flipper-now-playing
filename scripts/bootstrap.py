#!/usr/bin/env python3
"""Pinned Linux x86_64 bootstrap; all dependencies are project-local."""
import argparse,hashlib,json,os,platform,subprocess,tarfile,urllib.request,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1];os.chdir(root)
ap=argparse.ArgumentParser();ap.add_argument('--accept-android-sdk-license',action='store_true',help='Explicitly accept https://developer.android.com/studio/terms for this SDK installation');args=ap.parse_args()
assert platform.system()=='Linux' and platform.machine()=='x86_64','This lock supplies Linux x86_64 binaries only'
lock=json.load(open('.toolchains.lock.json'));cache=root/'.cache';downloads=cache/'downloads';downloads.mkdir(parents=True,exist_ok=True)
if not args.accept_android_sdk_license:
 raise SystemExit('Android SDK license acceptance required. Review https://developer.android.com/studio/terms then rerun with --accept-android-sdk-license. No license is accepted implicitly.')
for name,item in lock['downloads'].items():
 path=downloads/name
 if not path.exists():
  temporary=path.with_suffix('.download');print('Downloading',item['url'],flush=True);urllib.request.urlretrieve(item['url'],temporary);temporary.rename(path)
 assert hashlib.sha256(path.read_bytes()).hexdigest()==item['sha256'],f'Checksum mismatch: {name}'
 if name=='jdk.tar.gz':
  if not (cache/'jdk-17.0.16+8/bin/java').exists():
   with tarfile.open(path) as archive:archive.extractall(cache,filter='data')
 elif name=='gradle.zip':
  with zipfile.ZipFile(path) as archive:
   if not (cache/'gradle-8.11.1/bin/gradle').exists():archive.extractall(cache)
  (cache/'gradle-8.11.1/bin/gradle').chmod(0o755)
 else:
  destination={'platform.zip':'platforms/android-36','build-tools.zip':'build-tools/35.0.0','platform-tools.zip':'platform-tools'}[name]
  with zipfile.ZipFile(path) as archive:
   for info in archive.infolist():
    rel=Path(*Path(info.filename).parts[1:]);out=cache/'android-sdk'/destination/rel
    assert '..' not in rel.parts
    if info.is_dir():out.mkdir(parents=True,exist_ok=True)
    else:
     out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(archive.read(info));mode=(info.external_attr>>16)&0o777
     if mode:out.chmod(mode)
(root/'android/local.properties').write_text('sdk.dir='+str(cache/'android-sdk')+'\n')
sdk=cache/'flipper-firmware'
if not sdk.exists():subprocess.run(['git','clone','--depth','1','--branch',lock['flipper']['tag'],'--recurse-submodules','--shallow-submodules','https://github.com/flipperdevices/flipperzero-firmware.git',str(sdk)],check=True)
assert subprocess.check_output(['git','-C',str(sdk),'rev-parse','HEAD'],text=True).strip()==lock['flipper']['commit']
# The official pinned fbt script selects compiler 12.3.1 / Flipper toolchain 39.
subprocess.run(['./fbt','--version'],cwd=sdk,check=True)
print('Bootstrap complete. Run ./scripts/doctor.sh then ./scripts/test_all.sh.')

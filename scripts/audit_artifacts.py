#!/usr/bin/env python3
import json,os,struct,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];os.chdir(root)
tools=root/'.cache/android-sdk/build-tools/35.0.0'
env={**os.environ,'JAVA_HOME':str(root/'.cache/jdk-17.0.16+8')}
def run(cmd):return subprocess.check_output(list(map(str,cmd)),text=True,env=env)
apk=root/'dist/flipper-now-playing-debug.apk'
badging=run([tools/'aapt','dump','badging',apk]);permissions=run([tools/'aapt','dump','permissions',apk])
assert "name='io.github.flippernowplaying.bridge'" in badging
assert "versionCode='3'" in badging and "versionName='0.3'" in badging
assert "sdkVersion:'26'" in badging and "targetSdkVersion:'36'" in badging
for permission in ['INTERNET','ACCESS_BACKGROUND_LOCATION','QUERY_ALL_PACKAGES','RECORD_AUDIO','RECEIVE_BOOT_COMPLETED','MEDIA_CONTENT_CONTROL','BLUETOOTH_ADVERTISE']:
 assert 'android.permission.'+permission+"'" not in permissions,permission
signature=run([tools/'apksigner','verify','--verbose','--print-certs',apk])
assert 'Verified' in signature
fap=root/'dist/now_playing-fw1.4.3-api87.1.fap';meta=root/'build/evidence/fapmeta.bin'
objcopy=root/'.cache/flipper-firmware/toolchain/current/bin/arm-none-eabi-objcopy'
subprocess.run([str(objcopy),'--dump-section',f'.fapmeta={meta}',str(fap)],check=True)
magic,version,api,target=struct.unpack_from('<IIIh',meta.read_bytes())
assert magic==0x52474448 and version==1 and api==(87<<16|1) and target==7,(magic,version,api,target)
report=root/'build/evidence';report.mkdir(parents=True,exist_ok=True)
(report/'apk-badging.txt').write_text(badging);(report/'apk-permissions.txt').write_text(permissions);(report/'apk-signature.txt').write_text(signature)
(report/'artifact-audit.json').write_text(json.dumps({'android':{'applicationId':'io.github.flippernowplaying.bridge','minSdk':26,'targetSdk':36,'debug_signature_verified':True},'flipper':{'api':'87.1','hardware_target':target,'size':fap.stat().st_size}},indent=2)+'\n')
print('PASS: actual APK package/min/target/permissions/signature and FAP API 87.1 metadata verified')

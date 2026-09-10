#!/usr/bin/env python3
"""Independent production C/Kotlin codecs vs frozen fixtures and seeded valid/malformed corpus."""
import json,random,subprocess,sys,os
from pathlib import Path
root=Path(__file__).resolve().parents[1];sys.path.insert(0,str(root/'protocol'))
from reference_codec import Frame,snapshot_payload,state_payload
rng=random.Random(82391)
fixed=[x['frame_hex'] for x in json.loads((root/'protocol/golden_vectors.json').read_text())['vectors']]
fixed += [x['frame_hex'] for x in json.loads((root/'protocol/artwork_golden_vectors.json').read_text())['vectors']]
frames=list(fixed)
for i in range(300):
 duration=rng.randrange(1,315360000000);position=rng.randrange(duration+1);state=rng.randrange(1,7)
 args=dict(player_epoch=rng.randrange(1,2**32),track_revision=rng.randrange(1,2**32),state_seq=i+1,state=state,flags=3,speed_milli=rng.randrange(-8000,8001) if state==3 else 0,position_ms=position,duration_ms=duration,capabilities=rng.randrange(32),display_mode=rng.randrange(2))
 text=lambda n:''.join(chr(rng.randrange(32,127)) for _ in range(rng.randrange(n+1)))
 payload=snapshot_payload(title=text(192),artist=text(128),album=text(192),app=text(48),**args)
 frames.append(Frame(16,rng.randrange(1,2**32),i+1,payload).encode().hex())
 frames.append(Frame(17,rng.randrange(1,2**32),i+1,state_payload(**args)).encode().hex())
for i in range(100):
 pixels=bytearray(rng.randbytes(270))
 for j in range(5,270,6):pixels[j]&=31
 import struct
 frames.append(Frame(19,1,1000+i,struct.pack('<IIBBBB',1,i+1,45,45,1,0)+pixels).encode().hex())
bad=[]
for s in frames:
 b=bytearray.fromhex(s);b[rng.randrange(len(b))]^=1<<rng.randrange(8);bad.append(b.hex())
# CRC-valid semantic violations must also be rejected.
import binascii,struct
for offset in (12,13,35):
 b=bytearray.fromhex(fixed[2]);b[16+offset]=255;b[-4:]=struct.pack('<I',binascii.crc32(b[4:-4]));bad.append(b.hex())
art=bytearray.fromhex(fixed[18])
for offset,value in [(0,0),(8,44),(9,46),(10,2),(11,1),(17,255)]:
 b=art.copy()
 if offset==0:b[16:20]=bytes(4)
 else:b[16+offset]=value
 b[-4:]=struct.pack('<I',binascii.crc32(b[4:-4]));bad.append(b.hex())
all_frames=frames+bad
java=root/'.cache/jdk-17.0.16+8/bin/java';jars=list((root/'android/core/build/libs').glob('np-codec*.jar'));assert len(jars)==1
outputs=[]
for command in ([str(root/'build/c_codec')],[str(java),'-jar',str(jars[0])]):
 result=subprocess.run(command,input='\n'.join(all_frames)+'\n',text=True,capture_output=True,env={**os.environ,'ASAN_OPTIONS':'detect_leaks=0'})
 assert result.returncode==0,result.stderr
 out=result.stdout.splitlines();assert out==frames+['INVALID']*len(bad),(command,next((i for i,(a,b) in enumerate(zip(out,frames+['INVALID']*len(bad))) if a!=b),None))
 outputs.append(out)
assert outputs[0]==outputs[1]
print(f'PASS: {len(frames)} fixed/random valid frames and {len(bad)} malformed frames agree in C, Kotlin and independent reference')

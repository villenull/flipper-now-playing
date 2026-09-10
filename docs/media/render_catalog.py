#!/usr/bin/env python3
"""Generate catalog previews from the compiled production renderer, not AI mockups.
Run ./scripts/test_all.sh first, then python3 docs/media/render_catalog.py.
The display bits are unchanged; only palette and integer enlargement are added.
"""
from pathlib import Path
import binascii,struct,zlib,subprocess,sys,tempfile
root=Path(__file__).resolve().parents[2];out=Path(__file__).resolve().parent
sys.path.insert(0,str(root/'protocol'))
from reference_codec import Frame,snapshot_payload

def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',binascii.crc32(kind+data)&0xffffffff)
def write_png(path,data,scale,palette):
 rows=[]
 for y in range(64):
  row=bytes(v for x in range(128) for _ in range(scale) for v in palette[bool(data[y*16+x//8]&(128>>(x%8)))])
  rows.extend([b'\0'+row]*scale)
 path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',128*scale,64*scale,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b''))

cases=[('playing','approved',{}),('paused','paused',{}),('scrolling','scroll-moving',dict(title='Around the World (Live)',artist='Daft Punk',album='Alive 2007 - Live Edition',position_ms=217000,duration_ms=330000)),('no-artwork','no-artwork',{})]
with tempfile.TemporaryDirectory() as temp:
 for name,mode,fields in cases:
  source=Path(temp)/'state.hex';pbm=Path(temp)/'frame.pbm'
  source.write_text(Frame(16,1,1,snapshot_payload(**fields)).encode().hex()+'\n')
  subprocess.run([str(root/'build/render'),str(source),mode,str(pbm)],check=True)
  header,data=pbm.read_bytes().split(b'\n',2)[1:];assert header==b'128 64' and len(data)==1024
  write_png(out/(name+'.png'),data,3,[(255,154,0),(15,15,15)])
  write_png(out/(name+'-native.png'),data,1,[(255,255,255),(0,0,0)])
print('PASS: four production-render catalog previews plus native128x64 originals')

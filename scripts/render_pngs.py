#!/usr/bin/env python3
"""Dependency-free PBM→PNG nearest-neighbor evidence conversion; never retouches pixels."""
from pathlib import Path
import struct,zlib,binascii
def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',binascii.crc32(kind+data)&0xffffffff)
def png(path,pixels,scale):
 rows=[]
 for row in pixels:
  expanded=bytes(v for v in row for _ in range(scale))
  rows.extend([b'\0'+expanded]*scale)
 data=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',128*scale,64*scale,8,0,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b'')
 path.write_bytes(data)
for p in Path('build/screenshots').glob('*.pbm'):
 header,data=p.read_bytes().split(b'\n',2)[1:];assert header==b'128 64' and len(data)==1024
 pixels=[[0 if data[y*16+x//8]&(128>>(x%8)) else 255 for x in range(128)] for y in range(64)]
 png(p.with_suffix('.png'),pixels,1);png(p.with_name(p.stem+'-6x.png'),pixels,6)

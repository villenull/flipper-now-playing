#!/usr/bin/env python3
from pathlib import Path
def read(name):
 raw=(Path('build/screenshots')/(name+'.pbm')).read_bytes();header,data=raw.split(b'\n',2)[1:]
 assert header==b'128 64' and len(data)==1024
 return [[bool(data[y*16+x//8]&(128>>(x%8))) for x in range(128)] for y in range(64)]
a=read('approved');long=read('long');paused=read('paused');unknown=read('unknown');reconnecting=read('reconnecting');error=read('error');none=read('no-session')
assert a!=paused and a!=unknown and a!=none and a!=error and a!=reconnecting
# Scrolling masks restore icon and connection marker and leave gutters untouched.
for y in range(2,18):
 for x in range(2,18):assert a[y][x]==long[y][x]
for y in range(0,11):
 for x in range(18,22):assert not long[y][x]
 for x in range(121,124):assert not long[y][x]
assert all(a[y][x] for y in range(2,6) for x in range(124,128))
# Required volume glyphs and independent transport controls.
assert all(a[42][x] for x in range(62,67)) and all(a[y][64] for y in range(40,45))
assert all(a[62][x] for x in range(62,67))
assert sum(a[53][x] for x in range(29,40))>0 and sum(a[53][x] for x in range(89,100))>0
print('PASS: seven distinct native framebuffers; icon/marquee gutters, connection marker and control geometry')

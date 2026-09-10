#!/usr/bin/env python3
from pathlib import Path
def read(name):
 raw=(Path('build/screenshots')/(name+'.pbm')).read_bytes();header,data=raw.split(b'\n',2)[1:]
 assert header==b'128 64' and len(data)==1024
 return [[bool(data[y*16+x//8]&(128>>(x%8))) for x in range(128)] for y in range(64)]
a=read('approved');long=read('long');paused=read('paused');unknown=read('unknown');reconnecting=read('reconnecting');error=read('error');none=read('no-session')
assert a!=paused and a!=unknown and a!=none and a!=error and a!=reconnecting
# Artwork is a fixed 45x45 island that scrolling must never overwrite.
for y in range(1,46):
 for x in range(1,46):assert a[y][x]==long[y][x]
for y in range(0,46):
 for x in range(46,49):assert not long[y][x]
assert all(a[y][x] for y in range(2,6) for x in range(124,128))
start=read('scroll-start');wait=read('scroll-wait');moving=read('scroll-moving')
assert start[:46]==wait[:46], 'Metadata must remain stationary for first five seconds'
assert start[:46]!=moving[:46], 'Overflow metadata must scroll after pause'
assert a!=read('no-artwork'), 'Missing artwork must use visible fallback'
# Controls were explicitly removed; time/progress is now at the bottom.
assert not any(a[y][x] for y in range(46,56) for x in range(128))
assert any(a[58][x] for x in range(32,96))
print('PASS: artwork gutters preserved, five-second pause/scroll, fallback, time/progress and no control legend')

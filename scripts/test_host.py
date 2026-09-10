#!/usr/bin/env python3
import json,os,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1];os.chdir(root)
build=root/'build';build.mkdir(exist_ok=True)
def run(cmd,**kw):subprocess.run(list(map(str,cmd)),check=True,**kw)
core=Path('flipper/now_playing/core');u=Path('.cache/flipper-firmware/lib/u8g2')
flags=['cc','-std=c11','-Wall','-Wextra','-Werror','-fsanitize=address,undefined','-g','-I'+str(core)]
for name,files in [('c_codec',['np_protocol.c']),('test_core',['np_protocol.c','np_model.c'])]:
 source='codec_cli.c' if name=='c_codec' else 'test_core.c'
 run(flags+[core/f for f in files]+[Path('flipper/host_tests')/source,'-o',build/name])
v=json.load(open('protocol/golden_vectors.json'))['vectors'];extra=json.load(open('protocol/artwork_golden_vectors.json'))['vectors'];(build/'golden.hex').write_text('\n'.join(x['frame_hex'] for x in v+extra)+'\n')
run([build/'test_core',build/'golden.hex'],env={**os.environ,'ASAN_OPTIONS':'detect_leaks=0'})
# Compile the very same production rendering source with the pinned upstream
# rasterizer and fonts, through a small Canvas API adapter. No substitute font.
run(['cc','-std=c11','-O1','-ffunction-sections','-fdata-sections','-Wl,--gc-sections','-I'+str(u),'-Iflipper/host_tests/include','-I'+str(core),'-Iflipper/now_playing/ui','flipper/host_tests/render.c','flipper/now_playing/ui/np_view.c',core/'np_model.c',core/'np_protocol.c',*[p for p in u.glob('*.c') if p.name!='u8g2_glue.c'],'-o',build/'render'])
out=build/'screenshots';out.mkdir(exist_ok=True)
for name,index in [('approved',2),('paused',2),('long',15),('unknown',2),('no-session',5),('reconnecting',2),('error',2),('no-artwork',2),('scroll-start',15),('scroll-wait',15),('scroll-moving',15)]:
 p=out/(name+'.hex');p.write_text(v[index]['frame_hex']+'\n');run([build/'render',p,name,out/(name+'.pbm')])
run(['python3','scripts/render_pngs.py'])
run(['python3','scripts/check_pixels.py'])
print('PASS: production renderer generated all eleven 128x64 simulated states')

#!/usr/bin/env python3
import hashlib,json,os,xml.etree.ElementTree as E
from pathlib import Path
from datetime import datetime,timezone
root=Path(__file__).resolve().parents[1]
def source_digest():
 files=[root/'.toolchains.lock.json']
 for folder in ['android','flipper','protocol','scripts','tests']:
  files.extend(p for p in (root/folder).rglob('*') if p.is_file() and not any(x in p.parts for x in ['build','.gradle','__pycache__']) and p.name!='local.properties')
 h=hashlib.sha256()
 for p in sorted(files):h.update(str(p.relative_to(root)).encode()+b'\0'+p.read_bytes())
 return h.hexdigest()
if __name__=='__main__':
 os.chdir(root);suites=[]
 for folder in ['android/core/build/test-results/test','android/app/build/test-results/testDebugUnitTest']:
  paths=list(Path(folder).glob('TEST-*.xml'));assert paths,f'No tests in {folder}'
  for p in paths:
   suite=E.parse(p).getroot();assert int(suite.attrib['failures'])==0 and int(suite.attrib['errors'])==0
   suites.append({'name':suite.attrib['name'],'tests':int(suite.attrib['tests'])})
 lint=Path('android/app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt').read_text()
 assert 'No issues found' in lint or '0 errors, 0 warnings' in lint,lint
 names=['flipper-now-playing-debug.apk','flipper-now-playing-release-unsigned.apk','now_playing-fw1.4.3-api87.1.fap','developer-tools/np-testplayer-debug.apk']
 artifacts={n:hashlib.sha256((Path('dist')/n).read_bytes()).hexdigest() for n in names}
 Path('build/evidence/validation.json').write_text(json.dumps({'utc':datetime.now(timezone.utc).isoformat(),'source_digest':source_digest(),'artifacts':artifacts,'unit_suites':suites,'status':'PASS','hardware':'NOT_RUN'},indent=2)+'\n')
 print('PASS: validation stamp matches tested sources and built artifacts')

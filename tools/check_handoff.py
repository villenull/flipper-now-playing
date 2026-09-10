#!/usr/bin/env python3
"""Dependency-free integrity and consistency checks for the *handoff*, not the apps.

Run from any directory: python3 tools/check_handoff.py
Add --tests to run the bundled reference-codec unittest suite as well.
SHA256SUMS, when present, is checked by default; --skip-integrity permits deliberate
editing after the handoff has become an implementation repository.
"""
from __future__ import annotations
import argparse
import csv
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    'START_HERE.md', 'AGENTS.md', 'CODEX_KICKOFF_PROMPT.md',
    'HANDOFF_VALIDATION.md', 'assets/approved-concept.png',
    'protocol/constants.json', 'protocol/golden_vectors.json',
    'protocol/reference_codec.py', 'protocol/test_reference_codec.py',
    'docs/acceptance_matrix.csv',
    'docs/01_PRODUCT_AND_DECISIONS.md', 'docs/02_ARCHITECTURE_AND_RISKS.md',
    'docs/03_ANDROID_IMPLEMENTATION.md', 'docs/04_FLIPPER_IMPLEMENTATION.md',
    'docs/05_PROTOCOL.md', 'docs/06_DISPLAY_AND_INPUT.md',
    'docs/07_EXECUTION_AND_BUILD.md', 'docs/08_ACCEPTANCE_AND_TESTING.md',
    'docs/09_INSTALL_RELEASE_AND_SUPPORT.md', 'docs/10_SOURCES_AND_VERIFICATION.md',
]

def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--tests', action='store_true')
    parser.add_argument('--skip-integrity', action='store_true')
    args = parser.parse_args()
    for name in REQUIRED:
        p = ROOT / name
        if not p.is_file() or p.stat().st_size == 0:
            raise ValueError(f'Missing/empty required file: {name}')
    spec = importlib.util.spec_from_file_location('reference_codec', ROOT/'protocol/reference_codec.py')
    if spec is None or spec.loader is None:
        raise ValueError('Cannot import codec')
    codec = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = codec
    spec.loader.exec_module(codec)
    const = json.loads((ROOT/'protocol/constants.json').read_text())
    expected = {
        'version': codec.VERSION, 'magic_hex': codec.MAGIC.hex(),
        'service_uuid': codec.SERVICE_UUID, 'phone_to_flipper_uuid': codec.RX_UUID,
        'flipper_to_phone_uuid': codec.TX_UUID, 'message_types': codec.TYPES,
        'max_payload_bytes': codec.MAX_PAYLOAD, 'max_frame_bytes': codec.MAX_FRAME,
        'characteristic_max_bytes': codec.CHAR_MAX, 'max_media_ms': codec.MAX_MEDIA_MS,
        'header_bytes': codec.HEADER.size, 'crc_bytes': 4,
        'field_max_bytes': dict(codec.TEXT_FIELDS), 'layout': {'width':128, 'height':64},
    }
    for key, val in expected.items():
        if const.get(key) != val:
            raise ValueError(f'Constant mismatch: {key}')
    if codec.STATE_FIELDS.size != 36 or codec.HELLO_FIELDS.size != 12:
        raise ValueError('Struct size mismatch')
    if f'{codec.crc32(b"123456789"):08x}' != const['crc']['check_123456789_hex']:
        raise ValueError('CRC mismatch')
    fixture = json.loads((ROOT/'protocol/golden_vectors.json').read_text())
    vectors = fixture['vectors']
    if fixture.get('protocol') != const['protocol'] or vectors != codec.golden_vectors():
        raise ValueError('Golden-vector mismatch')
    for vector in vectors:
        raw = bytes.fromhex(vector['frame_hex'])
        if codec.Frame.decode(raw).encode() != raw:
            raise ValueError('Round-trip mismatch')
        for width in (20, 128):
            chunks = vector[f'fragments_{width}_hex']
            if b''.join(bytes.fromhex(c) for c in chunks) != raw:
                raise ValueError('Fragment mismatch')
            if any(len(bytes.fromhex(c)) > width for c in chunks):
                raise ValueError('Oversized fragment')
    source_file = ROOT/'docs/10_SOURCES_AND_VERIFICATION.md'
    defined = set(re.findall(r'^### \[(S\d{2})\]', source_file.read_text(), re.M))
    used = set()
    links = 0
    for p in ROOT.rglob('*.md'):
        text = p.read_text()
        used.update(re.findall(r'\[(S\d{2})\]', text))
        for target in re.findall(r'(?<!!)\[[^\]\n]+\]\(([^)\n]+)\)', text):
            target = target.strip().strip('<>')
            if not target or target.startswith('#') or '://' in target or target.startswith('mailto:'):
                continue
            target = unquote(target.split('#', 1)[0])
            if target and not (p.parent/target).exists():
                raise ValueError(f'Broken local Markdown link in {p.name}: {target}')
            links += 1
    if used - defined:
        raise ValueError(f'Undefined source IDs: {sorted(used-defined)}')
    with (ROOT/'docs/acceptance_matrix.csv').open(newline='') as f:
        criteria = list(csv.DictReader(f))
    ids = [row['ID'] for row in criteria]
    if len(ids) != 74 or len(set(ids)) != 74:
        raise ValueError('Acceptance criterion count/uniqueness mismatch')
    allowed_statuses = {'NOT_RUN', 'PASS', 'FAIL', 'BLOCKED', 'NOT_APPLICABLE'}
    if any(row['Status'] not in allowed_statuses for row in criteria):
        raise ValueError('Unknown acceptance status')
    image = (ROOT/'assets/approved-concept.png').read_bytes()
    if image[:8] != b'\x89PNG\r\n\x1a\n' or image[12:16] != b'IHDR':
        raise ValueError('Approved reference is not a PNG')
    width, height = struct.unpack('>II', image[16:24])
    hashed = 0
    checksums = ROOT/'SHA256SUMS'
    if checksums.exists() and not args.skip_integrity:
        for line in checksums.read_text().splitlines():
            digest, name = line.split('  ', 1)
            path = (ROOT/name).resolve()
            if not path.is_relative_to(ROOT) or not path.is_file():
                raise ValueError(f'Invalid checksum path: {name}')
            if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
                raise ValueError(f'Checksum mismatch: {name}')
            hashed += 1
    print(f'PASS: {len(REQUIRED)} required files; {len(vectors)} valid vectors; '
          f'{len(criteria)} unique acceptance criteria; {len(defined)} source groups; '
          f'{links} local Markdown links; {hashed} checked file hashes; '
          f'approved PNG {width}x{height}.')
    if args.tests:
        return subprocess.run([sys.executable, '-m', 'unittest', 'discover', '-s', 'protocol', '-v'], cwd=ROOT).returncode
    return 0

if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print(f'FAIL: {exc}', file=sys.stderr)
        raise SystemExit(1)

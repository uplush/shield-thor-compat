#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Apply/reverse the exact Odin 3 subtitle patch to a NEW file, never in place."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

def digest(data):
    return hashlib.sha256(data).hexdigest()

def transform(data, part, reverse=False):
    """Pure, hash-guarded transform; also used with synthetic fixtures in tests."""
    before, after = ('modified', 'baseline') if reverse else ('baseline', 'modified')
    if len(data) != part['size'] or digest(data) != part[before+'_sha256']:
        raise ValueError('Input does not match the exact '+before+' preimage')
    out = bytearray(data)
    ranges = []
    for patch in part['patches']:
        old = bytes.fromhex(patch['new' if reverse else 'old'])
        new = bytes.fromhex(patch['old' if reverse else 'new'])
        offset = patch['offset']
        if not isinstance(offset, int) or offset < 0 or not old or len(old) != len(new) or offset+len(old) > len(data):
            raise ValueError('Invalid patch range/length')
        if any(offset < end and start < offset+len(old) for start,end in ranges):
            raise ValueError('Overlapping patch ranges')
        ranges.append((offset, offset+len(old)))
        if data[offset:offset+len(old)] != old:
            raise ValueError('Expected bytes mismatch')
        out[offset:offset+len(old)] = new
    result = bytes(out)
    if digest(result) != part[after+'_sha256']:
        raise ValueError('Output hash mismatch')
    return result

def write_new(source, output, part, reverse=False):
    source, output = Path(source).resolve(), Path(output).resolve()
    if source == output or output.exists():
        raise ValueError('Output must be a new file distinct from the source')
    data = source.read_bytes()
    result = transform(data, part, reverse)
    # Exclusive creation prevents overwriting an output created after the check.
    with output.open('xb') as stream:
        stream.write(result)
    if output.read_bytes() != result or source.read_bytes() != data:
        raise ValueError('Post-write verification failed')
    return digest(result)

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=['verify','apply','rollback'])
    p.add_argument('--kind', required=True, choices=['alf','native'])
    p.add_argument('--source', required=True, type=Path)
    p.add_argument('--output', type=Path)
    p.add_argument('--state', choices=['baseline','modified'], default='baseline')
    a = p.parse_args()
    part = json.loads((ROOT/'patches/odin3-subtitle.json').read_text(encoding='utf-8'))['parts'][a.kind]
    if a.action == 'verify':
        if a.output: p.error('verify does not take --output')
        data = a.source.read_bytes()
        if len(data) != part['size'] or digest(data) != part[a.state+'_sha256']:
            raise ValueError('File does not match '+a.state)
        value = digest(data)
    else:
        if not a.output: p.error('--output is required')
        value = write_new(a.source, a.output, part, a.action == 'rollback')
    print('SUBTITLE_PATCH='+a.action.upper()+' KIND='+a.kind+' RESULT=PASS')
    print('SHA256='+value)
    print('SOURCE_UNCHANGED=1')

if __name__ == '__main__':
    try: main()
    except (ValueError, OSError) as error:
        print('SUBTITLE_PATCH_ERROR='+str(error),file=sys.stderr)
        sys.exit(2)

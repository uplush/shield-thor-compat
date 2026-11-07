#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Validate the explicit source-only export list before committing or packaging."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
ALLOW_SUFFIX={'.java','.c','.h','.py','.sh','.patch','.md','.txt','.rst','.json'}
ALLOW_NAMES={'LICENSE','.gitignore','.gitattributes'}
PATTERNS=[
    ('private key',re.compile(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----')),
    ('access credential',re.compile(r'\bgh[pousr]_[A-Za-z0-9]{20,}\b|\bgithub_pat_[A-Za-z0-9_]{20,}\b')),
    ('user profile path',re.compile(r'[A-Za-z]:[/\\]Users[/\\][A-Za-z0-9_. -]+[/\\]')),
    ('signing password argument',re.compile(r'(?:--ks-pass|--key-pass)\s+pass:[^\s]+')),
]

def check(name,data):
    p=Path(name)
    if p.suffix not in ALLOW_SUFFIX and p.name not in ALLOW_NAMES:
        raise ValueError('Unexpected release file type: '+name)
    if len(data)>1024*1024 or b'\0' in data:
        raise ValueError('Binary or oversized release entry: '+name)
    text=data.decode('utf-8')
    for label,pattern in PATTERNS:
        if pattern.search(text): raise ValueError(label+' in '+name)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--archive',type=Path)
    p.add_argument('--index',action='store_true')
    a=p.parse_args()
    expected=set(json.loads((ROOT/'release-files.json').read_text(encoding='utf-8'))['files'])
    if a.archive:
        with zipfile.ZipFile(a.archive) as z:
            bad=z.testzip()
            if bad: raise ValueError('ZIP CRC failure: '+bad)
            names={n for n in z.namelist() if not n.endswith('/')}
            if names!=expected: raise ValueError('ZIP file list differs from allowlist')
            for name in sorted(names): check(name,z.read(name))
        print('ZIP_CRC=PASS')
    elif a.index:
        data=subprocess.check_output(['git','ls-files','--stage','-z'],cwd=ROOT).decode('utf-8')
        entries={}
        for entry in data.split('\0'):
            if not entry: continue
            meta,name=entry.split('\t',1);mode,oid,stage=meta.split()
            if stage!='0' or mode not in ['100644','100755']: raise ValueError('Unexpected Git entry: '+name)
            entries[name]=(mode,oid)
        if set(entries)!=expected: raise ValueError('Index differs from allowlist')
        if entries['ROLLBACK.sh'][0]!='100755': raise ValueError('ROLLBACK.sh is not executable in Git')
        for name,(_,oid) in entries.items(): check(name,subprocess.check_output(['git','cat-file','blob',oid],cwd=ROOT))
    else:
        names={x.relative_to(ROOT).as_posix() for x in ROOT.rglob('*') if x.is_file() and not any(part in ['.git','.build','__pycache__'] for part in x.relative_to(ROOT).parts)}
        if names!=expected: raise ValueError('Working source list differs from allowlist: '+str(sorted(names^expected)))
        for name in sorted(names): check(name,(ROOT/name).read_bytes())
    print('SOURCE_ALLOWLIST_FILES='+str(len(expected)))
    print('PROHIBITED_BINARY_ENTRIES=0\nCREDENTIAL_PATTERN_MATCHES=0\nRELEASE_AUDIT_PASS=1')

if __name__=='__main__':main()

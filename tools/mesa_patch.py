#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Hash-gated Mesa source patching. Apply and rollback always make fresh copies."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = json.loads((ROOT / 'patches/manifest.json').read_text(encoding='utf-8'))
PATCH = ROOT / 'patches/mesa-thor.patch'

def digest(p):
    return hashlib.sha256(p.read_bytes().replace(b'\r\n', b'\n')).hexdigest() if p.is_file() else None

def validate_manifest():
    if hashlib.sha256(PATCH.read_bytes()).hexdigest() != MANIFEST['patch_sha256']:
        raise ValueError('Patch checksum mismatch')
    for entry in MANIFEST['files']:
        p = PurePosixPath(entry['path'])
        if p.is_absolute() or '..' in p.parts or '\\' in str(p) or ':' in str(p):
            raise ValueError('Invalid manifest path')

def verify(root, state):
    key = 'before_sha256' if state == 'baseline' else 'after_sha256'
    for entry in MANIFEST['files']:
        path = root / entry['path']
        if not path.resolve().is_relative_to(root.resolve()):
            raise ValueError('Source path escapes tree')
        if digest(path) != entry[key]:
            raise ValueError('Source hash mismatch: ' + entry['path'])
    print('MESA_STATE=' + state)
    print('MATCHED_FILES=' + str(len(MANIFEST['files'])))
    print('SOURCE_HASH_CHECK=PASS')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['verify', 'apply', 'rollback'])
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--state', choices=['baseline', 'modified'], default='baseline')
    args = parser.parse_args()
    validate_manifest()
    source = args.source.resolve(strict=True)
    if not source.is_dir(): raise ValueError('Source must be a directory')
    if args.action == 'verify':
        verify(source, args.state)
        return
    if args.output is None: parser.error('--output is required')
    output = args.output.resolve()
    if output.exists() or output.is_relative_to(source) or source.is_relative_to(output):
        raise ValueError('Output must be a new, non-overlapping directory')
    reverse = args.action == 'rollback'
    before, after = ('modified', 'baseline') if reverse else ('baseline', 'modified')
    verify(source, before)
    # Upstream CI has Unix-only symlinks; CI metadata is not a build input.
    shutil.copytree(source, output, ignore=shutil.ignore_patterns('.git', '.gitlab-ci', '__pycache__', '*.pyc'))
    for entry in MANIFEST['files']:
        p = output / entry['path']
        if p.is_file(): p.write_bytes(p.read_bytes().replace(b'\r\n', b'\n'))
    # Use an isolated Git administration directory; never discover a parent repo.
    env = dict(os.environ)
    for key in ['GIT_DIR', 'GIT_WORK_TREE', 'GIT_INDEX_FILE']: env.pop(key, None)
    with tempfile.TemporaryDirectory(prefix='thor-patch-git-') as admin:
        subprocess.run(['git', 'init', '--quiet', admin], env=env, check=True)
        command = ['git', '--git-dir', str(Path(admin)/'.git'), '--work-tree', str(output), '-c', 'core.autocrlf=false', 'apply', '--whitespace=nowarn']
        if reverse: command.append('--reverse')
        subprocess.run(command + ['--check', str(PATCH)], cwd=output, env=env, check=True)
        subprocess.run(command + [str(PATCH)], cwd=output, env=env, check=True)
    verify(output, after)
    print('ORIGINAL_SOURCE_UNCHANGED=1')
    print('ROLLBACK_PASS=1' if reverse else 'PATCH_APPLY_PASS=1')

if __name__ == '__main__': main()

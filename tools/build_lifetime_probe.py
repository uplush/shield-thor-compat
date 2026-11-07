#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Build a probe against a configured Mesa tree; never connect/install/run on a device."""
import argparse
import ctypes
import json
import os
from pathlib import Path
import shlex
import subprocess

ROOT = Path(__file__).resolve().parents[1]

def split_command(command):
    if os.name != 'nt': return shlex.split(command)
    f = ctypes.windll.shell32.CommandLineToArgvW
    f.argtypes = [ctypes.c_wchar_p, ctypes.POINTER(ctypes.c_int)]
    f.restype = ctypes.POINTER(ctypes.c_wchar_p)
    n = ctypes.c_int(); ptr = f(command, ctypes.byref(n))
    if not ptr: raise ValueError('CommandLineToArgvW failed')
    try: return [ptr[i] for i in range(n.value)]
    finally:
        ctypes.windll.kernel32.LocalFree.argtypes = [ctypes.c_void_p]
        ctypes.windll.kernel32.LocalFree(ctypes.cast(ptr, ctypes.c_void_p))

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--mesa-build', type=Path, required=True)
    p.add_argument('--out', type=Path, required=True)
    a=p.parse_args(); build=a.mesa_build.resolve()
    rows=json.loads((build/'compile_commands.json').read_text(encoding='utf-8'))
    row=next(r for r in rows if r['file'].replace('\\','/').endswith('/zink_context.c'))
    args=row.get('arguments') or split_command(row['command'])
    flags=[]; i=0
    while i<len(args):
        arg=args[i]
        if arg in ['-o','-MF','-MQ']: i+=2; continue
        if arg in ['-c','-MD','-MMD','-DNDEBUG'] or arg.replace('\\','/').endswith('/zink_context.c'): i+=1; continue
        flags.append(arg); i+=1
    a.out.parent.mkdir(parents=True,exist_ok=True)
    flags += ['-UNDEBUG','-I'+str(ROOT/'tests/mesa'),str(ROOT/'tests/mesa/lifetime_probe.c'),str(build/'src/util/libmesa_util.a'),'-Wl,--gc-sections','-llog','-lz','-o',str(a.out.resolve())]
    subprocess.run(flags,cwd=row.get('directory',str(build)),check=True)
    print('REAL_MESA_PROBE_BUILT=1\nDEVICE_EXECUTED=0')

if __name__=='__main__': main()

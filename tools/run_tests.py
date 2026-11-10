#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Run the actual Java components against explicit API doubles / synthetic data."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]

def jdk_tool(name, home):
    suffix = '.exe' if os.name == 'nt' else ''
    p = str(Path(home) / 'bin' / (name + suffix)) if home else shutil.which(name)
    if not p or not Path(p).is_file(): raise ValueError('Set --java-home or JAVA_HOME to a JDK')
    return p

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    p.add_argument('--out', type=Path, default=ROOT/'.build/host-tests')
    a = p.parse_args()
    javac, java = jdk_tool('javac', a.java_home), jdk_tool('java', a.java_home)
    rows = [
        ('bundle', 'HostStoreTest', 'com/nvidia/NvAppBase/BundleStore.java'),
        ('clock', 'HostClock', 'com/nvidia/lightspeed/mediaplayer/PortMovie.java'),
        ('lifecycle', 'HostTest', 'com/nvidia/NvAppBase/PortGameView.java'),
    ]
    for test, main, source in rows:
        out = a.out.resolve()/test
        out.mkdir(parents=True, exist_ok=True)
        sources = [ROOT/'src/java'/source, *sorted((ROOT/'tests'/test).rglob('*.java'))]
        subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(out), *map(str,sources)], check=True)
        args = [java, '-cp', str(out), main]
        if test == 'bundle': args.append(str(out))
        subprocess.run(args, check=True)
    package = 'com/nvidia/lightspeed/mediaplayer'
    extra = [
        ('subtitle', 'TimelineTest', [ROOT/'src/odin3/java'/package/'SubtitleTimeline.java', ROOT/'tests/subtitle/TimelineTest.java']),
        ('odin3_clock', 'OdinClockTest', [ROOT/'src/odin3/java'/package/'PortMovie.java',
            *sorted((ROOT/'tests/clock').rglob('*.java')), *sorted((ROOT/'tests/odin3_clock').rglob('*.java'))]),
    ]
    for test, main, sources in extra:
        out = a.out.resolve()/test
        out.mkdir(parents=True, exist_ok=True)
        subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(out), *map(str,sources)], check=True)
        subprocess.run([java, '-cp', str(out), main], check=True)
    subprocess.run([sys.executable,str(ROOT/'tests/subtitle/test_patch.py')],check=True)
    print('HOST_TEST_GROUPS=6\nHOST_TESTS_PASS=1\nDEVICE_RUNTIME_TESTED_BY_THIS_COMMAND=0')

if __name__ == '__main__': main()

#!/usr/bin/env python3
# SPDX-License-Identifier: MIT
"""Build standalone compatibility helpers; this does not create a game APK."""
import argparse
import os
from pathlib import Path
import subprocess
import sys
from run_tests import jdk_tool

ROOT = Path(__file__).resolve().parents[1]

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--sdk', type=Path, default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    p.add_argument('--ndk', type=Path, default=os.environ.get('ANDROID_NDK_HOME'))
    p.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    p.add_argument('--platform', default='android-35')
    p.add_argument('--build-tools', default='35.0.0')
    p.add_argument('--profile', choices=['thor','odin3'], default='thor')
    p.add_argument('--host-tag', default='windows-x86_64' if os.name=='nt' else 'darwin-x86_64' if sys.platform=='darwin' else 'linux-x86_64')
    p.add_argument('--out', type=Path)
    a = p.parse_args()
    if not a.sdk or not a.ndk: p.error('--sdk and --ndk (or environment variables) are required')
    android = a.sdk.resolve()/'platforms'/a.platform/'android.jar'
    d8 = a.sdk.resolve()/'build-tools'/a.build_tools/'lib/d8.jar'
    clang = a.ndk.resolve()/'toolchains/llvm/prebuilt'/a.host_tag/'bin'/('clang.exe' if os.name=='nt' else 'clang')
    for f in [android,d8,clang]:
        if not f.is_file(): raise ValueError('Missing build dependency: '+str(f))
    out = (a.out or ROOT/('.build/components' if a.profile=='thor' else '.build/components-odin3')).resolve()
    if out.exists() and any(out.iterdir()):
        p.error('Use a new or empty output directory to avoid stale classes from another profile')
    classes, dex = out/'classes', out/'dex'
    classes.mkdir(parents=True, exist_ok=True); dex.mkdir(exist_ok=True)
    selected = {f.relative_to(ROOT/'src/java'): f for f in (ROOT/'src/java').rglob('*.java')}
    if a.profile == 'odin3':
        selected.update({f.relative_to(ROOT/'src/odin3/java'):f for f in (ROOT/'src/odin3/java').rglob('*.java')})
    sources = sorted(selected.values())
    subprocess.run([jdk_tool('javac',a.java_home),'-J-Duser.language=en','-J-Duser.country=US','-J-Dfile.encoding=UTF-8','-encoding','UTF-8','-source','8','-target','8','-classpath',str(android),'-d',str(classes),*map(str,sources)],check=True)
    subprocess.run([jdk_tool('jar',a.java_home),'cf',str(out/'port-helpers.jar'),'-C',str(classes),'.'],check=True)
    subprocess.run([jdk_tool('java',a.java_home),'-cp',str(d8),'com.android.tools.r8.D8','--min-api','23','--lib',str(android),'--output',str(dex),str(out/'port-helpers.jar')],check=True)
    subprocess.run([str(clang),'--target=aarch64-linux-android28','-shared','-fPIC','-O2','-Wl,-z,max-page-size=16384',str(ROOT/'src/native/video_upload.c'),'-llog','-ldl','-o',str(out/'libsmg_video.so')],check=True)
    if a.profile == 'odin3':
        native=ROOT/'src/odin3/native'
        flags=[str(clang),'--target=aarch64-linux-android28','-O2','-Wall','-Wextra','-Werror','-Wl,-z,max-page-size=16384']
        subprocess.run([*flags,'-shared','-fPIC',str(native/'subtitle_native.c'),'-llog','-ldl','-o',str(out/'libsmg_subtitle.so')],check=True)
        subprocess.run([*flags,'-I'+str(native),str(ROOT/'tests/subtitle/native_test.c'),'-o',str(out/'subtitle_store_test')],check=True)
        subprocess.run([*flags,'-shared','-fPIC','-I'+str(native),str(ROOT/'tests/odin3/header_probe.c'),'-ldl','-landroid','-llog','-o',str(out/'libodin_header_probe.so')],check=True)
        print('ARM64_SUBTITLE_BRIDGE_COMPILED=1\nARM64_STORE_TEST_COMPILED=1\nARM64_TIMING_HEADERS_COMPILED=1\nNATIVE_TEST_EXECUTED=0')
    print('BUILD_PROFILE='+a.profile+'\nJAVA_HELPERS_COMPILED='+str(len(sources))+'\nDEX_COMPILED=1\nARM64_VIDEO_BRIDGE_COMPILED=1\nGAME_APK_BUILT=0')

if __name__ == '__main__': main()

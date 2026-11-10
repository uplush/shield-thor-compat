// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
#define _GNU_SOURCE
#include <jni.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdint.h>
#include <string.h>
#include <android/log.h>
#include "subtitle_store.h"

static pthread_once_t once=PTHREAD_ONCE_INIT;
static void *(*find_player)(int);
static void initialize(void) {
    void *h=dlopen("libsmg.so",RTLD_NOW|RTLD_NOLOAD);
    if(!h) return;
    void *symbol=dlsym(h,"Java_com_nvidia_lightspeed_mediaplayer_MediaPlayer_setPlayedTime");
    Dl_info info;
    if(symbol && dladdr(symbol,&info)) {
        unsigned char *base=info.dli_fbase;
        uint32_t patched, getter[2];
        memcpy(&patched,base+0x52ae14,4);
        memcpy(getter,base+0x52a754,8);
        if((unsigned char*)symbol==base+0x52a464 && patched==0x14000005u &&
           getter[0]==0x91006000u && getter[1]==0x88dffc00u)
            find_player=(void*(*)(int))(base+0x529d5c);
    }
    __android_log_print(ANDROID_LOG_INFO,"SMGSubtitle","NATIVE_READY=%d",find_player!=0);
    dlclose(h); // libsmg is independently held by the app for the whole movie lifetime
}
JNIEXPORT jboolean JNICALL Java_com_nvidia_lightspeed_mediaplayer_PortSubtitle_ready(JNIEnv *env,jclass cls) {
    (void)env;(void)cls;pthread_once(&once,initialize);return find_player!=0;
}
JNIEXPORT jboolean JNICALL Java_com_nvidia_lightspeed_mediaplayer_PortSubtitle_publish(JNIEnv *env,jclass cls,jint id,jint frame,jlong pts) {
    (void)env;(void)cls;pthread_once(&once,initialize);
    if(!find_player || frame<1 || pts<0) return JNI_FALSE;
    return subtitle_store(find_player(id),frame,pts)?JNI_TRUE:JNI_FALSE;
}

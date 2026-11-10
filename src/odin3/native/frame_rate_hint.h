// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
#ifndef SMG_FRAME_RATE_HINT_H
#define SMG_FRAME_RATE_HINT_H
/* A window-local rate vote, not a frame limiter. No resolution, swap interval,
 * present mode, GPU queue, fences, or global display settings are changed.
 * Resolve once so older Android releases retain their previous behavior. */
typedef int32_t (*PortSetFrameRate)(ANativeWindow*,float,int8_t,int8_t);
static pthread_once_t port_rate_once=PTHREAD_ONCE_INIT;
static PortSetFrameRate port_set_frame_rate;
static void port_rate_initialize(void){
 void *h=dlopen("libandroid.so",RTLD_NOW|RTLD_LOCAL);
 if(h)port_set_frame_rate=(PortSetFrameRate)dlsym(h,"ANativeWindow_setFrameRateWithChangeStrategy");
 /* Retain the loader handle for the function pointer's lifetime. */
}
static void port_frame_rate_vote(ANativeWindow *window){
 if(!window)return;
 int saved_errno=errno;
 pthread_once(&port_rate_once,port_rate_initialize);
 int result=port_set_frame_rate?port_set_frame_rate(window,60.0f,
   ANATIVEWINDOW_FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
   0 /* ANATIVEWINDOW_CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS; API 31 enum value */):-ENOSYS;
 LOG("PORT_FRAME_RATE_TARGET=60 SEAMLESS_ONLY=1 RESULT=%d",result);
 errno=saved_errno;
}
#endif

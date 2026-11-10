// SPDX-License-Identifier: MIT
// Compile-only check against Android headers; not a device or EGL integration test.
#include <android/native_window.h>
#include <stdint.h>
#include <pthread.h>
#include <dlfcn.h>
#include <errno.h>
#include <android/log.h>
#define LOG(...) __android_log_print(ANDROID_LOG_INFO,"PortHeaderProbe",__VA_ARGS__)
#include "frame_rate_hint.h"
#include "thread_timing.h"
void port_header_probe(ANativeWindow *window, int bound) {
    port_frame_rate_vote(window);
    port_thread_timing_bound(bound);
}

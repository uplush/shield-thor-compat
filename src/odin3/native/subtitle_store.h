// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
#ifndef SMG_SUBTITLE_STORE_H
#define SMG_SUBTITLE_STORE_H
#include <stdint.h>
#include <string.h>
static int subtitle_store(void *player,int frame,int64_t pts_us) {
    if(!player || ((uintptr_t)player&3) || frame<1 || pts_us<0) return 0;
    float seconds=(float)((double)pts_us/1000000.0);
    uint32_t bits;memcpy(&bits,&seconds,4);
    __atomic_store_n((uint32_t*)((char*)player+0x1c),bits,__ATOMIC_RELAXED);
    __atomic_store_n((uint32_t*)((char*)player+0x18),(uint32_t)frame,__ATOMIC_RELEASE);
    return 1;
}
#endif

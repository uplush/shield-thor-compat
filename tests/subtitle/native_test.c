// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
#include "subtitle_store.h"
#include <assert.h>
#include <stdio.h>
int main(void) {
 uint32_t player[32];for(int i=0;i<32;i++)player[i]=0x12345678;
 assert(!subtitle_store(0,1,0));assert(!subtitle_store((char*)player+1,1,0));
 assert(!subtitle_store(player,0,0));assert(!subtitle_store(player,1,-1));
 for(int f=1;f<=7000;f++) {
  int64_t us=((int64_t)(f-1)*1000000+30)/60;
  assert(subtitle_store(player,f,us));
  assert(__atomic_load_n(player+6,__ATOMIC_ACQUIRE)==(uint32_t)f);
  float secs;memcpy(&secs,player+7,4);assert(secs==(float)((double)us/1000000.0));
  for(int i=0;i<32;i++)if(i!=6&&i!=7)assert(player[i]==0x12345678);
 }
 puts("NATIVE_STORE: PASS updates=7000 invalid_inputs=4 field_canaries=30 frame=player+0x18 seconds=player+0x1c");
 return 0;
}

// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
#include "zink_resource.h"
#include "zink_barrier_lifetime.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
static unsigned destroyed;
static void destroy_fixture(struct pipe_screen *screen,struct pipe_resource *res){(void)screen;(void)res;destroyed++;}
int main(int argc,char **argv){
   assert(argc==2);bool strong=!strcmp(argv[1],"modified");
   struct pipe_screen screen={0};screen.resource_destroy=destroy_fixture;
   struct zink_resource *res=calloc(1,sizeof(*res));assert(res);
   struct pipe_resource *owner=&res->base.b;owner->screen=&screen;pipe_reference_init(&owner->reference,1);
   struct set sets[4];for(unsigned i=0;i<4;i++)assert(_mesa_set_init(&sets[i],NULL,_mesa_hash_pointer,_mesa_key_pointer_equal));
   for(unsigned i=0;i<4;i++)for(unsigned j=0;j<3;j++){
      if(strong)assert(zink_barrier_add(&sets[i],res));else assert(_mesa_set_add(&sets[i],res));
   }
   assert(owner->reference.count==(strong?5:1));
   pipe_resource_reference(&owner,NULL);
   printf("DANGLING_AFTER_OWNER_RELEASE=%u\n",destroyed);
   assert(destroyed==(strong?0:1));
   if(strong){
      for(unsigned i=0;i<3;i++){zink_barrier_remove_key(&sets[i],res);zink_barrier_remove_key(&sets[i],res);assert(!destroyed);}
      struct set_entry *e=_mesa_set_search(&sets[3],res);assert(e);
      assert(zink_barrier_add_pre_hashed(&sets[0],e->hash,res));
      zink_barrier_remove(&sets[3],e);assert(!destroyed);
      zink_barrier_clear(&sets[0]);assert(destroyed==1);
      for(unsigned i=0;i<4;i++)zink_barrier_clear(&sets[i]);assert(destroyed==1);
      puts("DUPLICATE_INSERT_NO_EXTRA_REF=1\nFOUR_SETS_BALANCED=1\nSET_TRANSFER_KEEPS_RESOURCE=1\nFINAL_REFERENCE_RELEASED=1");
   }else for(unsigned i=0;i<4;i++)_mesa_set_clear(&sets[i],NULL);
   for(unsigned i=0;i<4;i++)ralloc_free(sets[i].table);
   free(res);puts("LIFETIME_PROBE_PASS=1");return 0;
}

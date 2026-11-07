#ifndef ZINK_BARRIER_LIFETIME_H
#define ZINK_BARRIER_LIFETIME_H
#include "util/set.h"
#include "util/hash_table.h"
#include "util/u_inlines.h"

/* A deferred barrier outlives the binding that queued it. In shared contexts,
 * another context may drop the last binding/reference before this set is read.
 * Each unique entry owns exactly one pipe_resource reference until removed.
 * The current->next barrier-set transfer acquires before releasing its source.
 * Do not replace this with an invalid-pointer check or skip required barriers.
 */
static inline struct set_entry *
zink_barrier_add_pre_hashed(struct set *set, uint32_t hash, struct zink_resource *res)
{
   bool found = false;
   struct set_entry *entry = _mesa_set_search_or_add_pre_hashed(set, hash, res, &found);
   if (entry && !found) {
      struct pipe_resource *owned = NULL;
      pipe_resource_reference(&owned, &res->base.b);
   }
   return entry;
}

static inline struct set_entry *
zink_barrier_add(struct set *set, struct zink_resource *res)
{
   return zink_barrier_add_pre_hashed(set, _mesa_hash_pointer(res), res);
}

static inline void
zink_barrier_remove(struct set *set, struct set_entry *entry)
{
   if (!entry)
      return;
   struct pipe_resource *owned = &((struct zink_resource *)entry->key)->base.b;
   _mesa_set_remove(set, entry);
   pipe_resource_reference(&owned, NULL);
}

static inline void
zink_barrier_remove_key(struct set *set, struct zink_resource *res)
{
   zink_barrier_remove(set, _mesa_set_search(set, res));
}

static inline void
zink_barrier_clear(struct set *set)
{
   /* Zero-initialized sets are possible on failed context construction. */
   if (!set->entries)
      return;
   set_foreach(set, entry)
      zink_barrier_remove(set, entry);
}
#endif

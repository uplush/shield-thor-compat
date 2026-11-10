// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
#ifndef PORT_THREAD_TIMING_H
#define PORT_THREAD_TIMING_H
#include <errno.h>
#include <sys/prctl.h>

#ifndef PORT_TIMING_PRCTL
#define PORT_TIMING_PRCTL(op, value) prctl((op), (unsigned long)(value), 0ul, 0ul, 0ul)
#endif
#define PORT_THREAD_TIMING_TARGET_NS 10000ul
/* Change only the calling GL owner's timer coalescing allowance, not a sleep
 * duration, scheduling policy, GPU fence, render resolution, or driver queue.
 * Preserve an already tighter allowance. Restore on successful unbind unless
 * another component changed it; retain state after a failed restore for retry. */
static _Thread_local struct {
    unsigned long original, applied;
    int active;
} port_thread_timing;

static void port_thread_timing_bound(int bound) {
    int saved_errno = errno;
    if (bound) {
        if (port_thread_timing.active) goto done;
        long current = PORT_TIMING_PRCTL(PR_GET_TIMERSLACK, 0);
        if (current <= 0) {
            LOG("THREAD_TIMING_READ_FAILED=%d", errno);
            goto done;
        }
        if ((unsigned long)current <= PORT_THREAD_TIMING_TARGET_NS) goto done;
        if (PORT_TIMING_PRCTL(PR_SET_TIMERSLACK, PORT_THREAD_TIMING_TARGET_NS)) {
            LOG("THREAD_TIMING_SET_FAILED=%d", errno);
            goto done;
        }
        port_thread_timing.original = (unsigned long)current;
        port_thread_timing.applied = PORT_THREAD_TIMING_TARGET_NS;
        port_thread_timing.active = 1;
        LOG("THREAD_TIMING_APPLIED OLD_NS=%lu NEW_NS=%lu",
            port_thread_timing.original, port_thread_timing.applied);
    } else if (port_thread_timing.active) {
        long current = PORT_TIMING_PRCTL(PR_GET_TIMERSLACK, 0);
        if (current <= 0) {
            LOG("THREAD_TIMING_RESTORE_READ_FAILED=%d", errno);
            goto done;
        }
        if ((unsigned long)current == port_thread_timing.applied) {
            if (PORT_TIMING_PRCTL(PR_SET_TIMERSLACK, port_thread_timing.original)) {
                LOG("THREAD_TIMING_RESTORE_FAILED=%d", errno);
                goto done;
            }
            LOG("THREAD_TIMING_RESTORED NS=%lu", port_thread_timing.original);
        } else {
            LOG("THREAD_TIMING_EXTERNAL_CHANGE NS=%ld", current);
        }
        port_thread_timing.active = 0;
    }
done:
    errno = saved_errno;
}
#endif

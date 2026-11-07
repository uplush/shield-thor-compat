// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package android.media; public class MediaTimestamp { public long us,ns=System.nanoTime(); public MediaTimestamp(long p){us=p;} public long getAnchorMediaTimeUs(){return us;} public long getAnchorSytemNanoTime(){return ns;} public float getMediaClockRate(){return 1;} }
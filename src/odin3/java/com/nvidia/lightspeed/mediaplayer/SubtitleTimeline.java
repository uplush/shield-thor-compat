// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package com.nvidia.lightspeed.mediaplayer;

/** Exact release-token -> decoded PTS association. No Android or wall-clock dependency. */
public final class SubtitleTimeline {
    private final int fps;
    private final long[] tokens = new long[64], times = new long[64];
    private int size;
    private long origin = Long.MIN_VALUE, lastToken = Long.MIN_VALUE;
    public long presentedUs;
    public int presentedFrame;
    public SubtitleTimeline(int fps) {
        if (fps <= 0 || fps > 240) throw new IllegalArgumentException("invalid video frame rate");
        this.fps = fps;
    }
    public void observe(long ptsUs) {
        if (ptsUs >= 0 && origin == Long.MIN_VALUE) origin = ptsUs;
    }
    public void queue(long token, long ptsUs) {
        if (ptsUs < 0) throw new IllegalArgumentException("negative PTS");
        observe(ptsUs);
        if (size > 0 && token <= tokens[size - 1]) throw new IllegalArgumentException("nonmonotonic release token");
        if (size == tokens.length) {
            System.arraycopy(tokens, 1, tokens, 0, size - 1);
            System.arraycopy(times, 1, times, 0, size - 1);
            --size;
        }
        tokens[size] = token;
        times[size++] = ptsUs;
    }
    public int present(long token) {
        if (token == lastToken) return 0; // unchanged SurfaceTexture, not a new frame
        int index = -1;
        for (int i = 0; i < size; ++i) if (tokens[i] == token) { index = i; break; }
        if (index < 0) return -1; // never silently substitute latest queued/demux time
        long elapsed = Math.max(0, times[index] - origin);
        if (elapsed > (Integer.MAX_VALUE - 1L) * 1000000L / fps) return -1;
        presentedUs = elapsed;
        presentedFrame = 1 + (int)((elapsed * fps + 500000L) / 1000000L);
        lastToken = token;
        int remaining = size - index - 1;
        System.arraycopy(tokens, index + 1, tokens, 0, remaining);
        System.arraycopy(times, index + 1, times, 0, remaining);
        size = remaining;
        return 1;
    }
    public void flush() { size = 0; lastToken = Long.MIN_VALUE; }
}

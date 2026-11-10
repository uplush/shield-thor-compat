// SPDX-License-Identifier: MIT
package com.nvidia.lightspeed.mediaplayer;
import android.media.MediaCodec;
/** Observe-only double; does not pretend to test JNI or actual SurfaceTexture presentation. */
public final class PortSubtitle {
    public static long observed;
    public static void observe(MediaCodec codec, long pts) {
        if (codec == null || pts < 0) throw new AssertionError("invalid decoded PTS");
        observed++;
    }
}

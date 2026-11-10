// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package com.nvidia.lightspeed.mediaplayer;

import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.util.Log;
import java.util.IdentityHashMap;

/** Publishes a movie clock only after the corresponding video texture upload succeeded. */
public final class PortSubtitle {
    static { System.loadLibrary("smg_subtitle"); }
    private static native boolean ready();
    private static native boolean publish(int id, int frame, long ptsUs);
    private static final Object lock = new Object();
    private static final IdentityHashMap<MediaCodec, Track> codecs = new IdentityHashMap<>();
    private static final IdentityHashMap<SurfaceTexture, Track> surfaces = new IdentityHashMap<>();
    private static final class Track {
        final MediaCodec codec;
        final int id;
        final SubtitleTimeline timeline;
        int matches, misses;
        Track(MediaCodec c, int id, int fps) { codec=c; this.id=id; timeline=new SubtitleTimeline(fps); }
    }
    public static void bind(MediaCodec codec, SurfaceTexture surface, int id, int fps) {
        if (!ready()) throw new IllegalStateException("subtitle native layout mismatch");
        Track t = new Track(codec, id, fps);
        synchronized (lock) {
            Track old = surfaces.put(surface, t);
            if (old != null) codecs.remove(old.codec);
            codecs.put(codec, t);
        }
        Log.i("SMGSubtitle", "BIND id="+id+" fps="+fps+" clock=presented_video_pts");
    }
    public static void observe(MediaCodec codec, long ptsUs) {
        synchronized (lock) { Track t=codecs.get(codec); if(t!=null) t.timeline.observe(ptsUs); }
    }
    public static void queue(MediaCodec codec, long token, long ptsUs) {
        synchronized (lock) { Track t=codecs.get(codec); if(t!=null) t.timeline.queue(token,ptsUs); }
    }
    public static void flush(MediaCodec codec) {
        synchronized (lock) { Track t=codecs.get(codec); if(t!=null) t.timeline.flush(); }
    }
    public static void presented(SurfaceTexture surface) {
        long token=surface.getTimestamp(), ptsUs; int id, frame, matches;
        synchronized (lock) {
            Track t=surfaces.get(surface); if(t==null) return;
            int result=t.timeline.present(token);
            if(result==0) return;
            if(result<0) {
                if(++t.misses<=3 || t.misses%300==0) Log.w("SMGSubtitle","UNMATCHED id="+t.id+" token="+token+" count="+t.misses);
                return;
            }
            id=t.id; frame=t.timeline.presentedFrame; ptsUs=t.timeline.presentedUs; matches=++t.matches;
        }
        // Do not hold the Java map lock across the game's native registry lookup.
        if(!publish(id,frame,ptsUs)) Log.e("SMGSubtitle","PUBLISH_FAILED id="+id+" frame="+frame);
        else if(matches<=3 || matches%300==0) Log.i("SMGSubtitle","PRESENT id="+id+" frame="+frame+" pts_us="+ptsUs+" token="+token+" matched="+matches);
    }
    public static void release(SurfaceTexture surface) {
        synchronized (lock) {
            Track t=surfaces.remove(surface);
            if(t!=null) { codecs.remove(t.codec); Log.i("SMGSubtitle","RELEASE id="+t.id+" matched="+t.matches+" unmatched="+t.misses); }
        }
    }
}

// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package com.nvidia.lightspeed.mediaplayer;
import android.media.MediaCodec;
import android.media.MediaSync;
import android.media.MediaTimestamp;
import android.util.Log;
import java.util.IdentityHashMap;

/** Audio-only MediaSync remains unchanged. Catch up late video without stalling demux.
 * No video is sent through a MediaSync input surface; no PCM samples are modified. */
public final class PortMovie {
  private static final class Timing {
    boolean initialized;
    long epochNs,epochUs,lastNs,lastPts=-1,dropped;
  }
  private static final IdentityHashMap<MediaCodec,Timing> timings=new IdentityHashMap<>();
  private static synchronized Timing timing(MediaCodec c){
    Timing t=timings.get(c);if(t==null){t=new Timing();timings.put(c,t);}return t;
  }
  public static synchronized void reset(MediaCodec c){timings.remove(c);}
  private static long audioTime(MediaSync sync,long now){
    if(sync==null)return -1;
    try{
      MediaTimestamp t=sync.getTimestamp();if(t==null||t.getMediaClockRate()<=0)return -1;
      return t.getAnchorMediaTimeUs()+(long)(Math.max(0,now-t.getAnchorSytemNanoTime())/1000.0*t.getMediaClockRate());
    }catch(RuntimeException e){return -1;}
  }
  public static int dequeue(MediaCodec codec,MediaCodec.BufferInfo info,MediaSync sync){
    Timing t=timing(codec);long now=System.nanoTime();
    if(t.lastNs!=0&&now-t.lastNs>500000000L)t.initialized=false;
    t.lastNs=now;
    for(int n=0;n<4;n++){
      int id=codec.dequeueOutputBuffer(info,0);
      if(id<0||(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)return id;
      now=System.nanoTime();long pts=info.presentationTimeUs,audio=audioTime(sync,now);
      if(!t.initialized||pts<t.lastPts){
        t.epochNs=now;t.epochUs=audio>=0?audio:pts;t.initialized=true;
      }
      t.lastPts=pts;
      // A stalled audio timestamp must not perpetuate demux starvation.
      long target=t.epochUs+Math.max(0,now-t.epochNs)/1000;
      if(audio>=0)target=Math.max(target,audio);
      if(n<3&&pts+25000<target){
        codec.releaseOutputBuffer(id,false);t.dropped++;
        if(t.dropped<=3||t.dropped%120==0)
          Log.i("SMGMovie","VIDEO_CATCHUP_DROP="+t.dropped+" LATE_US="+(target-pts));
        continue;
      }
      return id;
    }
    return MediaCodec.INFO_TRY_AGAIN_LATER;
  }
}

// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
import android.media.*;import com.nvidia.lightspeed.mediaplayer.PortMovie;
public class HostClock {
 static int checks;static void ok(boolean x){checks++;if(!x)throw new AssertionError("check "+checks);}
 public static void main(String[] a){
  MediaCodec c=new MediaCodec();MediaCodec.BufferInfo b=new MediaCodec.BufferInfo();MediaSync s=new MediaSync();
  c.pts.add(0L);ok(PortMovie.dequeue(c,b,s)==0);ok(b.presentationTimeUs==0);ok(c.discarded==0);
  s.us=120000;for(int i=1;i<=5;i++)c.pts.add(i*16667L);
  ok(PortMovie.dequeue(c,b,s)==0);ok(c.discarded==3);ok(b.presentationTimeUs==66668);ok(c.pts.size()==1);
  c.pts.clear();s.us=0;c.pts.add(5000000L);ok(PortMovie.dequeue(c,b,s)==0);ok(c.discarded==3);
  c.pts.add(-1L);ok(PortMovie.dequeue(c,b,s)==0);ok((b.flags&4)!=0);ok(c.discarded==3);
  ok(PortMovie.dequeue(c,b,s)==-1);
  c.negativeCode=-2;ok(PortMovie.dequeue(c,b,s)==-2);c.negativeCode=-3;ok(PortMovie.dequeue(c,b,s)==-3);c.negativeCode=-1;
  PortMovie.reset(c);c.pts.add(0L);ok(PortMovie.dequeue(c,b,null)==0);ok(c.discarded==3);
  c.pts.add(16000L);ok(PortMovie.dequeue(c,b,null)==0);c.pts.add(0L);ok(PortMovie.dequeue(c,b,null)==0);ok(c.discarded==3);
  PortMovie.reset(c);PortMovie.reset(c);PortMovie.reset(null);
  c.throwsOnDequeue=true;boolean propagated=false;try{PortMovie.dequeue(c,b,s);}catch(IllegalStateException e){propagated=true;}ok(propagated);
  System.out.println("HOST_CLOCK_CHECKS="+checks+" PASS=1");
  System.out.println("MAX_OUTPUTS_PER_CALL=4\nEOS_AND_FORMAT_CODES_PRESERVED=1\nPCM_MODIFICATIONS=0\nDEVICE_AUDIO_QUALITY_TESTED=0");
 }
}
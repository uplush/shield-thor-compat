// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
import com.nvidia.lightspeed.mediaplayer.SubtitleTimeline;
public class TimelineTest {
 static int cases;
 static void check(boolean b) { ++cases; if(!b) throw new AssertionError("case="+cases); }
 public static void main(String[] args) {
  for(int fps:new int[]{30,60,120}) {
   SubtitleTimeline t=new SubtitleTimeline(fps);t.observe(12345);
   for(int f=0;f<7000;f++) {
    long pts=12345+Math.round(f*1000000.0/fps),token=100000000000L+f*17000000L;
    t.observe(pts);
    if(f%21==0) continue; // includes first-frame drop; origin stays at decoded frame zero
    t.queue(token,pts);
    if(f%17==0) continue; // SurfaceTexture may coalesce submitted frames
    check(t.present(token)==1);check(t.presentedFrame==f+1);check(t.presentedUs==pts-12345);
    check(t.present(token)==0);check(t.present(-2)==-1);
   }
   int held=t.presentedFrame;t.flush();check(t.present(-3)==-1);check(t.presentedFrame==held);
   t.queue(200000000000L,12345);check(t.present(200000000000L)==1);check(t.presentedFrame==1);
  }
  SubtitleTimeline t=new SubtitleTimeline(60);t.observe(0);
  for(int i=0;i<100;i++)t.queue(i+1,Math.round(i*1000000.0/60));
  check(t.present(1)==-1);check(t.present(100)==1);check(t.presentedFrame==100);
  t.queue(101,Long.MAX_VALUE);check(t.present(101)==-1);
  try{new SubtitleTimeline(0);throw new AssertionError();}catch(IllegalArgumentException ok){cases++;}
  try{t.queue(102,-1);throw new AssertionError();}catch(IllegalArgumentException ok){cases++;}
  try{t.queue(101,0);throw new AssertionError();}catch(IllegalArgumentException ok){cases++;}
  System.out.println("TIMELINE: PASS assertions="+cases+" initial_drop,PTS,coalescing,duplicate,pause,flush,overflow,ring_bounds,30_60_120fps");
 }
}

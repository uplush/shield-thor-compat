// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
import com.nvidia.NvAppBase.PortGameView;import android.content.*;import android.graphics.*;import android.view.*;
public class HostTest {
 static int checks,created,changed,destroyed;static void ok(boolean x){checks++;if(!x)throw new AssertionError("check "+checks);}
 public static void main(String[]args){PortGameView v=new PortGameView(new Context());SurfaceHolder h=v.getHolder();
 SurfaceHolder.Callback cb=new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder h){created++;}public void surfaceChanged(SurfaceHolder h,int f,int w,int y){changed++;}public void surfaceDestroyed(SurfaceHolder h){destroyed++;}};
 h.addCallback(cb);h.addCallback(cb);SurfaceTexture original=new SurfaceTexture();v.current=original;ok(h.isCreating());v.onSurfaceTextureAvailable(original,1920,1080);Surface producer=h.getSurface();ok(created==1&&changed==1);ok(!h.isCreating());ok(producer.texture==original);ok(original.w==1920&&original.h==1080);
 for(int i=0;i<100;i++){ok(!v.onSurfaceTextureDestroyed(original));ok(original.releases==0&&producer.releases==0);SurfaceTexture replacement=new SurfaceTexture();v.current=replacement;v.onSurfaceTextureAvailable(replacement,1920,1080);ok(v.current==original);ok(replacement.releases==1);ok(h.getSurface()==producer);ok(created==1&&changed==1&&destroyed==0);}
 v.onSurfaceTextureAvailable(original,1920,1080);ok(v.reattachments==100);h.setFixedSize(1280,720);ok(original.w==1280&&original.h==720);h.setSizeFromLayout();ok(h.getSurfaceFrame().right==1920);Rect copy=h.getSurfaceFrame();copy.right=1;ok(h.getSurfaceFrame().right==1920);h.setKeepScreenOn(true);ok(v.keep);
 boolean threw=false;try{h.lockCanvas();}catch(UnsupportedOperationException e){threw=true;}ok(threw);h.setFormat(PixelFormat.RGBA_8888);v.dispose();ok(producer.releases==1&&original.releases==1);ok(h.getSurface()==null);v.dispose();ok(producer.releases==1&&original.releases==1);
 System.out.println("HOST_LIFECYCLE_CHECKS="+checks+" PASS=1");System.out.println("RETAIN_REATTACH_CYCLES=100 NATIVE_CREATE_COUNT="+created+" TEMPORARY_DESTROY_COUNT="+destroyed);System.out.println("DEVICE_RENDERING_TESTED_BY_THIS_COMMAND=0");
 }}
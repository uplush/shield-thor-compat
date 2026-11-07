// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package android.view;import android.graphics.*;import android.content.*;public class TextureView {
 public interface SurfaceTextureListener {void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h);boolean onSurfaceTextureDestroyed(SurfaceTexture s);void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h);void onSurfaceTextureUpdated(SurfaceTexture s);}
 public SurfaceTexture current;public int reattachments;public boolean keep;public TextureView(Context c){}public void setOpaque(boolean x){}public void setSurfaceTextureListener(SurfaceTextureListener l){}public void setKeepScreenOn(boolean k){keep=k;}public int getWidth(){return 1920;}public int getHeight(){return 1080;}
 public void setSurfaceTexture(SurfaceTexture t){if(current==t)throw new IllegalArgumentException();if(current!=null)current.release();current=t;reattachments++;}
}
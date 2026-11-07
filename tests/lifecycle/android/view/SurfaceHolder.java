// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package android.view; import android.graphics.*; public interface SurfaceHolder {
 interface Callback {void surfaceCreated(SurfaceHolder h);void surfaceChanged(SurfaceHolder h,int f,int w,int v);void surfaceDestroyed(SurfaceHolder h);}
 void addCallback(Callback c);void removeCallback(Callback c);Surface getSurface();Rect getSurfaceFrame();boolean isCreating();void setFixedSize(int w,int h);void setSizeFromLayout();void setKeepScreenOn(boolean k);void setFormat(int f);void setType(int t);Canvas lockCanvas();Canvas lockCanvas(Rect r);void unlockCanvasAndPost(Canvas c);
}
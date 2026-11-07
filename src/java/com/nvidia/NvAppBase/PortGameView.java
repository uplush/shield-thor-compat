// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package com.nvidia.NvAppBase;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import java.util.ArrayList;

/** Keep the producer/consumer pair across a temporary window detach.
 * The native engine still receives its normal audio/input/graphics pause.
 * No native state is serialized, restarted, or falsely acknowledged.
 */
public final class PortGameView extends TextureView implements TextureView.SurfaceTextureListener {
    private SurfaceTexture retained;
    private Surface surface;
    private final Holder holder = new Holder();
    private boolean disposed;
    public PortGameView(Context context) {
        super(context);
        setOpaque(true);
        setSurfaceTextureListener(this);
    }
    public SurfaceHolder getHolder() { return holder; }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        if (disposed) return;
        if (retained == null) {
            retained = texture;
            retained.setDefaultBufferSize(w, h);
            surface = new Surface(retained);
            holder.frame.set(0, 0, w, h);
            for (SurfaceHolder.Callback c : holder.callbacks) {
                c.surfaceCreated(holder);
                c.surfaceChanged(holder, PixelFormat.RGBA_8888, w, h);
            }
            Log.i("SMGLifecycle", "TEXTURE_CREATE=" + System.identityHashCode(retained));
        } else {
            if (texture != retained) setSurfaceTexture(retained);
            Log.i("SMGLifecycle", "TEXTURE_REATTACH=" + System.identityHashCode(retained));
        }
    }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Log.i("SMGLifecycle", "TEXTURE_RETAIN=" + System.identityHashCode(texture));
        return disposed; // false transfers release responsibility to this owner.
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
        if (retained != null && !disposed) {
            retained.setDefaultBufferSize(w, h);
            holder.frame.set(0, 0, w, h);
        }
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
    // Called only after the native renderer has been finalized, never on Home.
    public void dispose() {
        disposed = true;
        if (surface != null) { surface.release(); surface = null; }
        if (retained != null) { retained.release(); retained = null; }
        holder.callbacks.clear();
        Log.i("SMGLifecycle", "TEXTURE_DISPOSE=1");
    }
    private final class Holder implements SurfaceHolder {
        final ArrayList<Callback> callbacks = new ArrayList<>();
        final Rect frame = new Rect();
        @Override public void addCallback(Callback c) { if (!callbacks.contains(c)) callbacks.add(c); }
        @Override public void removeCallback(Callback c) { callbacks.remove(c); }
        @Override public Surface getSurface() { return surface; }
        @Override public Rect getSurfaceFrame() { return new Rect(frame); }
        @Override public boolean isCreating() { return surface == null; }
        @Override public void setFixedSize(int w, int h) {
            if (retained != null) retained.setDefaultBufferSize(w, h);
            frame.set(0, 0, w, h);
        }
        @Override public void setSizeFromLayout() { setFixedSize(getWidth(), getHeight()); }
        @Override public void setKeepScreenOn(boolean keep) { PortGameView.this.setKeepScreenOn(keep); }
        @Override public void setFormat(int format) {
            if (format != PixelFormat.RGBA_8888 && format != PixelFormat.OPAQUE)
                throw new UnsupportedOperationException("Only RGBA game rendering is supported");
        }
        @Override public void setType(int type) { throw new UnsupportedOperationException("EGL only"); }
        @Override public Canvas lockCanvas() { throw new UnsupportedOperationException("EGL owns this producer"); }
        @Override public Canvas lockCanvas(Rect dirty) { return lockCanvas(); }
        @Override public void unlockCanvasAndPost(Canvas c) { throw new UnsupportedOperationException("EGL only"); }
    }
}

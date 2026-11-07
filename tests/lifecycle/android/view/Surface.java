// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package android.view; import android.graphics.SurfaceTexture;public class Surface {public int releases;public SurfaceTexture texture;public Surface(SurfaceTexture t){texture=t;}public void release(){if(++releases>1)throw new AssertionError("double release");}}
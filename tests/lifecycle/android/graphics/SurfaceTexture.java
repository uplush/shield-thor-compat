// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package android.graphics; public class SurfaceTexture {public int releases,w,h;public void setDefaultBufferSize(int x,int y){w=x;h=y;}public void release(){releases++;if(releases>1)throw new AssertionError("double release");}}
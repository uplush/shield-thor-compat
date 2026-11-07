// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package android.graphics; public class Rect {public int left,top,right,bottom;public Rect(){} public Rect(Rect r){set(r.left,r.top,r.right,r.bottom);} public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}
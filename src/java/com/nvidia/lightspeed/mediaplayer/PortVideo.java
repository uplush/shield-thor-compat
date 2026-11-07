// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package com.nvidia.lightspeed.mediaplayer;
import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.util.Log;
import java.nio.*;
import java.util.IdentityHashMap;

/** App-local bridge: system SurfaceTexture/OES -> RGBA -> private Mesa texture.
 * Owns its EGL objects only. The game keeps its original decoder and audio clock. */
public final class PortVideo {
  static { System.loadLibrary("smg_video"); }
  private static native boolean upload(int texture,int width,int height,ByteBuffer data,boolean allocate);
  private static final IdentityHashMap<SurfaceTexture,PortVideo> players=new IdentityHashMap<>();
  private final int gameTexture;
  private final SurfaceTexture input;
  private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
  private EGLContext context=EGL14.EGL_NO_CONTEXT;
  private EGLSurface pbuffer=EGL14.EGL_NO_SURFACE;
  private int external,rgba,fbo,program,width,height,allocatedW,allocatedH,frames;
  private ByteBuffer pixels;
  private FloatBuffer vertices;
  private final float[] matrix=new float[16], identity=new float[16];
  private boolean attached,closed;
  private PortVideo(int texture){gameTexture=texture;input=new SurfaceTexture(false);Matrix.setIdentityM(identity,0);}
  public static synchronized SurfaceTexture create(int texture){PortVideo p=new PortVideo(texture);players.put(p.input,p);return p.input;}
  public static synchronized void size(SurfaceTexture s,int w,int h){PortVideo p=players.get(s);if(p!=null){p.width=w;p.height=h;}}
  public static synchronized float[] update(SurfaceTexture s){PortVideo p=players.get(s);if(p==null)return null;try{return p.frame();}catch(RuntimeException e){Log.e("SMGVideo","VIDEO_FRAME_FAILED",e);return null;}}
  public static synchronized void release(SurfaceTexture s){PortVideo p=players.remove(s);if(p!=null)p.close();}
  private void check(boolean b,String what){if(!b)throw new IllegalStateException(what+" EGL=0x"+Integer.toHexString(EGL14.eglGetError()));}
  private int shader(int type,String source){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);int[] ok={0};GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetShaderInfoLog(s));return s;}
  private void initialize(){
    if(context!=EGL14.EGL_NO_CONTEXT)return;
    display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);int[] version=new int[2];check(EGL14.eglInitialize(display,version,0,version,1),"initialize");
    int[] attrs={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};EGLConfig[] configs=new EGLConfig[1];int[] n={0};check(EGL14.eglChooseConfig(display,attrs,0,configs,0,1,n,0)&&n[0]>0,"config");
    context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);check(context!=EGL14.EGL_NO_CONTEXT,"context");
    pbuffer=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);check(pbuffer!=EGL14.EGL_NO_SURFACE,"pbuffer");
  }
  private void initializeGL(){
    if(attached)return;
    int[] ids=new int[2];GLES20.glGenTextures(2,ids,0);external=ids[0];rgba=ids[1];input.attachToGLContext(external);attached=true;
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
    int v=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 pos;varying vec2 uv;uniform mat4 transform;void main(){gl_Position=vec4(pos*2.-1.,0.,1.);uv=(transform*vec4(pos,0.,1.)).xy;}");
    int f=shader(GLES20.GL_FRAGMENT_SHADER,"#extension GL_OES_EGL_image_external : require\nprecision mediump float;varying vec2 uv;uniform samplerExternalOES image;void main(){gl_FragColor=texture2D(image,uv);}");
    program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glBindAttribLocation(program,0,"pos");GLES20.glLinkProgram(program);int[] ok={0};GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);GLES20.glGenFramebuffers(1,ids,0);fbo=ids[0];
    vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();vertices.put(new float[]{0,0,1,0,0,1,1,1}).position(0);
    Log.i("SMGVideo","VIDEO_BRIDGE_READY SYSTEM_GL="+GLES20.glGetString(GLES20.GL_RENDERER)+" GAME_TEXTURE="+gameTexture);
  }
  private float[] frame(){
    if(closed||width<=0||height<=0||width>4096||height>4096)throw new IllegalStateException("invalid video state/dimensions");
    EGLDisplay od=EGL14.eglGetCurrentDisplay();EGLContext oc=EGL14.eglGetCurrentContext();EGLSurface os=EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW),or=EGL14.eglGetCurrentSurface(EGL14.EGL_READ);
    boolean resized=allocatedW!=width||allocatedH!=height;
    try{
      initialize();check(EGL14.eglMakeCurrent(display,pbuffer,pbuffer,context),"bind");initializeGL();input.updateTexImage();input.getTransformMatrix(matrix);
      if(resized){pixels=ByteBuffer.allocateDirect(width*height*4).order(ByteOrder.nativeOrder());GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,rgba);GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D,0,GLES20.GL_RGBA,width,height,0,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,null);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);}
      GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER,fbo);GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,GLES20.GL_COLOR_ATTACHMENT0,GLES20.GL_TEXTURE_2D,rgba,0);check(GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)==GLES20.GL_FRAMEBUFFER_COMPLETE,"framebuffer");GLES20.glViewport(0,0,width,height);GLES20.glUseProgram(program);GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"transform"),1,false,matrix,0);GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,external);GLES20.glEnableVertexAttribArray(0);vertices.position(0);GLES20.glVertexAttribPointer(0,2,GLES20.GL_FLOAT,false,8,vertices);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);pixels.position(0);GLES20.glReadPixels(0,0,width,height,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixels);int err=GLES20.glGetError();if(err!=0)throw new IllegalStateException("system GL error "+err);
    }finally{if(display!=EGL14.EGL_NO_DISPLAY)check(EGL14.eglMakeCurrent(oc==EGL14.EGL_NO_CONTEXT?display:od,os,or,oc),"restore");}
    pixels.position(0);if(!upload(gameTexture,width,height,pixels,resized))throw new IllegalStateException("Mesa upload failed");allocatedW=width;allocatedH=height;
    if(++frames<=3||frames%300==0)Log.i("SMGVideo","VIDEO_FRAME="+frames+" WIDTH="+width+" HEIGHT="+height+" TIMESTAMP="+input.getTimestamp());return identity;
  }
  private void close(){
    closed=true;
    try{input.release();if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);if(pbuffer!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,pbuffer);}finally{pixels=null;Log.i("SMGVideo","VIDEO_RELEASE FRAMES="+frames);}
  }
}

// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
#include <jni.h>
#include <EGL/egl.h>
#include <GLES3/gl32.h>
#include <dlfcn.h>
#include <stdint.h>
#include <android/log.h>
JNIEXPORT jboolean JNICALL Java_com_nvidia_lightspeed_mediaplayer_PortVideo_upload(JNIEnv*e,jclass c,jint texture,jint w,jint h,jobject data,jboolean allocate){
 (void)c;if(w<=0||h<=0||w>4096||h>4096||texture<=0)return JNI_FALSE;void*p=(*e)->GetDirectBufferAddress(e,data);jlong n=(*e)->GetDirectBufferCapacity(e,data);if(!p||n<(jlong)w*h*4)return JNI_FALSE;
 void*d=dlopen("libEGL_mesa.so",RTLD_NOW|RTLD_LOCAL);if(!d)return JNI_FALSE;__typeof__(&eglGetProcAddress)proc=dlsym(d,"eglGetProcAddress");__typeof__(&eglGetCurrentContext)ctx=dlsym(d,"eglGetCurrentContext");if(!proc||!ctx||ctx()==EGL_NO_CONTEXT){dlclose(d);return JNI_FALSE;}
 #define LOAD(x) __typeof__(&x) p_##x=(__typeof__(&x))proc(#x);if(!p_##x){dlclose(d);return JNI_FALSE;}
 LOAD(glGetIntegerv) LOAD(glBindTexture) LOAD(glBindBuffer) LOAD(glPixelStorei) LOAD(glTexImage2D) LOAD(glTexSubImage2D) LOAD(glTexParameteri) LOAD(glGetError)
 GLint binding,pbo,align,row,skipr,skipp;p_glGetIntegerv(GL_TEXTURE_BINDING_2D,&binding);p_glGetIntegerv(GL_PIXEL_UNPACK_BUFFER_BINDING,&pbo);p_glGetIntegerv(GL_UNPACK_ALIGNMENT,&align);p_glGetIntegerv(GL_UNPACK_ROW_LENGTH,&row);p_glGetIntegerv(GL_UNPACK_SKIP_ROWS,&skipr);p_glGetIntegerv(GL_UNPACK_SKIP_PIXELS,&skipp);
 GLenum prior=p_glGetError();if(prior)__android_log_print(ANDROID_LOG_WARN,"SMGVideo","MESA_PREEXISTING_ERROR=%x",prior);
 p_glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);p_glPixelStorei(GL_UNPACK_ALIGNMENT,1);p_glPixelStorei(GL_UNPACK_ROW_LENGTH,0);p_glPixelStorei(GL_UNPACK_SKIP_ROWS,0);p_glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);p_glBindTexture(GL_TEXTURE_2D,(GLuint)texture);
 if(allocate){p_glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,p);p_glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);p_glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);p_glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);p_glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);}else p_glTexSubImage2D(GL_TEXTURE_2D,0,0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,p);
 GLenum err=p_glGetError();p_glBindTexture(GL_TEXTURE_2D,binding);p_glBindBuffer(GL_PIXEL_UNPACK_BUFFER,pbo);p_glPixelStorei(GL_UNPACK_ALIGNMENT,align);p_glPixelStorei(GL_UNPACK_ROW_LENGTH,row);p_glPixelStorei(GL_UNPACK_SKIP_ROWS,skipr);p_glPixelStorei(GL_UNPACK_SKIP_PIXELS,skipp);if(err)__android_log_print(ANDROID_LOG_ERROR,"SMGVideo","MESA_UPLOAD_ERROR=%x",err);dlclose(d);return err==GL_NO_ERROR;
}

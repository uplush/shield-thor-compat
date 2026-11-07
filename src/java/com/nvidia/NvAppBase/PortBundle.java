// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package com.nvidia.NvAppBase;

import android.app.*;
import android.content.*;
import android.os.*;
import android.system.Os;
import android.util.Log;
import android.view.WindowManager;
import java.io.*;
import java.lang.ref.WeakReference;

/** Launch gate: no native game initialization before all assets are committed. */
public final class PortBundle {
    public interface Ready { void onPortResourcesReady(); }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> owner=new WeakReference<>(null);
    private static ProgressDialog dialog;
    private static AlertDialog errorDialog;
    private static Job job;
    private static boolean foreground,launching;
    public static boolean readyAtProcessStart(Context app) {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];
            try(InputStream in=app.getAssets().open("smg_bundle/manifest.tsv")) {
                int n;while((n=in.read(buf))!=-1) { if(bytes.size()+n>65536) return false;bytes.write(buf,0,n); }
            }
            File root=app.getObbDir();
            boolean ready=root!=null && BundleStore.ready(root,new File(app.getFilesDir(),"port-bundle-state"),bytes.toByteArray());
            Log.i("SMGBundle","EARLY_NATIVE_READY="+ready);return ready;
        } catch(Exception e) {Log.i("SMGBundle","EARLY_NATIVE_READY=false",e);return false;}
    }
    private static final class Job {
        volatile boolean cancel,done;
        volatile String error,phase="正在检查资料包…";
        volatile int progress;
    }
    private static void dismiss() {
        if(dialog!=null) { try { dialog.dismiss(); } catch(RuntimeException ignored) {} dialog=null; }
        if(errorDialog!=null) { try { errorDialog.dismiss(); } catch(RuntimeException ignored) {} errorDialog=null; }
    }
    private static void draw() {
        Activity a=owner.get();
        if(a==null || a.isFinishing() || a.isDestroyed() || job==null) return;
        if(job.done && foreground && !launching) {
            dismiss();launching=true;a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            try { ((Ready)a).onPortResourcesReady(); }
            catch(Throwable e) { launching=false;job.error="游戏启动失败："+e.getClass().getSimpleName()+": "+e.getMessage();job.done=false;Log.e("SMGBundle","NATIVE_START_FAILED",e);draw(); }
            return;
        }
        if(job.error!=null) {
            if(errorDialog!=null) return;
            dismiss();a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            final String error=job.error;
            errorDialog=new AlertDialog.Builder(a).setTitle("资料包准备未完成").setMessage(error+"\n已完成的资源会保留；现有存档不受影响。")
                .setPositiveButton("重试",(d,w)->{ dismiss();job=null;start(a); })
                .setNegativeButton("关闭",(d,w)->a.finish()).setCancelable(false).create();
            errorDialog.show();
            return;
        }
        if(dialog==null) {
            dialog=new ProgressDialog(a);dialog.setTitle("首次启动 · 准备游戏资源");dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);dialog.setMax(1000);dialog.setCancelable(false);
            dialog.setButton(DialogInterface.BUTTON_NEGATIVE,"取消",(d,w)->{job.cancel=true;a.finish();});dialog.show();
        }
        dialog.setMessage(job.phase+"\n首次启动需要释放约 1.49 GB 数据，请等待校验完成。");dialog.setProgress(job.progress);
    }
    public static void start(Activity a) {
        if(owner.get()!=a) {dismiss();owner=new WeakReference<>(a);launching=false;foreground=false;}
        a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(job==null || job.cancel) {
            final Job j=new Job();job=j;final Context app=a.getApplicationContext();
            new Thread(()->{
                try {
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buf=new byte[4096];
                    try(InputStream in=app.getAssets().open("smg_bundle/manifest.tsv")) {
                        int n;while((n=in.read(buf))!=-1) { if(bytes.size()+n>65536) throw new IOException("资源清单异常");bytes.write(buf,0,n); }
                    }
                    File root=app.getObbDir();if(root==null) throw new IOException("外部存储尚未挂载，请重试");
                    BundleStore.Result r=BundleStore.prepare(root,new File(app.getFilesDir(),"port-bundle-state"),bytes.toByteArray(),
                        name->app.getAssets().open("smg_bundle/data/"+name),
                        new BundleStore.Platform() {
                            public void replace(File from,File to) throws IOException { try { Os.rename(from.getAbsolutePath(),to.getAbsolutePath()); } catch(Exception e) { throw new IOException("资源提交失败",e); } }
                            public long available(File dir) { return dir.getUsableSpace(); }
                        },new BundleStore.Progress() {
                            long last;
                            public boolean cancelled() { return j.cancel; }
                            public void update(long done,long total,String phase) {
                                j.phase=phase;j.progress=total==0?1000:(int)(done*1000/total);
                                long now=SystemClock.uptimeMillis();if(now-last>=150 || done==total) {last=now;MAIN.post(()->{if(job==j)draw();});}
                            }
                        });
                    j.done=true;Log.i("SMGBundle","RESOURCES_READY copied="+r.copied+" reused="+r.reused+" fast="+r.fast);
                } catch(Exception e) {j.error=e.getMessage();Log.e("SMGBundle","EXTRACTION_FAILED",e);}
                MAIN.post(()->{if(job==j)draw();});
            },"SMG-resource-unpack").start();
        }
        draw();
    }
    public static void resume(Activity a) { if(owner.get()==a) {foreground=true;draw();} }
    public static void pause(Activity a) { if(owner.get()==a)foreground=false; }
    public static void destroy(Activity a) {
        if(owner.get()==a) {dismiss();foreground=false;owner.clear();if(a.isFinishing() && job!=null && !job.done) job.cancel=true;}
    }
}

// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
package com.nvidia.NvAppBase;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** No Android dependency: the same extraction engine is executed by host tests. */
public final class BundleStore {
    public interface Source { InputStream open(String name) throws IOException; }
    public interface Platform {
        void replace(File from, File to) throws IOException;
        long available(File directory);
    }
    public interface Progress {
        boolean cancelled();
        void update(long done, long total, String phase);
    }
    public static final class Entry {
        public final String name, hash;
        public final long size;
        Entry(String name, long size, String hash) { this.name=name; this.size=size; this.hash=hash; }
    }
    public static final class Result {
        public int copied, reused;
        public boolean fast;
    }
    public static String hex(byte[] bytes) {
        StringBuilder b=new StringBuilder();
        for (byte x:bytes) b.append(String.format(Locale.US,"%02x",x & 255));
        return b.toString();
    }
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String id(byte[] manifest) { return hex(digest().digest(manifest)); }
    public static List<Entry> parse(byte[] manifest) throws IOException {
        if (manifest.length>65536) throw new IOException("Manifest too large");
        List<Entry> out=new ArrayList<>(); Set<String> names=new HashSet<>(); long total=0;
        for (String line:new String(manifest,"UTF-8").split("\n")) {
            if (line.isEmpty()) continue;
            String[] c=line.split("\t",-1);
            if (c.length!=3 || !c[0].matches("[A-Za-z0-9][A-Za-z0-9_.-]*") || !c[2].matches("[a-f0-9]{64}") || !names.add(c[0]))
                throw new IOException("Invalid resource manifest");
            long size;
            try { size=Long.parseLong(c[1]); } catch (NumberFormatException e) { throw new IOException("Invalid resource size",e); }
            if (size<0 || size>2147483647L) throw new IOException("Resource size out of range");
            total+=size; if (total>4L*1024*1024*1024 || out.size()>=128) throw new IOException("Resource bundle too large");
            out.add(new Entry(c[0],size,c[2]));
        }
        if (out.isEmpty()) throw new IOException("Empty resource manifest");
        return out;
    }
    private static File child(File root,String name) throws IOException {
        File f=new File(root,name);
        if (!f.getCanonicalFile().getParentFile().equals(root.getCanonicalFile())) throw new IOException("Resource path escapes destination");
        return f;
    }
    private static void check(Progress p) throws InterruptedIOException {
        if (p.cancelled() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Extraction cancelled");
    }
    private static byte[] readSmall(File file) throws IOException {
        if (!file.isFile() || file.length()>65536) return new byte[0];
        ByteArrayOutputStream b=new ByteArrayOutputStream(); byte[] buf=new byte[4096];
        try (InputStream in=new FileInputStream(file)) { int n; while((n=in.read(buf))!=-1) { if(b.size()+n>65536) throw new IOException("State too large"); b.write(buf,0,n); } }
        return b.toByteArray();
    }
    private static String state(File root,List<Entry> entries,String id) throws IOException {
        StringBuilder s=new StringBuilder(id).append('\n');
        for(Entry e:entries) {
            File f=child(root,e.name);
            if (!f.isFile() || f.length()!=e.size) return "";
            s.append(e.name).append('\t').append(e.size).append('\t').append(f.lastModified()).append('\n');
        }
        return s.toString();
    }
    private static boolean valid(File f,Entry e,Progress p,long base,long total) throws IOException {
        if(!f.isFile() || f.length()!=e.size) return false;
        MessageDigest d=digest(); byte[] buf=new byte[1024*1024]; long nread=0;
        try(InputStream in=new FileInputStream(f)) {
            int n; while((n=in.read(buf))!=-1) { check(p); d.update(buf,0,n); nread+=n; p.update(base+nread,total,"校验 "+e.name); }
        }
        return nread==e.size && hex(d.digest()).equals(e.hash);
    }
    /** Metadata-only readiness check, before any Activity/native graphics lifecycle begins. */
    public static synchronized boolean ready(File root,File stateDir,byte[] manifest) throws IOException {
        List<Entry> entries=parse(manifest);
        String expected=state(root,entries,id(manifest));
        return !expected.isEmpty() && expected.equals(new String(readSmall(child(stateDir,"complete-v1.txt")),"UTF-8"));
    }
    public static synchronized Result prepare(File root,File stateDir,byte[] manifest,Source source,Platform platform,Progress p) throws IOException {
        List<Entry> entries=parse(manifest); String id=id(manifest);
        if ((!root.isDirectory() && !root.mkdirs()) || (!stateDir.isDirectory() && !stateDir.mkdirs())) throw new IOException("资源目录创建失败");
        File marker=child(stateDir,"complete-v1.txt");
        long total=0; for(Entry e:entries) total+=e.size;
        Result result=new Result(); check(p);
        String expected=state(root,entries,id);
        if (!expected.isEmpty() && expected.equals(new String(readSmall(marker),"UTF-8"))) {
            result.fast=true; result.reused=entries.size(); p.update(total,total,"资源已就绪"); return result;
        }
        long done=0;
        for(Entry e:entries) {
            check(p); File target=child(root,e.name);
            if (valid(target,e,p,done,total)) { result.reused++; done+=e.size; continue; }
            File temp=child(root,".smg-bundle-"+e.name+".part");
            // Reclaim only this installer's own incomplete staging file.
            if(temp.exists() && !temp.delete()) throw new IOException("暂存文件清理失败: "+e.name);
            if(platform.available(root)<e.size+64L*1024*1024) throw new IOException("存储空间不足，请至少再预留 "+((e.size+64L*1024*1024)/1024/1024)+" MB 后重试");
            boolean committed=false;
            try {
                MessageDigest d=digest(); byte[] buf=new byte[1024*1024]; long written=0;
                try(InputStream in=source.open(e.name); FileOutputStream out=new FileOutputStream(temp)) {
                    int n;
                    while((n=in.read(buf))!=-1) {
                        check(p); written+=n;
                        if(written>e.size) throw new IOException("资源长度异常: "+e.name);
                        out.write(buf,0,n);d.update(buf,0,n);p.update(done+written,total,"释放 "+e.name);
                    }
                    out.getFD().sync();
                }
                check(p);
                if(written!=e.size || !hex(d.digest()).equals(e.hash)) throw new IOException("资源校验失败: "+e.name);
                platform.replace(temp,target); committed=true; result.copied++; done+=e.size;
            } finally { if(!committed && temp.exists()) temp.delete(); }
        }
        check(p); String complete=state(root,entries,id);
        if(complete.isEmpty()) throw new IOException("资源提交后校验失败");
        File markTemp=child(stateDir,"complete-v1.tmp");
        try {
            try(FileOutputStream out=new FileOutputStream(markTemp)) { out.write(complete.getBytes("UTF-8"));out.getFD().sync(); }
            check(p);platform.replace(markTemp,marker);
        } finally { if(markTemp.exists()) markTemp.delete(); }
        p.update(total,total,"资源已就绪");return result;
    }
}

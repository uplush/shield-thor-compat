// SPDX-License-Identifier: MIT
// Copyright (c) 2026 shield-thor-compat contributors
import com.nvidia.NvAppBase.BundleStore;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

public final class HostStoreTest {
    static int checks;
    static void check(boolean yes) {checks++;if(!yes)throw new AssertionError("check "+checks);}
    interface Task {void run() throws Exception;}
    static void fails(Task t) throws Exception {boolean caught=false;try{t.run();}catch(IOException e){caught=true;}check(caught);}
    static String hash(byte[] b)throws Exception{return BundleStore.hex(MessageDigest.getInstance("SHA-256").digest(b));}
    static BundleStore.Platform platform=new BundleStore.Platform(){
        public void replace(File a,File b)throws IOException{Files.move(a.toPath(),b.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        public long available(File d){return d.getUsableSpace();}
    };
    static class Progress implements BundleStore.Progress {
        boolean cancel;long seen;
        public boolean cancelled(){return cancel;}
        public void update(long done,long total,String phase){check(done>=0 && done<=total);seen=done;}
    }
    static byte[] manifest(Map<String,byte[]> data)throws Exception{
        StringBuilder s=new StringBuilder();for(Map.Entry<String,byte[]> e:data.entrySet())s.append(e.getKey()).append('\t').append(e.getValue().length).append('\t').append(hash(e.getValue())).append('\n');return s.toString().getBytes("UTF-8");
    }
    public static void main(String[] args)throws Exception {
        Path base=Files.createTempDirectory(Paths.get(args[0]),"store-");
        Map<String,byte[]> data=new LinkedHashMap<>();byte[] a=new byte[2500000];new Random(42).nextBytes(a);data.put("a.pak",a);data.put("b.dat","second resource".getBytes("UTF-8"));
        byte[] manifest=manifest(data);int[] opened={0};BundleStore.Source src=name->{opened[0]++;return new ByteArrayInputStream(data.get(name));};
        File root=base.resolve("obb").toFile(),state=base.resolve("private-state").toFile();
        BundleStore.Result r=BundleStore.prepare(root,state,manifest,src,platform,new Progress());check(r.copied==2 && !r.fast);check(opened[0]==2);
        check(BundleStore.ready(root,state,manifest));
        check(Arrays.equals(Files.readAllBytes(new File(root,"a.pak").toPath()),a));
        byte[] sentinel="unrelated-save".getBytes("UTF-8");Files.write(base.resolve("product_default_A.npak"),sentinel);
        r=BundleStore.prepare(root,state,manifest,src,platform,new Progress());check(r.fast && r.reused==2);check(opened[0]==2);
        Files.delete(new File(root,"b.dat").toPath());r=BundleStore.prepare(root,state,manifest,src,platform,new Progress());check(r.copied==1 && r.reused==1);check(opened[0]==3);
        check(BundleStore.ready(root,state,manifest));
        byte[] bad=a.clone();bad[17]^=1;Files.write(new File(root,"a.pak").toPath(),bad);new File(root,"a.pak").setLastModified(1000);
        r=BundleStore.prepare(root,state,manifest,src,platform,new Progress());check(r.copied==1 && r.reused==1);check(Arrays.equals(Files.readAllBytes(new File(root,"a.pak").toPath()),a));
        File root2=base.resolve("bad-source").toFile(),state2=base.resolve("state2").toFile();
        check(!BundleStore.ready(root2,state2,manifest));
        fails(()->BundleStore.prepare(root2,state2,manifest,n->new ByteArrayInputStream(bad),platform,new Progress()));
        check(!new File(root2,"a.pak").exists());check(!new File(root2,".smg-bundle-a.pak.part").exists());check(!new File(state2,"complete-v1.txt").exists());
        Progress cancelled=new Progress(){public void update(long d,long t,String p){super.update(d,t,p);cancel=true;}};
        fails(()->BundleStore.prepare(root2,state2,manifest,src,platform,cancelled));check(!new File(root2,"a.pak").exists());check(!new File(state2,"complete-v1.txt").exists());
        r=BundleStore.prepare(root2,state2,manifest,src,platform,new Progress());check(r.copied==2);
        File root3=base.resolve("no-space").toFile(),state3=base.resolve("state3").toFile();
        BundleStore.Platform low=new BundleStore.Platform(){public void replace(File a,File b)throws IOException{throw new IOException("unexpected");}public long available(File d){return 1;}};
        fails(()->BundleStore.prepare(root3,state3,manifest,src,low,new Progress()));check(!new File(root3,"a.pak").exists());
        BundleStore.Platform commitFail=new BundleStore.Platform(){public void replace(File a,File b)throws IOException{throw new IOException("injected commit failure");}public long available(File d){return Long.MAX_VALUE;}};
        fails(()->BundleStore.prepare(root3,state3,manifest,src,commitFail,new Progress()));check(!new File(state3,"complete-v1.txt").exists());
        fails(()->BundleStore.parse(("../escape\t1\t"+hash(new byte[1])+"\n").getBytes("UTF-8")));
        fails(()->BundleStore.parse(("/absolute\t1\t"+hash(new byte[1])+"\n").getBytes("UTF-8")));
        fails(()->BundleStore.parse(("x\t-1\t"+hash(new byte[1])+"\n").getBytes("UTF-8")));
        fails(()->BundleStore.parse((new String(manifest,"UTF-8")+new String(manifest,"UTF-8")).getBytes("UTF-8")));
        check(Arrays.equals(Files.readAllBytes(base.resolve("product_default_A.npak")),sentinel));
        check(java.lang.reflect.Modifier.isSynchronized(BundleStore.class.getMethod("prepare",File.class,File.class,byte[].class,BundleStore.Source.class,BundleStore.Platform.class,BundleStore.Progress.class).getModifiers()));
        System.out.println("HOST_EXTRACTION_CHECKS="+checks+" PASS=1");
        System.out.println("COLD_COPY_FAST_REOPEN_REPAIR_CANCEL_LOW_SPACE_HASH_FAILURE_COMMIT_FAILURE_PATH_VALIDATION=PASS");
        if(args.length>1) {
            try(ZipFile zip=new ZipFile(args[1])) {
                byte[] full;try(InputStream in=zip.getInputStream(zip.getEntry("assets/smg_bundle/manifest.tsv"))){full=in.readAllBytes();}
                File dest=base.resolve("full-payload").toFile(),meta=base.resolve("full-state").toFile();
                BundleStore.Source assets=n->zip.getInputStream(zip.getEntry("assets/smg_bundle/data/"+n));
                r=BundleStore.prepare(dest,meta,full,assets,platform,new Progress());check(r.copied==16 && !r.fast);
                r=BundleStore.prepare(dest,meta,full,assets,platform,new Progress());check(r.fast && r.reused==16);
                System.out.println("REAL_APK_FULL_EXTRACTION_FILES=16\nREAL_APK_SECOND_OPEN_FAST=1");
            }
        }
        System.out.println("ANDROID_FIRST_LAUNCH_TESTED=0");
    }
}

package com.blackice.bc1;

import android.content.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ProjectRecoveryTest {
    Context context;File root;ProjectStore store;
    @Before public void setup(){Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();root=new File(base.getCacheDir(),"recovery-test-"+java.util.UUID.randomUUID());root.mkdirs();context=new ContextWrapper(base){@Override public File getFilesDir(){return root;}@Override public File getCacheDir(){return root;}@Override public Context getApplicationContext(){return this;}};store=new ProjectStore(context);}
    @After public void cleanup(){main(()->{for(ProjectSession s:new java.util.ArrayList<>(ProjectSession.ACTIVE.values()))if(s.model.name.startsWith("recovery-test"))s.finish();});remove(root);}
    static void remove(File f){File[] children=f.listFiles();if(children!=null)for(File c:children)remove(c);f.delete();}
    static void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    static void drain()throws Exception{for(int i=0;i<3;i++){ProjectSession.WRITER.submit(()->{}).get(10,TimeUnit.SECONDS);InstrumentationRegistry.getInstrumentation().waitForIdleSync();}}
    EditProject project()throws Exception{EditProject p=new EditProject();p.name="recovery-test";store.save(p);return p;}
    @Test public void checkpointFallbackAndOlderSaveRetirementPreserveNewerEdits()throws Exception{
        EditProject p=project();String base=DocumentIdentity.fingerprint(p);RecoveryStore recovery=new RecoveryStore(context);
        p.name="recovery-test first";recovery.write(p,"session",base,1);p.name="recovery-test second";RecoveryStore.Candidate newest=recovery.write(p,"session",base,2);
        recovery.saved(p.id,"session",1,"different");assertEquals(2,recovery.read(p.id).revision);
        // A new fallback is retained before deliberately corrupting the current generation.
        p.name="recovery-test third";recovery.write(p,"session",base,3);Files.write(new File(root,"project-recovery-v1/"+p.id+".current").toPath(),new byte[]{1,2,3});
        assertEquals(newest.identity,recovery.read(p.id).identity);assertEquals("recovery-test second",recovery.read(p.id).project.name);
        assertEquals("recovery-test",store.load(p.id).name);recovery.discard(p.id,newest.identity);assertNull(recovery.read(p.id));
    }
    @Test public void savingRestoredContentRetiresItsAncestorsButNotANewerCheckpoint()throws Exception{
        EditProject p=project();RecoveryStore recovery=new RecoveryStore(context);String base=DocumentIdentity.fingerprint(p);
        p.name="recovery-test old";recovery.write(p,"old-session",base,1);p.name="recovery-test restored";RecoveryStore.Candidate restored=recovery.write(p,"old-session",base,2);
        // A successful save of the restored revision supersedes its older fallback too.
        store.save(p);recovery.saved(p.id,"new-session",1,DocumentIdentity.fingerprint(p),restored.identity);assertNull(recovery.read(p.id));
        p.name="recovery-test before restore";restored=recovery.write(p,"old-session",base,3);p.name="recovery-test edited after restore";store.save(p);
        recovery.saved(p.id,"new-session",1,DocumentIdentity.fingerprint(p),restored.identity);assertNull(recovery.read(p.id));
        p.name="recovery-test new checkpoint";RecoveryStore.Candidate newest=recovery.write(p,"new-session",base,3);recovery.saved(p.id,"new-session",2,"older content",restored.identity);assertEquals(newest.identity,recovery.read(p.id).identity);
        // An interrupted AtomicFile promotion cannot replace the committed generation.
        Files.write(new File(root,"project-recovery-v1/"+p.id+".current.new").toPath(),new byte[]{1,2,3});assertEquals(newest.identity,new RecoveryStore(context).read(p.id).identity);
    }
    @Test public void failedOrdinarySaveIsStickyAndRetryUsesCurrentModel()throws Exception{
        EditProject p=project();String base=DocumentIdentity.fingerprint(p);ProjectSession[] holder=new ProjectSession[1];main(()->holder[0]=ProjectSession.attach(context,p,base));ProjectSession s=holder[0];
        File directory=new File(root,"projects-v1"),parked=new File(root,"parked-projects");assertTrue(directory.renameTo(parked));assertTrue(directory.createNewFile());
        main(()->{p.name="recovery-test unsaved";s.edited(p,false);s.save(null);});drain();main(()->assertEquals("Save failed",s.status()));
        main(()->{p.name="recovery-test newest";s.edited(p,false);assertEquals("Save failed",s.status());});drain();assertTrue(directory.delete());assertTrue(parked.renameTo(directory));
        main(()->s.save(null));drain();assertEquals("recovery-test newest",store.load(p.id).name);main(()->assertEquals("Saved",s.status()));main(s::finish);
    }
    @Test public void editDuringOlderSaveStaysDirtyAndUndoMatchesSavedContent()throws Exception{
        EditProject p=project();String hash=DocumentIdentity.fingerprint(p);ProjectSession[] holder=new ProjectSession[1];main(()->holder[0]=ProjectSession.attach(context,p,hash));ProjectSession s=holder[0];
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ProjectSession.WRITER.execute(()->{entered.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(10,TimeUnit.SECONDS));
        main(()->{p.name="recovery-test revision 1";s.edited(p,false);s.save(null);p.name="recovery-test revision 2";s.edited(p,false);});release.countDown();drain();
        assertEquals("recovery-test revision 1",store.load(p.id).name);main(()->assertEquals("Unsaved changes",s.status()));
        main(()->{p.name="recovery-test revision 1";s.edited(p,false);});drain();main(()->assertEquals("Saved",s.status()));main(s::finish);
    }
    @Test public void quotaNamesAndRecoveryKeepIdentityAndMissingMedia()throws Exception{
        EditProject p=new EditProject();p.name="";store.save(p);assertEquals("ICE_01",p.name);EditProject manual=new EditProject();manual.name="ICE_02";store.save(manual);EditProject third=new EditProject();third.name="  ";store.save(third);assertEquals("ICE_03",third.name);
        for(int i=3;i<99;i++){EditProject item=new EditProject();item.name="Custom "+i;store.save(item);}assertEquals(99,store.browser().size());
        try{store.duplicate(p.id);fail("duplicate must enforce quota");}catch(IOException expected){}assertEquals(99,store.browser().size());
        EditProject.Overlay o=new EditProject.Overlay();o.imageUri="file:///missing-original.png";o.motion.put(Motion.X,new Motion.Key(0,0));Motion.Key ending=new Motion.Key(2000,1);o.motion.put(Motion.X,ending);Keyframes.ease(o.motion.curves.get(Motion.X).get(0),3);p.overlays.add(o);
        RecoveryStore recovery=new RecoveryStore(context);recovery.write(p,"session",DocumentIdentity.fingerprint(store.load(p.id)),1);EditProject restored=recovery.read(p.id).project;assertEquals(p.id,restored.id);assertEquals("ICE_01",restored.name);assertEquals(o.imageUri,restored.overlays.get(0).imageUri);store.save(restored);assertEquals(99,store.browser().size());
        store.delete(p.id);assertNull(recovery.read(p.id));try{store.save(restored);fail("deleted project must not be resurrected by a pending save");}catch(IOException expected){}
    }
}

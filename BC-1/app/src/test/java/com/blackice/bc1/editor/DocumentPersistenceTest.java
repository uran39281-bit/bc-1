package com.blackice.bc1.editor;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DocumentPersistenceTest {
    @Test public void playheadAndCollapsedRowsDoNotDirtyTheDocumentButContentDoes()throws Exception{
        EditProject p=new EditProject();String saved=DocumentIdentity.fingerprint(p);p.playheadMs=33;p.tracks[0].collapsed=true;assertEquals(saved,DocumentIdentity.fingerprint(p));
        p.name="Changed";assertNotEquals(saved,DocumentIdentity.fingerprint(p));p.name="Untitled project";assertEquals(saved,DocumentIdentity.fingerprint(p));
        p.frameRate=60;assertNotEquals(saved,DocumentIdentity.fingerprint(p));
    }
    @Test public void queueCoalescesRapidRequestsAndPreservesCommitOrder(){
        List<Runnable> tasks=new ArrayList<>();List<Integer> committed=new ArrayList<>();
        @SuppressWarnings("unchecked") LatestQueue<Integer>[] holder=new LatestQueue[1];
        holder[0]=new LatestQueue<>(tasks::add,n->{committed.add(n);if(n==1){for(int i=2;i<=100;i++)holder[0].submit(i);assertEquals(2,holder[0].queued());}});
        holder[0].submit(1);assertEquals(1,tasks.size());tasks.remove(0).run();assertEquals(Arrays.asList(1,100),committed);assertEquals(0,holder[0].queued());
    }
    @Test public void detachedSnapshotCannotObservePartialLaterEdits()throws Exception{
        EditProject p=new EditProject();EditProject.Overlay o=new EditProject.Overlay();o.shape=1;p.overlays.add(o);EditProject snapshot=p.copy();String before=ProjectStore.encode(snapshot);o.x=3;o.scale=2;o.motion.put(Motion.X,new Motion.Key(0,3));assertEquals(before,ProjectStore.encode(snapshot));assertNotEquals(before,ProjectStore.encode(p));
    }
}

package com.blackice.bc1.editor;
import org.junit.Test;
import static org.junit.Assert.*;
public class EffectsRenderTest {
    private static EditProject withEffect(int effect){EditProject p=new EditProject();EditProject.Clip c=new EditProject.Clip();c.uri="content://test/media";c.name="Effect clip";c.effect=effect;c.outMs=c.sourceDurationMs=3000;p.clips.add(c);return p;}
    @Test public void renderSnapshotsKeepTheAppliedEffect()throws Exception{
        EditProject p=withEffect(12);
        assertEquals(12,ProjectStore.snapshot(p).clips.get(0).effect);
        assertEquals(12,ProjectStore.decode(ProjectStore.encode(p)).clips.get(0).effect);
    }
    @Test public void effectChangesProduceADifferentRender()throws Exception{
        assertNotEquals(ProjectStore.encode(withEffect(0)),ProjectStore.encode(withEffect(4)));
    }
    @Test public void captionLimitHasItsOwnMessage(){
        EditProject p=new EditProject();
        for(int i=0;i<=EditProject.MAX_CAPTIONS;i++){EditProject.Overlay o=new EditProject.Overlay();o.group=com.blackice.bc1.core.CaptionBuilder.GROUP;o.startMs=i*100L;o.endMs=o.startMs+100;p.overlays.add(o);}
        try{p.validate();fail();}catch(IllegalArgumentException e){assertTrue(e.getMessage(),e.getMessage().contains("captions"));}
    }
}

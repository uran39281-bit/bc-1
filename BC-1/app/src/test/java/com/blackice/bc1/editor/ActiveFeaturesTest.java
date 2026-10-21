package com.blackice.bc1.editor;
import org.junit.Test;
import static org.junit.Assert.*;
public class ActiveFeaturesTest {
 @Test public void bypassDoesNotEraseSavedEffects()throws Exception{EditProject p=new EditProject();EditProject.Clip c=new EditProject.Clip();c.uri="content://test/media";c.effect=12;c.outMs=c.sourceDurationMs=3000;p.clips.add(c);EditProject render=ProjectStore.snapshot(p);ActiveFeatures.prepareRender(render);assertEquals(0,render.clips.get(0).effect);assertEquals(12,p.clips.get(0).effect);assertEquals(12,ProjectStore.decode(ProjectStore.encode(p)).clips.get(0).effect);}
}

package com.blackice.bc1;

import android.content.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class RecoveryLifecycleTest {
    final WorkspaceV2Test h=new WorkspaceV2Test();
    AccessibilityNodeInfo find(AccessibilityNodeInfo n,String text){if(n==null)return null;if(n.getText()!=null&&text.equalsIgnoreCase(n.getText().toString()))return n;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo r=find(n.getChild(i),text);if(r!=null)return r;}return null;}
    AccessibilityNodeInfo await(String text)throws Exception{for(int i=0;i<100;i++){AccessibilityNodeInfo n=find(h.inst.getUiAutomation().getRootInActiveWindow(),text);if(n!=null)return n;Thread.sleep(100);}throw new AssertionError("Missing recovery control: "+text);}
    void click(String text)throws Exception{assertTrue(await(text).performAction(AccessibilityNodeInfo.ACTION_CLICK));h.inst.waitForIdleSync();}
    @Test public void restoreRetainsOrdinarySaveRecreationAndLaterThenExplicitDiscard()throws Exception{
        Context context=h.inst.getTargetContext();ProjectStore store=new ProjectStore(context);RecoveryStore recovery=new RecoveryStore(context);
        EditProject saved=new EditProject();saved.name="Recovery lifecycle";store.save(saved);String base=DocumentIdentity.fingerprint(saved);
        EditProject changed=saved.copy();changed.name="Recovered edits";EditProject.Overlay shape=ShapeCatalog.create(1,0);shape.imageUri="file:///unavailable-original.png";shape.motion.put(Motion.ROTATION,new Motion.Key(0,0));shape.motion.put(Motion.ROTATION,new Motion.Key(2000,100));Keyframes.ease(shape.motion.curves.get(Motion.ROTATION).get(0),1);changed.overlays.add(shape);recovery.write(changed,"interrupted-session",base,1);
        Intent intent=new Intent(context,EditorActivity.class).putExtra("project-id",saved.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try{
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(intent)){
                await("Recover project?");h.capture("v145-recovery-prompt");click("Restore");h.waitLoaded(scene);
                scene.onActivity(a->{assertEquals(saved.id,a.project.id);assertEquals("Recovered edits",a.project.name);assertTrue(a.projectSession.dirty());assertEquals(6.25f,a.project.overlays.get(0).motion.value(Motion.ROTATION,500,0),.0001f);a.selectOverlay(0);a.easingControls.select(Motion.ROTATION,0);});
                assertEquals("Recovery lifecycle",store.load(saved.id).name);scene.recreate();h.waitLoaded(scene);
                scene.onActivity(a->{assertEquals("Recovered edits",a.project.name);assertEquals(saved.id,a.projectSession.id);assertFalse(a.easingControls.selected.isEmpty());a.edit("Later recovered edit",()->a.project.name="Recovered again");a.projectSession.checkpoint();});ProjectRecoveryTest.drain();
                assertEquals("Recovered again",recovery.read(saved.id).project.name);
                // Simulate process-retained state being absent on the next cold open.
                scene.onActivity(a->a.projectSession.finish());
            }
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(intent)){await("Recover project?");click("Later");Thread.sleep(500);assertNotNull(recovery.read(saved.id));assertNull("Later must not immediately reopen recovery from Home",find(h.inst.getUiAutomation().getRootInActiveWindow(),"Recover project?"));}
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(intent)){
                await("Recover project?");click("Keep saved version");click("Discard recovered edits");h.waitLoaded(scene);assertNull(recovery.read(saved.id));
                scene.onActivity(a->{assertEquals("Recovery lifecycle",a.project.name);assertEquals("Saved",a.projectSession.status());a.projectSession.finish();});
            }
        }finally{h.inst.runOnMainSync(()->{ProjectSession s=ProjectSession.ACTIVE.get(saved.id);if(s!=null)s.finish();});store.delete(saved.id);}
    }
}

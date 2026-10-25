package com.blackice.bc1;
import android.content.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class WorkflowControlsTest {
    @Test public void landmarksShortcutsClockAndResponsiveMedia()throws Exception{
        WorkspaceV2Test h=new WorkspaceV2Test();android.content.Context context=h.inst.getTargetContext();String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        EditProject p=new EditProject();p.name="Workflow QA";new ProjectStore(context).save(p);context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putBoolean("ui-locked",false).putString("mode","AUTO").commit();Shortcuts.prefs(context).edit().clear().commit();
        try{h.shell("wm size 1080x1920");h.shell("wm density 320");try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
            h.waitLoaded(scenario);Thread.sleep(1200);
            scenario.onActivity(a->{assertEquals(p.id,a.project.id);for(int i=0;i<5;i++)LandmarkControls.add(a,i*100);assertEquals(5,a.project.landmarks.size());LandmarkControls.add(a,999);assertEquals(5,a.project.landmarks.size());a.undo(false);assertEquals(4,a.project.landmarks.size());a.undo(true);assertEquals(5,a.project.landmarks.size());
                a.workClock.checkpoint();assertTrue(a.workClock.total>0);long total=a.workClock.total;a.workClock.paused=true;a.workClock.checkpoint();assertTrue(a.workClock.total>=total);
                EditText input=new EditText(a);a.inspector.addView(input);input.requestFocus();assertFalse(Shortcuts.dispatch(a,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_M)));assertEquals(5,a.project.landmarks.size());input.clearFocus();
                Shortcuts.prefs(a).edit().putString(Shortcuts.Command.MARKER.name(),KeyEvent.KEYCODE_K+":0").commit();assertEquals(Shortcuts.Command.MARKER,Shortcuts.resolve(a,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_K)));assertNull(Shortcuts.resolve(a,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_M)));
                a.mobile.openAdd(0);a.mediaKind=MediaFilter.Kind.SHAPES;a.renderMediaGrid();assertNotNull(WorkspaceV2Test.described(a.mediaGrid,"Insert shape: Square"));});h.capture("workflow-portrait-media");
            scenario.onActivity(a->{a.addShapeAt(1,a.project.playheadMs,a.project.overlays.size());assertEquals(1,a.project.overlays.size());assertEquals(1,a.project.overlays.get(0).shape);a.saveNow();});Thread.sleep(600);
            h.shell("wm size 1920x1080");h.shell("wm density 160");scenario.recreate();h.waitLoaded(scenario);scenario.onActivity(a->{assertEquals(5,a.project.landmarks.size());assertEquals(1,a.project.overlays.size());assertEquals(MediaFilter.Kind.SHAPES,a.mediaKind);assertTrue(a.workClock.prefs().getBoolean(p.id+"-paused",false));a.selectOverlay(0);assertEquals(Motion.COUNT,a.panels.currentValues().length);EditProject.Overlay shape=a.overlay();Motion.Key firstOpacity=new Motion.Key(0,1);firstOpacity.x1=firstOpacity.y1=0;firstOpacity.x2=firstOpacity.y2=1;shape.motion.put(Motion.OPACITY,firstOpacity);shape.motion.put(Motion.OPACITY,new Motion.Key(1000,.2f));shape.motion.put(Motion.X,new Motion.Key(500,.3f));a.panels.category("Blending & Opacity");a.seek(0);
                TextView previous=WorkspaceV2Test.text(a.inspector,"‹  Previous Key"),next=WorkspaceV2Test.text(a.inspector,"Next Key  ›");assertFalse(previous.isEnabled());assertTrue(next.isEnabled());next.performClick();assertEquals(1000,a.project.playheadMs);assertEquals(2,shape.motion.curves.get(Motion.OPACITY).size());assertFalse(WorkspaceV2Test.text(a.inspector,"Next Key  ›").isEnabled());a.seek(500);assertTrue(WorkspaceV2Test.text(a.inspector,"Next Key  ›").isEnabled());assertTrue(WorkspaceV2Test.text(a.inspector,"‹  Previous Key").isEnabled());assertEquals("60.00",WorkspaceV2Test.field(a.inspector,"Opacity exact value in %").getText().toString());
                a.deselect();a.mediaKind=MediaFilter.Kind.ALL;a.renderMediaGrid();});h.capture("workflow-landscape-media");
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);long paused=context.getSharedPreferences("project-work-clocks",0).getLong(p.id,0);Thread.sleep(300);assertEquals(paused,context.getSharedPreferences("project-work-clocks",0).getLong(p.id,0));scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            scenario.onActivity(a->Shortcuts.assign(a,Shortcuts.Command.MARKER,Shortcuts.defaultBinding(Shortcuts.Command.PLAY),()->{}));click(h,"Cancel");assertEquals(Shortcuts.Command.PLAY,Shortcuts.resolve(context,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SPACE)));
            scenario.onActivity(a->Shortcuts.assign(a,Shortcuts.Command.MARKER,Shortcuts.defaultBinding(Shortcuts.Command.PLAY),()->{}));click(h,"Reassign");assertEquals(Shortcuts.Command.MARKER,Shortcuts.resolve(context,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SPACE)));assertEquals("",Shortcuts.binding(context,Shortcuts.Command.PLAY));
            scenario.onActivity(EditorActivity::projectSettingsDialog);h.capture("workflow-landscape-project-settings");h.inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            scenario.onActivity(EditorActivity::exportMenu);h.capture("workflow-landscape-export");h.inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            scenario.onActivity(a->Shortcuts.show(a));h.capture("workflow-landscape-shortcuts");h.inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        }}finally{h.shell("wm size "+size);h.shell("wm density "+density);Shortcuts.prefs(context).edit().clear().commit();context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
    }
    static void click(WorkspaceV2Test h,String label)throws Exception{h.inst.waitForIdleSync();android.view.accessibility.AccessibilityNodeInfo root=h.inst.getUiAutomation().getRootInActiveWindow();assertNotNull(root);boolean clicked=false;for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label)){if(label.contentEquals(node.getText()==null?"":node.getText())){android.view.accessibility.AccessibilityNodeInfo target=node;while(target!=null&&!target.isClickable())target=target.getParent();if(target!=null){clicked=target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);break;}}}assertTrue("Dialog button "+label,clicked);h.inst.waitForIdleSync();}
}

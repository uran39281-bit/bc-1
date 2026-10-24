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
                a.mobile.openAdd(0);a.mediaKind=MediaFilter.Kind.SHAPES;a.renderMediaGrid();assertNotNull(WorkspaceV2Test.described(a.mediaGrid,"Insert shape: Rectangle"));});h.capture("workflow-portrait-media");
            scenario.onActivity(a->{WorkspaceV2Test.described(a.mediaGrid,"Insert shape: Rectangle").performClick();assertEquals(1,a.project.overlays.size());assertEquals(1,a.project.overlays.get(0).shape);a.saveNow();});Thread.sleep(600);
            h.shell("wm size 1920x1080");h.shell("wm density 160");scenario.recreate();h.waitLoaded(scenario);scenario.onActivity(a->{assertEquals(5,a.project.landmarks.size());assertEquals(1,a.project.overlays.size());assertEquals(MediaFilter.Kind.SHAPES,a.mediaKind);assertTrue(a.workClock.prefs().getBoolean(p.id+"-paused",false));a.deselect();a.mediaKind=MediaFilter.Kind.ALL;a.renderMediaGrid();});h.capture("workflow-landscape-media");
            scenario.onActivity(EditorActivity::projectSettingsDialog);h.capture("workflow-landscape-project-settings");h.inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            scenario.onActivity(a->Shortcuts.show(a));h.capture("workflow-landscape-shortcuts");h.inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        }}finally{h.shell("wm size "+size);h.shell("wm density "+density);Shortcuts.prefs(context).edit().clear().commit();context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
    }
}

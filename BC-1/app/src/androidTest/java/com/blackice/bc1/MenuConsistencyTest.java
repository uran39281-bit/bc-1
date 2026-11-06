package com.blackice.bc1;

import android.app.AlertDialog;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.menu.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Independent native phone/wide evidence for the 1.5.4 controls and modal routes. */
@RunWith(AndroidJUnit4.class)
public class MenuConsistencyTest {
    final WorkspaceV2Test h=new WorkspaceV2Test();
    static <T extends View> T find(View v,Class<T> type){if(type.isInstance(v))return type.cast(v);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){T found=find(((ViewGroup)v).getChildAt(i),type);if(found!=null)return found;}return null;}
    static void keys(View v){if(v instanceof KeyframeButton){assertTrue("Keyframe has measurable bounds",v.getWidth()>0);assertEquals("Square keyframe box",v.getWidth(),v.getHeight());}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)keys(((ViewGroup)v).getChildAt(i));}
    @Test public void rulersCollapsedCurvesAndSquareKeysWorkInBothWorkspaces()throws Exception{
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{for(boolean wide:new boolean[]{true,false}){
            new Workspace144Test().fixture();h.shell("wm size "+(wide?"1920x1080":"824x1784"));h.shell("wm density "+(wide?"160":"320"));
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
                scene.onActivity(a->{a.getSharedPreferences(MenuScreenFactory.CONTROL_PREFS,0).edit().putString(MenuScreenFactory.POSITION_CONTROL,"RULER").commit();a.selectClip(0,false);a.seek(0);a.panels.category("Move & Transform");assertNull("No conventional position menu slider",find(a.inspector,SeekBar.class));assertNotNull(find(a.inspector,RulerView.class));CollapsibleSection section=find(a.inspector,CollapsibleSection.class);assertNotNull(section);assertFalse("First open is collapsed",section.expanded());});
                Thread.sleep(250);scene.onActivity(a->keys(a.inspector));h.capture("v154-"+(wide?"wide":"phone")+"-position-collapsed");
                scene.onActivity(a->{String before;try{before=ProjectStore.encode(a.project);}catch(Exception ex){throw new AssertionError(ex);}int undo=a.undo.size();CollapsibleSection section=find(a.inspector,CollapsibleSection.class);section.getChildAt(0).performClick();assertTrue(section.expanded());try{assertEquals("Expanding is not a document edit",before,ProjectStore.encode(a.project));}catch(Exception ex){throw new AssertionError(ex);}assertEquals(undo,a.undo.size());});
                Thread.sleep(200);h.capture("v154-"+(wide?"wide":"phone")+"-position-expanded");
                for(String route:new String[]{"Blending & Opacity","Effects","Color & Fill","Shape & Style"}){
                    scene.onActivity(a->{a.panels.reset();if(route.equals("Color & Fill")||route.equals("Shape & Style"))a.selectOverlay(0);else a.selectClip(0,false);if(route.equals("Effects"))a.edit("Stack review",()->{a.clip().addEffect(9);a.clip().addEffect(25);a.clip().addEffect(4);});if(route.equals("Color & Fill")){a.edit("Fill curve review",()->{a.overlay().fill=new FillStyle(Color.RED);a.overlay().fill.solid.toggle(0);a.overlay().fill.solid.set(2000,Color.BLUE);});a.easingControls.selectFill("Fill color and alpha",a.overlay().fill.solid,0);}a.panels.category(route);assertNull(route+" has no conventional slider",find(a.inspector,SeekBar.class));if(route.equals("Shape & Style")){for(String duplicate:new String[]{"Move & Transform","Duration","Animation"})assertNull(duplicate,WorkspaceV2Test.text(a.inspector,duplicate));}});
                    Thread.sleep(200);scene.onActivity(a->keys(a.inspector));h.capture("v154-"+(wide?"wide":"phone")+"-"+route.toLowerCase(java.util.Locale.ROOT).replace(" & ","-").replace(" ","-"));
                    if(route.equals("Effects"))scene.onActivity(a->{
                        assertEquals(3,a.clip().effects().size());assertFalse(WorkspaceV2Test.text(a.inspector,"Add Effects").isEnabled());String last=a.clip().effects().get(2).id;float first=a.clip().intensity;a.transformPanel.menus.selectedEffectId=last;a.renderInspector();EditText strength=WorkspaceV2Test.field(a.inspector,"Effect strength exact value in percent");strength.setText("65");strength.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);assertEquals(.65f,a.clip().effect(last).intensity,1e-5);assertEquals("Other instance's strength is independent",first,a.clip().intensity,0);
                        KeyframeButton key=find(a.inspector,KeyframeButton.class);assertNotNull(key);key.performClick();assertEquals(1,a.clip().effect(last).motion.curves.get(Motion.INTENSITY).size());assertTrue(a.clip().motion.curves.get(Motion.INTENSITY).isEmpty());a.undo(false);assertTrue(a.clip().effect(last).motion.curves.get(Motion.INTENSITY).isEmpty());
                        String middle=a.clip().effects().get(1).id;a.transformPanel.menus.selectedEffectId=middle;a.renderInspector();WorkspaceV2Test.text(a.inspector,"Remove Effect").performClick();assertEquals(2,a.clip().effects().size());assertNotNull(a.clip().effect(last));assertTrue(WorkspaceV2Test.text(a.inspector,"Add Effects").isEnabled());a.undo(false);assertEquals(middle,a.clip().effects().get(1).id);
                    });
                    if(route.equals("Color & Fill")){
                        scene.onActivity(a->{CollapsibleSection section=find(a.inspector,CollapsibleSection.class);assertFalse(section.expanded());int undo=a.undo.size();section.getChildAt(0).performClick();assertEquals(undo,a.undo.size());CurveEditorView graph=find(section,CurveEditorView.class);assertTrue(graph.isEnabled());graph.onKeyDown(KeyEvent.KEYCODE_DPAD_UP,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));assertEquals(undo+1,a.undo.size());assertEquals(-1,a.overlay().fill.solid.interval(0).easing);a.undo(false);assertEquals(0,a.overlay().fill.solid.interval(0).easing);a.undo(true);assertEquals(-1,a.overlay().fill.solid.interval(0).easing);});
                        Thread.sleep(200);h.capture("v154-"+(wide?"wide":"phone")+"-color-fill-expanded");
                    }

                }
            }
        }}finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }
    @Test public void dialogsUseRealDataAndPortraitCreationDoesNotSwitchWorkspace()throws Exception{
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{for(boolean wide:new boolean[]{true,false}){
            new Workspace144Test().fixture();h.shell("wm size "+(wide?"1920x1080":"824x1784"));h.shell("wm density "+(wide?"160":"320"));
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);scene.onActivity(a->a.exportMenu());Thread.sleep(250);h.capture("v154-"+(wide?"wide":"phone")+"-export");scene.onActivity(a->{assertTrue(a.exportFormDialog.isShowing());assertNotNull(WorkspaceV2Test.text(a.exportFormDialog.getWindow().getDecorView(),"Output summary"));a.exportFormDialog.dismiss();a.showSettingsWindow();});Thread.sleep(250);h.capture("v154-"+(wide?"wide":"phone")+"-settings");scene.onActivity(a->a.settingsDialog.dismiss());}
            final AlertDialog[] dialog={null};AtomicReference<EditProject> created=new AtomicReference<>();
            try(ActivityScenario<HomeActivity> home=ActivityScenario.launch(HomeActivity.class)){Thread.sleep(350);home.onActivity(a->{dialog[0]=NewProjectDialog.show(a,created::set);View ratio=WorkspaceV2Test.described(dialog[0].getWindow().getDecorView(),"Aspect ratio 9:16");assertNotNull(ratio);ratio.performClick();assertTrue(ratio.isSelected());});Thread.sleep(200);h.capture("v154-"+(wide?"wide":"phone")+"-create-portrait");home.onActivity(a->WorkspaceV2Test.text(dialog[0].getWindow().getDecorView(),"Create project").performClick());assertNotNull(created.get());assertArrayEquals(new int[]{1080,1920},created.get().canvasSize(1080));assertArrayEquals(new int[]{1080,1920},ProjectStore.decode(ProjectStore.encode(created.get())).canvasSize(1080));}
        }}finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }
}

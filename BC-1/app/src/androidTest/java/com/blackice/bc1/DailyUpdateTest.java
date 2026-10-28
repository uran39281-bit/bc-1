package com.blackice.bc1;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DailyUpdateTest {
    final WorkspaceV2Test h=new WorkspaceV2Test();
    @Test public void framingEasingRulersAndToolbarUseRealModelOnBothLayouts()throws Exception{
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{new Workspace144Test().fixture();h.shell("wm size 1920x1080");h.shell("wm density 240");
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
                scene.onActivity(a->{a.selectOverlay(0);a.seek(1000);a.panels.category("Move & Transform");a.transformPanel.tab=2;a.renderInspector();
                    EditProject.Overlay o=a.overlay();assertFalse(o.motion.animated());int undo=a.undo.size();
                    WorkspaceV2Test.text(a.inspector,"Fit Canvas").performClick();assertEquals(undo+1,a.undo.size());assertFalse("Framing static layers does not create animation",o.motion.animated());
                    float scale=o.scale;WorkspaceV2Test.text(a.inspector,"Fit Canvas").performClick();assertEquals("Repeated fit is a no-op",undo+1,a.undo.size());assertEquals(scale,o.scale,0);
                    WorkspaceV2Test.text(a.inspector,"Fill Canvas").performClick();assertEquals(undo+2,a.undo.size());a.undo(false);assertEquals(scale,a.overlay().scale,.0001f);
                    assertNotNull(WorkspaceV2Test.described(a.header,"Back to Projects"));assertNull(WorkspaceV2Test.described(a.header,"Undo"));assertNotNull(WorkspaceV2Test.described(a.timelineTools,"Trim Start"));assertNotNull(WorkspaceV2Test.described(a.timelineTools,"Split"));assertNotNull(WorkspaceV2Test.described(a.timelineTools,"Trim End"));
                    View trash=WorkspaceV2Test.described(a.timelineTools,"Delete selected layer");int index=a.timelineTools.indexOfChild(trash);assertEquals("Undo",a.timelineTools.getChildAt(index+1).getContentDescription());assertEquals("Redo",a.timelineTools.getChildAt(index+2).getContentDescription());
                    long playhead=a.project.playheadMs;a.zoomRuler.set(0);assertEquals(8,a.pixelsPerSecond,0);a.zoomRuler.set(10);assertEquals(2400,a.pixelsPerSecond,.01);assertEquals(playhead,a.project.playheadMs);a.fitTimeline();
                    a.edit("Animation fixture",()->{EditProject.Overlay shape=a.overlay();shape.motion.put(Motion.ROTATION,new Motion.Key(0,0));shape.motion.put(Motion.ROTATION,new Motion.Key(2000,100));shape.motion.put(Motion.X,new Motion.Key(0,0));shape.motion.put(Motion.X,new Motion.Key(2000,1));});
                    a.easingControls.select(Motion.ROTATION,0);a.transformPanel.tab=1;a.renderInspector();WorkspaceV2Test.text(a.inspector,"Ease In").performClick();assertEquals(6.25f,a.overlay().motion.value(Motion.ROTATION,500,0),.0001f);assertEquals(.25f,a.overlay().motion.value(Motion.X,500,0),.0001f);
                    a.panels.back();a.panels.category("Blending & Opacity");NumberRuler opacity=findRuler(a.inspector);assertNotNull(opacity);opacity.begin.run();opacity.set(0);opacity.end.run();assertEquals(0,a.overlay().opacity,0);assertTrue(a.overlay().motion.curves.get(Motion.OPACITY).isEmpty());
                    opacity=findRuler(a.inspector);opacity.begin.run();opacity.set(100);opacity.end.run();assertEquals(1,a.overlay().opacity,0);
                    a.panels.back();a.panels.category("Effects");assertEquals("Only title/back row and Add Effects",2,a.inspector.getChildCount());WorkspaceV2Test.text(a.inspector,"Add Effects").performClick();assertTrue(a.effectCatalog.showing);a.effectCatalog.close();a.panels.back();a.panels.category("Move & Transform");a.transformPanel.tab=2;a.renderInspector();
                });Thread.sleep(500);h.capture("v145-wide-transform");
                h.shell("wm size 1080x1920");h.shell("wm density 420");Thread.sleep(1000);
                scene.onActivity(a->{assertNotNull(a.mobile);assertNotNull(WorkspaceV2Test.text(a.inspector,"Fit Canvas"));assertNotNull(WorkspaceV2Test.described(a.timelineTools,"Undo"));a.saveNow();});Thread.sleep(500);h.capture("v145-phone-transform");
            }
        }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }
    private NumberRuler findRuler(View view){if(view instanceof NumberRuler)return (NumberRuler)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){NumberRuler r=findRuler(((ViewGroup)view).getChildAt(i));if(r!=null)return r;}return null;}
}

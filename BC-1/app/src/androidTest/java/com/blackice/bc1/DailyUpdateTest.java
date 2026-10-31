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
                    a.easingControls.select(Motion.ROTATION,0);a.transformPanel.tab=1;a.renderInspector();undo=a.undo.size();
                    assertNull("The four preset buttons are gone",WorkspaceV2Test.text(a.inspector,"Ease In"));
                    dragHandle(a,1d/3,1d/3,1d/3,0);dragHandle(a,2d/3,2d/3,2d/3,1d/3);assertEquals("One undo step per handle drag",undo+2,a.undo.size());
                    Motion.Key eased=a.overlay().motion.curves.get(Motion.ROTATION).get(0);assertEquals("A custom curve replaces the preset on that one key",-1,eased.easingToNext);
                    assertEquals("Dragged handles approximate the quadratic ease in",6.25f,a.overlay().motion.value(Motion.ROTATION,500,0),.6f);assertEquals("The X track is untouched",.25f,a.overlay().motion.value(Motion.X,500,0),.0001f);
                    assertEquals("Handles never leave the unit square",0,eased.y1,0);assertEquals(2,a.overlay().motion.curves.get(Motion.ROTATION).size());
                    a.panels.back();a.panels.category("Blending & Opacity");dragRuler(a,-3000);assertEquals(0,a.overlay().opacity,0);assertTrue("A static layer stays static",a.overlay().motion.curves.get(Motion.OPACITY).isEmpty());
                    dragRuler(a,3000);assertEquals(1,a.overlay().opacity,0);
                    a.panels.back();a.edit("Fill easing fixture",()->{a.overlay().fill=new FillStyle(0xFFFF0000);a.overlay().fill.gradient=true;a.overlay().fill.angle.toggle(0);a.overlay().fill.angle.set(2000,100);});a.panels.category("Color & Fill");a.easingControls.selectFill("Gradient direction",a.overlay().fill.angle,0);a.renderInspector();WorkspaceV2Test.described(a.inspector,"Gradient direction Ease Out").performClick();assertEquals(43.75,a.overlay().fill.angle.at(500),1e-9);assertTrue(a.overlay().fill.solid.keys.isEmpty());a.undo(false);assertEquals(25,a.overlay().fill.angle.at(500),1e-9);
                    a.panels.back();a.panels.category("Effects");assertEquals("Only the menu body, with Add Effects and no invented effect",1,a.inspector.getChildCount());assertNull(WorkspaceV2Test.text(a.inspector,"Remove Effect"));WorkspaceV2Test.text(a.inspector,"Add Effects").performClick();assertTrue(a.effectCatalog.showing);a.effectCatalog.close();a.panels.back();a.panels.category("Move & Transform");a.transformPanel.tab=2;a.renderInspector();
                });Thread.sleep(500);h.capture("v145-wide-transform");
                h.shell("wm size 1080x1920");h.shell("wm density 420");Thread.sleep(1000);
                scene.onActivity(a->{assertNotNull("Phone workspace",a.mobile);assertNotNull("Framing panel survives resize",WorkspaceV2Test.text(a.inspector,"Fit Canvas"));assertNotNull("Undo in phone timeline toolbar",WorkspaceV2Test.described(a.timelineTools,"Undo"));a.saveNow();});Thread.sleep(500);h.capture("v145-phone-transform");
            }
        }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }
    private static View find(View v,Class<?> type){if(type.isInstance(v))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type);if(found!=null)return found;}return null;}
    private static void send(View v,long down,long time,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);try{v.dispatchTouchEvent(e);}finally{e.recycle();}}
    /** Drags the first ruler by dx pixels (finger right increases the value). */
    private static void dragRuler(EditorActivity a,float dx){View ruler=find(a.inspector,com.blackice.bc1.menu.RulerView.class);assertNotNull(ruler);ruler.layout(0,0,600,64);long t=android.os.SystemClock.uptimeMillis();float from=dx<0?3200:0;send(ruler,t,t,MotionEvent.ACTION_DOWN,from,20);send(ruler,t,t+20,MotionEvent.ACTION_MOVE,from+dx/2,20);send(ruler,t,t+40,MotionEvent.ACTION_MOVE,from+dx,20);send(ruler,t,t+60,MotionEvent.ACTION_UP,from+dx,20);}
    /** Drags a graph handle between two normalized graph positions through real touch events. */
    private static void dragHandle(EditorActivity a,double fromX,double fromY,double toX,double toY){
        View graph=find(a.inspector,com.blackice.bc1.menu.CurveEditorView.class);assertNotNull(graph);graph.layout(0,0,600,400);graph.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(600,400,android.graphics.Bitmap.Config.ARGB_8888)));
        android.content.Context c=a;float left=com.blackice.bc1.menu.MenuPalette.dp(c,47),top=com.blackice.bc1.menu.MenuPalette.dp(c,18),right=600-com.blackice.bc1.menu.MenuPalette.dp(c,18),bottom=400-com.blackice.bc1.menu.MenuPalette.dp(c,46);
        float x0=left+(float)fromX*(right-left),y0=bottom-(float)fromY*(bottom-top),x1=left+(float)toX*(right-left),y1=bottom-(float)toY*(bottom-top);
        long t=android.os.SystemClock.uptimeMillis();send(graph,t,t,MotionEvent.ACTION_DOWN,x0,y0);send(graph,t,t+20,MotionEvent.ACTION_MOVE,(x0+x1)/2,(y0+y1)/2);send(graph,t,t+40,MotionEvent.ACTION_MOVE,x1,y1);send(graph,t,t+60,MotionEvent.ACTION_UP,x1,y1);
    }
}

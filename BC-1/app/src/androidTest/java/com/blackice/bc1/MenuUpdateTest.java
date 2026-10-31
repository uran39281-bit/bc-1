package com.blackice.bc1;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.core.BlendMode;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** The five redesigned menus, driven through their real controls against the real project model. */
@RunWith(AndroidJUnit4.class)
public class MenuUpdateTest {
    final WorkspaceV2Test h=new WorkspaceV2Test();

    private static String field(EditorActivity a,String description){EditText f=WorkspaceV2Test.field(a.inspector,description);assertNotNull(description,f);return f.getText().toString();}
    private static void type(EditorActivity a,String description,String value){EditText f=WorkspaceV2Test.field(a.inspector,description);assertNotNull(description,f);f.setText(value);f.onEditorAction(EditorInfo.IME_ACTION_DONE);}
    private static void click(EditorActivity a,String description){View v=WorkspaceV2Test.described(a.inspector,description);assertNotNull(description,v);assertTrue(description+" enabled",v.isEnabled());v.performClick();}
    private static void assertNoPresetButtons(EditorActivity a){for(String old:new String[]{"Linear","Ease In","Ease Out","Ease In/Out"})assertNull(old+" preset button removed",WorkspaceV2Test.text(a.inspector,old));}

    @Test public void menusFollowTheReferenceStructureAndStayConnectedToTheModel()throws Exception{
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{new Workspace144Test().fixture();h.shell("wm size 1080x1920");h.shell("wm density 420");
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
                // Position: order, no automatic keyframes, no preset buttons.
                scene.onActivity(a->{a.selectClip(0,false);a.seek(1000);a.transformPanel.tab=0;a.panels.category("Move & Transform");
                    for(String text:new String[]{"Move & Transform","Position","Rotation","Scale","Previous Key","Reset all","Next Key","Easing Curve Editor","Reset curve","Drag handles to adjust easing"})assertNotNull(text,WorkspaceV2Test.text(a.inspector,text));
                    assertNoPresetButtons(a);assertFalse("Opening the menu does not create animation",a.selectedMotion().animated());
                    assertEquals("No next keyframe",WorkspaceV2Test.startsWith(a.inspector,"No next keyframe").getText().toString());assertFalse("Reset curve is disabled without an interval",WorkspaceV2Test.described(a.inspector,"Reset curve").isEnabled());
                    assertEquals("0.00",field(a,"X exact value in pixels"));assertEquals("0.00",field(a,"Y exact value in pixels"));
                    type(a,"X exact value in pixels","100");assertEquals(100*2f/a.exportSize()[0],a.clip().x,1e-5);assertFalse("A static layer stays static",a.selectedMotion().animated());a.undo(false);assertEquals(0,a.clip().x,0);
                    type(a,"X exact value in pixels","not a number");assertEquals("Invalid text never reaches the model",0,a.clip().x,0);});
                Thread.sleep(300);h.capture("v147-phone-position");
                // Rotation: cumulative presets and one undo step each.
                scene.onActivity(a->{a.transformPanel.tab=1;a.renderInspector();
                    for(String text:new String[]{"Angle presets","45°","90°","180°","360°"})assertNotNull(text,WorkspaceV2Test.text(a.inspector,text));assertNoPresetButtons(a);
                    int undo=a.undo.size();click(a,"Add 360 degrees");click(a,"Add 45 degrees");assertEquals(405,-a.clip().rotation,1e-4);assertEquals("405.00",field(a,"Rotation exact value in degrees"));assertEquals(undo+2,a.undo.size());
                    click(a,"Subtract 90 degrees");assertEquals(315,-a.clip().rotation,1e-4);a.undo(false);assertEquals(405,-a.clip().rotation,1e-4);
                    a.clip().rotation=0;a.renderInspector();click(a,"Subtract 360 degrees");assertEquals("Negative turns are valid",-360,-a.clip().rotation,1e-4);assertFalse(a.selectedMotion().animated());});
                Thread.sleep(300);h.capture("v147-phone-rotation");
                // Scale: independent and linked dimensions.
                scene.onActivity(a->{a.clip().rotation=0;a.transformPanel.tab=2;a.renderInspector();
                    for(String text:new String[]{"Fit Canvas","Fill Canvas","Lock W and H","Width","Height"})assertNotNull(text,WorkspaceV2Test.text(a.inspector,text));
                    assertTrue(a.transformPanel.linked);assertEquals("100.00",field(a,"Width exact value in percent"));assertEquals("100.00",field(a,"Height exact value in percent"));
                    click(a,"Lock W and H");assertFalse(a.transformPanel.linked);type(a,"Width exact value in percent","150");
                    assertEquals("150.00",field(a,"Width exact value in percent"));assertEquals("Unlocked: height keeps its size","100.00",field(a,"Height exact value in percent"));
                    click(a,"Lock W and H");assertTrue(a.transformPanel.linked);type(a,"Width exact value in percent","300");
                    assertEquals("300.00",field(a,"Width exact value in percent"));assertEquals("Locked: height follows the same factor",200d,Double.parseDouble(field(a,"Height exact value in percent")),.01);
                    assertFalse("Resizing never invents keyframes",a.selectedMotion().animated());});
                Thread.sleep(300);h.capture("v147-phone-scale");
                // Blending: seven modes mapped to real compositor modes, opacity bounded.
                scene.onActivity(a->{a.transformPanel.tab=0;a.panels.back();a.panels.category("Blending & Opacity");
                    for(String text:new String[]{"Blending & Opacity","Opacity","Apply","Reset","Blend Mode","Multiply","Screen","Overlay","Add","Darken","Lighten","0% transparent · 100% visible"})assertNotNull(text,WorkspaceV2Test.text(a.inspector,text));
                    assertNotNull("Selected mode carries a marker",WorkspaceV2Test.described(a.inspector,"Normal, selected"));assertNoPresetButtons(a);
                    String[] names={"Multiply","Screen","Overlay","Add","Darken","Lighten"};BlendMode[] modes={BlendMode.MULTIPLY,BlendMode.SCREEN,BlendMode.OVERLAY,BlendMode.LINEAR_DODGE,BlendMode.DARKEN,BlendMode.LIGHTEN};
                    for(int i=0;i<names.length;i++){click(a,names[i]);assertEquals(names[i]+" maps to its own compositor mode",modes[i].ordinal(),a.clip().blend);assertEquals("Blend changes never touch opacity",1,a.clip().opacity,0);assertNotNull(WorkspaceV2Test.described(a.inspector,names[i]+", selected"));}
                    int undo=a.undo.size();a.undo(false);assertEquals(BlendMode.DARKEN.ordinal(),a.clip().blend);a.undo(true);assertEquals(BlendMode.LIGHTEN.ordinal(),a.clip().blend);assertEquals(undo,a.undo.size());
                    type(a,"Opacity exact value in percent","150");assertEquals("Opacity above 100% is rejected",1,a.clip().opacity,0);type(a,"Opacity exact value in percent","-5");assertEquals(1,a.clip().opacity,0);
                    type(a,"Opacity exact value in percent","60");assertEquals(.6f,a.clip().opacity,1e-5);assertFalse(a.selectedMotion().animated());click(a,"Reset");assertEquals(1,a.clip().opacity,0);});
                Thread.sleep(300);h.capture("v147-phone-blending");
                // Shapes keep their own seven stored overlay modes.
                scene.onActivity(a->{a.panels.back();a.selectOverlay(0);a.panels.category("Blending & Opacity");click(a,"Screen");assertEquals(2,a.overlay().blend);click(a,"Add");assertEquals(4,a.overlay().blend);assertNull("Overlays have no extra clip-only modes",WorkspaceV2Test.text(a.inspector,"More…"));a.panels.back();a.selectClip(0,false);});
                // Effects: empty state, then a real applied effect with its own animation.
                scene.onActivity(a->{a.panels.reset();a.panels.category("Effects");assertEquals(1,a.inspector.getChildCount());assertNotNull(WorkspaceV2Test.text(a.inspector,"Add Effects"));assertNull("No invented effect",WorkspaceV2Test.text(a.inspector,"Remove Effect"));assertNull(WorkspaceV2Test.text(a.inspector,"Effect strength"));
                    a.panels.back();a.edit("Effect fixture",()->{EditProject.Clip c=a.clip();c.effect=2;c.intensity=.5f;c.motion.put(Motion.INTENSITY,new Motion.Key(0,.2f));c.motion.put(Motion.INTENSITY,new Motion.Key(2000,.8f));c.motion.put(Motion.X,new Motion.Key(0,0));c.motion.put(Motion.X,new Motion.Key(1000,.5f));});
                    a.panels.category("Effects");assertEquals("Shows the real effect name",com.blackice.bc1.core.EffectCatalog.name(2),WorkspaceV2Test.text(a.inspector,com.blackice.bc1.core.EffectCatalog.name(2)).getText().toString());
                    for(String text:new String[]{"Effect strength","Apply","Reset","Previous Key","Next Key","Easing Curve Editor","Remove Effect"})assertNotNull(text,WorkspaceV2Test.text(a.inspector,text));assertNoPresetButtons(a);});
                Thread.sleep(300);h.capture("v147-phone-effects");
                scene.onActivity(a->{int undo=a.undo.size();click(a,"Remove Effect");EditProject.Clip c=a.clip();assertEquals(0,c.effect);assertTrue("Only the effect's own animation is removed",c.motion.curves.get(Motion.INTENSITY).isEmpty());assertEquals("Other animation is preserved",2,c.motion.curves.get(Motion.X).size());assertEquals(undo+1,a.undo.size());
                    a.undo(false);c=a.clip();assertEquals("Undo restores the effect",2,c.effect);assertEquals("Undo restores its animation exactly",2,c.motion.curves.get(Motion.INTENSITY).size());assertEquals(.8f,c.motion.curves.get(Motion.INTENSITY).get(1).value,0);});
                // Resized wide workspace keeps the same menus.
                h.shell("wm size 1920x1080");h.shell("wm density 160");Thread.sleep(1000);
                scene.onActivity(a->{a.panels.reset();a.selectClip(0,false);a.transformPanel.tab=0;a.panels.category("Move & Transform");assertNotNull(WorkspaceV2Test.text(a.inspector,"Easing Curve Editor"));assertNoPresetButtons(a);});
                Thread.sleep(500);h.capture("v147-wide-position");
            }
        }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }

    @Test public void interruptedGraphGesturesRestoreTheOriginalCurve()throws Exception{
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{new Workspace144Test().fixture();h.shell("wm size 1920x1080");h.shell("wm density 160");
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
                scene.onActivity(a->{a.selectOverlay(0);a.edit("Keys",()->{a.overlay().motion.put(Motion.OPACITY,new Motion.Key(0,1));a.overlay().motion.put(Motion.OPACITY,new Motion.Key(2000,0));a.overlay().motion.put(Motion.OPACITY,new Motion.Key(3000,1));});
                    a.easingControls.select(Motion.OPACITY,0);a.panels.category("Blending & Opacity");
                    assertEquals("Opacity · Key 1 → Key 2",WorkspaceV2Test.startsWith(a.inspector,"Opacity · Key 1").getText().toString());
                    Motion.Key first=a.overlay().motion.curves.get(Motion.OPACITY).get(0);int preset=first.easingToNext;float[] before={first.x1,first.y1,first.x2,first.y2};int undo=a.undo.size();
                    View graph=find(a.inspector,com.blackice.bc1.menu.CurveEditorView.class);assertNotNull(graph);graph.layout(0,0,600,400);graph.draw(new android.graphics.Canvas(android.graphics.Bitmap.createBitmap(600,400,android.graphics.Bitmap.Config.ARGB_8888)));
                    float left=com.blackice.bc1.menu.MenuPalette.dp(a,47),top=com.blackice.bc1.menu.MenuPalette.dp(a,18),right=600-com.blackice.bc1.menu.MenuPalette.dp(a,18),bottom=400-com.blackice.bc1.menu.MenuPalette.dp(a,46);
                    float x0=left+(right-left)/3f,y0=bottom-(bottom-top)/3f;long t=android.os.SystemClock.uptimeMillis();
                    send(graph,t,t,MotionEvent.ACTION_DOWN,x0,y0);send(graph,t,t+20,MotionEvent.ACTION_MOVE,left+(right-left)*.2f,top);
                    assertEquals("The drag previews live as a custom curve",-1,first.easingToNext);
                    send(graph,t,t+40,MotionEvent.ACTION_CANCEL,x0,y0);
                    Motion.Key restored=a.overlay().motion.curves.get(Motion.OPACITY).get(0);assertEquals("Cancel restores the preset",preset,restored.easingToNext);assertArrayEquals(before,new float[]{restored.x1,restored.y1,restored.x2,restored.y2},0);assertEquals("Cancel adds no undo entry",undo,a.undo.size());
                    assertEquals("Other intervals are untouched",3,a.overlay().motion.curves.get(Motion.OPACITY).size());});
            }
        }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }

    private static View find(View v,Class<?> type){if(type.isInstance(v))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type);if(found!=null)return found;}return null;}
    private static void send(View v,long down,long time,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);try{v.dispatchTouchEvent(e);}finally{e.recycle();}}
}

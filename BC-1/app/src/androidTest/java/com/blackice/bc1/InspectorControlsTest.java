package com.blackice.bc1;

import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.menu.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real inspector gestures, project transactions and measured layout, not screenshot-only checks. */
@RunWith(AndroidJUnit4.class)
public class InspectorControlsTest {
 private final WorkspaceV2Test h=new WorkspaceV2Test();
 private static <T extends View> T find(View view,Class<T> type){if(type.isInstance(view))return type.cast(view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){T child=find(group.getChildAt(i),type);if(child!=null)return child;}}return null;}
 private static void mode(EditorActivity a,String name){View button=WorkspaceV2Test.described(a.inspector,"Position control: "+name);assertNotNull(button);button.performClick();}
 private static void send(View view,long time,int action,float x,float y){MotionEvent event=MotionEvent.obtain(time,time+16,action,x,y,0);try{view.dispatchTouchEvent(event);}finally{event.recycle();}}
 private static PositionTrackpadView pad(EditorActivity a){PositionTrackpadView pad=find(a.inspector,PositionTrackpadView.class);assertNotNull(pad);pad.layout(0,0,a.dp(280),a.dp(160));return pad;}
 private static void drag(EditorActivity a,boolean cancel){PositionTrackpadView pad=pad(a);long t=android.os.SystemClock.uptimeMillis();send(pad,t,MotionEvent.ACTION_DOWN,a.dp(80),a.dp(70));send(pad,t,MotionEvent.ACTION_MOVE,a.dp(110),a.dp(90));send(pad,t,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,a.dp(110),a.dp(90));}
 private static void open(EditorActivity a,boolean overlay){a.panels.reset();if(overlay)a.selectOverlay(0);else a.selectClip(0,false);a.transformPanel.tab=0;a.panels.category("Move & Transform");mode(a,"Trackpad");}
 private static float x(EditorActivity a){return a.transformPanel.current().x;}
 private static float y(EditorActivity a){return a.transformPanel.current().y;}
 private static String snapshot(EditorActivity a){try{return ProjectStore.encode(a.project);}catch(org.json.JSONException e){throw new AssertionError(e);}}
 private void clearPreference(){h.inst.getTargetContext().getSharedPreferences(MenuScreenFactory.CONTROL_PREFS,0).edit().remove(MenuScreenFactory.POSITION_CONTROL).commit();}

 @Test public void trackpadMovesBothAxesWithOneUndoAndCancelRestoresStaticAndAnimatedLayers()throws Exception{
  clearPreference();try{new Workspace144Test().fixture();try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
   scene.onActivity(a->{for(boolean overlay:new boolean[]{false,true})for(boolean animated:new boolean[]{false,true}){
    open(a,overlay);a.seek(1000);
    a.edit("Trackpad fixture",()->{Motion m=a.selectedMotion();m.curves.get(Motion.X).clear();m.curves.get(Motion.Y).clear();if(animated){m.put(Motion.X,new Motion.Key(0,0));m.put(Motion.X,new Motion.Key(2000,.4f));m.put(Motion.Y,new Motion.Key(0,0));m.put(Motion.Y,new Motion.Key(2000,.2f));}});
    float beforeX=x(a),beforeY=y(a);int undo=a.undo.size();
    drag(a,true);assertEquals(beforeX,x(a),1e-6);assertEquals(beforeY,y(a),1e-6);assertEquals(undo,a.undo.size());
    drag(a,false);assertEquals(beforeX+60f/a.exportSize()[0],x(a),1e-5);assertEquals(beforeY-40f/a.exportSize()[1],y(a),1e-5);assertEquals(undo+1,a.undo.size());
    if(!animated)assertFalse("Static layers stay static",a.selectedMotion().animated());
    a.undo(false);assertEquals(beforeX,x(a),1e-6);assertEquals(beforeY,y(a),1e-6);a.undo(true);assertEquals(beforeX+60f/a.exportSize()[0],x(a),1e-5);
   }});
  }}finally{clearPreference();}
 }

 @Test public void sliderAndTrackpadKeyboardUseTheRealPositionAndRespectLocks()throws Exception{
  clearPreference();try{new Workspace144Test().fixture();try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
   scene.onActivity(a->{open(a,false);float beforeX=x(a),beforeY=y(a);int undo=a.undo.size();PositionTrackpadView pad=pad(a);pad.requestFocus();
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
    assertTrue("Trackpad keeps keyboard focus after commit",pad(a).hasFocus());
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
    assertEquals(beforeX+4f/a.exportSize()[0],x(a),1e-6);assertEquals(beforeY,y(a),0);assertEquals(undo+2,a.undo.size());a.undo(false);a.undo(false);
    mode(a,"Slider");SeekBar slider=(SeekBar)WorkspaceV2Test.described(a.inspector,"X slider");assertNotNull(slider);slider.layout(0,0,a.dp(280),a.dp(48));long t=android.os.SystemClock.uptimeMillis();int n=a.undo.size();
    send(slider,t,MotionEvent.ACTION_DOWN,a.dp(140),a.dp(24));send(slider,t,MotionEvent.ACTION_MOVE,a.dp(200),a.dp(24));assertTrue("Slider previews position",Math.abs(x(a)-beforeX)>.001);send(slider,t,MotionEvent.ACTION_CANCEL,a.dp(200),a.dp(24));assertEquals(beforeX,x(a),1e-6);assertEquals(n,a.undo.size());
    slider=(SeekBar)WorkspaceV2Test.described(a.inspector,"Y slider");slider.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));assertNotEquals(beforeY,y(a),1e-6);assertEquals(beforeX,x(a),1e-6);assertEquals(n+1,a.undo.size());a.undo(false);
    mode(a,"Trackpad");pad=pad(a);t=android.os.SystemClock.uptimeMillis();send(pad,t,MotionEvent.ACTION_DOWN,a.dp(80),a.dp(70));send(pad,t,MotionEvent.ACTION_MOVE,a.dp(110),a.dp(90));pad.setEnabled(false);assertEquals(beforeX,x(a),1e-6);assertEquals(beforeY,y(a),1e-6);assertEquals(n,a.undo.size());
    a.uiLocked=true;a.renderInspector();assertFalse(pad(a).isEnabled());a.uiLocked=false;
   });
  }}finally{clearPreference();}
 }

 @Test public void controlChoiceSurvivesRecreationWithoutChangingTheProject()throws Exception{
  clearPreference();try{new Workspace144Test().fixture();try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
   scene.onActivity(a->{open(a,false);String before=snapshot(a);int undo=a.undo.size();mode(a,"Slider");mode(a,"Trackpad");assertEquals(before,snapshot(a));assertEquals(undo,a.undo.size());});
   scene.recreate();h.waitLoaded(scene);scene.onActivity(a->{a.selectClip(0,false);a.transformPanel.tab=0;a.panels.category("Move & Transform");assertNotNull(find(a.inspector,PositionTrackpadView.class));assertTrue(WorkspaceV2Test.described(a.inspector,"Position control: Trackpad").isSelected());mode(a,"Ruler");assertNotNull(find(a.inspector,RulerView.class));assertNull(find(a.inspector,PositionTrackpadView.class));});
  }}finally{clearPreference();}
 }

 @Test public void wideMenusUseCompactRowsAndScrollableGraphs()throws Exception{
  String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));clearPreference();
  try{new Workspace144Test().fixture();h.shell("wm size 1920x1080");h.shell("wm density 160");
   try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
    scene.onActivity(a->{open(a,false);mode(a,"Ruler");});Thread.sleep(200);
    scene.onActivity(a->{View previous=WorkspaceV2Test.described(a.inspector,"Previous Key"),next=WorkspaceV2Test.described(a.inspector,"Next Key"),reset=WorkspaceV2Test.described(a.inspector,"Reset all");assertSame(previous.getParent(),reset.getParent());assertSame(previous.getParent(),next.getParent());assertEquals(a.dp(36),reset.getHeight());
     EditText x=WorkspaceV2Test.field(a.inspector,"X exact value in pixels");assertEquals("Diamond, label, value, unit, reset share one row",5,((ViewGroup)x.getParent()).getChildCount());
     CurveEditorView graph=find(a.inspector,CurveEditorView.class);assertNotNull(graph);assertTrue("Graph is readable instead of squeezed to fit",graph.getHeight()>=a.dp(230));ScrollView scroll=(ScrollView)a.inspector.getParent();assertTrue("Inspector scrolls to reach its lower controls",a.inspector.getHeight()>scroll.getHeight());});
    h.capture("v151-wide-position-ruler");
    for(String control:new String[]{"Slider","Trackpad"}){scene.onActivity(a->mode(a,control));Thread.sleep(150);h.capture("v151-wide-position-"+control.toLowerCase(java.util.Locale.US));}
    scene.onActivity(a->{mode(a,"Ruler");a.transformPanel.tab=1;a.renderInspector();});Thread.sleep(150);h.capture("v151-wide-rotation");
    scene.onActivity(a->{a.transformPanel.tab=2;a.renderInspector();});Thread.sleep(150);h.capture("v151-wide-scale");
    scene.onActivity(a->{a.panels.back();a.panels.category("Blending & Opacity");});Thread.sleep(150);h.capture("v151-wide-blending");
    scene.onActivity(a->{a.panels.back();a.edit("Effect fixture",()->{a.clip().effect=2;a.clip().intensity=.5f;});a.panels.category("Effects");});Thread.sleep(150);h.capture("v151-wide-effects");
    scene.onActivity(a->{a.panels.back();a.layerTools.open();});Thread.sleep(150);h.capture("v151-wide-layer-tools");
    h.shell("wm size 1080x1920");h.shell("wm density 420");Thread.sleep(800);
    scene.onActivity(a->{a.panels.reset();a.selectClip(0,false);a.transformPanel.tab=0;a.panels.category("Move & Transform");mode(a,"Trackpad");});Thread.sleep(200);h.capture("v151-phone-position-trackpad");
   }
  }finally{clearPreference();h.shell("wm size "+size);h.shell("wm density "+density);}
 }
}

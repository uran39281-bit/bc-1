package com.blackice.bc1;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class TransformPanelTest {
 @Test public void liveDialPresetsCancellationAndScaleKeys()throws Exception{
  WorkspaceV2Test h=new WorkspaceV2Test();EditProject p=new EditProject();EditProject.Overlay o=new EditProject.Overlay();o.shape=2;o.endMs=4000;o.scale=1;p.overlays.add(o);new ProjectStore(h.inst.getTargetContext()).save(p);
  h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","WIDE").putBoolean("ui-locked",false).commit();
  try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
   scene.onActivity(a->{a.selectOverlay(0);a.panels.category("Move & Transform");assertNull(WorkspaceV2Test.text(a.inspector,"Skew"));assertNull(WorkspaceV2Test.text(a.inspector,"Anchor"));assertNull(WorkspaceV2Test.text(a.inspector,"Align"));
    a.transformPanel.tab=1;a.renderInspector();View dial=find(a.inspector,TransformPanel.Dial.class);dial.layout(0,0,300,300);int history=a.undo.size();long t=android.os.SystemClock.uptimeMillis();send(dial,t,MotionEvent.ACTION_DOWN,150,10);
    for(int turn=0;turn<2;turn++){send(dial,t,MotionEvent.ACTION_MOVE,290,150);send(dial,t,MotionEvent.ACTION_MOVE,150,290);send(dial,t,MotionEvent.ACTION_MOVE,10,150);send(dial,t,MotionEvent.ACTION_MOVE,150,10);}
    assertEquals("Dial edits the renderer's project while dragging",720,a.overlay().rotation,.01);send(dial,t,MotionEvent.ACTION_UP,150,10);assertEquals(history+1,a.undo.size());a.undo(false);assertEquals(0,a.overlay().rotation,.01);a.undo(true);assertEquals(720,a.overlay().rotation,.01);
    WorkspaceV2Test.described(a.inspector,"Add 45 degrees").performClick();assertEquals(765,a.overlay().rotation,.01);WorkspaceV2Test.described(a.inspector,"Subtract 90 degrees").performClick();assertEquals(675,a.overlay().rotation,.01);
    dial=find(a.inspector,TransformPanel.Dial.class);dial.layout(0,0,300,300);history=a.undo.size();send(dial,t,MotionEvent.ACTION_DOWN,150,10);send(dial,t,MotionEvent.ACTION_MOVE,290,150);assertEquals(765,a.overlay().rotation,.01);send(dial,t,MotionEvent.ACTION_CANCEL,290,150);assertEquals(675,a.overlay().rotation,.01);assertEquals(history,a.undo.size());
    a.overlay().motion.put(Motion.ROTATION,new Motion.Key(0,0));a.overlay().motion.put(Motion.ROTATION,new Motion.Key(2000,720));a.seek(1000);a.renderInspector();WorkspaceV2Test.described(a.inspector,"Reset Rotation").performClick();assertEquals(3,a.overlay().motion.curves.get(Motion.ROTATION).size());assertEquals(720,a.overlay().motion.value(Motion.ROTATION,2000,0),0);
    a.transformPanel.tab=2;a.renderInspector();a.transformPanel.commit("Scale",()->a.transformPanel.writeDimensions(2,1,true));a.overlay().motion.put(Motion.WIDTH,new Motion.Key(0,2));a.seek(1000);a.renderInspector();View ruler=find(a.inspector,TransformPanel.ValueRuler.class);ruler.layout(0,0,400,56);t=android.os.SystemClock.uptimeMillis();send(ruler,t,MotionEvent.ACTION_DOWN,20,30);send(ruler,t,MotionEvent.ACTION_MOVE,20+a.dp(600),30);send(ruler,t,MotionEvent.ACTION_UP,20+a.dp(600),30);
    TransformMath.Transform scaled=a.transformPanel.current();assertEquals(3,scaled.scale,.01);assertEquals(1.5,scaled.scale*scaled.aspect,.01);assertNotNull(Keyframes.keyAt(a.overlay().motion,Motion.HEIGHT,1000,1));assertNotNull(Keyframes.keyAt(a.overlay().motion,Motion.WIDTH,1000,1));
    try{EditProject back=ProjectStore.decode(ProjectStore.encode(a.project));TransformMath.Transform restored=TransformMath.of(back.overlays.get(0),1000);assertEquals(scaled.scale,restored.scale,0);assertEquals(scaled.aspect,restored.aspect,0);}catch(Exception e){throw new AssertionError(e);}
   });h.capture("transform-scale-keyframes");scene.onActivity(a->{a.transformPanel.tab=1;a.renderInspector();});h.capture("transform-rotation");
  }
 }
 private static View find(View v,Class<?> type){if(type.isInstance(v))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),type);if(found!=null)return found;}return null;}
 private static void send(View v,long down,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),action,x,y,0);try{v.dispatchTouchEvent(e);}finally{e.recycle();}}
}

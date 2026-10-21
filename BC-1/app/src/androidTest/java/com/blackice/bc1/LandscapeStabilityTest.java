package com.blackice.bc1;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class LandscapeStabilityTest {
 @Test public void rotationReleaseSurvivesPanelRebuildAndUndo()throws Exception{
  WorkspaceV2Test h=new WorkspaceV2Test();String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));try{h.shell("wm size 1920x1080");h.shell("wm density 160");EditProject p=new EditProject();EditProject.Overlay o=new EditProject.Overlay();o.text="Rotation regression";o.endMs=3000;p.overlays.add(o);new ProjectStore(h.inst.getTargetContext()).save(p);
  h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","WIDE").putBoolean("ui-locked",false).commit();
  try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);scene.onActivity(a->{a.selectOverlay(0);a.transformPanel.tab=1;a.panels.category("Move & Transform");View dial=find(a.inspector,TransformPanel.Dial.class);assertNotNull(dial);dial.layout(0,0,300,300);
    long t=android.os.SystemClock.uptimeMillis();send(dial,t,t,MotionEvent.ACTION_DOWN,280,150);send(dial,t,t+40,MotionEvent.ACTION_MOVE,150,20);send(dial,t,t+80,MotionEvent.ACTION_UP,150,20);
    assertEquals(90,a.overlay().rotation,.01);a.undo(false);assertEquals(0,a.overlay().rotation,.01);a.undo(true);assertEquals(90,a.overlay().rotation,.01);
    try{assertEquals(90,ProjectStore.decode(ProjectStore.encode(a.project)).overlays.get(0).rotation,.01);}catch(Exception e){throw new AssertionError(e);}
    a.panels.reset();a.renderInspector();assertNotNull(WorkspaceV2Test.text(a.inspector,"Media Settings"));assertNull(WorkspaceV2Test.text(a.inspector,"More footage tools"));assertNull(WorkspaceV2Test.text(a.inspector,"Project"));
    CrashLogs.operation("Rotation regression");CrashLogs.record("Regression diagnostic",new IllegalStateException("private content://secret/path"));String report=CrashLogs.report(a);assertTrue(report.contains("IllegalStateException"));assertFalse(report.contains("content://secret/path"));
  });}
  }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
 }
 private static View find(View v,Class<?> type){if(type.isInstance(v))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View result=find(g.getChildAt(i),type);if(result!=null)return result;}}return null;}
 private static void send(View v,long down,long time,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);try{v.dispatchTouchEvent(e);}finally{e.recycle();}}
}

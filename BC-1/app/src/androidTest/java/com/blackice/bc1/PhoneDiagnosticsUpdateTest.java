package com.blackice.bc1;
import android.content.Context;
import android.os.SystemClock;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class PhoneDiagnosticsUpdateTest {
 final WorkspaceV2Test h=new WorkspaceV2Test();
 @Test public void phoneRemoveResizeAndClockSurviveRecreation()throws Exception{
  String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
  Context context=h.inst.getTargetContext();String mode=context.getSharedPreferences(EditorActivity.PREFS,0).getString("mode","AUTO");
  try{h.shell("wm size 824x1784");h.shell("wm density 320");EditProject p=new EditProject();p.name="Phone diagnostics QA";EditProject.Overlay o=new EditProject.Overlay();o.shape=1;o.startMs=0;o.endMs=3000;p.overlays.add(o);new ProjectStore(context).save(p);context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").putBoolean("ui-locked",false).commit();context.getSharedPreferences("project-work-clocks",0).edit().putLong(p.id,123000).putBoolean(p.id+"-paused",true).commit();
   AtomicReference<String> original=new AtomicReference<>();AtomicReference<Float> height=new AtomicReference<>();
   try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);Thread.sleep(1100);
    scene.onActivity(a->{a.selectOverlay(0);original.set(PortraitWorkspaceTest.document(a.project));assertFalse(a.workClock.label.isClickable());assertFalse(a.workClock.label.performClick());assertTrue(a.workClock.label.getContentDescription().toString().contains("Read only"));assertNull(WorkspaceV2Test.text(a.inspector,"Duration"));View remove=WorkspaceV2Test.described(a.inspector,"Remove");assertNotNull(remove);int count=a.undo.size();remove.performClick();assertTrue(a.project.overlays.isEmpty());assertEquals(count+1,a.undo.size());a.undo(false);assertEquals(1,a.project.overlays.size());a.selectOverlay(0);});Thread.sleep(200);h.capture("v155-phone-standard");
    scene.onActivity(a->{int before=a.mobile.sheet.getHeight();a.mobile.portrait.resizeTo(before+a.dp(120));height.set(a.phoneState.drawerHeightDp);assertTrue(a.phoneState.drawerHeightDp>0);assertEquals(original.get(),PortraitWorkspaceTest.document(a.project));});Thread.sleep(200);h.capture("v155-phone-expanded");scene.recreate();h.waitLoaded(scene);Thread.sleep(1100);
    scene.onActivity(a->{assertEquals(height.get(),a.phoneState.drawerHeightDp,0);assertEquals(original.get(),PortraitWorkspaceTest.document(a.project));a.workClock.checkpoint();assertTrue(a.getSharedPreferences("project-work-clocks",0).getLong(p.id,0)>=123000);assertFalse(a.getSharedPreferences("project-work-clocks",0).contains(p.id+"-paused"));assertFalse(a.workClock.label.isClickable());});
   }
  }finally{h.shell("wm size "+size);h.shell("wm density "+density);context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode",mode).commit();}
 }
 @Test public void completeTraceKeepsOrderedMovementAndOperationBoundaries()throws Exception{
  String op="trace-acceptance-"+java.util.UUID.randomUUID();ActionTrace.Span span=ActionTrace.begin(op);span.started();for(int i=0;i<500;i++)ActionTrace.event(op,"sample",span.id,"sample",i,"x",i*.25f);span.end("cancelled");String report=ActionTrace.snapshot();int seen=0;long previous=0;boolean complete=false;for(String line:report.split("\n")){if(!line.startsWith("{"))continue;JSONObject event=new JSONObject(line);if(!event.optString("operation_id").equals(span.id))continue;assertTrue(event.getLong("sequence")>previous);previous=event.getLong("sequence");assertTrue(event.has("wall_time_utc"));assertTrue(event.has("session_elapsed_ms"));if(event.optString("phase").equals("sample")){assertEquals(seen,event.getInt("sample"));seen++;}if(event.optString("outcome").equals("cancelled"))complete=true;}assertEquals(500,seen);assertTrue(complete);
 }
}

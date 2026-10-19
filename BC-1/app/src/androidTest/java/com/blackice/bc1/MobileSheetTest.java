package com.blackice.bc1;
import android.graphics.*;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.BcExporter;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class MobileSheetTest {
 @Test public void emptyProjectShapeSheetPreservesFrameAndPersistsVectors()throws Exception{
  WorkspaceV2Test h=new WorkspaceV2Test();String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
  try{h.shell("wm size 1080x1920");h.shell("wm density 480");EditProject p=new EditProject();p.name="Mobile V1";new ProjectStore(h.inst.getTargetContext()).save(p);h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","PHONE").putBoolean("ui-locked",false).commit();
   try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
    h.waitLoaded(scenario);Thread.sleep(250);
    scenario.onActivity(a->{assertNotNull(a.mobile);assertEquals(View.GONE,a.mobile.sheet.getVisibility());assertEquals("Tap + to add media.",a.emptyPreview.getText().toString());a.mobile.openAdd(3);});Thread.sleep(300);h.capture("v130-add-shapes");
    scenario.onActivity(a->{assertEquals(3,a.addDrawer.category);int preview=a.phonePreview.getHeight();View surface=a.surface;long at=a.project.playheadMs;assertEquals(View.VISIBLE,a.mobile.sheet.getVisibility());a.mobile.dismiss();assertEquals(at,a.project.playheadMs);assertSame(surface,a.surface);assertEquals(preview,a.phonePreview.getHeight());a.mobile.openAdd(3);a.addOverlay(2,"",1);assertEquals(1,a.project.overlays.size());assertEquals(0,a.project.overlays.get(0).startMs);assertEquals(3000,a.project.overlays.get(0).endMs);a.undo(false);assertTrue(a.project.overlays.isEmpty());a.undo(true);assertEquals(1,a.project.overlays.size());a.seek(500);a.mobile.openAdd(0);a.onKeyDown(KeyEvent.KEYCODE_BACK,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));assertEquals(500,a.project.playheadMs);assertEquals(View.GONE,a.mobile.sheet.getVisibility());a.saveNow();});
    Thread.sleep(700);scenario.recreate();h.waitLoaded(scenario);Thread.sleep(300);scenario.onActivity(a->{assertEquals(1,a.project.overlays.size());assertEquals(2,a.project.overlays.get(0).shape);assertEquals(3000,a.project.durationMs());a.mobile.dismiss();});h.capture("v130-main-workspace");
   }
   // All shape IDs persist, have nonempty vector geometry, and render through the shared painter.
   EditProject vectors=new EditProject();for(int id=1;id<EditProject.SHAPES.length;id++){EditProject.Overlay o=new EditProject.Overlay();o.shape=id;o.startMs=0;o.endMs=1000;vectors.overlays.add(o);RectF bounds=new RectF();ShapePaths.create(id,50,2).computeBounds(bounds,true);assertTrue(EditProject.SHAPES[id],bounds.width()>0&&bounds.height()>0);}
   assertEquals(18,ProjectStore.decode(ProjectStore.encode(vectors)).overlays.size());
   File out=new File(h.inst.getTargetContext().getFilesDir(),"qa/v130-shapes-only.mp4");out.getParentFile().mkdirs();new BcExporter(h.inst.getTargetContext(),vectors,180,new AtomicBoolean(),n->{}).export(out);assertTrue(out.length()>1000);
  }finally{h.shell("wm size "+size);h.shell("wm density "+density);h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
 }
}

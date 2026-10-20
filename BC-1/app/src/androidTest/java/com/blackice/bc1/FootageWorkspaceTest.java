package com.blackice.bc1;
import android.graphics.*;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class FootageWorkspaceTest {
 @Test public void mainAndFootageShareStateAndKeepTimelineAccessible()throws Exception{
  WorkspaceV2Test h=new WorkspaceV2Test();String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
  try{h.shell("wm size 1080x1920");h.shell("wm density 480");EditProject p=new EditProject();p.name="Two-state workspace";new ProjectStore(h.inst.getTargetContext()).save(p);
   h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","PHONE").putBoolean("ui-locked",false).putInt("workspace-focus",0).commit();
   try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
    h.waitLoaded(scenario);Thread.sleep(300);scenario.onActivity(a->{assertEquals("Tap + to add media.",a.emptyPreview.getText().toString());assertFalse(a.play.isEnabled());assertEquals(a.dp(56),a.timeline.headerWidth());assertEquals(6,a.mobile.navigation.size());assertNull(WorkspaceV2Test.text(a.inspector,"Footage"));});h.capture("v131-main-empty");
    File file=new File(h.inst.getTargetContext().getFilesDir(),"mint-real-image.png");Bitmap b=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);Paint paint=new Paint();paint.setShader(new LinearGradient(0,0,640,360,0xFF126383,0xFF48E59F,Shader.TileMode.CLAMP));canvas.drawRect(0,0,640,360,paint);try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    scenario.onActivity(a->{a.mobile.add.performClick();assertEquals(0,a.tab);assertNull(WorkspaceV2Test.text(a.inspector,"Footage"));a.mobile.dismiss();EditProject.Clip asset=new EditProject.Clip();asset.image=true;asset.name="Imported test image";asset.uri=android.net.Uri.fromFile(file).toString();asset.outMs=asset.sourceDurationMs=6000;a.dropMedia(asset,0,0);a.seek(2000);a.deselect();a.selectClip(0,false);assertEquals(2000,a.project.playheadMs);assertNotNull(WorkspaceV2Test.text(a.inspector,"Footage"));for(String removed:new String[]{"Color & Fill","Border & Shadow","Presets"})assertNull(WorkspaceV2Test.text(a.inspector,removed));for(String keep:new String[]{"Blending & Opacity","Move & Transform","Edit Shape","Effects"})assertNotNull(WorkspaceV2Test.text(a.inspector,keep));assertFalse(WorkspaceV2Test.text(a.inspector,"Edit Shape").isEnabled());assertFalse(WorkspaceV2Test.text(a.inspector,"Mute").isEnabled());assertNotNull(WorkspaceV2Test.text(a.inspector,"Duration"));a.mobile.snap.performClick();assertEquals(a.snapping,a.mobile.snap.isSelected());});
    Thread.sleep(1000);scenario.onActivity(a->{assertTrue("Selection leaves timeline usable",a.phoneTimelineArea.getHeight()>=a.dp(90));assertEquals(View.GONE,a.mobile.nav.getVisibility());assertEquals(View.GONE,a.mobile.add.getVisibility());});h.capture("v131-footage-selected");
    scenario.onActivity(a->{WorkspaceV2Test.text(a.inspector,"Blending & Opacity").performClick();assertTrue(a.panels.showing());a.mobile.back();assertFalse(a.panels.showing());assertEquals(0,a.selected);assertEquals(2000,a.project.playheadMs);a.mobile.back();assertEquals(-1,a.selected);assertEquals(View.GONE,a.mobile.sheet.getVisibility());a.selectClip(0,false);WorkspaceV2Test.text(a.inspector,"Trim Start").performClick();assertEquals(2000,a.project.placements().get(0).startMs);assertEquals(6000,a.project.placements().get(0).endMs);a.undo(false);assertEquals(0,a.project.placements().get(0).startMs);a.undo(true);assertEquals(2000,a.project.placements().get(0).startMs);a.saveNow();});
    Thread.sleep(500);scenario.recreate();h.waitLoaded(scenario);scenario.onActivity(a->{assertEquals(2000,a.project.placements().get(0).startMs);assertEquals(6000,a.project.placements().get(0).endMs);assertEquals("Imported test image",a.project.clips.get(0).name);});
   }
  }finally{h.shell("wm size "+size);h.shell("wm density "+density);h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
 }
}

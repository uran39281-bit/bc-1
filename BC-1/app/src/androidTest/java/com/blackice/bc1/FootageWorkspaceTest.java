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
  WorkspaceV2Test h=new WorkspaceV2Test();String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density")),font=h.shell("settings get system font_scale").trim();
  try{h.shell("wm size 1080x2340");h.shell("wm density 440");Thread.sleep(3000);EditProject p=new EditProject();p.name="Two-state workspace";new ProjectStore(h.inst.getTargetContext()).save(p);
   h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","PHONE").putBoolean("ui-locked",false).putInt("workspace-focus",0).commit();
   try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
    h.waitLoaded(scenario);Thread.sleep(1000);scenario.onActivity(a->{assertEquals("Tap + to add media.",a.emptyPreview.getText().toString());assertFalse(a.play.isEnabled());assertEquals(a.dp(56),a.timeline.headerWidth());assertEquals(6,a.mobile.navigation.size());assertNull(WorkspaceV2Test.text(a.inspector,"Media Settings"));});h.capture("v131-main-empty");
    File file=new File(h.inst.getTargetContext().getFilesDir(),"mint-real-image.png");Bitmap b=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(b);Paint paint=new Paint();paint.setShader(new LinearGradient(0,0,640,360,0xFF126383,0xFF48E59F,Shader.TileMode.CLAMP));canvas.drawRect(0,0,640,360,paint);try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
    scenario.onActivity(a->{a.mobile.add.performClick();assertEquals(0,a.tab);assertNull(WorkspaceV2Test.text(a.inspector,"Media Settings"));a.mobile.dismiss();EditProject.Clip asset=new EditProject.Clip();asset.image=true;asset.name="Imported test image";asset.uri=android.net.Uri.fromFile(file).toString();asset.outMs=asset.sourceDurationMs=6000;a.dropMedia(asset,0,0);a.seek(2000);a.deselect();a.selectClip(0,false);assertEquals(2000,a.project.playheadMs);assertNotNull(WorkspaceV2Test.text(a.inspector,"Media Settings"));for(String removed:new String[]{"Color & Fill","Border & Shadow","Presets"})assertNull(WorkspaceV2Test.text(a.inspector,removed));for(String keep:new String[]{"Blending & Opacity","Move & Transform","Audio","Effects — Coming Later"})assertNotNull(WorkspaceV2Test.text(a.inspector,keep));assertFalse(WorkspaceV2Test.described(a.inspector,"Media Settings: Audio").isEnabled());a.moreAction("Duration");assertEquals("Duration",a.panels.title());a.panels.back();a.mobile.snap.performClick();assertEquals(a.snapping,a.mobile.snap.isSelected());});
    Thread.sleep(1000);scenario.onActivity(a->{assertTrue("Selection leaves timeline usable",a.phoneTimelineArea.getHeight()>=a.dp(90));assertEquals(View.GONE,a.mobile.nav.getVisibility());assertEquals(View.GONE,a.mobile.add.getVisibility());});waitForImportedFrame(scenario);h.capture("v131-footage-selected");
    scenario.onActivity(a->{WorkspaceV2Test.text(a.inspector,"Blending & Opacity").performClick();assertTrue(a.panels.showing());a.onBackPressed();assertFalse(a.panels.showing());assertEquals(0,a.selected);assertEquals(2000,a.project.playheadMs);a.onBackPressed();assertEquals(-1,a.selected);assertEquals(View.GONE,a.mobile.sheet.getVisibility());a.selectClip(0,false);a.moreAction("Trim Start");assertEquals(2000,a.project.placements().get(0).startMs);assertEquals(6000,a.project.placements().get(0).endMs);a.undo(false);assertEquals(0,a.project.placements().get(0).startMs);a.undo(true);assertEquals(2000,a.project.placements().get(0).startMs);a.saveNow();});
    Thread.sleep(500);scenario.recreate();h.waitLoaded(scenario);scenario.onActivity(a->{assertEquals(2000,a.project.placements().get(0).startMs);assertEquals(6000,a.project.placements().get(0).endMs);assertEquals("Imported test image",a.project.clips.get(0).name);});scenario.onActivity(a->a.selectClip(0,false));h.shell("wm size 1920x1080");h.shell("wm density 320");Thread.sleep(800);h.waitLoaded(scenario);scenario.onActivity(a->{assertEquals(0,a.selected);assertTrue(a.phoneTimelineArea.getHeight()>=a.dp(80));assertTrue(a.phoneTimelineArea.getWidth()>=a.dp(80));assertNotNull(WorkspaceV2Test.text(a.inspector,"Media Settings"));});waitForImportedFrame(scenario);h.capture("v131-phone-landscape");
    h.shell("wm size 1080x2340");h.shell("wm density 440");h.shell("settings put system font_scale 1.3");Thread.sleep(1000);h.waitLoaded(scenario);scenario.onActivity(a->{assertTrue(a.header.getHeight()>=a.dp(80));assertTrue(a.projectLabel.getHeight()>=a.projectLabel.getLineHeight());assertTrue(a.phoneTimelineArea.getHeight()>=a.dp(80));assertNotNull(WorkspaceV2Test.text(a.inspector,"Media Settings"));});waitForImportedFrame(scenario);scenario.onActivity(a->{assertEquals("Export stays on one line",1,a.exportButton.getLineCount());assertTrue("Export label is not clipped",a.exportButton.getWidth()-a.exportButton.getCompoundPaddingLeft()-a.exportButton.getCompoundPaddingRight()>=a.exportButton.getPaint().measureText("Export"));});h.capture("v131-large-font");scenario.onActivity(a->{a.seek(1000);a.buildWorkspace();});Thread.sleep(350);scenario.onActivity(a->{assertEquals("Rebuilding chrome retains the current time",1000,a.project.playheadMs);assertEquals("Rebuilding keeps playhead centered",a.timeline.viewport()/2f,a.timeline.x(1000)-a.timelineScroll.getScrollX(),1f);});
    scenario.onActivity(a->{EditProject.Overlay image=new EditProject.Overlay();image.imageUri=a.project.clips.get(0).uri;image.endMs=3000;a.edit("Legacy image layer",()->a.project.overlays.add(image));a.selectOverlay(0);assertNotNull(WorkspaceV2Test.text(a.inspector,"Media Settings"));a.moreAction("Duration");assertEquals("Duration",a.panels.title());a.panels.back();assertFalse(WorkspaceV2Test.described(a.inspector,"Media Settings: Audio").isEnabled());assertFalse(WorkspaceV2Test.text(a.inspector,"Effects — Coming Later").isEnabled());a.project.tracks[0].locked=true;a.dropMedia(a.project.clips.get(0).copy(),0,0);assertEquals(1,a.clip().track);assertTrue(a.project.tracks[0].locked);});
   }
  }finally{h.shell(font.equals("null")?"settings delete system font_scale":"settings put system font_scale "+font);h.shell("wm size "+size);h.shell("wm density "+density);h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
 }
 private void waitForImportedFrame(ActivityScenario<EditorActivity> scenario)throws Exception{
  java.util.concurrent.atomic.AtomicBoolean rendered=new java.util.concurrent.atomic.AtomicBoolean();
  long until=android.os.SystemClock.elapsedRealtime()+30000;
  while(!rendered.get()&&android.os.SystemClock.elapsedRealtime()<until){
   scenario.onActivity(a->{Bitmap frame=a.liveView.getBitmap();if(frame!=null){int pixel=frame.getPixel(frame.getWidth()/2,frame.getHeight()/2);rendered.set(a.liveView.getVisibility()==View.VISIBLE&&a.live.lastFrameMs()==a.project.playheadMs&&Color.green(pixel)>100&&Color.blue(pixel)>100);frame.recycle();}});
   Thread.sleep(100);
  }
  scenario.onActivity(a->assertTrue("Imported image is visibly rendered at the playhead: "+a.live.debug(),rendered.get()));
 }

}

package com.blackice.bc1;

import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Identical wide fixtures before/after the phone-only update. App rendering is never restyled here. */
@RunWith(AndroidJUnit4.class)
public class PortraitWideBaselineTest {
 final WorkspaceV2Test h=new WorkspaceV2Test();
 private void fixture()throws Exception{
  EditProject p=new EditProject();p.id="portrait-wide-baseline";p.name="Wide reference fixture";
  File file=new File(h.inst.getTargetContext().getFilesDir(),"portrait-wide-fixture.png");Bitmap b=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);b.eraseColor(0xff244366);
  try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
  EditProject.Clip c=new MediaImporter(h.inst.getTargetContext()).importClip(Uri.fromFile(file),false);c.id="fixture-image";c.name="Wide source.png";c.sourceDurationMs=c.outMs=5000;c.effect=2;c.intensity=.5f;p.clips.add(c);p.library.add(c.copy());
  EditProject.Overlay o=ShapeCatalog.create(14,0);o.id="fixture-shape";o.name="Wide shape";o.endMs=5000;p.overlays.add(o);
  new ProjectStore(h.inst.getTargetContext()).save(p);
  h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","WIDE").putBoolean("ui-locked",false).putBoolean("reduced-motion",true).putInt("panel-width",360).putInt("timeline-height",0).putInt("workspace-focus",0).commit();
 }
 private static void nodes(View v,JSONArray list)throws JSONException{
  if(v.getVisibility()!=View.VISIBLE)return;int[] xy=new int[2];v.getLocationOnScreen(xy);
  JSONObject n=new JSONObject().put("class",v.getClass().getName()).put("x",xy[0]).put("y",xy[1]).put("w",v.getWidth()).put("h",v.getHeight()).put("enabled",v.isEnabled()).put("selected",v.isSelected());
  n.put("description",v.getContentDescription()==null?"":v.getContentDescription().toString());if(v instanceof TextView)n.put("text",((TextView)v).getText().toString());list.put(n);
  if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)nodes(((ViewGroup)v).getChildAt(i),list);
 }
 private void capture(ActivityScenario<EditorActivity> scene,String name)throws Exception{
  h.inst.waitForIdleSync();scene.onActivity(a->{
   a.workClock.pause();a.workClock.total=0;a.message="Ready";if(a.status!=null)a.status.setText("Ready");
   if(a.workClock.label!=null){a.workClock.label.setText("◷ 00:00:00");a.workClock.label.setContentDescription("Project work clock");}
   View root=(View)a.header.getParent();root.setFocusableInTouchMode(true);root.requestFocus();
  });h.inst.waitForIdleSync();Thread.sleep(250);
  scene.onActivity(a->{try{
   View root=(View)a.header.getParent();JSONArray tree=new JSONArray();nodes(root,tree);
   File qa=new File(a.getFilesDir(),"qa");qa.mkdirs();try(FileWriter writer=new FileWriter(new File(qa,"phone-wide-"+name+".json"))){writer.write(tree.toString(2));}
   // Native view pixels exclude separate video surfaces; the full device capture below includes them.
   Bitmap nativeView=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(nativeView));
   try(OutputStream out=new FileOutputStream(new File(qa,"phone-wide-native-"+name+".png"))){nativeView.compress(Bitmap.CompressFormat.PNG,100,out);}nativeView.recycle();
  }catch(Exception e){throw new AssertionError(e);}});h.capture("phone-wide-"+name);
 }
 @Test public void captureUnchangedWideWorkspaceAndMenus()throws Exception{
  String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
  try{h.shell("wm size 1920x1080");h.shell("wm density 160");fixture();
   try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);
    scene.onActivity(a->{assertTrue(a.wideLayout);assertNull(a.mobile);a.workClock.pause();a.selectClip(0,false);a.seek(1000);a.saveNow();});
    AtomicBoolean saved=new AtomicBoolean();for(int i=0;i<60&&!saved.get();i++){scene.onActivity(a->saved.set(!a.dirty));Thread.sleep(50);}assertTrue("Fixture saved",saved.get());Thread.sleep(1000);
    capture(scene,"media-settings");
    scene.onActivity(a->{a.transformPanel.tab=0;a.panels.category("Move & Transform");});capture(scene,"position");
    scene.onActivity(a->{a.transformPanel.tab=1;a.renderInspector();});capture(scene,"rotation");
    scene.onActivity(a->{a.transformPanel.tab=2;a.renderInspector();});capture(scene,"scale");
    scene.onActivity(a->{a.panels.reset();a.panels.category("Blending & Opacity");});capture(scene,"blending");
    scene.onActivity(a->{a.panels.reset();a.panels.category("Effects");});capture(scene,"effects");
    scene.onActivity(a->{a.panels.reset();a.layerTools.open();});capture(scene,"layer-tools");
    scene.onActivity(a->{a.panels.reset();a.tab=0;a.addDrawer.category=0;a.renderInspector();});capture(scene,"add");
   }
  }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
 }
}

package com.blackice.bc1;
import android.graphics.*;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class MediaShapesTest {
 @Test public void requestedShapesFitSquareWithoutStretching(){
  for(int id:new int[]{1,14,3,2,19,9,20,21,22,15,23,13,5,4,24,25,26,27}){
   Bitmap b=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.translate(120,120);Paint paint=new Paint();paint.setColor(Color.WHITE);c.drawPath(ShapePaths.create(id,100,3),paint);
   int count=0;for(int y=0;y<240;y++)for(int x=0;x<240;x++)if(Color.alpha(b.getPixel(x,y))>0){count++;assertTrue(EditProject.SHAPES[id]+" outside square",x>=20&&x<220&&y>=20&&y<220);}assertTrue(count>0);b.recycle();
  }
  RectF circle=new RectF(),oval=new RectF();ShapePaths.create(2,100,3).computeBounds(circle,true);ShapePaths.create(19,100,3).computeBounds(oval,true);assertEquals(circle.width(),circle.height(),.01);assertEquals(.62f,oval.height()/oval.width(),.01);
 }
 @Test public void browserAndShapeDragPreserveProjectAcrossLayouts()throws Exception{
  WorkspaceV2Test h=new WorkspaceV2Test();String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));android.content.Context context=h.inst.getTargetContext();EditProject p=new EditProject();p.name="Media and shapes QA";new ProjectStore(context).save(p);context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").putBoolean("ui-locked",false).commit();
  try{h.shell("wm size 1920x1080");h.shell("wm density 160");try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
   h.waitLoaded(scenario);Thread.sleep(500);
   scenario.onActivity(a->{a.mediaKind=MediaFilter.Kind.ALL;a.renderMediaGrid();assertNull(WorkspaceV2Test.described(a.mediaGrid,"Insert shape: Square"));a.openMediaBrowser();});h.inst.waitForIdleSync();Thread.sleep(400);
   scenario.onActivity(a->{assertTrue(a.mediaImportWindow.showing());int[] bin=new int[2],browser=new int[2];a.mediaBrowser.getLocationOnScreen(bin);a.mediaImportWindow.content.getLocationOnScreen(browser);assertTrue("Browse window beside project bin",browser[0]>=bin[0]+a.mediaBrowser.getWidth());a.mediaImportWindow.query="remember query";a.mediaImportWindow.filter=2;});h.capture("v142-wide-browse-files");
   scenario.onActivity(a->{a.mediaImportWindow.close();assertTrue(a.project.library.isEmpty());a.mediaKind=MediaFilter.Kind.SHAPES;a.renderMediaGrid();assertNotNull(WorkspaceV2Test.described(a.mediaGrid,"Insert shape: Monkey head"));});Thread.sleep(300);h.capture("v142-wide-shapes");
   final float[] points=new float[4];scenario.onActivity(a->{View card=WorkspaceV2Test.described(a.mediaGrid,"Insert shape: Square");int[] at=new int[2];card.getLocationOnScreen(at);points[0]=at[0]+card.getWidth()/2f;points[1]=at[1]+card.getHeight()/2f;a.timeline.getLocationOnScreen(at);points[2]=at[0]+a.timeline.x(1000);points[3]=at[1]+a.timeline.rowTopOfTrack(0)+a.dp(TimelineView.ROW_DP/2f);});
   drag(h,points);Thread.sleep(600);scenario.onActivity(a->{assertEquals("Real long-press drag creates a layer",1,a.project.overlays.size());assertEquals(1,a.overlay().shape);assertEquals(1000,a.overlay().startMs,60);a.undo(false);assertTrue(a.project.overlays.isEmpty());a.undo(true);assertEquals(1,a.project.overlays.size());a.panels.category("Effects");assertNotNull(WorkspaceV2Test.text(a.inspector,"No effects applied"));assertTrue(WorkspaceV2Test.text(a.inspector,"+ Add Effects").isEnabled());a.saveNow();});Thread.sleep(500);
   h.shell("wm size 960x600");Thread.sleep(600);scenario.onActivity(a->{a.tab=0;a.addDrawer.category=0;a.renderInspector();});Thread.sleep(200);scenario.onActivity(a->{a.renderInspector();View importMedia=WorkspaceV2Test.described(a.mediaBrowser,"+ Import media");assertNotNull("Project bin remains in the left panel after compact-wide refresh",importMedia);assertTrue("Import stays visible and enabled",importMedia.isShown()&&importMedia.isEnabled());assertFalse("Media and selected-layer inspector remain separate",a.mediaBrowser==a.inspector);});
   h.shell("wm size 1080x1920");h.shell("wm density 320");scenario.recreate();h.waitLoaded(scenario);Thread.sleep(500);
   scenario.onActivity(a->{assertEquals(1,a.project.overlays.size());a.selectOverlay(0);assertTrue(WorkspaceV2Test.described(a.inspector,"Media Settings: Move & Transform").isEnabled());assertTrue(WorkspaceV2Test.described(a.inspector,"Media Settings: Shape & Style").isEnabled());a.seek(1500);a.mobile.openAdd(3);});Thread.sleep(300);h.capture("v142-phone-shapes");
   scenario.onActivity(a->a.openMediaBrowser());h.inst.waitForIdleSync();Thread.sleep(400);scenario.onActivity(a->{assertTrue(a.mediaImportWindow.showing());assertEquals("remember query",a.mediaImportWindow.query);assertEquals(2,a.mediaImportWindow.filter);assertTrue(a.mediaImportWindow.content.getWidth()<=a.getWindow().getDecorView().getWidth());});h.capture("v142-phone-browse-files");
   scenario.recreate();h.waitLoaded(scenario);Thread.sleep(500);scenario.onActivity(a->{assertTrue(a.mediaImportWindow.showing());assertEquals("remember query",a.mediaImportWindow.query);assertEquals(1,a.project.overlays.size());a.mediaImportWindow.close();});
  }}finally{h.shell("wm size "+size);h.shell("wm density "+density);context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
 }
 @Test public void audioImportsThroughUnifiedFlowAndEffectsEditExistingTimeline()throws Exception{
  WorkspaceV2Test h=new WorkspaceV2Test();android.content.Context context=h.inst.getTargetContext();EditProject p=new EditProject();p.name="Unified import QA";new ProjectStore(context).save(p);context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putBoolean("ui-locked",false).commit();
  java.io.File audio=new java.io.File(context.getCacheDir(),"browse-audio.wav");try(java.io.InputStream in=h.inst.getContext().getAssets().open("music.wav");java.io.OutputStream out=new java.io.FileOutputStream(audio)){in.transferTo(out);}
  java.io.File image=new java.io.File(context.getCacheDir(),"browse-image.png");Bitmap b=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);b.eraseColor(0xFF289DD2);try(java.io.OutputStream out=new java.io.FileOutputStream(image)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
  try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
   h.waitLoaded(scenario);scenario.onActivity(a->a.importUris(java.util.List.of(android.net.Uri.fromFile(audio),android.net.Uri.fromFile(image)),EditorActivity.IMPORT));
   java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean();long deadline=android.os.SystemClock.elapsedRealtime()+20000;
   while(!done.get()&&android.os.SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->done.set(!a.importing));Thread.sleep(100);}
   scenario.onActivity(a->{assertEquals(a.message,2,a.project.library.size());assertTrue(a.project.library.get(0).audioOnly);assertTrue(a.project.library.get(1).image);assertTrue(a.project.clips.isEmpty());assertTrue(a.project.overlays.isEmpty());assertTrue(audio.exists());assertTrue(image.exists());
    a.dropMedia(a.project.library.get(1),0,0);a.dropMedia(a.project.library.get(1),0,3000);a.selectClip(0,false);a.panels.reset();a.tab=2;a.renderInspector();WorkspaceV2Test.text(a.inspector,"Transitions").performClick();WorkspaceV2Test.text(a.inspector,"Dissolve").performClick();assertEquals(0,a.project.clips.get(0).transition);assertEquals(500,a.project.overlapAfter(0));a.undo(false);assertEquals(-1,a.project.clips.get(0).transition);a.undo(true);assertEquals(0,a.project.clips.get(0).transition);
    a.addShapeAt(27,0,0);WorkspaceV2Test.described(a.inspector,"Media Settings: Shape & Style").performClick();WorkspaceV2Test.text(a.inspector,"Color & Fill").performClick();assertNotNull(WorkspaceV2Test.text(a.inspector,"Color & Fill"));a.overlay().aspect=2;a.panels.reset();a.renderInspector();WorkspaceV2Test.described(a.inspector,"Media Settings: Shape & Style").performClick();WorkspaceV2Test.text(a.inspector,"Reset natural proportions").performClick();assertEquals(1,a.overlay().aspect,0);a.saveNow();});
   Thread.sleep(600);scenario.recreate();h.waitLoaded(scenario);scenario.onActivity(a->{assertEquals(2,a.project.library.size());assertEquals(2,a.project.clips.size());assertEquals(0,a.project.clips.get(0).transition);assertEquals(27,a.project.overlays.get(0).shape);assertEquals(1,a.project.overlays.get(0).aspect,0);});h.capture("v142-monkey-shape-editor");
  }
 }
 static void drag(WorkspaceV2Test h,float[] p)throws Exception{
  long start=android.os.SystemClock.uptimeMillis();send(h,start,MotionEvent.ACTION_DOWN,p[0],p[1]);Thread.sleep(700);
  for(int i=1;i<=20;i++){send(h,start,MotionEvent.ACTION_MOVE,p[0]+(p[2]-p[0])*i/20,p[1]+(p[3]-p[1])*i/20);Thread.sleep(25);}send(h,start,MotionEvent.ACTION_UP,p[2],p[3]);h.inst.waitForIdleSync();
 }
 static void send(WorkspaceV2Test h,long start,int action,float x,float y){MotionEvent e=MotionEvent.obtain(start,android.os.SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);assertTrue(h.inst.getUiAutomation().injectInputEvent(e,true));e.recycle();}
}

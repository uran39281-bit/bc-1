package com.blackice.bc1;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class MediaBrowserGalleryTest {
 @Test public void browseGalleryImportsToBinAndRestoresAlbumAcrossWindows()throws Exception{
  WorkspaceV2Test h=new WorkspaceV2Test();Context context=h.inst.getTargetContext();String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
  for(String permission:new String[]{"READ_MEDIA_IMAGES","READ_MEDIA_VIDEO","READ_MEDIA_AUDIO"})h.shell("pm grant "+context.getPackageName()+" android.permission."+permission);
  ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"QA shape sample.png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/BlackIceQA");values.put(MediaStore.Images.Media.IS_PENDING,1);
  Uri source=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(source);
  try{
   Bitmap bitmap=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);Paint paint=new Paint();paint.setShader(new LinearGradient(0,0,640,360,0xFF123757,0xFF1CBFE8,Shader.TileMode.CLAMP));canvas.drawPaint(paint);paint.setShader(null);paint.setColor(0xFFB8F2FF);canvas.translate(320,180);canvas.drawPath(ShapePaths.create(15,120,3),paint);try(java.io.OutputStream out=context.getContentResolver().openOutputStream(source)){assertNotNull(out);bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);context.getContentResolver().update(source,values,null,null);
   EditProject p=new EditProject();p.name="Browse Files QA";new ProjectStore(context).save(p);context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").putBoolean("ui-locked",false).commit();h.shell("wm size 1920x1080");h.shell("wm density 160");
   try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
    h.waitLoaded(scenario);scenario.onActivity(EditorActivity::openMediaBrowser);waitBrowser(scenario);
    scenario.onActivity(a->{View root=a.mediaImportWindow.content;WorkspaceV2Test.described(root,"Browse filter: Image").performClick();((EditText)WorkspaceV2Test.described(root,"Search device media")).setText("shape sample");WorkspaceV2Test.described(root,"Browse album: BlackIceQA").performClick();WorkspaceV2Test.described(root,"Import QA shape sample.png to Project Media").performClick();});
    java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean();long end=android.os.SystemClock.elapsedRealtime()+15000;while(!done.get()&&android.os.SystemClock.elapsedRealtime()<end){scenario.onActivity(a->done.set(!a.importing&&a.project.library.size()==1));Thread.sleep(100);}
    scenario.onActivity(a->{assertEquals(a.message,1,a.project.library.size());assertTrue(a.project.clips.isEmpty());assertTrue(a.project.overlays.isEmpty());assertEquals("BlackIceQA",a.mediaImportWindow.album);assertNotEquals(source.toString(),a.project.library.get(0).uri);});try(java.io.InputStream in=context.getContentResolver().openInputStream(source)){assertNotNull(in);assertTrue(in.read()>=0);}Thread.sleep(300);h.capture("v142-wide-import-gallery");
    h.shell("wm size 1080x1920");h.shell("wm density 320");Thread.sleep(800);scenario.recreate();h.waitLoaded(scenario);waitBrowser(scenario);
    scenario.onActivity(a->{assertEquals("shape sample",a.mediaImportWindow.query);assertEquals("BlackIceQA",a.mediaImportWindow.album);assertEquals(3,a.mediaImportWindow.filter);assertEquals(1,a.project.library.size());assertNotNull(WorkspaceV2Test.described(a.mediaImportWindow.content,"Import QA shape sample.png to Project Media"));});Thread.sleep(300);h.capture("v142-phone-import-gallery");scenario.onActivity(a->a.mediaImportWindow.close());
   }
  }finally{context.getContentResolver().delete(source,null,null);h.shell("wm size "+size);h.shell("wm density "+density);context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
 }
 static void waitBrowser(ActivityScenario<EditorActivity> scenario)throws Exception{
  java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();long end=android.os.SystemClock.elapsedRealtime()+15000;
  while(!ready.get()&&android.os.SystemClock.elapsedRealtime()<end){scenario.onActivity(a->ready.set(a.mediaImportWindow.showing()&&!a.mediaImportWindow.loading&&a.mediaImportWindow.items!=null));Thread.sleep(100);}if(!ready.get())scenario.onActivity(a->fail("Browser loaded: showing="+a.mediaImportWindow.showing()+", requested="+a.mediaImportWindow.restoreOpen+", loading="+a.mediaImportWindow.loading+", items="+(a.mediaImportWindow.items==null?"null":a.mediaImportWindow.items.size())+", projectLoading="+a.loading));
 }
}

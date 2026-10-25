package com.blackice.bc1.captions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.List;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class OfflineSpeechTest {
 @Test(timeout=180000) public void bundledModelRecognizesEnglishWithoutInternetPermission()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
  assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission("android.permission.INTERNET"));
  assertTrue("Native speech library must be packaged",WhisperLib.available());
  File model=SpeechModels.ensure(c,SpeechModels.Model.BASE_EN,()->false,p->{});
  assertEquals(59721011L,model.length());float[] pcm=SpeechBenchmark.sample(c);
  try(WhisperLib w=new WhisperLib(model.getPath())){
   List<WhisperLib.Segment> segments=w.transcribe(pcm,"en",false,2,null);
   StringBuilder words=new StringBuilder();long previous=0;
   for(WhisperLib.Segment s:segments){assertTrue(s.startMs>=previous);assertTrue(s.endMs>=s.startMs);assertTrue(s.endMs<=pcm.length*1000L/16000+100);previous=s.endMs;words.append(s.text);}
   assertTrue(words.toString(),words.toString().toLowerCase(java.util.Locale.ROOT).contains("country"));
  }
 }
 @Test public void disabledVisualizerDoesNotReadSourceOrDraw()throws Exception{
  Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();Bitmap b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLACK);
  EditProject.Overlay o=new EditProject.Overlay();o.visualizerClipId="missing-source";o.imageUri="file:///does-not-exist";
  new OverlayPainter(c).draw(new Canvas(b),32,32,o,0);
  int[] pixels=new int[1024];b.getPixels(pixels,0,32,0,0,32,32);for(int p:pixels)assertEquals(Color.BLACK,p);b.recycle();
 }
}

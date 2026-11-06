package com.blackice.bc1;
import android.content.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.file.Files;
import java.util.concurrent.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class TracePressureTest {
 @Test public void pressureRecordsGapsAndReservesCommandCapacity()throws Exception{
  Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();File dir=new File(base.getCacheDir(),"trace-pressure-"+java.util.UUID.randomUUID());assertTrue(dir.mkdirs());Context isolated=new ContextWrapper(base){@Override public File getFilesDir(){return dir;}};
  ActionTrace.Recorder recorder=new ActionTrace.Recorder(isolated);CountDownLatch ready=new CountDownLatch(1),release=new CountDownLatch(1);
  try{recorder.worker.execute(()->{ready.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(ready.await(5,TimeUnit.SECONDS));
   for(int i=0;i<9000;i++)recorder.offer(new ActionTrace.Entry("pointer","input","pressure",new Object[]{"x",i}),true);
   recorder.offer(new ActionTrace.Entry("save","complete","critical",new Object[]{"outcome","success"}),false);release.countDown();recorder.worker.submit(recorder::drain).get(10,TimeUnit.SECONDS);
   boolean gap=false,critical=false;long previous=0;for(File f:recorder.files())for(String line:Files.readAllLines(f.toPath())){JSONObject e=new JSONObject(line);assertTrue(e.getLong("sequence")>previous);previous=e.getLong("sequence");gap|=e.optString("action").equals("capture_gap")&&e.getLong("count")>0;critical|=e.optString("operation_id").equals("critical");}assertTrue(gap);assertTrue(critical);
  }finally{release.countDown();recorder.worker.shutdownNow();for(File f:recorder.files())f.delete();recorder.directory.delete();dir.delete();}
 }
}

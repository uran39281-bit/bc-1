package com.blackice.bc1;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.BcExporter;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Decode both real export backends: animated gradients, uncapped scale and translucent clover. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class FillExportTest {
 @Test public void bothEnginesRenderAnimatedFillHugeScaleAndClover()throws Exception{
  android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context context=inst.getTargetContext();
  EditProject p=new EditProject();p.name="1.4.3 fill export";p.frameRate=30;
  EditProject.Overlay ramp=ShapeCatalog.create(1,0);ramp.endMs=2000;ramp.scale=5;ramp.fill=new FillStyle(Color.RED);ramp.fill.gradient=true;
  FillStyle f=ramp.fill;FillStyle.Stop left=f.stops.get(f.active.get(0)),right=f.stops.get(f.active.get(1));
  left.color.keys.put(0L,(double)Color.RED);left.color.keys.put(1000L,(double)Color.GREEN);right.color.keys.put(0L,(double)Color.BLUE);right.color.keys.put(1000L,(double)Color.GREEN);
  f.angle.keys.put(0L,0d);f.angle.keys.put(1000L,45d);left.position.keys.put(0L,0d);left.position.keys.put(1000L,.2d);p.overlays.add(ramp);
  EditProject.Overlay huge=ShapeCatalog.create(2,2000);huge.endMs=3000;huge.scale=70;huge.rotation=27;huge.color=0x80FF0000;huge.opacity=.5f;p.overlays.add(huge);
  EditProject.Overlay clover=ShapeCatalog.create(26,3000);clover.endMs=4000;clover.scale=1;clover.rotation=27;clover.color=0x80FF0000;clover.opacity=.5f;p.overlays.add(clover);
  p=ProjectStore.decode(ProjectStore.encode(p));final EditProject project=p;
  for(boolean ice:new boolean[]{false,true}){
   File file=new File(context.getFilesDir(),"qa/v143-fill-"+(ice?"ice":"media3")+".mp4");file.getParentFile().mkdirs();file.delete();
   if(ice)new BcExporter(context,project,180,new AtomicBoolean(),n->{}).export(file);
   else {CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();inst.runOnMainSync(()->{try{
    Transformer t=new Transformer.Builder(context).setAssetLoaderFactory(CompositionFactory.assetLoader(context)).setVideoMimeType(MimeTypes.VIDEO_H264).setVideoFrameProcessorFactory(CompositionFactory.frameProcessor()).addListener(new Transformer.Listener(){
     @Override public void onCompleted(Composition c,ExportResult r){done.countDown();}
     @Override public void onError(Composition c,ExportResult r,ExportException e){error.set(e);done.countDown();}
    }).build();t.start(CompositionFactory.buildAtHeight(context,project,180,false),file.getPath());
   }catch(Exception e){error.set(e);done.countDown();}});assertTrue(done.await(180,TimeUnit.SECONDS));if(error.get()!=null)throw new AssertionError("Fill export failed",error.get());}
   MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(file.getPath());Bitmap start=r.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(start);int a=start.getPixel(30,start.getHeight()/2),b=start.getPixel(start.getWidth()-31,start.getHeight()/2);assertTrue("Gradient varies spatially",Color.red(a)>Color.red(b)+50&&Color.blue(b)>Color.blue(a)+50);start.recycle();
    Bitmap green=r.getFrameAtTime(1_200_000,MediaMetadataRetriever.OPTION_CLOSEST);int c=green.getPixel(green.getWidth()/2,green.getHeight()/2);assertTrue("Fill keyframes update export",Color.green(c)>200&&Color.red(c)<40&&Color.blue(c)<40);green.recycle();
    for(long time:new long[]{2_500_000,3_500_000}){Bitmap red=r.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST);int pixel=red.getPixel(red.getWidth()/2,red.getHeight()/2);assertEquals("Alpha multiplied exactly once; clover center is solid",64,Color.red(pixel),18);assertTrue(Color.blue(pixel)<20&&Color.green(pixel)<20);if(time==2_500_000)assertEquals("10000% circle covers rotated frame corner",64,Color.red(red.getPixel(2,2)),18);red.recycle();}
   }finally{r.release();}
  }
 }
}

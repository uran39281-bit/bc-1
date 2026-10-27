package com.blackice.bc1;
import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.core.CanvasFormat;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.BcExporter;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class CanvasShapeExportTest {
 @Test public void bothEnginesExportFiveRatiosWithSolidTranslucentShapeCenters()throws Exception{
  android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context context=inst.getTargetContext();File background=new File(context.getFilesDir(),"v144-export-background.png");Bitmap black=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);black.eraseColor(Color.BLACK);try(OutputStream out=new FileOutputStream(background)){black.compress(Bitmap.CompressFormat.PNG,100,out);}black.recycle();
  for(String ratio:CanvasFormat.RATIOS){EditProject p=new EditProject();p.setCanvasRatio(ratio);p.frameRate=30;EditProject.Clip base=new MediaImporter(context).importClip(android.net.Uri.fromFile(background),false);base.outMs=base.sourceDurationMs=800;p.clips.add(base);for(int i=0;i<2;i++){EditProject.Overlay shape=ShapeCatalog.create(i==0?7:10,i*400);shape.endMs=(i+1)*400;shape.color=0x80FFFFFF;shape.opacity=.5f;shape.scale=2;p.overlays.add(shape);}final EditProject project=ProjectStore.decode(ProjectStore.encode(p));int edge=ratio.equals("16:9")?1080:360;
   for(boolean ice:new boolean[]{true,false}){File output=new File(context.getFilesDir(),"qa/v144-"+ratio.replace(':','x')+"-"+(ice?"ice":"media3")+".mp4");output.getParentFile().mkdirs();output.delete();
    if(ice)new BcExporter(context,project,edge,new AtomicBoolean(),n->{}).export(output);else{CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();inst.runOnMainSync(()->{try{Transformer t=new Transformer.Builder(context).setAssetLoaderFactory(CompositionFactory.assetLoader(context)).setVideoMimeType(MimeTypes.VIDEO_H264).setVideoFrameProcessorFactory(CompositionFactory.frameProcessor()).addListener(new Transformer.Listener(){@Override public void onCompleted(Composition c,ExportResult r){done.countDown();}@Override public void onError(Composition c,ExportResult r,ExportException e){error.set(e);done.countDown();}}).build();t.start(CompositionFactory.buildAtHeight(context,project,edge,false),output.getPath());}catch(Exception e){error.set(e);done.countDown();}});assertTrue(done.await(120,TimeUnit.SECONDS));if(error.get()!=null)throw new AssertionError(error.get());}
    try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(output.getPath());int[] size=project.canvasSize(edge);assertEquals(size[0],Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)));assertEquals(size[1],Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));for(long time:new long[]{100000,600000}){Bitmap frame=r.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(frame);assertEquals("Filled center, alpha applied once: "+ratio,64,Color.red(frame.getPixel(frame.getWidth()/2,frame.getHeight()/2)),16);if(time==100000){int x=Math.round(frame.getWidth()/2f-.3f*edge*.4f),y=Math.round(frame.getHeight()/2f+.55f*edge*.4f);assertEquals("Speech-bubble body and tail form one fill",64,Color.red(frame.getPixel(x,y)),16);}if(edge==1080&&time==600000)try(OutputStream out=new FileOutputStream(new File(output.getParentFile(),"v144-1080p-flower-"+(ice?"ice":"media3")+".png"))){frame.compress(Bitmap.CompressFormat.PNG,100,out);}frame.recycle();}}
   }
  }
 }
}

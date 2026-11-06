package com.blackice.bc1.engine;
import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.LivePreview;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
/** Compare decoder/compositor orientation with Android's metadata-aware frame retriever. */
@RunWith(AndroidJUnit4.class)
public class SourceOrientationTest {
 @Test public void sourceMetadataAppliedOnceAtEveryQuarterTurn()throws Exception{
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();File source=new File(context.getCacheDir(),"orientation-source.mp4");try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("red.mp4");OutputStream out=new FileOutputStream(source)){in.transferTo(out);}
  for(int rotation:new int[]{0,90,180,270}){
   File video=new File(context.getCacheDir(),"orientation-"+rotation+".mp4");MediaExtractor ex=new MediaExtractor();MediaMuxer mux=new MediaMuxer(video.toString(),MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);try{ex.setDataSource(source.toString());int track=-1;for(int i=0;i<ex.getTrackCount();i++)if(ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME).startsWith("video/")){track=i;break;}assertTrue(track>=0);ex.selectTrack(track);int output=mux.addTrack(ex.getTrackFormat(track));mux.setOrientationHint(rotation);mux.start();ByteBuffer bytes=ByteBuffer.allocate(1024*1024);MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();while(true){bytes.clear();int n=ex.readSampleData(bytes,0);if(n<0)break;info.set(0,n,ex.getSampleTime(),ex.getSampleFlags());mux.writeSampleData(output,bytes,info);ex.advance();}mux.stop();}finally{ex.release();mux.release();}
   Bitmap original;try(MediaMetadataRetriever retriever=new MediaMetadataRetriever()){retriever.setDataSource(video.toString());original=retriever.getFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST);}assertNotNull(original);
   Bitmap expected=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(expected);canvas.drawColor(Color.BLACK);float scale=Math.min(640f/original.getWidth(),360f/original.getHeight());float w=scale*original.getWidth(),h=scale*original.getHeight();canvas.drawBitmap(original,null,new RectF((640-w)/2,(360-h)/2,(640+w)/2,(360+h)/2),null);original.recycle();
   HandlerThread thread=new HandlerThread("orientation-reader");thread.start();AtomicReference<int[]> pixels=new AtomicReference<>();ImageReader reader=ImageReader.newInstance(640,360,PixelFormat.RGBA_8888,3);reader.setOnImageAvailableListener(r->{try(Image image=r.acquireLatestImage()){if(image==null)return;Image.Plane plane=image.getPlanes()[0];ByteBuffer b=plane.getBuffer();int[] sample=new int[640*360];for(int y=0;y<360;y++)for(int x=0;x<640;x++){int off=y*plane.getRowStride()+x*plane.getPixelStride();sample[y*640+x]=Color.rgb(b.get(off)&255,b.get(off+1)&255,b.get(off+2)&255);}pixels.set(sample);}},new Handler(thread.getLooper()));
   LivePreview live=new LivePreview(context);try{live.attach(reader.getSurface());EditProject p=new EditProject();p.previewHeight=360;EditProject.Clip c=new MediaImporter(context).importClip(Uri.fromFile(video),false);assertEquals("Source orientation is not user rotation",0,c.rotation,0);p.clips.add(c);assertTrue(live.renderNow(p,1000,15000));long end=SystemClock.elapsedRealtime()+5000;while(pixels.get()==null&&SystemClock.elapsedRealtime()<end)Thread.sleep(25);assertNotNull(pixels.get());int bad=0,total=0;for(int y=8;y<352;y+=8)for(int x=8;x<632;x+=8){int wanted=expected.getPixel(x,y),got=pixels.get()[y*640+x];int error=Math.abs(Color.red(wanted)-Color.red(got))+Math.abs(Color.green(wanted)-Color.green(got))+Math.abs(Color.blue(wanted)-Color.blue(got));if(error>100)bad++;total++;}assertTrue("Orientation "+rotation+": wrong sample fraction "+bad+"/"+total,bad<total*.015);assertMarker(expected,pixels.get(),rotation);
    File exported=new File(context.getCacheDir(),"orientation-export-"+rotation+".mp4");c.outMs=1200;
    new com.blackice.bc1.engine.bc.BcExporter(context,ProjectStore.snapshot(p),360,new java.util.concurrent.atomic.AtomicBoolean(),progress->{}).export(exported);
    try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(exported.toString());Bitmap frame=r.getFrameAtTime(1000000,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(frame);assertEquals(640,frame.getWidth());assertEquals(360,frame.getHeight());int[] actual=new int[640*360];frame.getPixels(actual,0,640,0,0,640,360);assertMarker(expected,actual,rotation);frame.recycle();}
    File qa=new File(context.getFilesDir(),"qa");qa.mkdirs();java.nio.file.Files.copy(exported.toPath(),new File(qa,"orientation-"+rotation+".mp4").toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);exported.delete();}finally{live.release();reader.close();thread.quitSafely();expected.recycle();video.delete();}
  }
 }
 private static void assertMarker(Bitmap expected,int[] actual,int rotation){
  double ex=0,ey=0,gx=0,gy=0;int ec=0,gc=0;
  for(int y=0;y<360;y++)for(int x=0;x<640;x++){int e=expected.getPixel(x,y),g=actual[y*640+x];if(Color.red(e)>180&&Color.green(e)>180&&Color.blue(e)>180){ec++;ex+=x;ey+=y;}if(Color.red(g)>180&&Color.green(g)>180&&Color.blue(g)>180){gc++;gx+=x;gy+=y;}}
  assertTrue("White orientation marker present: "+rotation,ec>30&&gc>30);assertEquals("Marker X at "+rotation,ex/ec,gx/gc,6);assertEquals("Marker Y at "+rotation,ey/ec,gy/gc,6);
 }
}

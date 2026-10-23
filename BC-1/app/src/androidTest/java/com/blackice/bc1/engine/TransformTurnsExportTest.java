package com.blackice.bc1.engine;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.BcExporter;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
/** Decode actual exported frames to distinguish full turns from shortest-path interpolation. */
@RunWith(AndroidJUnit4.class)
public class TransformTurnsExportTest {
 @Test public void twoTurnsSurviveProjectReopenAndEncoding()throws Exception{
  android.content.Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();File source=new File(context.getCacheDir(),"rotation-marker.png");Bitmap image=Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(Color.BLACK);Paint paint=new Paint();paint.setColor(Color.WHITE);canvas.drawRect(225,140,275,180,paint);try(OutputStream out=new FileOutputStream(source)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
  for(int direction:new int[]{1,-1}){EditProject p=new EditProject();EditProject.Clip c=new MediaImporter(context).importClip(Uri.fromFile(source),false);c.outMs=2200;c.sourceDurationMs=2200;c.widthScale=.8f;c.heightScale=.8f;Motion.Key start=new Motion.Key(0,0);Keyframes.ease(start,0);c.motion.put(Motion.ROTATION,start);c.motion.put(Motion.ROTATION,new Motion.Key(2000,direction*720));p.clips.add(c);p=ProjectStore.decode(ProjectStore.encode(p));File qa=new File(context.getFilesDir(),"qa");qa.mkdirs();File file=new File(qa,"transform-turns-"+direction+".mp4");new BcExporter(context,p,180,new AtomicBoolean(),v->{}).export(file);
   MediaMetadataRetriever r=new MediaMetadataRetriever();try{r.setDataSource(file.getPath());for(long ms:new long[]{0,250,500,750,1000,1250,1500,1750}){Bitmap frame=r.getFrameAtTime(ms*1000,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(frame);double sx=0,sy=0,n=0;for(int y=0;y<frame.getHeight();y++)for(int x=0;x<frame.getWidth();x++){int pixel=frame.getPixel(x,y);if(Color.red(pixel)>180&&Color.green(pixel)>180&&Color.blue(pixel)>180){sx+=x;sy+=y;n++;}}assertTrue("White orientation marker remains visible",n>10);double theta=Math.toRadians(direction*720*ms/2000d);double dx=sx/n-frame.getWidth()/2d,dy=sy/n-frame.getHeight()/2d;assertTrue("Encoded cumulative turn at "+ms+" ms: "+dx+","+dy,dx*Math.cos(theta)-dy*Math.sin(theta)>20);frame.recycle();}}finally{r.release();}
  }
 }
}

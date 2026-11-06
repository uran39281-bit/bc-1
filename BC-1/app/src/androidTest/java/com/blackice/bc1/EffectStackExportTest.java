package com.blackice.bc1;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.*;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Actual ordered GLES processing and independently decoded exports through both engines. */
@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class EffectStackExportTest {
    final WorkspaceV2Test h=new WorkspaceV2Test();
    private static int expected(long time){float u=Math.min(1,time/1000f);double[] c={51/255d,102/255d,178/255d};double exposure=Math.pow(2,((.55+.2*u)-.5)*4),invert=.1+.3*u,strength=.2+.6*u;
        for(int i=0;i<3;i++){c[i]=Math.max(0,Math.min(1,c[i]*exposure));c[i]=c[i]*(1-invert)+(1-c[i])*invert;}
        double gray=c[0]*.2126+c[1]*.7152+c[2]*.0722;for(int i=0;i<3;i++)c[i]=c[i]*(1-strength)+gray*strength;
        return Color.rgb((int)Math.round(c[0]*255),(int)Math.round(c[1]*255),(int)Math.round(c[2]*255));
    }
    private static void pixel(int expected,int actual,int tolerance){assertEquals("red",Color.red(expected),Color.red(actual),tolerance);assertEquals("green",Color.green(expected),Color.green(actual),tolerance);assertEquals("blue",Color.blue(expected),Color.blue(actual),tolerance);}
    @Test public void threeAnimatedEffectsComposeInOrderInPreviewAndBothPortraitExports()throws Exception{
        Context context=h.inst.getTargetContext();File source=new File(context.getFilesDir(),"v154-effect-stack.png");Bitmap image=Bitmap.createBitmap(180,320,Bitmap.Config.ARGB_8888);image.eraseColor(Color.rgb(51,102,178));try(OutputStream out=new FileOutputStream(source)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
        EditProject p=new EditProject();p.setCanvasRatio("9:16");EditProject.Clip clip=new MediaImporter(context).importClip(Uri.fromFile(source),false);clip.sourceDurationMs=clip.outMs=1200;p.clips.add(clip);
        int[] types={9,25,4};float[][] values={{.55f,.75f},{.1f,.4f},{.2f,.8f}};for(int i=0;i<3;i++){String id=clip.addEffect(types[i]);EffectInstance e=clip.effect(id);e.params=new float[]{0,0,0,0};e.motion.put(Motion.INTENSITY,new Motion.Key(0,values[i][0]));e.motion.put(Motion.INTENSITY,new Motion.Key(1000,values[i][1]));clip.updateEffect(e);}
        p=ProjectStore.decode(ProjectStore.encode(p));final EditProject project=p;
        for(long time:new long[]{0,600,1000}){Bitmap rendered=LayerCapture.render(context,ProjectStore.snapshot(project),clip.id,time,180);pixel(expected(time),rendered.getPixel(rendered.getWidth()/2,rendered.getHeight()/2),3);File png=new File(context.getFilesDir(),"qa/v154-stack-preview-"+time+".png");png.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(png)){rendered.compress(Bitmap.CompressFormat.PNG,100,out);}rendered.recycle();}
        for(boolean ice:new boolean[]{true,false}){
            File file=new File(context.getFilesDir(),"qa/v154-stack-"+(ice?"ice":"media3")+".mp4");file.getParentFile().mkdirs();file.delete();
            if(ice)new BcExporter(context,project,180,new AtomicBoolean(),n->{}).export(file);
            else{CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();h.inst.runOnMainSync(()->{try{Transformer t=new Transformer.Builder(context).setAssetLoaderFactory(CompositionFactory.assetLoader(context)).setVideoMimeType(MimeTypes.VIDEO_H264).setPortraitEncodingEnabled(true).setVideoFrameProcessorFactory(CompositionFactory.frameProcessor()).addListener(new Transformer.Listener(){public void onCompleted(Composition c,ExportResult r){done.countDown();}public void onError(Composition c,ExportResult r,ExportException e){error.set(e);done.countDown();}}).build();t.start(CompositionFactory.buildAtHeight(context,project,180,false),file.getPath());}catch(Exception e){error.set(e);done.countDown();}});assertTrue(done.await(120,TimeUnit.SECONDS));if(error.get()!=null)throw new AssertionError(error.get());}
            try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(file.getPath());assertEquals("180",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));assertEquals("320",r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));for(long time:new long[]{0,600,1000}){Bitmap frame=r.getFrameAtTime(time*1000,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(frame);pixel(expected(time),frame.getPixel(frame.getWidth()/2,frame.getHeight()/2),12);frame.recycle();}}
        }
    }
}

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

@androidx.annotation.OptIn(markerClass=androidx.media3.common.util.UnstableApi.class)
@RunWith(AndroidJUnit4.class)
public class FramingExportTest {
    @Test public void recoveredFramingAndQuadraticIntervalRenderThroughBothEngines()throws Exception{
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();Context context=inst.getTargetContext();
        EditProject p=new EditProject();p.name="Recovered framing export";p.frameRate=30;
        File background=new File(context.getCacheDir(),"v145-black.png");Bitmap black=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);black.eraseColor(Color.BLACK);try(OutputStream out=new FileOutputStream(background)){black.compress(Bitmap.CompressFormat.PNG,100,out);}black.recycle();
        EditProject.Clip base=new MediaImporter(context).importClip(android.net.Uri.fromFile(background),false);base.outMs=base.sourceDurationMs=4000;p.clips.add(base);
        for(int i=0;i<2;i++){EditProject.Overlay o=ShapeCatalog.create(1,i*1000);o.endMs=(i+1)*1000;o.color=Color.WHITE;RectF b=LayerGeometry.shapeBounds(o,1920,1080);TransformMath.Transform framed=CanvasFraming.solve(TransformMath.of(o,0),16d/9,b.left,-b.bottom,b.right,-b.top,i==1,.1f);o.x=framed.x;o.y=framed.y;o.scale=framed.scale;o.heightScale=framed.scale*framed.aspect;p.overlays.add(o);}
        EditProject.Overlay moving=ShapeCatalog.create(1,2000);moving.endMs=4000;moving.color=Color.WHITE;moving.scale=.5f;moving.motion.put(Motion.X,new Motion.Key(0,-.6f));moving.motion.put(Motion.X,new Motion.Key(2000,.6f));Keyframes.ease(moving.motion.curves.get(Motion.X).get(0),1);p.overlays.add(moving);
        ProjectStore store=new ProjectStore(context);store.save(p);RecoveryStore recovery=new RecoveryStore(context);p.name="Recovered framed and eased";recovery.write(p,"export-session","saved-base",1);final EditProject project=recovery.read(p.id).project;
        try{for(boolean ice:new boolean[]{false,true}){
            File output=new File(context.getFilesDir(),"qa/v145-framing-"+(ice?"ice":"media3")+".mp4");output.getParentFile().mkdirs();output.delete();
            if(ice)new BcExporter(context,project,180,new AtomicBoolean(),n->{}).export(output);
            else{CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();inst.runOnMainSync(()->{try{Transformer t=new Transformer.Builder(context).setAssetLoaderFactory(CompositionFactory.assetLoader(context)).setVideoMimeType(MimeTypes.VIDEO_H264).setVideoFrameProcessorFactory(CompositionFactory.frameProcessor()).addListener(new Transformer.Listener(){@Override public void onCompleted(Composition c,ExportResult r){done.countDown();}@Override public void onError(Composition c,ExportResult r,ExportException e){error.set(e);done.countDown();}}).build();t.start(CompositionFactory.buildAtHeight(context,project,180,false),output.getPath());}catch(Exception e){error.set(e);done.countDown();}});assertTrue(done.await(120,TimeUnit.SECONDS));if(error.get()!=null)throw new AssertionError(error.get());}
            try(MediaMetadataRetriever r=new MediaMetadataRetriever()){r.setDataSource(output.getPath());
                Bitmap fit=r.getFrameAtTime(500000,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(fit);assertTrue(Color.red(fit.getPixel(20,90))<25);assertTrue(Color.red(fit.getPixel(72,90))>225);assertTrue(Color.red(fit.getPixel(160,2))>225);fit.recycle();
                Bitmap fill=r.getFrameAtTime(1500000,MediaMetadataRetriever.OPTION_CLOSEST);for(int[] xy:new int[][]{{2,2},{317,2},{2,177},{317,177}})assertTrue("Fill contains every corner",Color.red(fill.getPixel(xy[0],xy[1]))>225);fill.recycle();
                Bitmap eased=r.getFrameAtTime(2500000,MediaMetadataRetriever.OPTION_CLOSEST);int left=320,right=-1;for(int x=0;x<320;x++)if(Color.red(eased.getPixel(x,90))>200){left=Math.min(left,x);right=x;}assertTrue(right>left);assertEquals("Quarter-time Ease In is 6.25% progress",(.5+(-.6+1.2*.0625)/2)*320,(left+right+1)/2d,4);eased.recycle();
            }
        }}finally{store.delete(p.id);}
    }
}

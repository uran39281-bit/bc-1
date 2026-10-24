package com.blackice.bc1.editor;

import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PreviewRecoveryTest {
    private final android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Context context=inst.getTargetContext();
    private File prepare(PreviewProxy proxy,EditProject project)throws Exception{
        CountDownLatch done=new CountDownLatch(1);AtomicReference<File> file=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();
        inst.runOnMainSync(()->proxy.prepare(project,new PreviewProxy.Listener(){
            public void onProgress(int percent){}
            public void onReady(File result){file.set(result);done.countDown();}
            public void onError(String message){error.set(message);done.countDown();}
        }));
        assertTrue("Recovery finishes",done.await(90,TimeUnit.SECONDS));assertNull(error.get());assertNotNull(file.get());return file.get();
    }
    @Test public void realMedia3WatchdogRecoversOnceWithCorrectFramesAndCache()throws Exception{
        EditProject project=new EditProject();project.previewHeight=360;
        java.util.List<File> sources=new java.util.ArrayList<>();PreviewProxy proxy=new PreviewProxy(context,1);
        try{
            for(int color:new int[]{Color.RED,Color.BLUE}){
                File source=new File(context.getFilesDir(),"recovery-"+project.id+"-"+sources.size()+".png");sources.add(source);
                Bitmap image=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);image.eraseColor(color);
                try(OutputStream out=new FileOutputStream(source)){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
                EditProject.Clip clip=new EditProject.Clip();clip.name="Recovery image";clip.uri=Uri.fromFile(source).toString();clip.image=true;clip.sourceDurationMs=clip.outMs=1000;project.clips.add(clip);
            }
            File recovered=prepare(proxy,project);inst.runOnMainSync(()->assertEquals("Exactly one alternate-renderer recovery",1,proxy.timeoutRecoveries));
            try(MediaMetadataRetriever media=new MediaMetadataRetriever()){
                media.setDataSource(recovered.getPath());assertEquals(640,Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)));
                assertEquals(360,Integer.parseInt(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)));
                assertTrue(Math.abs(Long.parseLong(media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))-2000)<150);
                for(int i=0;i<2;i++){
                    Bitmap frame=media.getFrameAtTime(i*1000000L+300000,MediaMetadataRetriever.OPTION_CLOSEST);assertNotNull(frame);
                    int pixel=frame.getPixel(frame.getWidth()/2,frame.getHeight()/2);frame.recycle();
                    if(i==0)assertTrue("Recovered first clip is red",Color.red(pixel)>180&&Color.blue(pixel)<70);
                    else assertTrue("Recovered second clip is blue",Color.blue(pixel)>180&&Color.red(pixel)<70);
                }
            }
            project.playheadMs=1500;assertEquals("Recovered output remains seek-independent and cacheable",recovered,prepare(proxy,project));
        }finally{inst.runOnMainSync(proxy::close);for(File source:sources)source.delete();}
    }
}

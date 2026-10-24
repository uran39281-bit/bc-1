package com.blackice.bc1.engine.bc;
import android.net.Uri;
import androidx.media3.transformer.*;
import androidx.media3.common.MimeTypes;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.core.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class AnimatedVolumeExportTest {
    @Test public void trimmedDoubleSpeedVolumeMatchesInBothExportPipelines()throws Exception{
        android.app.Instrumentation inst=InstrumentationRegistry.getInstrumentation();android.content.Context context=inst.getTargetContext();
        File source=new File(context.getFilesDir(),"volume-source.mp4");try(InputStream in=inst.getContext().getAssets().open("red.mp4");OutputStream out=new FileOutputStream(source)){in.transferTo(out);}
        EditProject project=new EditProject();project.previewHeight=180;EditProject.Clip clip=new MediaImporter(context).importClip(Uri.fromFile(source),false);clip.inMs=500;clip.outMs=2500;clip.speed=2;clip.volume=0;
        clip.motion.put(Motion.VOLUME,new Motion.Key(500,0));clip.motion.put(Motion.VOLUME,new Motion.Key(1400,0));clip.motion.put(Motion.VOLUME,new Motion.Key(1600,1));clip.motion.put(Motion.VOLUME,new Motion.Key(2500,1));project.clips.add(clip);
        for(boolean nativeEngine:new boolean[]{false,true}){
            File output=new File(context.getCacheDir(),"volume-"+nativeEngine+".mp4");output.delete();
            if(nativeEngine)new BcExporter(context,project,ExportPlanner.fixed(180,30,VideoCodec.H264,false),new AtomicBoolean(),p->{}).export(output);
            else{CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();AtomicReference<Transformer> transformer=new AtomicReference<>();
                inst.runOnMainSync(()->{Transformer t=new Transformer.Builder(context).setAssetLoaderFactory(CompositionFactory.assetLoader(context)).setVideoFrameProcessorFactory(CompositionFactory.frameProcessor()).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(new Transformer.Listener(){public void onCompleted(Composition c,ExportResult result){done.countDown();}public void onError(Composition c,ExportResult result,ExportException e){error.set(e);done.countDown();}}).build();transformer.set(t);t.start(CompositionFactory.buildAtHeight(context,project,180),output.getPath());});
                try{assertTrue(done.await(90,TimeUnit.SECONDS));assertNull(error.get());}finally{inst.runOnMainSync(()->transformer.get().cancel());}
            }
            EditProject check=new EditProject();EditProject.Clip audio=new EditProject.Clip();audio.name="Rendered audio";audio.uri=Uri.fromFile(output).toString();audio.audioOnly=true;audio.track=EditProject.AUDIO_FIRST;audio.sourceDurationMs=audio.outMs=1000;audio.volume=1;check.clips.add(audio);
            AudioMixer.Opened decoded=AudioDecoderSource.open(context,AudioPlan.of(check,false).segments.get(0));assertNotNull(decoded);double quiet=0,loud=0;long nq=0,nl=0,frame=0;float[] pcm=new float[2048];try{int n;while((n=decoded.source.read(pcm,1024))>0){for(int i=0;i<n;i++,frame++){double ms=frame*1000.0/decoded.sampleRate;if(ms>=100&&ms<350){quiet+=pcm[2*i]*pcm[2*i];nq++;}if(ms>=700&&ms<900){loud+=pcm[2*i]*pcm[2*i];nl++;}}}}finally{decoded.closer.close();}
            assertTrue("Decoded windows exist",nq>0&&nl>0);double q=Math.sqrt(quiet/nq),l=Math.sqrt(loud/nl);assertTrue("Audible late window "+nativeEngine+": "+l,l>.015);assertTrue("Silent early window "+nativeEngine+": "+q+" / "+l,q<l*.05);output.delete();
        }source.delete();
    }
}

package com.blackice.bc1.engine.bc;
import android.content.Context;
import com.blackice.bc1.audio.LoudnessMeter;
import com.blackice.bc1.core.*;
import com.blackice.bc1.editor.EditProject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
public final class AudioAnalysis {
 public static LoudnessMeter measure(Context context,EditProject.Clip clip,AtomicBoolean stop,IntConsumer progress)throws Exception{
  EditProject p=new EditProject();EditProject.Clip c=clip.copy();c.track=EditProject.AUDIO_FIRST;c.startMs=0;c.gapMs=0;c.transition=-1;c.audio.normalizationDb=0;p.clips.add(c);LoudnessMeter meter=new LoudnessMeter();
  try(AudioMixer mixer=new AudioMixer(AudioPlan.of(p,false),segment->AudioDecoderSource.open(context,segment),48000)){
   float[] buffer=new float[8192];int last=-1;
   while(!mixer.done()){if(stop.get())throw new java.util.concurrent.CancellationException();int n=mixer.render(buffer,4096);if(n<=0)break;meter.add(buffer,n);int percent=(int)(mixer.position()*100/Math.max(1,mixer.totalFrames()));if(percent!=last){last=percent;progress.accept(percent);}}
  }return meter;
 }
}

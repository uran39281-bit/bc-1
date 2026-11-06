package com.blackice.bc1.audio;
import org.junit.Test;
import static org.junit.Assert.*;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.core.*;
public class AudioProcessingTest {
 @Test public void timestampGapsAndOverlapDoNotShiftLaterWords(){
  PcmTimestampCursor c=new PcmTimestampCursor();PcmTimestampCursor.Span s=c.accept(1000000,1000000,48000,480);assertEquals(480,s.keep);assertEquals(0,s.silence);
  s=c.accept(1020000,1000000,48000,480);assertEquals(480,s.silence);
  s=c.accept(1025000,1000000,48000,480);assertEquals(240,s.skip);assertEquals(240,s.keep);
 }
 @Test public void flatProcessingPreservesSamples(){float[] p={.1f,-.2f,0,.5f};float[] expected=p.clone();new AudioDsp(new AudioSettings(),48000,2).process(p,2);assertArrayEquals(expected,p,1e-6f);}
 @Test public void normalizationRespectsSamplePeakCeiling(){LoudnessMeter m=new LoudnessMeter();float[] p=new float[96000];for(int i=0;i<48000;i++)p[2*i]=p[2*i+1]=(float)(.8*Math.sin(2*Math.PI*1000*i/48000));m.add(p,48000);double gain=m.gainFor(-5);assertTrue(Double.isFinite(m.integrated()));assertTrue(m.peak*Math.pow(10,gain/20)<=Math.pow(10,-1.0/20)+1e-6);}
 @Test public void silenceCannotProduceInfiniteNormalization(){try{new LoudnessMeter().gainFor(-16);fail();}catch(IllegalArgumentException expected){}}
 @Test public void settingsAndDisabledVisualizerSurviveSave()throws Exception{
  EditProject p=new EditProject();EditProject.Clip c=new EditProject.Clip();c.uri="file:///voice.wav";c.name="Voice";c.track=8;c.sourceDurationMs=c.outMs=5000;c.audio.noise=2;c.audio.eqDb[2]=3;c.audio.normalizationDb=-4;p.clips.add(c);
  EditProject.Overlay o=new EditProject.Overlay();o.visualizerClipId=c.id;o.visualizerStyle=1;p.overlays.add(o);
  EditProject restored=ProjectStore.decode(ProjectStore.encode(p));assertEquals(2,restored.clips.get(0).audio.noise);assertEquals(3,restored.clips.get(0).audio.eqDb[2],0);assertEquals(c.id,restored.overlays.get(0).visualizerClipId);assertEquals(1,restored.overlays.get(0).visualizerStyle);
  assertNotSame(c.audio,c.copy().audio);
 }
 @Test public void captionShiftIsAtomicAndBounded(){EditProject p=new EditProject();EditProject.Overlay o=new EditProject.Overlay();o.group=CaptionBuilder.GROUP;o.startMs=100;o.endMs=900;p.overlays.add(o);try{CaptionTiming.shift(p,-200);fail();}catch(IllegalArgumentException expected){}assertEquals(100,o.startMs);CaptionTiming.shift(p,-100);assertEquals(0,o.startMs);assertEquals(800,o.endMs);}
}

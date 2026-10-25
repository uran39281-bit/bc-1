package com.blackice.bc1.editor;
import com.blackice.bc1.core.AudioPlan;
import org.junit.Test;
import static org.junit.Assert.*;
public class WorkflowUpdateTest {
    private EditProject project(){EditProject p=new EditProject();EditProject.Clip c=new EditProject.Clip();c.uri="file:///test.wav";c.name="Audio";c.audioOnly=true;c.track=EditProject.AUDIO_FIRST;c.sourceDurationMs=10000;c.inMs=2000;c.outMs=6000;c.volume=.8f;p.clips.add(c);return p;}
    @Test public void animatedVolumeUsesSourceTimeAndRetainsLegacyFadeAndMute()throws Exception{
        EditProject p=project();EditProject.Clip c=p.clips.get(0);c.speed=2;c.fadeInMs=1000;
        Motion.Key first=new Motion.Key(2000,0);first.x1=0;first.y1=0;first.x2=1;first.y2=1;c.motion.put(Motion.VOLUME,first);c.motion.put(Motion.VOLUME,new Motion.Key(6000,1));
        assertEquals(.125f,ClipAudio.gain(c,500,false),.001f);assertEquals(.5f,ClipAudio.gain(c,1000,false),.001f);
        assertEquals(ClipAudio.gain(c,500,false),AudioPlan.of(p,false).segments.get(0).gainAt(500),.00001f);
        EditProject back=ProjectStore.decode(ProjectStore.encode(p));assertEquals(1000,back.clips.get(0).fadeInMs);assertEquals(.5f,ClipAudio.gain(back.clips.get(0),1000,false),.001f);
        ClipAudio.toggleMute(c);assertEquals(0,ClipAudio.gain(c,1000,false),0);assertTrue(AudioPlan.of(p,false).segments.isEmpty());ClipAudio.toggleMute(c);assertEquals(.5f,ClipAudio.gain(c,1000,false),.001f);
    }
    @Test public void zeroStaticVolumeDoesNotDiscardAnimatedSound(){EditProject p=project();EditProject.Clip c=p.clips.get(0);c.volume=0;c.motion.put(Motion.VOLUME,new Motion.Key(2000,1));assertEquals(1,AudioPlan.of(p,false).segments.size());}
    @Test public void fiveLandmarksRoundTripWithoutExtendingVideoAndSlotsRemainStable()throws Exception{
        EditProject p=project();long duration=p.durationMs();for(int i=0;i<5;i++)p.addLandmark(100000+i);p.landmarks.get(2).label="Third";
        try{p.addLandmark(5);fail("Limit must reject a sixth marker");}catch(IllegalArgumentException expected){}
        EditProject restored=ProjectStore.decode(ProjectStore.encode(p));assertEquals(5,restored.landmarks.size());assertEquals(duration,restored.durationMs());assertEquals("Third",restored.landmarks.get(2).label);
        restored.landmarks.remove(1);EditProject.Landmark next=restored.addLandmark(-10);assertEquals(1,next.slot);assertEquals(0,next.timeMs);assertEquals(2,restored.landmarks.get(1).slot);restored.validate();
    }
    @Test public void oldElevenChannelProjectsRemainReadable()throws Exception{
        org.json.JSONObject data=new org.json.JSONObject(ProjectStore.encode(project()));data.put("schema",6);data.remove("landmarks");org.json.JSONArray motion=data.getJSONArray("clips").getJSONObject(0).getJSONArray("motion");motion.remove(11);
        EditProject restored=ProjectStore.decode(data.toString());assertTrue(restored.clips.get(0).motion.curves.get(Motion.VOLUME).isEmpty());assertEquals(.8f,ClipAudio.gain(restored.clips.get(0),0,false),.001f);
    }
    @Test public void normalizedAnimatedGainSurvivesMuteAndSnapshot(){
        EditProject p=project();EditProject.Clip c=p.clips.get(0);c.speed=2;c.fadeInMs=1000;c.volume=0;c.audio.normalizationDb=-6.0206f;
        c.motion.put(Motion.VOLUME,new Motion.Key(2000,.8f));
        AudioPlan.Segment segment=AudioPlan.of(p,false).segments.get(0);
        assertEquals(.2f,segment.gainAt(500),.0001f);assertEquals(.4f,ClipAudio.gain(c,1000,false),.0001f);
        c.audio.normalizationDb=0;assertEquals(.2f,segment.gainAt(500),.0001f);
        ClipAudio.toggleMute(c);assertEquals(0,ClipAudio.gain(c,1000,false),0);assertTrue(AudioPlan.of(p,false).segments.isEmpty());
    }
    @Test public void combinedProjectKeepsAudioCaptionsLandmarksAndVolumeKeys()throws Exception{
        EditProject p=project();EditProject.Clip c=p.clips.get(0);c.audio.noise=2;c.audio.eqDb[2]=3;c.audio.normalizationDb=-4;c.motion.put(Motion.VOLUME,new Motion.Key(c.inMs,.25f));c.fadeOutMs=400;
        p.addLandmark(1000).label="Verse";p.appliedCaptionJobId="caption-receipt";
        EditProject.Overlay caption=new EditProject.Overlay();caption.text="Speech";caption.group=com.blackice.bc1.core.CaptionBuilder.GROUP;caption.startMs=0;caption.endMs=1000;p.overlays.add(caption);
        String encoded=ProjectStore.encode(p);assertEquals(8,new org.json.JSONObject(encoded).getInt("schema"));EditProject restored=ProjectStore.decode(encoded);
        assertEquals("Verse",restored.landmarks.get(0).label);assertEquals("caption-receipt",restored.appliedCaptionJobId);assertEquals("Speech",restored.overlays.get(0).text);
        assertEquals(2,restored.clips.get(0).audio.noise);assertEquals(3,restored.clips.get(0).audio.eqDb[2],0);assertEquals(-4,restored.clips.get(0).audio.normalizationDb,0);assertEquals(400,restored.clips.get(0).fadeOutMs);assertEquals(1,restored.clips.get(0).motion.curves.get(Motion.VOLUME).size());
    }
}

package com.blackice.bc1.core;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.ProjectStore;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class SpeechIntegrationTest {
    private EditProject project() {
        EditProject p = new EditProject();
        EditProject.Clip c = new EditProject.Clip(); c.uri="file:///speech.wav"; c.name="Speech"; c.outMs=c.sourceDurationMs=120000;
        p.clips.add(c); return p;
    }
    @Test public void longTranscriptRoundTripsAndKeepsVisualOverlayLimitSeparate() throws Exception {
        EditProject p=project(); List<CaptionBuilder.Cue> cues=new ArrayList<>();
        for(int i=0;i<100;i++)cues.add(new CaptionBuilder.Cue(i*1000,i*1000+900,"Caption "+i));
        CaptionBuilder.apply(p,cues); p.overlays.add(new EditProject.Overlay());p.validate();
        EditProject loaded=ProjectStore.decode(ProjectStore.encode(p));
        assertEquals(100,SubtitleFiles.captions(loaded).size());assertEquals(1,loaded.regularOverlayCount());
    }
    @Test public void selectedClipWithSharedUriDoesNotMixItsNeighbour() {
        EditProject p=project();EditProject.Clip copy=p.clips.get(0).copy();copy.track=1;copy.volume=.2f;p.clips.add(copy);
        SpeechScope only=SpeechScope.of(p,copy.id);
        assertEquals(1,only.plan.segments.size());assertEquals(.2f,only.plan.segments.get(0).volume,.0001f);
    }
    @Test public void selectedClipKeepsTrimSpeedAndTimelineOffset() {
        EditProject p=project();EditProject.Clip c=p.clips.get(0);c.track=8;c.startMs=2500;c.inMs=1000;c.outMs=5000;c.speed=2;
        SpeechScope only=SpeechScope.of(p,c.id);assertEquals(2500,only.fromMs);assertEquals(4500,only.toMs);
        assertEquals(1000,only.plan.segments.get(0).sourceInMs);assertEquals(2,only.plan.segments.get(0).speed,0);
    }
    @Test public void selectedRangeReplacementPreservesOtherCaptions() {
        EditProject p=project();List<CaptionBuilder.Cue> old=new ArrayList<>();
        old.add(new CaptionBuilder.Cue(0,900,"Before"));old.add(new CaptionBuilder.Cue(1000,1900,"Old"));old.add(new CaptionBuilder.Cue(3000,3900,"After"));
        CaptionBuilder.apply(p,old);CaptionBuilder.apply(p,java.util.Collections.singletonList(new CaptionBuilder.Cue(1100,1800,"New")),1000,2000);
        List<EditProject.Overlay> rows=SubtitleFiles.captions(p);assertEquals(3,rows.size());assertEquals("Before",rows.get(0).text);assertEquals("New",rows.get(1).text);assertEquals("After",rows.get(2).text);
    }
    @Test public void rejectedTranscriptDoesNotEraseExistingCaptions() {
        EditProject p=project();CaptionBuilder.apply(p,java.util.Collections.singletonList(new CaptionBuilder.Cue(0,900,"Keep")));
        List<CaptionBuilder.Cue> huge=new ArrayList<>();for(int i=0;i<=EditProject.MAX_CAPTIONS;i++)huge.add(new CaptionBuilder.Cue(i*1000,i*1000+900,"Too many"));
        try { CaptionBuilder.apply(p,huge);fail(); } catch(IllegalArgumentException expected) { }
        assertEquals("Keep",SubtitleFiles.captions(p).get(0).text);
    }
    @Test public void subtitleFilesUseEditedTextSortedTimesAndCorrectDecimalSeparator() {
        EditProject p=project();CaptionBuilder.apply(p,java.util.Collections.singletonList(new CaptionBuilder.Cue(1250,3001,"Hola <amigo>\nSecond line")));
        assertEquals("1\n00:00:01,250 --> 00:00:03,001\nHola &lt;amigo&gt;\nSecond line\n\n",SubtitleFiles.encode(p,false));
        assertTrue(SubtitleFiles.encode(p,true).startsWith("WEBVTT\n\n00:00:01.250 --> 00:00:03.001"));
        assertEquals("01:01:01,001",SubtitleFiles.time(3661001,false));
    }
    @Test public void hiddenCaptionsAreNotExported() {
        EditProject p=project();CaptionBuilder.apply(p,java.util.Collections.singletonList(new CaptionBuilder.Cue(0,1000,"Hidden")));p.overlays.get(0).hidden=true;
        assertEquals("",SubtitleFiles.encode(p,false));
    }
    @Test public void editsInvalidateRecognitionSnapshotButPlayheadMovementDoesNot() {
        EditProject p=project();String before=CaptionState.fingerprint(p);p.playheadMs=5000;assertEquals(before,CaptionState.fingerprint(p));
        p.clips.get(0).inMs=1000;assertNotEquals(before,CaptionState.fingerprint(p));
    }
    @Test public void longUnspacedUnicodeTextFitsCaptionStorage() {
        StringBuilder text=new StringBuilder();for(int i=0;i<300;i++)text.append("語");
        List<CaptionBuilder.Cue> cues=CaptionBuilder.cues(new long[]{0},new long[]{6000},new String[]{text.toString()},0,6000,24);
        EditProject p=project();CaptionBuilder.apply(p,cues);p.validate();for(CaptionBuilder.Cue c:cues)assertTrue(c.text.length()<=200);
    }
    @Test public void invalidCaptionTimeIsRejected() {
        for(String value:new String[]{"NaN","Infinity","-1"})try{SubtitleFiles.seconds(value);fail(value);}catch(IllegalArgumentException expected){}
        assertEquals(12350,SubtitleFiles.seconds("12.350"));
    }
}

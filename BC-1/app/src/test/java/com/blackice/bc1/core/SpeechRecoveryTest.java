package com.blackice.bc1.core;

import com.blackice.bc1.editor.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SpeechRecoveryTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private EditProject project(){EditProject p=new EditProject();EditProject.Clip c=new EditProject.Clip();c.name="speech";c.uri="file:///speech.wav";c.outMs=c.sourceDurationMs=120000;p.clips.add(c);return p;}
    private List<CaptionBuilder.Cue> cues(){return Arrays.asList(new CaptionBuilder.Cue(1000,2500,"original words"));}
    @Test public void checkpointRestoresExactSampleCursorAndTranscript()throws Exception{
        File f=new File(temp.getRoot(),"checkpoint.json");SpeechCheckpoint c=new SpeechCheckpoint("job");
        c.nextFrame=937600;c.elapsedMs=1200;c.language="es";c.times.add(new long[]{100,1000});c.texts.add("¿Cómo estás?");c.save(f);
        SpeechCheckpoint loaded=SpeechCheckpoint.load(f,"job",1920000);assertEquals(937600,loaded.nextFrame);assertEquals("es",loaded.language);assertEquals(c.texts,loaded.texts);assertArrayEquals(c.times.get(0),loaded.times.get(0));assertEquals(1200,loaded.elapsedMs);
    }
    @Test public void abandonedTemporaryWriteDoesNotReplaceCommittedCheckpoint()throws Exception{
        File f=new File(temp.getRoot(),"checkpoint.json");SpeechCheckpoint c=new SpeechCheckpoint("job");c.nextFrame=16000;c.save(f);
        Files.write(new File(f+".tmp").toPath(),"broken new write".getBytes());assertEquals(16000,SpeechCheckpoint.load(f,"job",32000).nextFrame);
    }
    @Test public void wrongJobCannotResumeAnotherTranscript()throws Exception{
        File f=new File(temp.getRoot(),"checkpoint.json");new SpeechCheckpoint("first").save(f);
        try{SpeechCheckpoint.load(f,"second",100);fail();}catch(IOException expected){}
    }
    @Test public void cursorBeyondSelectionIsRejected()throws Exception{
        File f=new File(temp.getRoot(),"checkpoint.json");SpeechCheckpoint c=new SpeechCheckpoint("job");c.nextFrame=32001;c.save(f);
        try{SpeechCheckpoint.load(f,"job",32000);fail();}catch(IOException expected){}
    }
    @Test public void segmentBeyondCommittedAudioIsRejected()throws Exception{
        File f=new File(temp.getRoot(),"checkpoint.json");SpeechCheckpoint c=new SpeechCheckpoint("job");c.nextFrame=16000;c.times.add(new long[]{0,1100});c.texts.add("bad");c.save(f);
        try{SpeechCheckpoint.load(f,"job",32000);fail();}catch(IOException expected){}
    }
    @Test public void missingCheckpointStartsAtZero()throws Exception{
        assertEquals(0,SpeechCheckpoint.load(new File(temp.getRoot(),"missing"),"job",32000).nextFrame);
    }
    @Test public void failedSaveDoesNotAcknowledgeOrMutateEditor()throws Exception{
        EditProject p=project();AtomicBoolean ack=new AtomicBoolean();
        try{CaptionApplication.commit(p,cues(),0,120000,"job",x->{throw new IOException("disk full");},()->ack.set(true));fail();}catch(IOException expected){}
        assertFalse(ack.get());assertTrue(p.overlays.isEmpty());assertEquals("",p.appliedCaptionJobId);
    }
    @Test public void acknowledgementSeesDurableProjectAndJobMarker()throws Exception{
        AtomicReference<String> disk=new AtomicReference<>();AtomicBoolean ack=new AtomicBoolean();
        EditProject committed=CaptionApplication.commit(project(),cues(),0,120000,"job",p->disk.set(ProjectStore.encode(p)),()->{
            EditProject saved=ProjectStore.decode(disk.get());assertEquals("job",saved.appliedCaptionJobId);assertEquals(1,saved.overlays.size());ack.set(true);
        });assertTrue(ack.get());assertEquals("job",committed.appliedCaptionJobId);
    }
    @Test public void crashAfterSaveBeforeAcknowledgementDoesNotReplaceLaterEdits()throws Exception{
        AtomicReference<String> disk=new AtomicReference<>();
        CaptionApplication.commit(project(),cues(),0,120000,"job",p->disk.set(ProjectStore.encode(p)),()->{throw new IOException("interrupted cleanup");});
        EditProject restored=ProjectStore.decode(disk.get());restored.overlays.get(0).text="corrected by user";
        AtomicBoolean ack=new AtomicBoolean();EditProject again=CaptionApplication.commit(restored,cues(),0,120000,"job",p->disk.set(ProjectStore.encode(p)),()->ack.set(true));
        assertEquals(1,again.overlays.size());assertEquals("corrected by user",again.overlays.get(0).text);assertTrue(ack.get());
    }
    @Test public void invalidCuesDoNotSaveOrAcknowledge()throws Exception{
        AtomicBoolean saved=new AtomicBoolean(),ack=new AtomicBoolean();
        try{CaptionApplication.commit(project(),Arrays.asList(new CaptionBuilder.Cue(0,130000,"outside")),0,120000,"job",p->saved.set(true),()->ack.set(true));fail();}catch(IllegalArgumentException expected){}
        assertFalse(saved.get());assertFalse(ack.get());
    }
    @Test public void receiptDoesNotInvalidateSourceFingerprint()throws Exception{
        EditProject p=project();String before=CaptionState.fingerprint(p);p.appliedCaptionJobId="receipt";
        assertEquals(before,CaptionState.fingerprint(p));assertEquals("receipt",ProjectStore.decode(ProjectStore.encode(p)).appliedCaptionJobId);
    }
    @Test public void batchesAreContiguousBoundedAndSeekableAtMillisecondPrecision(){
        int rate=16000,total=rate*187;float[] pcm=new float[SpeechChunks.CHUNK_SECONDS*rate];Arrays.fill(pcm,.2f);
        for(int i=55*rate;i<56*rate;i++)pcm[i]=0;
        long position=0;int batches=0;
        while(position<total){int available=(int)Math.min(pcm.length,total-position);int cut=position+available>=total?available:SpeechChunks.quietestCut(pcm,available,rate,SpeechChunks.SEARCH_SECONDS*rate);
            assertTrue(cut>0&&cut<=60*rate);assertEquals(0,cut%16);position+=cut;batches++;
        }assertEquals(total,position);assertTrue(batches>=4);assertTrue(2L*pcm.length*4<8_000_000);
    }
}

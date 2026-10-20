package com.blackice.bc1.editor;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class FootageCommandsTest {
 private EditProject project(){EditProject p=new EditProject();for(int i=0;i<2;i++){EditProject.Clip c=new EditProject.Clip();c.name="Video "+i;c.uri="file:///video.mp4";c.sourceDurationMs=c.outMs=4000;p.clips.add(c);}return p;}
 @Test public void playheadTrimKeepsOppositeEdgeAndOtherClips(){EditProject p=project();p.playheadMs=1500;LayerOps.trimToPlayhead(p,0,true);assertEquals(1500,p.placements().get(0).startMs);assertEquals(4000,p.placements().get(0).endMs);assertEquals(4000,p.placements().get(1).startMs);assertEquals(1500,p.clips.get(0).inMs);p.playheadMs=2500;LayerOps.trimToPlayhead(p,0,false);assertEquals(1500,p.placements().get(0).startMs);assertEquals(2500,p.placements().get(0).endMs);assertEquals(4000,p.placements().get(1).startMs);p.validate();}
 @Test public void muteRestoresSavedGainAcrossPersistenceAndCopies()throws Exception{EditProject p=project();p.clips.get(0).volume=.43f;ClipAudio.toggleMute(p.clips.get(0));assertEquals(0,p.clips.get(0).volume,0);EditProject back=ProjectStore.decode(ProjectStore.encode(p));EditProject.Clip copy=back.clips.get(0).copy();ClipAudio.toggleMute(copy);assertEquals(.43f,copy.volume,0);assertEquals(0,back.clips.get(0).volume,0);assertEquals(.7f,back.clips.get(1).volume,0);ClipAudio.toggleMute(back.clips.get(0));assertEquals(.43f,back.clips.get(0).volume,0);}
 @Test public void oldProjectHasNeutralMuteMetadata()throws Exception{JSONObject j=new JSONObject(ProjectStore.encode(project()));j.getJSONArray("clips").getJSONObject(0).remove("unmutedVolume");EditProject p=ProjectStore.decode(j.toString());assertEquals(-1,p.clips.get(0).unmutedVolume,0);assertEquals(.7f,p.clips.get(0).volume,0);}
}

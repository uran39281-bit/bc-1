package com.blackice.bc1.editor;
import org.junit.Test;
import static org.junit.Assert.*;
/** Free placement: gaps on the main sequence, moving clips between rows and the pre-timeline media bin. */
public class PlacementTest {
    private static EditProject.Clip clip(String name,long duration,int track,long start){EditProject.Clip c=new EditProject.Clip();c.uri="file:///"+name;c.name=name;c.sourceDurationMs=duration;c.outMs=duration;c.track=track;c.startMs=start;return c;}
    private static EditProject mains(){EditProject p=new EditProject();p.clips.add(clip("a",1000,0,0));p.clips.add(clip("b",1000,0,0));p.clips.add(clip("c",1000,0,0));return p;}
    private static long start(EditProject p,String name){for(EditProject.Placement pl:p.placements())if(pl.clip.name.equals(name))return pl.startMs;throw new AssertionError(name);}

    @Test public void mainClipKeepsAGapWhenDraggedRight(){
        EditProject p=mains();int at=TimelineEdits.place(p,2,0,4000);
        assertEquals(2,at);assertEquals(0,start(p,"a"));assertEquals(1000,start(p,"b"));assertEquals(4000,start(p,"c"));assertEquals(5000,p.durationMs());
    }
    @Test public void movingAMainClipAwayLeavesOthersInPlace(){
        EditProject p=mains();TimelineEdits.place(p,1,1,6000);
        assertEquals(0,start(p,"a"));assertEquals(2000,start(p,"c"));assertEquals(6000,start(p,"b"));assertEquals(1,p.clips.get(1).track);assertEquals(0,p.clips.get(1).gapMs);
    }
    @Test public void reorderingByTimeShiftsOnlyOverlappedClips(){
        EditProject p=mains();int at=TimelineEdits.place(p,2,0,0);
        assertEquals(0,at);assertEquals("c",p.clips.get(0).name);assertEquals(0,start(p,"c"));assertEquals(1000,start(p,"a"));assertEquals(2000,start(p,"b"));
    }
    @Test public void layerClipDropsOntoMainSequenceAtItsTime(){
        EditProject p=mains();p.clips.add(clip("d",500,2,5000));int at=TimelineEdits.place(p,3,0,5000);
        assertEquals(0,p.clips.get(at).track);assertEquals(5000,start(p,"d"));assertEquals(2000,start(p,"c"));
    }
    @Test public void verticalMoveBetweenLayersKeepsTime(){
        EditProject p=mains();p.clips.add(clip("d",500,1,700));TimelineEdits.place(p,3,3,900);assertEquals(3,p.clips.get(3).track);assertEquals(900,start(p,"d"));
    }
    @Test public void visualAndAudioRowsStaySeparate(){
        EditProject p=mains();try{TimelineEdits.place(p,0,4,0);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void gapSuppressesTransitionAndCloseGapsRemovesIt(){
        EditProject p=mains();p.clips.get(0).transition=0;assertEquals(500,start(p,"b"));
        TimelineEdits.place(p,1,0,1500);assertEquals(1500,start(p,"b"));assertEquals(0,p.placements().get(1).incomingMs);
        LayerOps.closeGaps(p,java.util.List.of(0));assertEquals(500,start(p,"b"));
    }
    @Test public void splitDoesNotDuplicateTheGap(){
        EditProject p=mains();int at=TimelineEdits.place(p,0,0,2000);assertEquals(2000,start(p,"a"));assertEquals(3000,start(p,"c"));p.split(at,2500);
        assertEquals(2500,p.placements().get(at+1).startMs);assertEquals(0,p.clips.get(at+1).gapMs);assertEquals(3000,start(p,"c"));
    }
    @Test public void gapAndLibraryRoundTrip()throws Exception{
        EditProject p=mains();TimelineEdits.place(p,2,0,4000);EditProject.Clip unused=clip("unused",3000,0,0);p.library.add(EditProject.asset(unused));
        EditProject back=ProjectStore.decode(ProjectStore.encode(p));
        assertEquals(4000,start(back,"c"));assertEquals(4,back.library.size());assertEquals("file:///unused",back.library.get(0).uri);
    }
    @Test public void olderProjectsRebuildTheMediaBinFromTheTimeline()throws Exception{
        EditProject p=mains();p.clips.add(clip("a",1000,0,0));org.json.JSONObject j=new org.json.JSONObject(ProjectStore.encode(p));j.remove("library");String json=j.toString();
        EditProject back=ProjectStore.decode(json);assertEquals(3,back.library.size());
    }
}

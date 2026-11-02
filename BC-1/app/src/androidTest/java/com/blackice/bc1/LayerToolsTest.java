package com.blackice.bc1;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Layer Tools navigation and Copy All, plus the compact timeline toolbar without timeline zoom. */
@RunWith(AndroidJUnit4.class)
public class LayerToolsTest {
    final WorkspaceV2Test h=new WorkspaceV2Test();

    @Test public void layerToolsHasFiveSeparateTabsAndBackKeepsTheSelection()throws Exception{
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{new Workspace144Test().fixture();
            h.shell("wm size 1920x900");h.shell("wm density 160");
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);Thread.sleep(700);
                final long[] playhead=new long[1];final int[] undoBefore=new int[1];
                scene.onActivity(a->{a.selectClip(0,false);a.seek(700);playhead[0]=a.project.playheadMs;a.panels.reset();a.renderInspector();undoBefore[0]=a.undo.size();
                    View tile=WorkspaceV2Test.described(a.inspector,"Media Settings: Layer Tools");assertNotNull("Media Settings has a Layer Tools card",tile);
                    assertNotNull("Existing cards remain",WorkspaceV2Test.described(a.inspector,"Media Settings: Move & Transform"));
                    tile.performClick();assertEquals("Layer Tools",a.panels.title());});
                Thread.sleep(400);
                scene.onActivity(a->{
                    String[] order={"Speed","Reverse","Screenshot","Joint Movement","Copy All"};
                    assertNotNull(WorkspaceV2Test.described(a.inspector,"Speed, selected tab"));
                    for(int i=1;i<order.length;i++)assertNotNull(order[i]+" tab",WorkspaceV2Test.described(a.inspector,order[i]+" tab"));
                    assertNotNull("Back returns to Media Settings",WorkspaceV2Test.described(a.inspector,"Back to Media Settings"));
                    assertNotNull("A still image has no playback rate",WorkspaceV2Test.startsWith(a.inspector,"Pictures have no playback speed"));
                    for(int i=1;i<order.length;i++){a.layerTools.select(i);assertEquals("Switching tabs",i,a.layerTools.tab());assertEquals("Tab changes are not document edits",undoBefore[0],a.undo.size());}
                    assertNotNull("Copy All tab shows its action",WorkspaceV2Test.text(a.inspector,"Transformations and effects"));
                });
                h.capture("v150-layer-tools-copy-all");
                scene.onActivity(a->{
                    a.layerTools.select(1);assertNotNull(WorkspaceV2Test.startsWith(a.inspector,"Reversing footage needs"));
                    a.layerTools.select(2);assertNotNull(WorkspaceV2Test.startsWith(a.inspector,"Capture the selected layer at the playhead"));
                    a.layerTools.select(3);assertNotNull(WorkspaceV2Test.text(a.inspector,"Parent & Child"));
                    a.layerTools.select(4);
                    boolean dirty=a.dirty;int undo=a.undo.size();
                    a.layerTools.copy();
                    assertNotNull("Copy All snapshots the selected layer",a.copiedAttributes);assertEquals("Copy All is not a document edit",undo,a.undo.size());assertEquals(dirty,a.dirty);
                    int categories=a.copiedAttributes.categories;assertTrue((categories&com.blackice.bc1.editor.Attributes.TRANSFORM)!=0);
                    a.panels.back();assertNull("Back leaves Layer Tools",a.panels.title());assertEquals("Same layer",0,a.selected);assertEquals("Same playhead",playhead[0],a.project.playheadMs);
                    assertNotNull("Media Settings shown again",WorkspaceV2Test.described(a.inspector,"Media Settings: Layer Tools"));
                });
            }
        }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }

    @Test public void compactToolbarHasNoZoomAndKeepsTouchTargetsApart()throws Exception{
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{new Workspace144Test().fixture();
            h.shell("wm size 1920x900");h.shell("wm density 160");
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);Thread.sleep(700);
                scene.onActivity(a->{a.selectClip(0,false);a.forcePointerToolbar=true;a.renderTimelineTools();});
                Thread.sleep(500);
                scene.onActivity(a->{
                    LinearLayout bar=a.timelineTools;float dp=a.getResources().getDisplayMetrics().density;
                    for(String gone:new String[]{"Zoom in timeline","Zoom out timeline"})assertNull(gone+" is removed",WorkspaceV2Test.described(bar,gone));
                    for(int i=0;i<bar.getChildCount();i++){View v=bar.getChildAt(i);assertFalse("No zoom ruler",v instanceof NumberRuler);assertTrue("No empty spacer or leftover hit region",v.getWidth()>0&&v.getContentDescription()!=null);}
                    String[] names={"Delete selected layer","Undo","Redo","Trim Start","Split","Trim End"};
                    View first=WorkspaceV2Test.described(bar,"Delete selected layer");int at=bar.indexOfChild(first);assertEquals("Undo",bar.getChildAt(at+1).getContentDescription());assertEquals("Redo",bar.getChildAt(at+2).getContentDescription());
                    List<View> row=new ArrayList<>();for(int i=0;i<bar.getChildCount();i++)row.add(bar.getChildAt(i));
                    assertEquals("Trash, Undo, Redo, Trim Start, Split, Trim End, Snap, Fit, Markers",9,row.size());
                    float center=-1;
                    for(int i=0;i<row.size();i++){View v=row.get(i);assertEquals("Compact button height",32,v.getHeight()/dp,.6f);
                        float c=v.getTop()+v.getHeight()/2f;if(center<0)center=c;assertEquals("Common centerline",center,c,1.5f);
                        if(i>0){View p=row.get(i-1);float gap=(v.getLeft()-p.getRight())/dp;boolean group=i==3||i==6;assertTrue("Gap "+i+" "+gap,group?gap>=11.4f&&gap<=16.6f:Math.abs(gap-8)<=.6f);}}
                    for(int i=0;i<6;i++)assertEquals(names[i]+" is a 32 dp square",32,row.get(i).getWidth()/dp,.6f);
                });
                h.capture("v150-toolbar-pointer");
                scene.onActivity(a->{a.forcePointerToolbar=false;a.renderTimelineTools();});
                Thread.sleep(500);
                scene.onActivity(a->{
                    LinearLayout bar=a.timelineTools;float dp=a.getResources().getDisplayMetrics().density;
                    for(int i=0;i<bar.getChildCount();i++){View v=bar.getChildAt(i);assertTrue("Touch height "+v.getHeight()/dp,v.getHeight()/dp>=43.5f);assertTrue("Touch width "+v.getWidth()/dp,v.getWidth()/dp>=43.5f);
                        if(i>0)assertTrue("Touch targets never overlap",bar.getChildAt(i-1).getRight()<=v.getLeft());}
                    assertNotNull("Playhead tools remain",WorkspaceV2Test.described(bar,"Split"));
                    long playhead=a.project.playheadMs;a.fitTimeline();assertTrue(a.pixelsPerSecond>=8&&a.pixelsPerSecond<=2400);assertEquals(playhead,a.project.playheadMs);
                });
                h.capture("v150-toolbar-touch");
            }
        }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }
}

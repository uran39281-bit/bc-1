package com.blackice.bc1;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Free placement on a phone workspace: dragging an unselected clip leaves a gap on the main sequence,
 * dragging down/up moves it to another row, imports land in Project Media only, and dropped media is
 * placed at the drop time. */
@RunWith(AndroidJUnit4.class)
public class TimelineDragTest {
    final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    String shell(String command)throws Exception{try(ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    String override(String value){for(String line:value.split("\n"))if(line.startsWith("Override"))return line.substring(line.indexOf(':')+1).trim();return "reset";}
    void touch(View v,float x,float y,float endX,float endY){long t=SystemClock.uptimeMillis();for(int i=0;i<3;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==1?MotionEvent.ACTION_MOVE:MotionEvent.ACTION_UP;MotionEvent e=MotionEvent.obtain(t,t+i*40,action,i==0?x:endX,i==0?y:endY,0);v.dispatchTouchEvent(e);e.recycle();}}
    static float vx(EditorActivity a,long ms){return a.timeline.x(ms)-a.timelineScroll.getScrollX();}
    static float rowY(EditorActivity a,int track){return a.timeline.rowTopOfTrack(track)+a.dp(TimelineView.ROW_DP/2f);}
    static long start(EditorActivity a,int clip){return a.project.placements().get(clip).startMs;}
    File picture(String name,int color)throws IOException{File file=new File(inst.getTargetContext().getFilesDir(),name);Bitmap b=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);b.eraseColor(color);try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();return file;}

    @Test public void dragLeavesGapsMovesBetweenRowsAndDropsMedia()throws Exception{
        String size=override(shell("wm size")),density=override(shell("wm density"));
        try{
            shell("wm size 1080x1920");shell("wm density 320");
            EditProject p=new EditProject();p.name="Drag QA";
            for(int i=0;i<2;i++){EditProject.Clip c=new EditProject.Clip();c.name="Shot "+i;c.image=true;c.uri=android.net.Uri.fromFile(picture("drag-"+i+".png",i==0?0xFF2B7FB8:0xFF7B4FC0)).toString();c.outMs=c.sourceDurationMs=3000;p.clips.add(c);}
            new ProjectStore(inst.getTargetContext()).save(p);
            inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").putBoolean("ui-locked",false).putBoolean("snapping",false).putInt("workspace-focus",0).commit();
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                AtomicBoolean loaded=new AtomicBoolean();long deadline=SystemClock.elapsedRealtime()+15000;
                while(!loaded.get()&&SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->loaded.set(!a.loading));Thread.sleep(50);}assertTrue(loaded.get());inst.waitForIdleSync();
                scenario.onActivity(a->{
                    assertEquals("Timeline sources are listed in Project Media",2,a.project.library.size());
                    // Drag the unselected second clip one second to the right: a gap opens, the first clip stays.
                    float y=rowY(a,0);touch(a.timelineScroll,vx(a,4500),y,vx(a,5500),y);
                    assertEquals(0,start(a,0));assertEquals("Main-sequence clips can leave a gap",4000,start(a,1),3);assertEquals(1,a.selected);
                    // Drag it down onto the empty layer row: it becomes a layer at the same time.
                    assertTrue("An empty layer row is offered as a drop target",a.timeline.rowTopOfTrack(1)>=0);
                    touch(a.timelineScroll,vx(a,5500),rowY(a,0),vx(a,5500),rowY(a,1));
                    EditProject.Clip moved=a.project.clips.get(a.selected);assertEquals("Vertical drag changes the row",1,moved.track);assertEquals(4000,start(a,a.selected),3);assertEquals(0,start(a,0));
                    // Media dropped on the main row at 8 s keeps the space before it.
                    a.dropMedia(a.project.library.get(0),0,8000);EditProject.Clip dropped=a.project.clips.get(a.selected);
                    assertEquals(0,dropped.track);assertEquals(8000,start(a,a.selected));assertEquals(3,a.project.clips.size());
                    a.undo(false);assertEquals("A drop is one undo step",2,a.project.clips.size());
                });
                // Importing adds to Project Media, never to the timeline.
                File imported=picture("drag-import.png",0xFF30A060);
                scenario.onActivity(a->a.importUris(java.util.List.of(android.net.Uri.fromFile(imported)),EditorActivity.IMPORT));
                AtomicBoolean done=new AtomicBoolean();deadline=SystemClock.elapsedRealtime()+15000;
                while(!done.get()&&SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->done.set(!a.importing&&a.project.library.size()==3));Thread.sleep(100);}
                scenario.onActivity(a->{assertEquals("Import goes to Project Media",3,a.project.library.size());assertEquals("Import leaves the timeline unchanged",2,a.project.clips.size());});
            }
        }finally{shell("wm size "+size);shell("wm density "+density);inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").putBoolean("snapping",true).commit();}
    }
}

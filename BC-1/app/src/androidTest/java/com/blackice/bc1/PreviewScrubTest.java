package com.blackice.bc1;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Fast paused scrubbing (the ICE Lite live preview) must end on the last position and never leave the preview broken. */
@RunWith(AndroidJUnit4.class)
public class PreviewScrubTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final AtomicReference<EditorActivity> activeActivity = new AtomicReference<>();

    private String fixture(String name) throws Exception {
        File file = new File(inst.getTargetContext().getFilesDir(), name);
        try (InputStream in = inst.getContext().getAssets().open(name); OutputStream out = new FileOutputStream(file)) {
            byte[] bytes = new byte[8192]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
        }
        return Uri.fromFile(file).toString();
    }

    private void waitFor(ActivityScenario<EditorActivity> scenario, java.util.function.Predicate<EditorActivity> condition, long timeoutMs, String what) throws Exception {
        AtomicBoolean ok = new AtomicBoolean();
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (!ok.get() && SystemClock.elapsedRealtime() < deadline) {
            // ActivityScenario waits for UI idleness, which can arrive only after short playback ends.
            inst.runOnMainSync(() -> ok.set(condition.test(activeActivity.get())));
            if (!ok.get()) Thread.sleep(50);
        }
        // On failure, report the preview's own state so the cause is visible without a device.
        java.util.concurrent.atomic.AtomicReference<String> state = new java.util.concurrent.atomic.AtomicReference<>("");
        if (!ok.get()) scenario.onActivity(a -> state.set(a.previewDebug()));
        assertTrue("Timed out waiting for " + what + " • " + state.get(), ok.get());
    }

    @Test public void fastScrubbingKeepsTheLivePreviewWorking() throws Exception {
        EditProject p = new EditProject(); p.name = "Scrub QA";
        EditProject.Clip c = new MediaImporter(inst.getTargetContext()).importClip(Uri.parse(fixture("red.mp4")), false);
        c.name = "red"; p.clips.add(c);
        new ProjectStore(inst.getTargetContext()).save(p);
        inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS, 0).edit().putString("last-project", p.id).putBoolean("ui-locked", false).commit();
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(EditorActivity.class)) {
            scenario.onActivity(activeActivity::set);
            waitFor(scenario, a -> !a.loading && a.live != null && a.live.lastFrameMs() >= 0, 20_000, "the live preview's first frame");
            long duration = p.durationMs();
            // About 400 seeks in 2 seconds, sweeping back and forth like a fast finger on the timeline.
            long last = 0;
            for (int i = 0; i < 400; i++) {
                long target = Math.round((Math.sin(i / 9.0) * .5 + .5) * (duration - 1));
                last = target;
                scenario.onActivity(a -> a.seek(target));
                Thread.sleep(5);
            }
            final long expected = last;
            android.util.Log.i(EditorActivity.PREVIEW_LOG, "test: last scrub target " + expected);
            waitFor(scenario, a -> a.player == null && a.live.lastFrameMs() == Math.min(expected, a.project.durationMs())
                    && a.live.lastError() == null, 15_000, "the preview to settle on the last scrub position");
            scenario.onActivity(a -> assertEquals("Paused scrubbing never starts a player", null, a.player));
            AtomicReference<String> hint = new AtomicReference<>();
            scenario.onActivity(a -> { TextView v = a.emptyPreview; hint.set(v != null && v.getVisibility() == android.view.View.VISIBLE ? v.getText().toString() : ""); });
            assertFalse("Preview left broken: " + hint.get(), hint.get().contains("unavailable"));
        }
    }
    /** Exercise the visible line through the real timeline gesture handler, including player handoff. */
    @Test public void scrollingUnderCenteredPlayheadShowsPixelsWithoutPressingPlay() throws Exception {
        EditProject p=new EditProject();p.name="Visible playhead QA";
        for(String name:new String[]{"red.mp4","blue.mp4"})p.clips.add(new MediaImporter(inst.getTargetContext()).importClip(Uri.parse(fixture(name)),false));
        new ProjectStore(inst.getTargetContext()).save(p);
        inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","PHONE").putBoolean("ui-locked",false).commit();
        try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
            scenario.onActivity(activeActivity::set);
            waitFor(scenario,a->!a.loading&&a.project.id.equals(p.id),15000,"requested project");
            scenario.onActivity(a->a.seek(1000));waitForPixels(scenario,1000,false);
            scenario.onActivity(a->{int history=a.undo.size();long firstStart=a.project.placements().get(0).startMs,secondStart=a.project.placements().get(1).startMs;dragTimeline(a,4000);assertEquals(4000,a.project.playheadMs,2);assertEquals("Scrub does not edit clips",history,a.undo.size());assertEquals(2,a.project.clips.size());assertEquals(firstStart,a.project.placements().get(0).startMs);assertEquals(secondStart,a.project.placements().get(1).startMs);});
            waitForPixels(scenario,4000,true);
            inst.runOnMainSync(()->activeActivity.get().togglePlay());
            waitFor(scenario,a->a.player!=null&&a.player.isPlaying(),60000,"actual playback");
            inst.runOnMainSync(()->{EditorActivity a=activeActivity.get();a.pausePlayback();a.seek(4000);});waitForPixels(scenario,4000,true);
            scenario.onActivity(a->dragTimeline(a,1000));waitForPixels(scenario,1000,false);
            scenario.onActivity(a->{long time=a.project.playheadMs;float scale=a.timeline.timelineScale();a.zoomTimeline(1.5f,10);assertEquals("Zoom preserves current time",time,a.project.playheadMs);assertTrue(a.timeline.timelineScale()>scale);assertEquals(a.timeline.viewport()/2f,a.timeline.x(time)-a.timelineScroll.getScrollX(),1f);a.fitTimeline();assertEquals(time,a.project.playheadMs);assertTrue("Fit keeps the beginning outside the pinned header",a.timeline.x(0)-a.timelineScroll.getScrollX()>=a.timeline.headerWidth());assertTrue("Fit keeps the end visible",a.timeline.x(a.project.durationMs())-a.timelineScroll.getScrollX()<=a.timeline.viewport());assertNull("Scrubbing stays paused",a.player);assertEquals(android.view.View.INVISIBLE,a.surface.getVisibility());});
        }finally{inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
    }
    private void dragTimeline(EditorActivity a,long target){
        float y=a.timeline.rowTopOfTrack(0)+a.dp(26),from=a.timeline.pad()+a.dp(20);
        float to=from-(target-a.project.playheadMs)/1000f*a.timeline.timelineScale();
        long now=SystemClock.uptimeMillis();
        int[] actions={android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_MOVE,android.view.MotionEvent.ACTION_UP};
        for(int i=0;i<3;i++){android.view.MotionEvent e=android.view.MotionEvent.obtain(now,now+i*40,actions[i],i==0?from:to,y,0);a.timelineScroll.dispatchTouchEvent(e);e.recycle();}
        assertEquals("Playhead stays centered while the timeline moves",a.timeline.viewport()/2f,a.timeline.x(a.project.playheadMs)-a.timelineScroll.getScrollX(),1f);
    }

    private void waitForPixels(ActivityScenario<EditorActivity> scenario,long expected,boolean blue)throws Exception{
        waitFor(scenario,a->{if(a.live.lastFrameMs()!=expected||a.liveView.getVisibility()!=android.view.View.VISIBLE)return false;
            android.graphics.Bitmap frame=a.liveView.getBitmap();if(frame==null)return false;
            int pixel=frame.getPixel(frame.getWidth()/3,frame.getHeight()/3);frame.recycle();
            return blue?android.graphics.Color.blue(pixel)>150&&android.graphics.Color.red(pixel)<100:android.graphics.Color.red(pixel)>150&&android.graphics.Color.blue(pixel)<100;
        },30000,"visible "+(blue?"blue":"red")+" video frame at "+expected);
        // Also sample the actual screen: a correct off-screen texture alone must not pass.
        inst.waitForIdleSync();int[] point=new int[2];scenario.onActivity(a->{a.liveView.getLocationOnScreen(point);point[0]+=a.liveView.getWidth()/3;point[1]+=a.liveView.getHeight()/3;});
        // Texture readback can precede SurfaceFlinger's next displayed frame. Wait for the actual
        // screen too, retaining the pixel assertion (not merely a successful offscreen render).
        long deadline=SystemClock.elapsedRealtime()+5000;int pixel=0;boolean displayed=false;
        do{android.graphics.Bitmap screen=inst.getUiAutomation().takeScreenshot();assertNotNull(screen);pixel=screen.getPixel(point[0],point[1]);
            displayed=blue?android.graphics.Color.blue(pixel)>150&&android.graphics.Color.red(pixel)<100:android.graphics.Color.red(pixel)>150&&android.graphics.Color.blue(pixel)<100;
            if(!displayed&&SystemClock.elapsedRealtime()>=deadline){java.io.File qa=new java.io.File(inst.getTargetContext().getFilesDir(),"qa");qa.mkdirs();try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(qa,"scrub-screen-failure.png"))){screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}}
            screen.recycle();if(!displayed)Thread.sleep(50);
        }while(!displayed&&SystemClock.elapsedRealtime()<deadline);
        assertTrue("Rendered video must be visible on screen at "+expected+"; pixel="+Integer.toHexString(pixel),displayed);
    }

}

package com.blackice.bc1;

import android.app.Instrumentation;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.TextView;
import androidx.media3.common.Player;
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

/** Fast scrubbing on a single-clip project (the live CompositionPlayer preview) must not leave the preview broken. */
@RunWith(AndroidJUnit4.class)
public class PreviewScrubTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();

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
            scenario.onActivity(a -> ok.set(condition.test(a)));
            if (!ok.get()) Thread.sleep(50);
        }
        assertTrue("Timed out waiting for " + what, ok.get());
    }

    @Test public void fastScrubbingKeepsTheLivePreviewWorking() throws Exception {
        EditProject p = new EditProject(); p.name = "Scrub QA";
        EditProject.Clip c = new MediaImporter(inst.getTargetContext()).importClip(Uri.parse(fixture("red.mp4")), false);
        c.name = "red"; p.clips.add(c);
        new ProjectStore(inst.getTargetContext()).save(p);
        inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS, 0).edit().putString("last-project", p.id).putBoolean("ui-locked", false).commit();
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(EditorActivity.class)) {
            // Paused, the editor shows the ICE Lite live frame; Play prepares the Media3 player used for this check.
            waitFor(scenario, a -> !a.loading, 20_000, "the project");
            scenario.onActivity(a -> a.togglePlay());
            waitFor(scenario, a -> a.player != null && a.player.getPlaybackState() == Player.STATE_READY, 20_000, "the playback player");
            scenario.onActivity(a -> a.pausePlayback());
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
            waitFor(scenario, a -> a.player != null && a.player.getPlaybackState() == Player.STATE_READY
                    && Math.abs(a.player.getCurrentPosition() - expected) <= 60, 15_000, "the preview to settle on the last scrub position");
            AtomicReference<String> hint = new AtomicReference<>();
            scenario.onActivity(a -> { TextView v = a.emptyPreview; hint.set(v != null && v.getVisibility() == android.view.View.VISIBLE ? v.getText().toString() : ""); });
            assertFalse("Preview left broken: " + hint.get(), hint.get().contains("unavailable"));
        }
    }

    /** With no playback player (the normal paused state), scrubbing renders through the ICE Lite live preview. */
    @Test public void fastScrubbingWithTheLivePreview() throws Exception {
        EditProject p = new EditProject(); p.name = "Live scrub QA";
        EditProject.Clip c = new MediaImporter(inst.getTargetContext()).importClip(Uri.parse(fixture("red.mp4")), false);
        c.name = "red"; c.effect = 21; c.intensity = .5f; p.clips.add(c); // Hue Shift: red becomes cyan
        new ProjectStore(inst.getTargetContext()).save(p);
        inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS, 0).edit().putString("last-project", p.id).putBoolean("ui-locked", false).commit();
        try (ActivityScenario<EditorActivity> scenario = ActivityScenario.launch(EditorActivity.class)) {
            waitFor(scenario, a -> !a.loading && a.liveView != null && a.liveView.getVisibility() == android.view.View.VISIBLE && a.liveView.isAvailable(), 20_000, "the live preview");
            long duration = p.durationMs();
            for (int i = 0; i < 300; i++) {
                long target = Math.round((Math.sin(i / 7.0) * .5 + .5) * (duration - 1));
                scenario.onActivity(a -> a.seek(target));
                Thread.sleep(5);
            }
            Thread.sleep(1500);
            AtomicReference<String> state = new AtomicReference<>();
            scenario.onActivity(a -> {
                assertNull("No playback player while paused", a.player);
                android.graphics.Bitmap b = a.liveView.getBitmap();
                int px = b.getPixel(b.getWidth() / 2, b.getHeight() / 2); b.recycle();
                state.set(Integer.toHexString(px) + " error=" + a.live.lastError());
                assertNull("Live preview error", a.live.lastError());
                assertTrue("Hue-shifted red shows as cyan in the live frame: " + Integer.toHexString(px),
                        android.graphics.Color.red(px) < 90 && android.graphics.Color.green(px) > 150 && android.graphics.Color.blue(px) > 150);
            });
        }
    }
}

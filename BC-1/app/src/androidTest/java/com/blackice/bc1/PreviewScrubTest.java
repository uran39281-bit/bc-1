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
}

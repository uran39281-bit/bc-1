package com.blackice.bc1.captions;

import android.app.Instrumentation;
import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.core.CaptionBuilder;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.MediaImporter;
import com.blackice.bc1.editor.ProjectStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Auto captions end to end with the real whisper.cpp library and the Tiny model (downloaded once, as
 * in the app): the speech sample, a project clip placed later on the timeline, cancellation, and the
 * background service handing captions to the project.
 */
@RunWith(AndroidJUnit4.class)
public class AutoCaptionsTest {
    private static final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private static final Context context = inst.getTargetContext();
    private static final SpeechModels.Model MODEL = SpeechModels.Model.TINY;

    @BeforeClass public static void model() throws Exception {
        assertTrue("Native speech library loads: " + WhisperLib.systemInfo(), WhisperLib.available());
        long start = System.currentTimeMillis();
        SpeechModels.ensure(context, MODEL, () -> false, p -> { });
        Log.i("BC1Captions", "Model ready in " + (System.currentTimeMillis() - start) + " ms: " + SpeechModels.file(context, MODEL).length() + " bytes");
        assertTrue(SpeechModels.installed(context, MODEL));
    }

    private static String fixture(String name) throws Exception {
        File file = new File(context.getFilesDir(), name);
        try (InputStream in = inst.getContext().getAssets().open(name); OutputStream out = new FileOutputStream(file)) {
            byte[] bytes = new byte[8192]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
        }
        return Uri.fromFile(file).toString();
    }

    private static String words(String text) { return text.toLowerCase(Locale.US).replaceAll("[^a-z ]", " "); }

    @Test public void sampleIsTranscribedAndTimed() throws Exception {
        float[] pcm = SpeechBenchmark.sample(context);
        assertEquals("11 s at 16 kHz", 176_000, pcm.length);
        try (WhisperLib w = new WhisperLib(SpeechModels.file(context, MODEL).getPath())) {
            long start = System.nanoTime();
            List<WhisperLib.Segment> segments = w.transcribe(pcm, "auto", false, Transcriber.threads(), null);
            long ms = (System.nanoTime() - start) / 1_000_000;
            StringBuilder all = new StringBuilder();
            for (WhisperLib.Segment s : segments) {
                all.append(s.text);
                assertTrue("Segment inside the audio: " + s, s.startMs >= 0 && s.endMs <= 11_100 && s.endMs >= s.startMs);
            }
            Log.i("BC1Captions", "Tiny on the sample: " + ms + " ms, " + w.language() + ": " + all);
            String heard = words(all.toString());
            assertTrue("Heard the speech: " + all, heard.contains("country") && heard.contains("ask"));
            assertEquals("en", w.language());
        }
    }

    @Test public void cancellingStopsTranscription() throws Exception {
        float[] pcm = new float[16_000 * 90];
        float[] sample = SpeechBenchmark.sample(context);
        for (int i = 0; i < pcm.length; i++) pcm[i] = sample[i % sample.length];
        try (WhisperLib w = new WhisperLib(SpeechModels.file(context, MODEL).getPath())) {
            new Thread(() -> { try { Thread.sleep(1500); } catch (InterruptedException ignored) { } w.cancel(); }).start();
            long start = System.nanoTime();
            try { w.transcribe(pcm, "en", false, Transcriber.threads(), null); fail("Expected cancellation"); }
            catch (WhisperLib.Cancelled expected) { }
            long ms = (System.nanoTime() - start) / 1_000_000;
            assertTrue("Stopped promptly: " + ms + " ms", ms < 30_000);
        }
    }

    /** A speech clip at 2 s on the timeline, plus a music bed: captions follow the speech, at timeline times. */
    @Test public void projectSpeechBecomesTimedCaptions() throws Exception {
        EditProject p = new EditProject(); p.name = "Captions QA";
        EditProject.Clip lead = new MediaImporter(context).importClip(Uri.parse(fixture("red.mp4")), false);
        lead.name = "lead"; lead.inMs = 0; lead.outMs = 2000; lead.volume = 0; p.clips.add(lead);
        EditProject.Clip speech = new MediaImporter(context).importClip(Uri.parse(fixture("speech.mp4")), false);
        speech.name = "speech"; p.clips.add(speech);
        p.music = new EditProject.Music(); p.music.uri = fixture("music.wav"); p.music.sourceDurationMs = 1000; p.music.inMs = 0; p.music.outMs = 1000; p.music.loop = true; p.music.volume = .5f;
        p.validate();
        Transcriber.Options o = new Transcriber.Options(); o.model = MODEL;
        AtomicBoolean progressed = new AtomicBoolean();
        Transcriber.Result r = new Transcriber(context).run(ProjectStore.snapshot(p), o, (stage, percent) -> progressed.set(true));
        assertTrue(progressed.get());
        assertFalse("Captions found", r.cues.isEmpty());
        StringBuilder all = new StringBuilder();
        for (CaptionBuilder.Cue c : r.cues) {
            all.append(' ').append(c.text);
            // Speech starts 1 s into the clip, which starts at 2 s.
            assertTrue("Cue on the speech clip: " + c, c.startMs >= 2000 && c.endMs <= p.durationMs());
            assertTrue("At most two lines: " + c, c.text.split("\n").length <= 2);
        }
        Log.i("BC1Captions", String.format(Locale.US, "Project captions: %d cues, %.1fx real time:%s", r.cues.size(), r.realtimeFactor(), all));
        assertTrue("First caption after the leading silence: " + r.cues.get(0), r.cues.get(0).startMs >= 2700);
        assertTrue(words(all.toString()).contains("country"));
        assertEquals(r.cues.size(), CaptionBuilder.apply(p, r.cues));

        // Only the selected clip: the silent lead clip has no speech to caption.
        o.clipId = lead.id;
        try { new Transcriber(context).run(ProjectStore.snapshot(p), o, (s, x) -> { }); fail("A silent clip has nothing to caption"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("no sound")); }
        o.clipId = speech.id;
        Transcriber.Result clipOnly = new Transcriber(context).run(ProjectStore.snapshot(p), o, (s, x) -> { });
        assertTrue(words(clipOnly.cues.toString()).contains("country"));
    }

    /** The background service transcribes a snapshot and hands the editor a result to add with undo. */
    @Test public void serviceDeliversCaptionsForTheProject() throws Exception {
        EditProject p = new EditProject(); p.name = "Service captions";
        EditProject.Clip speech = new MediaImporter(context).importClip(Uri.parse(fixture("speech.mp4")), false);
        p.clips.add(speech); p.validate();
        CaptionService.takeResult(context, p.id); // clear any stale result
        Transcriber.Options o = new Transcriber.Options(); o.model = MODEL; o.language = "en";
        CaptionService.start(context, ProjectStore.snapshot(p), o);
        long deadline = System.currentTimeMillis() + 180_000;
        CaptionService.Taken taken = null;
        while (System.currentTimeMillis() < deadline && (taken = CaptionService.takeResult(context, p.id)) == null) Thread.sleep(500);
        String status = context.getSharedPreferences(CaptionService.PREFS, 0).getString("status", "");
        assertNotNull("Service finished: " + status, taken);
        assertFalse(taken.summary, taken.cues.isEmpty());
        assertTrue("Summary names the result: " + taken.summary, taken.summary.contains("captions") && taken.summary.contains("Tiny"));
        for (int i = 0; i < 40 && CaptionService.isRunning(); i++) Thread.sleep(100);
        assertFalse("Service stops when done", CaptionService.isRunning());
    }
}

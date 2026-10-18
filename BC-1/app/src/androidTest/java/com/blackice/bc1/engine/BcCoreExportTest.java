package com.blackice.bc1.engine;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.core.ExportPlanner;
import com.blackice.bc1.core.FrameClock;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.MediaImporter;
import com.blackice.bc1.engine.bc.BcExporter;
import com.blackice.bc1.engine.bc.EncoderCaps;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** BC Core end-to-end: real decoders, GLES compositor, AAC/H.264 encoders and muxer on the emulator. */
@RunWith(AndroidJUnit4.class)
public class BcCoreExportTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();
    private final Context context = inst.getTargetContext();

    private String fixture(String name) throws Exception {
        File file = new File(context.getFilesDir(), name);
        try (InputStream in = inst.getContext().getAssets().open(name); OutputStream out = new FileOutputStream(file)) {
            byte[] bytes = new byte[8192]; int n;
            while ((n = in.read(bytes)) != -1) out.write(bytes, 0, n);
        }
        return Uri.fromFile(file).toString();
    }

    private EditProject.Clip clip(String name) throws Exception {
        EditProject.Clip c = new MediaImporter(context).importClip(Uri.parse(fixture(name)), false);
        c.name = name; c.inMs = 300; c.outMs = 2300; c.easing = 0;
        return c;
    }

    /** red → blue with a 500 ms dissolve, a title and looping music: 3.5 s. */
    private EditProject acceptance() throws Exception {
        EditProject p = new EditProject(); p.name = "BC Core acceptance";
        EditProject.Clip red = clip("red.mp4"), blue = clip("blue.mp4");
        red.transition = 0; red.transitionMs = 500;
        p.clips.add(red); p.clips.add(blue);
        EditProject.Overlay title = new EditProject.Overlay(); title.text = "BC CORE"; title.startMs = 250; title.endMs = 1000; title.y = -.8f; p.overlays.add(title);
        EditProject.Music music = new EditProject.Music(); music.uri = fixture("music.wav"); music.name = "music"; music.sourceDurationMs = 1000; music.outMs = 1000; music.startMs = 500; p.music = music;
        return p;
    }

    private File export(EditProject p, int shortEdge, AtomicInteger progress) throws Exception {
        File out = new File(context.getCacheDir(), "bc-core-" + System.nanoTime() + ".mp4");
        new BcExporter(context, p, shortEdge, new AtomicBoolean(), progress::set).export(out);
        assertTrue("MP4 written", out.length() > 1000);
        return out;
    }

    private static int center(MediaMetadataRetriever r, long ms) {
        Bitmap frame = r.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
        assertNotNull("Frame at " + ms, frame);
        int color = frame.getPixel(frame.getWidth() / 2, frame.getHeight() / 2);
        frame.recycle();
        return color;
    }

    private static int[] trackSamples(File file) throws Exception {
        MediaExtractor e = new MediaExtractor();
        try {
            e.setDataSource(file.getPath());
            int video = 0, audio = 0;
            for (int t = 0; t < e.getTrackCount(); t++) {
                String mime = e.getTrackFormat(t).getString(MediaFormat.KEY_MIME);
                e.selectTrack(t);
                int count = 0;
                while (e.getSampleTime() >= 0) { count++; e.advance(); }
                e.unselectTrack(t); e.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
                if ("video/avc".equals(mime)) video = count; else if ("audio/mp4a-latm".equals(mime)) audio = count;
            }
            return new int[]{video, audio};
        } finally { e.release(); }
    }

    @Test public void acceptanceExportIsFrameAccurateWithSoundAndTransition() throws Exception {
        EditProject p = acceptance();
        AtomicInteger progress = new AtomicInteger(-1);
        File out = export(p, 1080, progress);
        try {
            assertEquals(100, progress.get());
            try (MediaMetadataRetriever r = new MediaMetadataRetriever()) {
                r.setDataSource(out.getPath());
                assertEquals("1920", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                assertEquals("1080", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                assertEquals("yes", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
                long duration = Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                assertTrue("Duration " + duration, Math.abs(duration - 3500) < 150);
                int first = center(r, 1200), middle = center(r, 1750), last = center(r, 3000);
                assertTrue("Red first: " + Integer.toHexString(first), Color.red(first) > 200 && Color.blue(first) < 40);
                assertTrue("Dissolve blends: " + Integer.toHexString(middle), Color.red(middle) > 60 && Color.blue(middle) > 60);
                assertTrue("Blue last: " + Integer.toHexString(last), Color.blue(last) > 200 && Color.red(last) < 40);
            }
            int[] samples = trackSamples(out);
            assertEquals("One encoded picture per timeline frame", FrameClock.frameCount(3500, 30), samples[0]);
            assertTrue("AAC access units: " + samples[1], samples[1] > 150);
            File qa = new File(context.getFilesDir(), "qa"); qa.mkdirs();
            java.nio.file.Files.copy(out.toPath(), new File(qa, "bc-core-1080p.mp4").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { out.delete(); }
    }

    @Test public void layersPicturesSpeedAndPortrait() throws Exception {
        EditProject p = new EditProject(); p.name = "BC Core layers"; p.portrait = true; p.frameRate = 25;
        EditProject.Clip base = clip("red.mp4"); base.speed = 2f; // 1 s on the timeline
        p.clips.add(base);
        // A white picture on layer 1, scaled down, over the first half second.
        Bitmap white = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888); white.eraseColor(Color.WHITE);
        File png = new File(context.getFilesDir(), "white.png");
        try (OutputStream o = new FileOutputStream(png)) { white.compress(Bitmap.CompressFormat.PNG, 100, o); }
        white.recycle();
        EditProject.Clip picture = new MediaImporter(context).importClip(Uri.fromFile(png), false);
        assertTrue(picture.image);
        picture.name = "white"; picture.track = 1; picture.startMs = 0; picture.inMs = 0; picture.outMs = 500; picture.sourceDurationMs = 500; picture.scale = .5f;
        p.clips.add(picture);
        File out = export(p, 360, new AtomicInteger());
        try (MediaMetadataRetriever r = new MediaMetadataRetriever()) {
            r.setDataSource(out.getPath());
            assertEquals("360", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            assertEquals("640", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            int covered = center(r, 200), plain = center(r, 800);
            assertTrue("Picture layer on top: " + Integer.toHexString(covered), Color.red(covered) > 200 && Color.green(covered) > 200 && Color.blue(covered) > 200);
            assertTrue("Red base after the picture: " + Integer.toHexString(plain), Color.red(plain) > 200 && Color.green(plain) < 60);
            assertEquals(FrameClock.frameCount(1000, 25), trackSamples(out)[0]);
        } finally { out.delete(); }
    }

    @Test public void highFrameRateSourceSkipsSupersededFrames() throws Exception {
        EditProject p = new EditProject(); p.name = "BC Core 60 fps"; p.frameRate = 60;
        EditProject.Clip fast = new MediaImporter(context).importClip(Uri.parse(fixture("fast120.mp4")), false);
        assertTrue("Fixture is two seconds: " + fast.sourceDurationMs, fast.sourceDurationMs >= 1990);
        fast.name = "fast120"; fast.inMs = 0; fast.outMs = 2000; fast.sourceDurationMs = Math.max(2000, fast.sourceDurationMs);
        p.clips.add(fast);
        File out = new File(context.getCacheDir(), "bc-core-60-" + System.nanoTime() + ".mp4");
        BcExporter exporter = new BcExporter(context, p, 360, new AtomicBoolean(), percent -> { });
        exporter.export(out);
        try {
            int frames = FrameClock.frameCount(2000, 60);
            assertEquals("One picture per 60 fps frame", frames, trackSamples(out)[0]);
            // 240 source frames for 120 output frames: about half are superseded and never reach the GPU.
            assertTrue("Shown " + exporter.shownFrames(), exporter.shownFrames() <= frames + 2);
            assertTrue("Skipped " + exporter.skippedFrames(), exporter.skippedFrames() >= 100);
            try (MediaMetadataRetriever r = new MediaMetadataRetriever()) {
                r.setDataSource(out.getPath());
                int early = center(r, 600), late = center(r, 1400);
                assertTrue("Red half: " + Integer.toHexString(early), Color.red(early) > 200 && Color.blue(early) < 40);
                assertTrue("Blue half: " + Integer.toHexString(late), Color.blue(late) > 200 && Color.red(late) < 40);
            }
            File qa = new File(context.getFilesDir(), "qa"); qa.mkdirs();
            java.nio.file.Files.copy(out.toPath(), new File(qa, "bc-core-60fps.mp4").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { out.delete(); }
    }

    @Test public void planStepsDownOnlyWhenTheEncoderRefuses() {
        ExportPlanner.Plan plan = EncoderCaps.plan(2160, 120, false);
        android.util.Log.i("BC1Plan", "2160p120 request on this device → " + plan.describe() + " @ " + plan.bitrate / 1_000_000 + " Mb/s");
        if (EncoderCaps.supports(3840, 2160, 120)) assertFalse(plan.reduced);
        if (plan.reduced) assertTrue("Chosen preset is encodable", EncoderCaps.supports(plan.width, plan.height, plan.frameRate));
        ExportPlanner.Plan standard = EncoderCaps.plan(1080, 30, false);
        assertEquals("1080p30 stays the default on every test device", "1080p30", standard.label());
        assertEquals(8_000_000, standard.bitrate);
    }

    @Test public void cancellationStopsAndRemovesOutput() throws Exception {
        EditProject p = acceptance();
        AtomicBoolean cancel = new AtomicBoolean();
        File out = new File(context.getCacheDir(), "bc-core-cancel.mp4");
        try {
            new BcExporter(context, p, 1080, cancel, percent -> { if (percent > 30) cancel.set(true); }).export(out);
            fail("Export should have been cancelled");
        } catch (CancellationException expected) {
            assertFalse("Partial output removed", out.exists());
        }
    }
}

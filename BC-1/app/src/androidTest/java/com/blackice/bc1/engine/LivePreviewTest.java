package com.blackice.bc1.engine;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.MediaImporter;
import com.blackice.bc1.editor.ProjectStore;
import com.blackice.bc1.engine.bc.LivePreview;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** ICE Lite live preview: frames at the playhead, parameter changes without decoding, scrubbing back and forth. */
@RunWith(AndroidJUnit4.class)
public class LivePreviewTest {
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

    /** red.mp4 then blue.mp4, two seconds each, previewed at 360p (640×360). */
    private EditProject project() throws Exception {
        EditProject p = new EditProject(); p.name = "Live preview"; p.previewHeight = 360;
        for (String name : new String[]{"red.mp4", "blue.mp4"}) {
            EditProject.Clip c = new MediaImporter(context).importClip(Uri.parse(fixture(name)), false);
            c.name = name; c.inMs = 300; c.outMs = 2300; p.clips.add(c);
        }
        return p;
    }

    private static final class Reader implements AutoCloseable {
        final ImageReader reader; final HandlerThread thread = new HandlerThread("live-test-reader");
        final AtomicReference<int[]> last = new AtomicReference<>();
        Reader(int w, int h) {
            thread.start();
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 3);
            reader.setOnImageAvailableListener(r -> {
                try (Image image = r.acquireLatestImage()) {
                    if (image == null) return;
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer b = plane.getBuffer();
                    int x = image.getWidth() / 2, y = image.getHeight() / 2;
                    int off = y * plane.getRowStride() + x * plane.getPixelStride();
                    last.set(new int[]{b.get(off) & 255, b.get(off + 1) & 255, b.get(off + 2) & 255});
                }
            }, new Handler(thread.getLooper()));
        }
        int[] centre() throws InterruptedException {
            Thread.sleep(250); // let any earlier queued frames drain; the newest one is kept
            for (int i = 0; i < 40 && last.get() == null; i++) Thread.sleep(25);
            int[] c = last.getAndSet(null); assertNotNull("No frame reached the surface", c); return c;
        }
        @Override public void close() { reader.close(); thread.quitSafely(); }
    }

    @Test public void framesFollowEditsAndScrubbing() throws Exception {
        LivePreview live = new LivePreview(context);
        try (Reader reader = new Reader(640, 360)) {
            live.attach(reader.reader.getSurface());
            EditProject p = project();

            assertTrue("First frame: " + live.lastError(), live.renderNow(ProjectStore.snapshot(p), 1000, 10_000));
            int[] red = reader.centre();
            assertTrue("Red clip at 1 s: " + red[0] + "," + red[1] + "," + red[2], red[0] > 200 && red[2] < 50);

            // Picking a new effect compiles its shader once (slow only on software GL), then redraws.
            p.clips.get(0).effect = 25; p.clips.get(0).intensity = .5f; // Invert
            assertTrue(live.renderNow(ProjectStore.snapshot(p), 1000, 30_000));
            // A parameter change redraws from the frame already decoded: no decoder, no compile, so it must be quick.
            p.clips.get(0).intensity = 1f; // full Invert: red becomes cyan
            long start = System.nanoTime();
            assertTrue(live.renderNow(ProjectStore.snapshot(p), 1000, 5_000));
            long redrawMs = (System.nanoTime() - start) / 1_000_000;
            int[] inverted = reader.centre();
            assertTrue("Inverted: " + inverted[0] + "," + inverted[1] + "," + inverted[2], inverted[0] < 60 && inverted[1] > 200 && inverted[2] > 200);
            android.util.Log.i("BC1Live", "Parameter redraw " + redrawMs + " ms");
            assertTrue("Parameter redraw took " + redrawMs + " ms", redrawMs < 1500);

            // Slider-style burst: only the newest request matters, and it must win.
            for (int i = 0; i <= 20; i++) { p.clips.get(0).intensity = i / 20f; live.request(ProjectStore.snapshot(p), 1000); }
            p.clips.get(0).intensity = 0f;
            assertTrue(live.renderNow(ProjectStore.snapshot(p), 1000, 5_000));
            int[] back = reader.centre();
            assertTrue("Latest value wins (intensity 0 = no change): " + back[0] + "," + back[2], back[0] > 200 && back[2] < 50);

            // Scrub forward into the blue clip, then back into the red one (decoders reopen backwards).
            assertTrue(live.renderNow(ProjectStore.snapshot(p), 3000, 10_000));
            int[] blue = reader.centre();
            assertTrue("Blue clip at 3 s: " + blue[0] + "," + blue[2], blue[2] > 200 && blue[0] < 50);
            assertTrue(live.renderNow(ProjectStore.snapshot(p), 500, 10_000));
            int[] again = reader.centre();
            assertTrue("Back to red at 0.5 s: " + again[0] + "," + again[2], again[0] > 200 && again[2] < 50);
            assertNull(live.lastError());
        } finally {
            live.release();
        }
    }

    /** The editor asks for a frame before its view's surface exists; the frame must appear once it does. */
    @Test public void frameRequestedBeforeTheSurfaceIsDrawnWhenItArrives() throws Exception {
        LivePreview live = new LivePreview(context);
        try (Reader reader = new Reader(640, 360)) {
            EditProject p = project();
            java.util.concurrent.LinkedBlockingQueue<Long> frames = new java.util.concurrent.LinkedBlockingQueue<>();
            live.setFrameListener((t, ms) -> frames.add(t));
            for (int i = 0; i < 5; i++) live.request(ProjectStore.snapshot(p), 1000 + i); // several, as a scrub would
            Thread.sleep(300);
            assertTrue("Nothing renders without a surface", frames.isEmpty());
            live.attach(reader.reader.getSurface());
            assertEquals("The newest request is drawn once the surface arrives", Long.valueOf(1004), frames.poll(15, java.util.concurrent.TimeUnit.SECONDS));
            int[] red = reader.centre();
            assertTrue("Red clip after attach: " + red[0] + "," + red[1] + "," + red[2], red[0] > 200 && red[2] < 50);
            live.request(ProjectStore.snapshot(p), 3000); // and later requests still render
            assertEquals(Long.valueOf(3000), frames.poll(15, java.util.concurrent.TimeUnit.SECONDS));
            int[] blue = reader.centre();
            assertTrue("Blue clip at 3 s: " + blue[0] + "," + blue[2], blue[2] > 200 && blue[0] < 50);
            assertNull(live.lastError());
        } finally {
            live.release();
        }
    }

    /** Playback renders frames on the clock, follows edits made while playing, and ends at the stop time. */
    @Test public void playbackRendersFramesAndEndsOnTime() throws Exception {
        LivePreview live = new LivePreview(context);
        try (Reader reader = new Reader(640, 360)) {
            live.attach(reader.reader.getSurface());
            EditProject p = project(); // red 0-2 s, blue 2-4 s
            java.util.concurrent.CountDownLatch ended = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicLong endAt = new java.util.concurrent.atomic.AtomicLong(-1);
            long start = System.nanoTime();
            live.play(ProjectStore.snapshot(p), 1000, 3000, at -> { endAt.set(at); ended.countDown(); });
            Thread.sleep(400);
            assertTrue(live.isPlaying());
            // An edit while playing applies to the frames that follow: invert the blue clip.
            p.clips.get(1).effect = 25; p.clips.get(1).intensity = 1f;
            live.request(ProjectStore.snapshot(p), 0);
            assertTrue("Playback ends", ended.await(15, java.util.concurrent.TimeUnit.SECONDS));
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            assertEquals(3000, endAt.get());
            assertFalse(live.isPlaying());
            long[] stats = live.playbackStats();
            android.util.Log.i("BC1Live", "Played 2 s in " + tookMs + " ms: " + stats[0] + " frames drawn, " + stats[1] + " skipped");
            assertTrue("About real time: " + tookMs + " ms", tookMs >= 1800 && tookMs < 6000);
            assertTrue("Frames drawn: " + stats[0], stats[0] >= 8);
            int[] last = reader.centre();
            assertTrue("Last frame is the inverted blue clip (yellow): " + last[0] + "," + last[1] + "," + last[2], last[0] > 200 && last[1] > 200 && last[2] < 60);
            assertEquals(3000, live.positionMs());
            assertNull(live.lastError());
            // Still frames work again after playback.
            assertTrue(live.renderNow(ProjectStore.snapshot(p), 500, 10_000));
            int[] red = reader.centre();
            assertTrue("Red after playback: " + red[0] + "," + red[2], red[0] > 200 && red[2] < 50);
        } finally {
            live.release();
        }
    }
}

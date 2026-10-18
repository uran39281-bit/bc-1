package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.opengl.GLES30;
import android.os.Build;
import android.view.Surface;
import com.blackice.bc1.core.ExportPlanner;
import com.blackice.bc1.core.FrameClock;
import com.blackice.bc1.core.VideoCodec;
import com.blackice.bc1.editor.EditProject;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Measures BC Core on this device with a synthetic project: {@code layers} video layers of 1080p at
 * {@code fps}, composited and encoded exactly like a real export. Generates its own source clip, so it
 * needs no media and leaves nothing behind.
 */
public final class EngineBenchmark {
    public static final class Result {
        public final int layers, fps, frames, passes, budget;
        public final long durationMs, elapsedMs;
        public final boolean directDrawing;
        Result(int layers, int fps, int frames, long durationMs, long elapsedMs, int passes, int budget, boolean direct) {
            this.layers = layers; this.fps = fps; this.frames = frames; this.durationMs = durationMs; this.elapsedMs = elapsedMs;
            this.passes = passes; this.budget = budget; directDrawing = direct;
        }
        /** Output frames rendered per second of wall time. */
        public double exportFps() { return frames * 1000.0 / Math.max(1, elapsedMs); }
        /** Wall time to export a project of the same shape lasting {@code minutes}. */
        public double minutesFor(double minutes) { return minutes * 60 * fps / exportFps() / 60; }
        public String describe() {
            return String.format(Locale.US,
                    "%s %s • Android %s\n%d layers × 1080p%d, %.1f s timeline\nExport speed: %.1f frames/s (%.2f× real time)\n" +
                    "Estimated 15-minute export: %.0f min\nRender passes: %d • decoder budget %d • direct layer drawing: %s",
                    Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, layers, fps, durationMs / 1000.0,
                    exportFps(), exportFps() / fps, minutesFor(15), passes, budget, directDrawing ? "yes" : "no (copy per layer)");
        }
    }

    private EngineBenchmark() {}

    public static Result run(Context context, int layers, int fps, long durationMs, AtomicBoolean cancelled, BcExporter.Listener progress) throws Exception {
        if (layers < 1 || layers > EditProject.VISUAL_TRACKS) throw new IllegalArgumentException("1 to " + EditProject.VISUAL_TRACKS + " layers");
        File dir = context.getCacheDir();
        File source = new File(dir, "bc1-benchmark-source.mp4"), out = new File(dir, "bc1-benchmark-out.mp4");
        try {
            generate(source, 1920, 1080, fps, durationMs + 200);
            EditProject p = new EditProject(); p.name = "BC Core benchmark"; p.frameRate = fps;
            for (int t = 0; t < layers; t++) {
                EditProject.Clip c = new EditProject.Clip();
                c.uri = Uri.fromFile(source).toString(); c.name = "layer " + t; c.track = t;
                c.sourceDurationMs = durationMs + 200; c.inMs = 0; c.outMs = durationMs; c.volume = 0;
                c.scale = 1f - t * 0.1f; c.x = (t % 2 == 0 ? -1 : 1) * t * 0.04f; c.rotation = t * 2f;
                p.clips.add(c);
            }
            BcExporter exporter = new BcExporter(context, p, ExportPlanner.fixed(1080, fps, VideoCodec.H264, false), cancelled, progress);
            long start = System.nanoTime();
            exporter.export(out);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return new Result(layers, fps, FrameClock.frameCount(durationMs, fps), durationMs, elapsed, exporter.passes(), exporter.budget(), exporter.directDrawing());
        } finally {
            source.delete(); out.delete();
        }
    }

    /** Writes a moving test pattern (colour sweep plus travelling bars) as H.264, video only. */
    static void generate(File file, int width, int height, int fps, long durationMs) throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, ExportPlanner.videoBitrate(width, height, fps));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        Surface input = null; EglCore egl = null; MediaMuxer muxer = null;
        boolean ok = false;
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            input = encoder.createInputSurface();
            encoder.start();
            egl = new EglCore(input);
            muxer = new MediaMuxer(file.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int track = -1; boolean started = false;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int frames = FrameClock.frameCount(durationMs, fps);
            for (int n = 0; n <= frames; n++) {
                boolean end = n == frames;
                if (!end) {
                    float t = n / (float) fps;
                    GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
                    GLES30.glClearColor(.5f + .5f * (float) Math.sin(t * 2), .5f + .5f * (float) Math.sin(t * 2 + 2), .5f + .5f * (float) Math.sin(t * 2 + 4), 1);
                    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                    GLES30.glEnable(GLES30.GL_SCISSOR_TEST);
                    for (int bar = 0; bar < 6; bar++) {
                        int x = (int) ((n * 7 + bar * width / 6) % width);
                        GLES30.glScissor(x, 0, width / 24, height);
                        GLES30.glClearColor(bar % 2, 1, 1 - bar % 2, 1);
                        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                    }
                    GLES30.glDisable(GLES30.GL_SCISSOR_TEST);
                    egl.setPresentationTime(FrameClock.frameTimeUs(n, fps) * 1000);
                    egl.swap();
                } else encoder.signalEndOfInputStream();
                long deadline = System.currentTimeMillis() + 10_000;
                while (true) {
                    int index = encoder.dequeueOutputBuffer(info, end ? 10_000 : 0);
                    if (index == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end) break; if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Benchmark encoder stalled"); continue; }
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(encoder.getOutputFormat()); muxer.start(); started = true; continue; }
                    if (index < 0) continue;
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && started) {
                        ByteBuffer data = encoder.getOutputBuffer(index);
                        data.position(info.offset); data.limit(info.offset + info.size);
                        muxer.writeSampleData(track, data, info);
                    }
                    encoder.releaseOutputBuffer(index, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                }
            }
            muxer.stop();
            ok = true;
        } finally {
            if (egl != null) egl.close();
            if (input != null) input.release();
            try { encoder.stop(); } catch (Exception ignored) { }
            encoder.release();
            if (muxer != null) try { muxer.release(); } catch (Exception ignored) { }
            if (!ok) file.delete();
        }
    }
}

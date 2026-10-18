package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.view.Surface;
import java.util.ArrayList;
import java.util.List;

/**
 * Frame-accurate forward decoder for one timeline placement. Decodes into a SurfaceTexture and keeps
 * the latest frame whose timestamp is at or before the requested source time, then fits it into a
 * frame-sized texture. Output buffers ahead of the request are held, never dropped.
 */
final class VideoSource implements FrameSource {
    private static final long WAIT_MS = 5_000;
    private static final long PREROLL_DROP_US = 250_000;

    private final MediaExtractor extractor = new MediaExtractor();
    private final MediaCodec codec;
    private final SurfaceTexture surfaceTexture;
    private final Surface surface;
    private final int oesTexture;
    private final GlKit.Target target;
    private final Compositor compositor;
    private final int rotation;
    private final double displayAspect;
    private final long endUs;
    private final float[] stMatrix = new float[16];
    private final Object frameLock = new Object();
    private boolean frameAvailable;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private int heldIndex = -1;
    private long heldPts;
    private boolean heldEos, inputEos, outputEos, fitted;
    private int rendered, skipped;
    private long latchedUs = -1;

    VideoSource(Context context, String uri, long inMs, long outMs, Compositor compositor, Handler callbacks) throws Exception {
        this.compositor = compositor;
        extractor.setDataSource(context, Uri.parse(uri), null);
        int track = -1; MediaFormat format = null;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("video/")) { track = i; format = f; break; }
        }
        if (track < 0) { extractor.release(); throw new IllegalArgumentException("No video track in a visual clip."); }
        extractor.selectTrack(track);
        rotation = format.containsKey("rotation-degrees") ? ((format.getInteger("rotation-degrees") % 360) + 360) % 360 : 0;
        int w = format.getInteger(MediaFormat.KEY_WIDTH), h = format.getInteger(MediaFormat.KEY_HEIGHT);
        displayAspect = rotation == 90 || rotation == 270 ? h / (double) w : w / (double) h;
        endUs = outMs * 1000;

        oesTexture = GlKit.textureExternal();
        surfaceTexture = new SurfaceTexture(oesTexture);
        surfaceTexture.setOnFrameAvailableListener(st -> { synchronized (frameLock) { frameAvailable = true; frameLock.notifyAll(); } }, callbacks);
        surface = new Surface(surfaceTexture);
        target = new GlKit.Target(compositor.width, compositor.height);

        MediaCodec created = null; Exception last = null;
        for (String name : decoders(format.getString(MediaFormat.KEY_MIME))) {
            try {
                created = MediaCodec.createByCodecName(name);
                created.configure(format, surface, null, 0);
                created.start();
                break;
            } catch (Exception e) {
                last = e;
                if (created != null) created.release();
                created = null;
            }
        }
        if (created == null) { release(); throw new IllegalStateException("No decoder accepted this video", last); }
        codec = created;
        extractor.seekTo(inMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
    }

    /** Hardware first on devices; emulators skip the goldfish host decoder that lost buffers in CI (same rule as Media3 path). */
    static List<String> decoders(String mime) {
        boolean emulator = Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish");
        List<String> preferred = new ArrayList<>(), fallback = new ArrayList<>();
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) if (type.equalsIgnoreCase(mime)) {
                (emulator && info.getName().contains("goldfish") ? fallback : preferred).add(info.getName());
                break;
            }
        }
        preferred.addAll(fallback);
        if (preferred.isEmpty()) throw new IllegalStateException("No decoder for " + mime);
        return preferred;
    }

    /**
     * Shows the latest decoded frame at or before {@code sourceTimeMs}. One output buffer of look-ahead
     * decides whether a frame is superseded; superseded frames are released without rendering, so a
     * 120 fps source exported at 30 fps sends one frame in four to the GPU instead of all four.
     */
    @Override public int textureAt(long sourceTimeMs) throws Exception {
        long targetUs = sourceTimeMs * 1000;
        boolean changed = false;
        while (fetch()) {
            if (heldPts > targetUs && latchedUs >= 0) break;          // belongs to a later output frame
            if (heldPts < targetUs - PREROLL_DROP_US && !heldEos) { // far before the in-point after a seek
                codec.releaseOutputBuffer(heldIndex, false); heldIndex = -1; skipped++; continue;
            }
            int candidate = heldIndex; long candidatePts = heldPts; boolean candidateEos = heldEos;
            heldIndex = -1;
            if (!candidateEos && candidatePts <= targetUs && fetch() && heldPts <= targetUs) {
                codec.releaseOutputBuffer(candidate, false); skipped++; // a later frame also fits
                continue;
            }
            codec.releaseOutputBuffer(candidate, true);
            awaitFrame();
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(stMatrix);
            latchedUs = candidatePts; changed = true; rendered++;
            break;
        }
        if (changed || !fitted) {
            if (latchedUs >= 0) compositor.fitExternal(oesTexture, stMatrix, rotation, displayAspect, target);
            else { target.bind(); android.opengl.GLES30.glClearColor(0, 0, 0, 0); android.opengl.GLES30.glClear(android.opengl.GLES30.GL_COLOR_BUFFER_BIT); }
            fitted = true;
        }
        return target.texture;
    }

    /** Ensures one decoded output buffer is held. False once the stream has no more frames. */
    private boolean fetch() {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (heldIndex < 0) {
            if (outputEos) return false;
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Video decoder stalled");
            feed();
            int index = codec.dequeueOutputBuffer(info, 2_000);
            if (index < 0) continue; // try again, or output format / buffers changed
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            if (eos) outputEos = true;
            if (eos && info.size == 0) { codec.releaseOutputBuffer(index, false); return false; }
            heldIndex = index; heldPts = info.presentationTimeUs; heldEos = eos;
        }
        return true;
    }

    /** Frames sent to the GPU / decoded but never shown (for tests and diagnostics). */
    int renderedFrames() { return rendered; }
    int skippedFrames() { return skipped; }

    private void feed() {
        while (!inputEos) {
            int index = codec.dequeueInputBuffer(0);
            if (index < 0) return;
            long time = extractor.getSampleTime();
            int size = time < 0 || time > endUs + 500_000 ? -1 : extractor.readSampleData(codec.getInputBuffer(index), 0);
            if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEos = true; }
            else { codec.queueInputBuffer(index, 0, size, time, 0); extractor.advance(); }
        }
    }

    private void awaitFrame() throws InterruptedException {
        synchronized (frameLock) {
            long until = System.currentTimeMillis() + WAIT_MS;
            while (!frameAvailable) {
                long left = until - System.currentTimeMillis();
                if (left <= 0) throw new IllegalStateException("Decoded frame never reached the GL texture");
                frameLock.wait(left);
            }
            frameAvailable = false;
        }
    }

    @Override public void release() {
        if (codec != null) { try { codec.stop(); } catch (Exception ignored) { } codec.release(); }
        extractor.release();
        surface.release(); surfaceTexture.release();
        android.opengl.GLES30.glDeleteTextures(1, new int[]{oesTexture}, 0);
        target.release();
    }
}

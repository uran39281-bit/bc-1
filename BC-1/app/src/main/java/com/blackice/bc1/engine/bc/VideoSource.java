package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.opengl.GLES30;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Frame-accurate forward decoder for one timeline placement.
 *
 * The codec runs in asynchronous mode on its own thread: input is fed from the extractor as soon as the
 * codec asks for it, and decoded frames queue up until the GL thread needs them. Every layer's decoder
 * therefore works in parallel with the others and with compositing; the codec's own output-buffer pool
 * provides back-pressure. The GL thread shows the latest frame at or before the requested source time,
 * looking one frame ahead so superseded frames are released without ever reaching the GPU.
 */
final class VideoSource implements FrameSource {
    /** Raised when the device has no decoder instance left; the exporter re-plans with fewer decoders. */
    static final class DecoderUnavailable extends Exception {
        DecoderUnavailable(Throwable cause) { super("No video decoder instance available", cause); }
    }

    private static final long WAIT_MS = 10_000;
    private static final long PREROLL_DROP_US = 250_000;

    private static final class Out {
        final int index; final long ptsUs; final boolean eos, empty;
        Out(int index, long ptsUs, boolean eos, boolean empty) { this.index = index; this.ptsUs = ptsUs; this.eos = eos; this.empty = empty; }
    }

    private final MediaExtractor extractor = new MediaExtractor();
    private final HandlerThread thread;
    private final LinkedBlockingDeque<Out> outputs = new LinkedBlockingDeque<>();
    private volatile Throwable failure;
    private final Object lock = new Object();
    private boolean released, inputEos;
    private MediaCodec codec;
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
    private Out held;
    private boolean outputEos, fitted;
    private long latchedUs = -1, pendingUs = -1;
    private int rendered, skipped;

    VideoSource(Context context, String uri, long inMs, long outMs, Compositor compositor) throws Exception {
        this(context, uri, inMs, outMs, compositor, !compositor.drawsExternal());
    }

    /** @param copy keep a fitted frame-sized copy (needed for full-frame use or without direct drawing) */
    VideoSource(Context context, String uri, long inMs, long outMs, Compositor compositor, boolean copy) throws Exception {
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
        extractor.seekTo(inMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        rotation = format.containsKey("rotation-degrees") ? ((format.getInteger("rotation-degrees") % 360) + 360) % 360 : 0;
        int w = format.getInteger(MediaFormat.KEY_WIDTH), h = format.getInteger(MediaFormat.KEY_HEIGHT);
        displayAspect = rotation == 90 || rotation == 270 ? h / (double) w : w / (double) h;
        endUs = outMs * 1000;

        thread = new HandlerThread("bc1-decoder");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        oesTexture = GlKit.textureExternal();
        surfaceTexture = new SurfaceTexture(oesTexture);
        surfaceTexture.setOnFrameAvailableListener(st -> { synchronized (frameLock) { frameAvailable = true; frameLock.notifyAll(); } }, handler);
        surface = new Surface(surfaceTexture);
        target = copy ? new GlKit.Target(compositor.width, compositor.height) : null;

        Exception last = null; boolean resources = false;
        for (String name : decoders(format.getString(MediaFormat.KEY_MIME))) {
            MediaCodec created = null;
            try {
                created = MediaCodec.createByCodecName(name);
                created.setCallback(new Callbacks(), handler);
                created.configure(format, surface, null, 0);
                codec = created;
                created.start();
                break;
            } catch (Exception e) {
                last = e; codec = null;
                if (e instanceof MediaCodec.CodecException && ((MediaCodec.CodecException) e).getErrorCode() == MediaCodec.CodecException.ERROR_INSUFFICIENT_RESOURCE) resources = true;
                if (created != null) created.release();
            }
        }
        if (codec == null) {
            release();
            if (resources) throw new DecoderUnavailable(last);
            throw new IllegalStateException("No decoder accepted this video", last);
        }
    }

    private final class Callbacks extends MediaCodec.Callback {
        @Override public void onInputBufferAvailable(MediaCodec mc, int index) {
            synchronized (lock) {
                if (released || inputEos) return;
                try {
                    long time = extractor.getSampleTime();
                    int size = time < 0 || time > endUs + 500_000 ? -1 : extractor.readSampleData(mc.getInputBuffer(index), 0);
                    if (size < 0) { mc.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEos = true; }
                    else { mc.queueInputBuffer(index, 0, size, time, 0); extractor.advance(); }
                } catch (RuntimeException e) { fail(e); }
            }
        }
        @Override public void onOutputBufferAvailable(MediaCodec mc, int index, MediaCodec.BufferInfo info) {
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            outputs.add(new Out(index, info.presentationTimeUs, eos, info.size == 0));
        }
        @Override public void onError(MediaCodec mc, MediaCodec.CodecException e) { fail(e); }
        @Override public void onOutputFormatChanged(MediaCodec mc, MediaFormat format) { }
    }

    private void fail(Throwable t) {
        if (failure == null) failure = t;
        outputs.add(new Out(-1, 0, true, true)); // wake the GL thread
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
     * Picks the latest decoded frame at or before {@code targetUs} and hands it to the SurfaceTexture
     * without waiting. One output buffer of look-ahead decides whether a frame is superseded; superseded
     * frames are released without rendering, so a 120 fps source exported at 30 fps sends one frame in
     * four to the GPU instead of all four.
     */
    @Override public void prepare(long targetUs) throws Exception {
        if (pendingUs >= 0) return;
        long shownUs = latchedUs;
        while (fetch()) {
            if (held.ptsUs > targetUs && shownUs >= 0) break;            // belongs to a later output frame
            if (held.ptsUs < targetUs - PREROLL_DROP_US && !held.eos) { // far before the in-point after a seek
                codec.releaseOutputBuffer(held.index, false); held = null; skipped++; continue;
            }
            Out candidate = held; held = null;
            if (!candidate.eos && candidate.ptsUs <= targetUs && fetch() && held.ptsUs <= targetUs) {
                codec.releaseOutputBuffer(candidate.index, false); skipped++; // a later frame also fits
                continue;
            }
            codec.releaseOutputBuffer(candidate.index, true);
            pendingUs = candidate.ptsUs; rendered++;
            break;
        }
    }

    /** Waits for the prepared frame to reach the texture (all layers' frames arrive in parallel). */
    @Override public void latch() throws Exception {
        boolean changed = false;
        if (pendingUs >= 0) {
            awaitFrame();
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(stMatrix);
            latchedUs = pendingUs; pendingUs = -1; changed = true;
        }
        if (target != null && (changed || !fitted)) {
            if (latchedUs >= 0) compositor.fitExternal(oesTexture, stMatrix, rotation, displayAspect, target);
            else { target.bind(); GLES30.glClearColor(0, 0, 0, 0); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT); }
            fitted = true;
        }
    }

    /** Direct mode samples the decoder texture in the layer shader; no intermediate copy per frame. */
    @Override public void draw(Compositor compositor, com.blackice.bc1.core.RenderPlan.LayerDraw layer) {
        if (target != null) compositor.drawLayer(layer, target.texture);
        else if (latchedUs >= 0) compositor.drawLayerExternal(layer, oesTexture, stMatrix, rotation, displayAspect);
    }

    /** Fitted copy for full-frame use; only available when the source was opened with a copy target. */
    @Override public int texture() {
        if (target == null) throw new IllegalStateException("Source opened for direct drawing");
        return target.texture;
    }

    /** Convenience for full-frame sources: prepare, latch and return the fitted texture. */
    int textureAtUs(long targetUs) throws Exception { prepare(targetUs); latch(); return texture(); }

    /** Ensures one decoded output buffer is held. False once the stream has no more frames. */
    private boolean fetch() throws Exception {
        while (held == null) {
            if (outputEos) return false;
            Out next = outputs.poll(WAIT_MS, TimeUnit.MILLISECONDS);
            if (failure != null) throw new IllegalStateException("Video decoder failed", failure);
            if (next == null) throw new IllegalStateException("Video decoder stalled");
            if (next.eos) outputEos = true;
            if (next.empty) { if (next.index >= 0) codec.releaseOutputBuffer(next.index, false); if (next.eos) return false; continue; }
            held = next;
        }
        return true;
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

    /** Frames sent to the GPU / decoded but never shown (for tests and diagnostics). */
    int renderedFrames() { return rendered; }
    int skippedFrames() { return skipped; }

    @Override public void release() {
        synchronized (lock) { released = true; }
        if (codec != null) { try { codec.stop(); } catch (Exception ignored) { } codec.release(); codec = null; }
        thread.quitSafely();
        try { thread.join(2_000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        extractor.release();
        surface.release(); surfaceTexture.release();
        GLES30.glDeleteTextures(1, new int[]{oesTexture}, 0);
        if (target != null) target.release();
    }
}

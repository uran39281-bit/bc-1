package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.view.Surface;
import com.blackice.bc1.core.AudioMixer;
import com.blackice.bc1.core.AudioPlan;
import com.blackice.bc1.core.ExportPlanner;
import com.blackice.bc1.core.FrameClock;
import com.blackice.bc1.core.OutputSpec;
import com.blackice.bc1.core.RenderPlan;
import com.blackice.bc1.core.Staging;
import com.blackice.bc1.core.VideoCodec;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.OverlayPainter;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BC Core export: BC-1's own frame scheduler, GLES compositor, audio mixer, encoders and muxer.
 * Runs synchronously on the calling thread (which owns the GL context); call from a worker thread.
 *
 * Pipeline: AudioPlan → mixer → AAC (temp file); RenderPlan → parallel decoders → compositor →
 * H.264 / HEVC → MP4. When the composition needs more simultaneous decoders than the device offers,
 * the layers are rendered in passes: each pass draws its layers over the previous pass's video.
 */
public final class BcExporter {
    public interface Listener { void onProgress(int percent); }

    private static final int AUDIO_SHARE = 10; // percent of progress spent on the audio pass

    private final Context context;
    private final EditProject project;
    private final ExportPlanner.Plan plan;
    private final OutputSpec spec;
    private final AtomicBoolean cancelled;
    private final Listener listener;
    private int budget = -1;
    private int passesUsed, budgetUsed, shownFrames, skippedFrames;
    private boolean directUsed;

    /** Export exactly as planned (codec, size, frame rate). The project's frame rate is set to the plan's. */
    public BcExporter(Context context, EditProject project, ExportPlanner.Plan plan, AtomicBoolean cancelled, Listener listener) {
        this.context = context.getApplicationContext();
        this.project = project;
        this.plan = plan;
        project.frameRate = plan.frameRate;
        project.validate();
        if (project.clips.isEmpty()) throw new IllegalArgumentException("Import a video or image first.");
        this.spec = OutputSpec.of(project, plan.shortEdge);
        this.cancelled = cancelled;
        this.listener = listener;
    }

    /** H.264 at the project frame rate and the given short edge, without a device check. */
    public BcExporter(Context context, EditProject project, int shortEdge, AtomicBoolean cancelled, Listener listener) {
        this(context, project, ExportPlanner.fixed(shortEdge, project.frameRate, VideoCodec.H264, project.portrait), cancelled, listener);
    }

    public OutputSpec spec() { return spec; }
    public ExportPlanner.Plan plan() { return plan; }

    /** Overrides the decoder budget (tests); by default it is estimated from the device. */
    public BcExporter decoderBudget(int budget) { this.budget = budget; return this; }

    /** Results of the last export: render passes, decoder budget used. */
    public int passes() { return passesUsed; }
    public int budget() { return budgetUsed; }
    /** Whether layers were drawn straight from decoder textures (no per-layer copy). */
    public boolean directDrawing() { return directUsed; }
    /** Decoded video frames sent to the GPU, and decoded frames skipped as superseded. */
    public int shownFrames() { return shownFrames; }
    public int skippedFrames() { return skippedFrames; }

    public void export(File output) throws Exception {
        RenderPlan renderPlan = new RenderPlan(project, false);
        AudioPlan audioPlan = AudioPlan.of(project, false);
        File temp = new File(output.getParentFile(), output.getName() + ".aac.tmp");
        AudioEncoder.Encoded audio;
        try (AudioMixer mixer = new AudioMixer(audioPlan, s -> AudioDecoderSource.open(context, s), OutputSpec.AUDIO_RATE)) {
            audio = AudioEncoder.encode(mixer, temp, cancelled::get, p -> listener.onProgress(p * AUDIO_SHARE / 100));
        }
        int limit = budget > 0 ? budget : DecoderBudget.estimate(context, project, spec).budget;
        try (AudioEncoder.Encoded encodedAudio = audio) {
            while (true) {
                try {
                    renderPasses(renderPlan, encodedAudio, output, limit);
                    budgetUsed = limit;
                    return;
                } catch (BudgetExceeded e) {
                    // The device ran out of decoder instances: re-plan with what actually opened.
                    int next = Math.max(DecoderBudget.MIN, Math.min(limit - 1, e.opened));
                    if (next >= limit) throw new IllegalStateException("Not enough video decoders for this project", e.getCause());
                    limit = next;
                    encodedAudio.rewind();
                }
            }
        }
    }

    /** A decoder could not be created after {@code opened} were already open in the current pass. */
    private static final class BudgetExceeded extends Exception {
        final int opened;
        BudgetExceeded(int opened, Throwable cause) { super(cause); this.opened = opened; }
    }

    private void checkCancelled() { if (cancelled.get()) throw new CancellationException("Export cancelled"); }

    private void renderPasses(RenderPlan renderPlan, AudioEncoder.Encoded audio, File output, int limit) throws Exception {
        List<int[]> passes = Staging.split(renderPlan.decoderDemand(), limit);
        passesUsed = passes.size(); shownFrames = 0; skippedFrames = 0;
        File base = null;
        try {
            for (int i = 0; i < passes.size(); i++) {
                boolean last = i == passes.size() - 1;
                File target = last ? output : new File(output.getParentFile(), output.getName() + ".pass" + i + ".mp4");
                int from = AUDIO_SHARE + (100 - AUDIO_SHARE) * i / passes.size();
                int to = AUDIO_SHARE + (100 - AUDIO_SHARE) * (i + 1) / passes.size() - (last ? 1 : 0);
                renderPass(renderPlan, passes.get(i), base, last, last ? audio : null, target, from, to);
                if (base != null) base.delete();
                base = last ? null : target;
            }
            listener.onProgress(100);
        } finally {
            if (base != null) base.delete();
        }
    }

    /**
     * Renders stacking groups [pass[0], pass[1]) over {@code base} (the previous pass, or black).
     * Intermediate passes carry no overlays or audio and use twice the bitrate to limit generation loss.
     */
    private void renderPass(RenderPlan renderPlan, int[] pass, File base, boolean last, AudioEncoder.Encoded audio,
                            File output, int progressFrom, int progressTo) throws Exception {
        VideoCodec codec = plan.codec;
        int bitrate = last ? plan.bitrate : (int) Math.min(200_000_000L, plan.bitrate * 2L);
        MediaFormat format = MediaFormat.createVideoFormat(codec.mime, spec.width, spec.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, spec.frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        MediaCodec encoder = MediaCodec.createEncoderByType(codec.mime);
        Surface input = null;
        EglCore egl = null;
        Compositor compositor = null;
        MediaMuxer muxer = null;
        Map<Integer, FrameSource> sources = new HashMap<>();
        VideoSource baseSource = null;
        OverlayLayer overlays = null;
        boolean succeeded = false;
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            input = encoder.createInputSurface();
            encoder.start();
            egl = new EglCore(input);
            compositor = new Compositor(context, spec.width, spec.height);
            directUsed = compositor.drawsExternal();
            if (last) overlays = new OverlayLayer(context, spec.width, spec.height);
            muxer = new MediaMuxer(output.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Mux mux = new Mux(muxer, audio);
            if (base != null) baseSource = new VideoSource(context, Uri.fromFile(base).toString(), 0, spec.durationMs + 1000, compositor, true);

            int frames = spec.frameCount();
            for (int n = 0; n < frames; n++) {
                checkCancelled();
                long timeUs = FrameClock.frameTimeUs(n, spec.frameRate);
                long timeMs = timeUs / 1000;
                List<RenderPlan.LayerDraw> layers = renderPlan.layersAt(timeMs, pass[0], pass[1]);
                // Phase 1: every source picks its frame and starts delivery, so all decoders work at once.
                FrameSource[] frame = new FrameSource[layers.size()];
                for (int i = 0; i < layers.size(); i++) {
                    RenderPlan.LayerDraw d = layers.get(i);
                    FrameSource source = sources.get(d.placementIndex);
                    if (source == null) {
                        EditProject.Placement p = renderPlan.placements().get(d.placementIndex);
                        try {
                            source = p.clip.image ? new ImageSource(context, p.clip.uri, compositor)
                                    : new VideoSource(context, p.clip.uri, p.clip.inMs, p.clip.outMs, compositor);
                        } catch (VideoSource.DecoderUnavailable e) {
                            int open = baseSource == null ? 0 : 1;
                            for (FrameSource s : sources.values()) if (s instanceof VideoSource) open++;
                            throw new BudgetExceeded(open, e);
                        }
                        sources.put(d.placementIndex, source);
                    }
                    frame[i] = source;
                    source.prepare(d.sourceTimeMs * 1000);
                }
                if (baseSource != null) baseSource.prepare(timeUs + 100); // the intermediate holds this exact frame
                // Phase 2: take the delivered frames.
                for (FrameSource source : frame) source.latch();
                if (baseSource != null) baseSource.latch();
                compositor.beginFrame();
                if (baseSource != null) compositor.drawPlain(baseSource.texture());
                for (int i = 0; i < layers.size(); i++) frame[i].draw(compositor, layers.get(i));
                if (overlays != null) {
                    List<EditProject.Overlay> active = renderPlan.overlaysAt(timeMs);
                    if (!active.isEmpty()) compositor.drawOverlay(overlays.texture(active, timeMs));
                }
                egl.setPresentationTime(timeUs * 1000);
                egl.swap();
                drain(encoder, mux, false);
                // Release decoders whose placement has ended before the next frame.
                long nextMs = FrameClock.frameTimeMs(n + 1, spec.frameRate);
                for (Iterator<Map.Entry<Integer, FrameSource>> it = sources.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Integer, FrameSource> e = it.next();
                    if (renderPlan.placements().get(e.getKey()).endMs <= nextMs) { releaseSource(e.getValue()); it.remove(); }
                }
                listener.onProgress(progressFrom + (int) ((n + 1L) * (progressTo - progressFrom) / frames));
            }
            encoder.signalEndOfInputStream();
            drain(encoder, mux, true);
            mux.finish();
            muxer.stop();
            succeeded = true;
        } finally {
            for (FrameSource s : sources.values()) try { releaseSource(s); } catch (Exception ignored) { }
            if (baseSource != null) try { baseSource.release(); } catch (Exception ignored) { }
            if (overlays != null) overlays.release();
            if (compositor != null) compositor.release();
            if (egl != null) egl.close();
            if (input != null) input.release();
            try { encoder.stop(); } catch (Exception ignored) { }
            encoder.release();
            if (muxer != null) try { muxer.release(); } catch (Exception ignored) { }
            if (!succeeded) output.delete();
        }
    }

    private void releaseSource(FrameSource source) {
        if (source instanceof VideoSource) { shownFrames += ((VideoSource) source).renderedFrames(); skippedFrames += ((VideoSource) source).skippedFrames(); }
        source.release();
    }

    /** Writes encoder output to the muxer, interleaving buffered audio up to each video timestamp. */
    private void drain(MediaCodec encoder, Mux mux, boolean endOfStream) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long deadline = System.currentTimeMillis() + 30_000;
        while (true) {
            int index = encoder.dequeueOutputBuffer(info, endOfStream ? 10_000 : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return;
                if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Video encoder did not finish");
                continue;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { mux.start(encoder.getOutputFormat()); continue; }
            if (index < 0) continue;
            deadline = System.currentTimeMillis() + 30_000;
            boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            if (!config && info.size > 0) {
                if (!mux.started) throw new IllegalStateException("Video sample before the encoder format");
                ByteBuffer data = encoder.getOutputBuffer(index);
                data.position(info.offset); data.limit(info.offset + info.size);
                mux.writeVideo(data, info);
            }
            encoder.releaseOutputBuffer(index, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
        }
    }

    /** Owns track setup and A/V interleaving for one MP4. Audio is optional (intermediate passes). */
    private static final class Mux {
        final MediaMuxer muxer; final AudioEncoder.Encoded audio;
        int videoTrack = -1, audioTrack = -1; boolean started;
        private final MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
        private final ByteBuffer audioBuffer;
        Mux(MediaMuxer muxer, AudioEncoder.Encoded audio) {
            this.muxer = muxer; this.audio = audio;
            long largest = 1;
            if (audio != null) for (long[] s : audio.samples) largest = Math.max(largest, s[2]);
            audioBuffer = ByteBuffer.allocateDirect((int) largest);
        }

        void start(MediaFormat videoFormat) {
            if (started) throw new IllegalStateException("Video format changed twice");
            videoTrack = muxer.addTrack(videoFormat);
            if (audio != null) audioTrack = muxer.addTrack(audio.format);
            muxer.start(); started = true;
        }
        void writeVideo(ByteBuffer data, MediaCodec.BufferInfo info) throws Exception {
            writeAudioUntil(info.presentationTimeUs);
            muxer.writeSampleData(videoTrack, data, info);
        }
        void writeAudioUntil(long timeUs) throws Exception {
            if (audio == null) return;
            while (audio.hasNext() && audio.nextTimeUs() <= timeUs) {
                audio.read(audioBuffer, audioInfo);
                muxer.writeSampleData(audioTrack, audioBuffer, audioInfo);
            }
        }
        void finish() throws Exception {
            if (!started) throw new IllegalStateException("Video encoder produced no output");
            writeAudioUntil(Long.MAX_VALUE);
        }
    }

    /** Overlay bitmap, repainted only when the visible set changes or something is animated. */
    private static final class OverlayLayer {
        private final OverlayPainter painter;
        private final Bitmap bitmap;
        private final int texture;
        private List<EditProject.Overlay> shown;
        OverlayLayer(Context context, int width, int height) {
            painter = new OverlayPainter(context);
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            texture = GlKit.texture2d();
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
        }
        int texture(List<EditProject.Overlay> active, long timeMs) throws Exception {
            boolean animated = false;
            for (EditProject.Overlay o : active) animated |= o.motion.animated();
            if (animated || !active.equals(shown)) {
                bitmap.eraseColor(Color.TRANSPARENT);
                Canvas canvas = new Canvas(bitmap);
                for (EditProject.Overlay o : active) painter.draw(canvas, bitmap.getWidth(), bitmap.getHeight(), o, Math.max(0, timeMs - o.startMs));
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
                GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bitmap);
                shown = active;
            }
            return texture;
        }
        void release() { painter.release(); bitmap.recycle(); GLES30.glDeleteTextures(1, new int[]{texture}, 0); }
    }
}

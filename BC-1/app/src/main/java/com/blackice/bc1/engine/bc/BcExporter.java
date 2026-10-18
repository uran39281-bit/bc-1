package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.os.HandlerThread;
import android.os.Handler;
import android.view.Surface;
import com.blackice.bc1.core.AudioMixer;
import com.blackice.bc1.core.AudioPlan;
import com.blackice.bc1.core.FrameClock;
import com.blackice.bc1.core.OutputSpec;
import com.blackice.bc1.core.RenderPlan;
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
 * Pipeline: AudioPlan → mixer → AAC (temp file) ; RenderPlan → decoders → compositor → H.264 → MP4.
 */
public final class BcExporter {
    public interface Listener { void onProgress(int percent); }

    private static final int AUDIO_SHARE = 10; // percent of progress spent on the audio pass

    private final Context context;
    private final EditProject project;
    private final OutputSpec spec;
    private final AtomicBoolean cancelled;
    private final Listener listener;

    public BcExporter(Context context, EditProject project, int shortEdge, AtomicBoolean cancelled, Listener listener) {
        this.context = context.getApplicationContext();
        this.project = project;
        project.validate();
        if (project.clips.isEmpty()) throw new IllegalArgumentException("Import a video or image first.");
        this.spec = OutputSpec.of(project, shortEdge);
        this.cancelled = cancelled;
        this.listener = listener;
    }

    public OutputSpec spec() { return spec; }

    public void export(File output) throws Exception {
        RenderPlan plan = new RenderPlan(project, false);
        AudioPlan audioPlan = AudioPlan.of(project, false);
        File temp = new File(output.getParentFile(), output.getName() + ".aac.tmp");
        AudioEncoder.Encoded audio;
        try (AudioMixer mixer = new AudioMixer(audioPlan, s -> AudioDecoderSource.open(context, s), OutputSpec.AUDIO_RATE)) {
            audio = AudioEncoder.encode(mixer, temp, cancelled::get, p -> listener.onProgress(p * AUDIO_SHARE / 100));
        }
        try (AudioEncoder.Encoded encodedAudio = audio) {
            renderVideo(plan, encodedAudio, output);
        }
    }

    private void checkCancelled() { if (cancelled.get()) throw new CancellationException("Export cancelled"); }

    private void renderVideo(RenderPlan plan, AudioEncoder.Encoded audio, File output) throws Exception {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, spec.width, spec.height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, OutputSpec.VIDEO_BITRATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, spec.frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        HandlerThread callbacks = new HandlerThread("bc1-frames");
        callbacks.start();
        MediaCodec encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
        Surface input = null;
        EglCore egl = null;
        Compositor compositor = null;
        MediaMuxer muxer = null;
        Map<Integer, FrameSource> sources = new HashMap<>();
        OverlayLayer overlays = null;
        boolean succeeded = false;
        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            input = encoder.createInputSurface();
            encoder.start();
            egl = new EglCore(input);
            compositor = new Compositor(context, spec.width, spec.height);
            overlays = new OverlayLayer(context, spec.width, spec.height);
            muxer = new MediaMuxer(output.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Mux mux = new Mux(muxer, audio);
            Handler handler = new Handler(callbacks.getLooper());

            int frames = spec.frameCount();
            for (int n = 0; n < frames; n++) {
                checkCancelled();
                long timeUs = FrameClock.frameTimeUs(n, spec.frameRate);
                long timeMs = timeUs / 1000;
                List<RenderPlan.LayerDraw> layers = plan.layersAt(timeMs);
                // Decode first (fit passes use their own framebuffers), then composite.
                int[] textures = new int[layers.size()];
                for (int i = 0; i < layers.size(); i++) {
                    RenderPlan.LayerDraw d = layers.get(i);
                    FrameSource source = sources.get(d.placementIndex);
                    if (source == null) {
                        EditProject.Placement p = plan.placements().get(d.placementIndex);
                        source = p.clip.image ? new ImageSource(context, p.clip.uri, compositor)
                                : new VideoSource(context, p.clip.uri, p.clip.inMs, p.clip.outMs, compositor, handler);
                        sources.put(d.placementIndex, source);
                    }
                    textures[i] = source.textureAt(d.sourceTimeMs);
                }
                compositor.beginFrame();
                for (int i = 0; i < layers.size(); i++) compositor.drawLayer(layers.get(i), textures[i]);
                List<EditProject.Overlay> active = plan.overlaysAt(timeMs);
                if (!active.isEmpty()) compositor.drawOverlay(overlays.texture(active, timeMs));
                egl.setPresentationTime(timeUs * 1000);
                egl.swap();
                drain(encoder, mux, false);
                // Release decoders whose placement has ended before the next frame.
                long nextMs = FrameClock.frameTimeMs(n + 1, spec.frameRate);
                for (Iterator<Map.Entry<Integer, FrameSource>> it = sources.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<Integer, FrameSource> e = it.next();
                    if (plan.placements().get(e.getKey()).endMs <= nextMs) { e.getValue().release(); it.remove(); }
                }
                listener.onProgress(AUDIO_SHARE + (int) ((n + 1L) * (99 - AUDIO_SHARE) / frames));
            }
            encoder.signalEndOfInputStream();
            drain(encoder, mux, true);
            mux.finishAudio();
            muxer.stop();
            succeeded = true;
            listener.onProgress(100);
        } finally {
            for (FrameSource s : sources.values()) try { s.release(); } catch (Exception ignored) { }
            if (overlays != null) overlays.release();
            if (compositor != null) compositor.release();
            if (egl != null) egl.close();
            if (input != null) input.release();
            try { encoder.stop(); } catch (Exception ignored) { }
            encoder.release();
            if (muxer != null) try { muxer.release(); } catch (Exception ignored) { }
            callbacks.quitSafely();
            if (!succeeded) output.delete();
        }
    }

    /** Writes encoder output to the muxer, interleaving buffered audio up to each video timestamp. */
    private void drain(MediaCodec encoder, Mux mux, boolean endOfStream) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long deadline = System.currentTimeMillis() + 10_000;
        while (true) {
            int index = encoder.dequeueOutputBuffer(info, endOfStream ? 10_000 : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return;
                if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Video encoder did not finish");
                continue;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { mux.start(encoder.getOutputFormat()); continue; }
            if (index < 0) continue;
            deadline = System.currentTimeMillis() + 10_000;
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

    /** Owns track setup and A/V interleaving for one MP4. */
    private static final class Mux {
        final MediaMuxer muxer; final AudioEncoder.Encoded audio;
        int videoTrack = -1, audioTrack = -1; boolean started;
        private final MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
        private final ByteBuffer audioBuffer;
        Mux(MediaMuxer muxer, AudioEncoder.Encoded audio) {
            this.muxer = muxer; this.audio = audio;
            long largest = 1;
            for (long[] s : audio.samples) largest = Math.max(largest, s[2]);
            audioBuffer = ByteBuffer.allocateDirect((int) largest);
        }

        void start(MediaFormat videoFormat) {
            if (started) throw new IllegalStateException("Video format changed twice");
            videoTrack = muxer.addTrack(videoFormat);
            audioTrack = muxer.addTrack(audio.format);
            muxer.start(); started = true;
        }
        void writeVideo(ByteBuffer data, MediaCodec.BufferInfo info) throws Exception {
            writeAudioUntil(info.presentationTimeUs);
            muxer.writeSampleData(videoTrack, data, info);
        }
        void writeAudioUntil(long timeUs) throws Exception {
            while (audio.hasNext() && audio.nextTimeUs() <= timeUs) {
                audio.read(audioBuffer, audioInfo);
                muxer.writeSampleData(audioTrack, audioBuffer, audioInfo);
            }
        }
        void finishAudio() throws Exception {
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

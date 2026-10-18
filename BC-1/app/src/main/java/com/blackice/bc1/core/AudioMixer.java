package com.blackice.bc1.core;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders an {@link AudioPlan} to interleaved stereo float PCM, block by block, in timeline order.
 * Pure Java: the backend only supplies decoded source PCM through {@link Opener}.
 */
public final class AudioMixer implements AutoCloseable {
    /** Decoded stereo source positioned at the segment's in-point. */
    public static final class Opened {
        public final Resampler.Source source; public final int sampleRate; public final AutoCloseable closer;
        public Opened(Resampler.Source source, int sampleRate, AutoCloseable closer) { this.source = source; this.sampleRate = sampleRate; this.closer = closer; }
    }
    /** Opens a segment's media at its source in-point; null when the media has no sound. */
    public interface Opener { Opened open(AudioPlan.Segment segment) throws Exception; }

    private static final class Voice {
        final AudioPlan.Segment segment; final long startFrame, endFrame; final Opened opened; final Resampler resampler;
        long position; // frames already produced, relative to startFrame
        Voice(AudioPlan.Segment s, long start, long end, Opened opened, int rate) {
            segment = s; startFrame = start; endFrame = end; this.opened = opened;
            resampler = opened == null ? null : new Resampler(opened.source, opened.sampleRate, rate, s.speed);
        }
    }

    private final AudioPlan plan;
    private final Opener opener;
    private final int rate;
    private final long totalFrames;
    private final Map<AudioPlan.Segment, Voice> voices = new IdentityHashMap<>();
    private final List<AudioPlan.Segment> finished = new ArrayList<>();
    private long cursor;
    private float[] scratch = new float[0];

    public AudioMixer(AudioPlan plan, Opener opener, int rate) {
        this.plan = plan; this.opener = opener; this.rate = rate;
        totalFrames = frameOf(plan.durationMs, rate);
    }

    public static long frameOf(long ms, int rate) { return Math.round(ms * (double) rate / 1000.0); }

    public long totalFrames() { return totalFrames; }
    public long position() { return cursor; }
    public boolean done() { return cursor >= totalFrames; }

    /** Mixes the next block. Returns frames written (0 when the timeline is complete). */
    public int render(float[] out, int maxFrames) throws Exception {
        int frames = (int) Math.min(maxFrames, totalFrames - cursor);
        if (frames <= 0) return 0;
        java.util.Arrays.fill(out, 0, frames * 2, 0f);
        long blockEnd = cursor + frames;
        for (AudioPlan.Segment s : plan.segments) {
            long start = frameOf(s.timelineStartMs, rate), end = Math.min(totalFrames, frameOf(s.timelineEndMs(), rate));
            if (start >= blockEnd || end <= cursor || finished.contains(s)) continue;
            Voice v = voices.get(s);
            if (v == null) { v = new Voice(s, start, end, opener.open(s), rate); voices.put(s, v); }
            long from = Math.max(cursor, start), to = Math.min(blockEnd, end);
            // Catch up if the voice was opened late (never happens when rendering from zero).
            long skip = from - (v.startFrame + v.position);
            if (skip > 0 && v.resampler != null) { discard(v, skip); }
            v.position = from - v.startFrame;
            int count = (int) (to - from);
            if (v.resampler != null && count > 0) {
                if (scratch.length < count * 2) scratch = new float[count * 2];
                int got = v.resampler.read(scratch, count);
                int offset = (int) (from - cursor);
                for (int f = 0; f < got; f++) {
                    double localMs = (v.position + f) * 1000.0 / rate;
                    float g = s.gainAt(localMs);
                    out[(offset + f) * 2] += scratch[f * 2] * g;
                    out[(offset + f) * 2 + 1] += scratch[f * 2 + 1] * g;
                }
            }
            v.position += count;
            if (v.startFrame + v.position >= v.endFrame) { close(v); voices.remove(s); finished.add(s); }
        }
        for (int i = 0; i < frames * 2; i++) out[i] = Resampler.clamp(out[i]);
        cursor = blockEnd;
        return frames;
    }

    private void discard(Voice v, long frames) throws Exception {
        float[] sink = new float[2 * 1024];
        while (frames > 0) { int n = v.resampler.read(sink, (int) Math.min(1024, frames)); if (n <= 0) break; frames -= n; }
    }

    private static void close(Voice v) {
        if (v.opened != null && v.opened.closer != null) try { v.opened.closer.close(); } catch (Exception ignored) { }
    }

    @Override public void close() {
        for (Voice v : voices.values()) close(v);
        voices.clear();
    }
}

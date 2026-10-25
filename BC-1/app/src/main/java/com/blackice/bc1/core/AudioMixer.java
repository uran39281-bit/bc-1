package com.blackice.bc1.core;

import com.blackice.bc1.audio.AudioDsp;
import com.blackice.bc1.core.AudioPlan;
import com.blackice.bc1.core.Resampler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/* loaded from: classes6.dex */
public final class AudioMixer implements AutoCloseable {
    private long cursor;
    private final Opener opener;
    private final AudioPlan plan;
    private final int rate;
    private final long totalFrames;
    private final Map<AudioPlan.Segment, Voice> voices = new IdentityHashMap();
    private final List<AudioPlan.Segment> finished = new ArrayList();
    private float[] scratch = new float[0];

    public static final class Opened {
        public final AutoCloseable closer;
        public final int sampleRate;
        public final Resampler.Source source;

        public Opened(Resampler.Source source, int sampleRate, AutoCloseable closer) {
            this.source = source;
            this.sampleRate = sampleRate;
            this.closer = closer;
        }
    }

    public interface Opener {
        Opened open(AudioPlan.Segment segment) throws Exception;

        default Opened openAt(AudioPlan.Segment segment, long localMs) throws Exception {
            return open(segment);
        }

        default boolean seeks() {
            return false;
        }
    }

    private static final class Voice {
        final AudioDsp dsp;
        final long endFrame;
        final Opened opened;
        long position;
        final Resampler resampler;
        final AudioPlan.Segment segment;
        final long startFrame;

        Voice(AudioPlan.Segment s, long start, long end, Opened opened, int rate) {
            this.dsp = new AudioDsp(s.audio, rate, 2);
            this.segment = s;
            this.startFrame = start;
            this.endFrame = end;
            this.opened = opened;
            this.resampler = opened == null ? null : new Resampler(opened.source, opened.sampleRate, rate, s.speed);
        }
    }

    public AudioMixer(AudioPlan plan, Opener opener, int rate) {
        this.plan = plan;
        this.opener = opener;
        this.rate = rate;
        this.totalFrames = frameOf(plan.durationMs, rate);
    }

    public void seek(long ms) {
        if (!this.voices.isEmpty() || !this.finished.isEmpty()) {
            throw new IllegalStateException("Seek before rendering");
        }
        this.cursor = Math.max(0L, Math.min(this.totalFrames, frameOf(ms, this.rate)));
        for (AudioPlan.Segment s : this.plan.segments) {
            if (frameOf(s.timelineEndMs(), this.rate) <= this.cursor) {
                this.finished.add(s);
            }
        }
    }

    public static long frameOf(long ms, int rate) {
        return Math.round((ms * rate) / 1000.0d);
    }

    public long totalFrames() {
        return this.totalFrames;
    }

    public long position() {
        return this.cursor;
    }

    public boolean done() {
        return this.cursor >= this.totalFrames;
    }

    public int render(float[] out, int maxFrames) throws Exception {
        int frames;
        int frames2 = (int) Math.min(maxFrames, this.totalFrames - this.cursor);
        if (frames2 <= 0) {
            return 0;
        }
        Arrays.fill(out, 0, frames2 * 2, 0.0f);
        long blockEnd = this.cursor + frames2;
        for (AudioPlan.Segment s : this.plan.segments) {
            long start = frameOf(s.timelineStartMs, this.rate);
            long end = Math.min(this.totalFrames, frameOf(s.timelineEndMs(), this.rate));
            if (start < blockEnd && end > this.cursor) {
                if (!this.finished.contains(s)) {
                    Voice v = this.voices.get(s);
                    long from = Math.max(this.cursor, start);
                    long to = Math.min(blockEnd, end);
                    if (v != null) {
                        frames = frames2;
                    } else {
                        long late = from - start;
                        if (late <= 0 || !this.opener.seeks()) {
                            frames = frames2;
                            v = new Voice(s, start, end, this.opener.open(s), this.rate);
                        } else {
                            frames = frames2;
                            Voice v2 = new Voice(s, start, end, this.opener.openAt(s, (1000 * late) / this.rate), this.rate);
                            v2.position = late;
                            v = v2;
                        }
                        this.voices.put(s, v);
                    }
                    long skip = from - (v.startFrame + v.position);
                    if (skip > 0 && v.resampler != null) {
                        discard(v, skip);
                    }
                    v.position = from - v.startFrame;
                    int count = (int) (to - from);
                    if (v.resampler != null && count > 0) {
                        if (this.scratch.length < count * 2) {
                            this.scratch = new float[count * 2];
                        }
                        int got = v.resampler.read(this.scratch, count);
                        v.dsp.process(this.scratch, got);
                        int offset = (int) (from - this.cursor);
                        int f = 0;
                        while (f < got) {
                            long skip2 = skip;
                            double localMs = ((v.position + f) * 1000.0d) / this.rate;
                            float g = s.gainAt(localMs);
                            int i = (offset + f) * 2;
                            out[i] = out[i] + (this.scratch[f * 2] * g);
                            int i2 = ((offset + f) * 2) + 1;
                            out[i2] = out[i2] + (this.scratch[(f * 2) + 1] * g);
                            f++;
                            skip = skip2;
                        }
                    }
                    v.position += count;
                    if (v.startFrame + v.position >= v.endFrame) {
                        close(v);
                        this.voices.remove(s);
                        this.finished.add(s);
                    }
                    frames2 = frames;
                }
            }
        }
        int frames3 = frames2;
        for (int i3 = 0; i3 < frames3 * 2; i3++) {
            out[i3] = Resampler.clamp(out[i3]);
        }
        this.cursor = blockEnd;
        return frames3;
    }

    private void discard(Voice v, long frames) throws Exception {
        int n;
        float[] sink = new float[2048];
        while (frames > 0 && (n = v.resampler.read(sink, (int) Math.min(1024L, frames))) > 0) {
            v.dsp.process(sink, n);
            frames -= n;
        }
    }

    private static void close(Voice v) {
        if (v.opened == null || v.opened.closer == null) {
            return;
        }
        try {
            v.opened.closer.close();
        } catch (Exception e) {
        }
    }

    @Override // java.lang.AutoCloseable
    public void close() {
        for (Voice v : this.voices.values()) {
            close(v);
        }
        this.voices.clear();
    }
}

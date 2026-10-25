package com.blackice.bc1.audio;

/* loaded from: classes6.dex */
public final class PcmTimestampCursor {
    private long next;

    public static final class Span {
        public final int keep;
        public final long silence;
        public final int skip;

        Span(int skip, int keep, long silence) {
            this.skip = skip;
            this.keep = keep;
            this.silence = silence;
        }
    }

    public Span accept(long ptsUs, long originUs, int rate, int frames) {
        if (rate <= 0 || frames < 0) {
            throw new IllegalArgumentException("Invalid audio packet");
        }
        long first = Math.round(((ptsUs - originUs) * rate) / 1000000.0d);
        int skip = (int) Math.min(frames, Math.max(0L, this.next - first));
        int keep = frames - skip;
        long gap = keep > 0 ? Math.max(0L, (skip + first) - this.next) : 0L;
        if (keep > 0) {
            this.next += keep + gap;
        }
        return new Span(skip, keep, gap);
    }
}

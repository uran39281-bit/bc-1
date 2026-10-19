package com.blackice.bc1.core;

/** Exact frame timing. Frame n starts at n / fps seconds; rounding never accumulates drift. */
public final class FrameClock {
    private FrameClock() {}

    public static long frameTimeUs(long frame, int fps) {
        check(fps);
        return Math.floorDiv(frame * 1_000_000L, fps);
    }

    /** Timeline millisecond at which a frame is sampled (its start time). */
    public static long frameTimeMs(long frame, int fps) { return frameTimeUs(frame, fps) / 1000; }

    public static int frameCount(long durationMs, int fps) {
        check(fps);
        if (durationMs <= 0) return 0;
        // ceil(duration * fps / 1000): frames whose start time lies inside [0, duration).
        long frames = (durationMs * fps + 999) / 1000;
        if (frames > Integer.MAX_VALUE) throw new IllegalArgumentException("Timeline is too long.");
        return (int) frames;
    }

    /** Index of the frame showing a timeline instant. */
    public static long frameAt(long timeMs, int fps) {
        check(fps);
        return Math.floorDiv(Math.max(0, timeMs) * fps, 1000);
    }

    private static void check(int fps) {
        if (fps <= 0 || fps > 240) throw new IllegalArgumentException("Unsupported frame rate: " + fps);
    }
}

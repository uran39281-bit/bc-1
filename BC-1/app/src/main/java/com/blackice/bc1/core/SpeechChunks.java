package com.blackice.bc1.core;

/**
 * Splits long speech audio for transcription: whisper takes the whole buffer at once, so long timelines
 * are transcribed in chunks of about {@link #CHUNK_SECONDS}, each cut at the quietest moment near its
 * end to reduce the chance of cutting through a word.
 */
public final class SpeechChunks {
    public static final int CHUNK_SECONDS = 60;
    /** How far back from the chunk end to look for a pause. */
    public static final int SEARCH_SECONDS = 5;
    private static final int WINDOW_MS = 50;

    private SpeechChunks() {}

    /**
     * Frame index in {@code (min, max]} to cut {@code mono} at: the start of the quietest 50 ms window
     * between {@code max - search} and {@code max}.
     */
    public static int quietestCut(float[] mono, int length, int rate, int searchFrames) {
        int window = Math.max(1, rate * WINDOW_MS / 1000);
        int from = Math.max(window, length - searchFrames), best = length;
        double bestEnergy = Double.MAX_VALUE;
        for (int start = from; start + window <= length; start += window / 2) {
            double e = 0;
            for (int i = start; i < start + window; i++) e += mono[i] * mono[i];
            if (e < bestEnergy) { bestEnergy = e; best = start + window / 2; }
        }
        return Math.max(1, Math.min(length, best));
    }

    /**
     * Narrows a recognised segment to where there is actually sound. Whisper's segment times are only
     * roughly aligned (a first segment after silence often starts at 0), so captions would appear early;
     * this moves the start to the first voiced 20 ms and the end to the last. Returns {startMs, endMs}.
     */
    public static long[] tighten(float[] pcm, int rate, long startMs, long endMs) {
        int window = rate / 50;
        int from = (int) Math.max(0, Math.min(pcm.length, startMs * rate / 1000)), to = (int) Math.max(from, Math.min(pcm.length, endMs * rate / 1000));
        int windows = (to - from) / window;
        if (windows < 2) return new long[]{startMs, endMs};
        double[] rms = new double[windows]; double peak = 0;
        for (int w = 0; w < windows; w++) {
            double e = 0;
            for (int i = from + w * window, end = i + window; i < end; i++) e += pcm[i] * pcm[i];
            rms[w] = Math.sqrt(e / window); peak = Math.max(peak, rms[w]);
        }
        double threshold = Math.max(.004, peak * .12);
        int first = -1, last = -1;
        for (int w = 0; w < windows; w++) if (rms[w] >= threshold) { if (first < 0) first = w; last = w; }
        if (first < 0) return new long[]{startMs, endMs};
        long voicedStart = (from + (long) first * window) * 1000 / rate, voicedEnd = (from + (long) (last + 1) * window) * 1000 / rate;
        // Keep a little lead-in so the caption is up as the first syllable starts.
        return new long[]{Math.max(startMs, voicedStart - 100), Math.min(endMs, Math.max(voicedEnd + 150, voicedStart + 300))};
    }
}

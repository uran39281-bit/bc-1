package com.blackice.bc1.core;

/**
 * Splits long speech audio for transcription: whisper takes the whole buffer at once, so long timelines
 * are transcribed in chunks of about {@link #CHUNK_SECONDS}, each cut at the quietest moment near its
 * end so no word is split between chunks.
 */
public final class SpeechChunks {
    public static final int CHUNK_SECONDS = 600;
    /** How far back from the chunk end to look for a pause. */
    public static final int SEARCH_SECONDS = 15;
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
}

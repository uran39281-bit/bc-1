package com.blackice.bc1.captions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * whisper.cpp speech-to-text (native, CPU). One instance holds one loaded model; it is not thread-safe,
 * except {@link #cancel()} and {@link #progress()}, which may be called from any thread while
 * {@link #transcribe} runs.
 */
public final class WhisperLib implements AutoCloseable {
    /** Whisper's input: 16 kHz mono float PCM in -1..1. */
    public static final int SAMPLE_RATE = 16_000;

    private static final Throwable LOAD_ERROR;
    static {
        Throwable error = null;
        try { System.loadLibrary("bc1whisper"); }
        catch (Throwable t) { error = t; }
        LOAD_ERROR = error;
    }

    /** False on devices whose CPU type this build has no speech library for. */
    public static boolean available() { return LOAD_ERROR == null; }

    /** One transcribed phrase. Times are milliseconds from the start of the audio. */
    public static final class Segment {
        public final long startMs, endMs; public final String text;
        public Segment(long startMs, long endMs, String text) { this.startMs = startMs; this.endMs = endMs; this.text = text; }
        @Override public String toString() { return startMs + "-" + endMs + ": " + text; }
    }

    public static final class Cancelled extends Exception { Cancelled() { super("Transcription cancelled"); } }

    private final Object handleLock = new Object();
    private volatile boolean cancelled;
    private long handle;

    /** Loads a ggml model file; throws if the file is not a usable whisper model. */
    public WhisperLib(String modelPath) {
        if (LOAD_ERROR != null) throw new IllegalStateException("Speech recognition is not available on this device", LOAD_ERROR);
        handle = nativeInit(modelPath);
        if (handle == 0) throw new IllegalArgumentException("Could not load the speech model. Prepare it again in Speech models.");
    }

    /**
     * Transcribes 16 kHz mono audio. {@code language} is an ISO code such as "en", or "auto" to detect it;
     * {@code translate} outputs English whatever was spoken. Blocks until done.
     */
    public synchronized List<Segment> transcribe(float[] pcm, String language, boolean translate, int threads, String prompt) throws Exception {
        if (handle == 0) throw new IllegalStateException("Closed");
        if (cancelled) throw new Cancelled();
        int result = nativeTranscribe(handle, pcm, language == null ? "auto" : language, translate, Math.max(1, threads), prompt);
        if (result == -1) throw new Cancelled();
        if (result != 0) throw new IllegalStateException("Speech recognition failed (" + result + ")");
        int n = nativeSegmentCount(handle);
        List<Segment> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long[] t = nativeSegmentTimes(handle, i);
            out.add(new Segment(t[0], t[1], new String(nativeSegmentText(handle, i), StandardCharsets.UTF_8)));
        }
        return out;
    }

    /** Language of the last transcription (ISO code), useful after "auto". */
    public synchronized String language() { return handle == 0 ? "" : nativeLanguage(handle); }
    /** 0..100 while {@link #transcribe} runs. */
    public int progress() { synchronized (handleLock) { return handle == 0 ? 0 : nativeProgress(handle); } }
    /** Stops a running transcription soon; {@link #transcribe} then throws {@link Cancelled}. */
    public void cancel() { cancelled = true; synchronized (handleLock) { if (handle != 0) nativeCancel(handle); } }

    public static String systemInfo() { return available() ? nativeSystemInfo() : "unavailable: " + LOAD_ERROR; }

    @Override public synchronized void close() { synchronized (handleLock) { if (handle != 0) { nativeFree(handle); handle = 0; } } }

    private static native long nativeInit(String path);
    private static native void nativeFree(long handle);
    private static native int nativeTranscribe(long handle, float[] pcm, String language, boolean translate, int threads, String prompt);
    private static native int nativeProgress(long handle);
    private static native void nativeCancel(long handle);
    private static native int nativeSegmentCount(long handle);
    private static native byte[] nativeSegmentText(long handle, int i);
    private static native long[] nativeSegmentTimes(long handle, int i);
    private static native String nativeLanguage(long handle);
    private static native String nativeSystemInfo();
}

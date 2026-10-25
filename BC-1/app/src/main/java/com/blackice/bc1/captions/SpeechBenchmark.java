package com.blackice.bc1.captions;

import android.content.Context;
import android.os.Build;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Captions speed test: transcribes a built-in 11-second public-domain speech sample (JFK, 1961 inaugural
 * address; the whisper.cpp test sample) and reports speed as a multiple of real time.
 */
public final class SpeechBenchmark {
    public static final String SAMPLE = "speech/sample.wav";
    private SpeechBenchmark() {}

    /** The sample as 16 kHz mono floats (the asset is 16 kHz mono 16-bit PCM WAV). */
    public static float[] sample(Context context) throws Exception {
        byte[] wav;
        try (InputStream in = context.getAssets().open(SAMPLE)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[65536]; int n;
            while ((n = in.read(b)) != -1) out.write(b, 0, n);
            wav = out.toByteArray();
        }
        ByteBuffer buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        int pos = 12, dataStart = -1, dataLength = 0;
        while (pos + 8 <= wav.length) { // walk RIFF chunks to "data"
            int id = buf.getInt(pos), size = buf.getInt(pos + 4);
            if (id == 0x61746164) { dataStart = pos + 8; dataLength = Math.min(size, wav.length - dataStart); break; }
            pos += 8 + size + (size & 1);
        }
        if (dataStart < 0) throw new IllegalStateException("Speech sample has no audio data");
        float[] pcm = new float[dataLength / 2];
        for (int i = 0; i < pcm.length; i++) pcm[i] = buf.getShort(dataStart + 2 * i) / 32768f;
        return pcm;
    }

    public static String run(Context context, SpeechModels.Model model, AtomicBoolean stop, Consumer<String> status) throws Exception {
        if (!WhisperLib.available()) return "Auto captions are not available on this device's CPU type.\n" + WhisperLib.systemInfo();
        File file = SpeechModels.ensure(context, model, stop::get, p -> status.accept(model.preparation() + " " + model.label + " model… " + p + "%"));
        float[] pcm = sample(context);
        status.accept("Loading model…");
        long loadStart = System.nanoTime();
        try (WhisperLib w = new WhisperLib(file.getPath())) {
            long loadMs = (System.nanoTime() - loadStart) / 1_000_000;
            if (stop.get()) throw new java.util.concurrent.CancellationException();
            status.accept("Transcribing an 11-second speech sample…");
            Thread watcher = new Thread(() -> { while (!Thread.currentThread().isInterrupted()) { if (stop.get()) w.cancel(); try { Thread.sleep(100); } catch (InterruptedException e) { return; } } });
            watcher.setDaemon(true); watcher.start();
            int threads = Transcriber.threads();
            long start = System.nanoTime();
            List<WhisperLib.Segment> segments;
            try { segments = w.transcribe(pcm, model.englishOnly() ? "en" : "auto", false, threads, null); }
            catch (WhisperLib.Cancelled c) { throw new java.util.concurrent.CancellationException(); }
            finally { watcher.interrupt(); }
            long ms = (System.nanoTime() - start) / 1_000_000;
            double audioMs = pcm.length * 1000.0 / WhisperLib.SAMPLE_RATE;
            StringBuilder text = new StringBuilder();
            for (WhisperLib.Segment s : segments) text.append(s.text);
            String heard = text.toString().trim();
            double factor = audioMs / Math.max(1, ms);
            return String.format(Locale.US,
                    "Captions speed test • %s %s • Android %s\n%s model, %d threads\n\nSpeed: %.1f× real time (%.1f s of speech in %.1f s)\nA 10-minute video takes about %s.\nModel load: %.1f s • language: %s\n\nHeard: \"%s\"\n\n%s",
                    Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE, model.label, threads,
                    factor, audioMs / 1000, ms / 1000.0, estimate(600_000 / Math.max(.01, factor)), loadMs / 1000.0, w.language(),
                    heard, WhisperLib.systemInfo().trim());
        }
    }

    static String estimate(double ms) {
        long s = Math.round(ms / 1000);
        return s < 90 ? s + " s" : String.format(Locale.US, "%.0f min", s / 60.0);
    }
}

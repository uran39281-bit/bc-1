package com.blackice.bc1.captions;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Whisper models for auto captions. Models are downloaded on first use (from the whisper.cpp model
 * repository on Hugging Face) into app storage, never bundled, so the APK stays small. Multilingual,
 * 5-bit quantised: smaller and faster on phone CPUs with near-identical accuracy.
 */
public final class SpeechModels {
    public enum Model {
        TINY("Tiny", "ggml-tiny-q5_1.bin", 32_000_000L, "fastest, rough"),
        BASE("Base", "ggml-base-q5_1.bin", 60_000_000L, "recommended"),
        SMALL("Small", "ggml-small-q5_1.bin", 190_000_000L, "most accurate, slower");

        public final String label, file, note; public final long approxBytes;
        Model(String label, String file, long approxBytes, String note) { this.label = label; this.file = file; this.approxBytes = approxBytes; this.note = note; }
        public String url() { return BASE_URL + file; }
        public String describe() { return label + " (" + Math.round(approxBytes / 1e6) + " MB, " + note + ")"; }
    }

    public static final String BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";
    /** whisper.cpp model files start with the ggml magic 0x67676d6c, stored little-endian. */
    private static final int MAGIC = 0x67676d6c;

    private SpeechModels() {}

    public static File dir(Context context) { File d = new File(context.getFilesDir(), "speech-models"); d.mkdirs(); return d; }
    public static File file(Context context, Model m) { return new File(dir(context), m.file); }
    public static boolean installed(Context context, Model m) { File f = file(context, m); return f.isFile() && f.length() > 1_000_000 && validMagic(f); }

    public static boolean delete(Context context, Model m) {
        new File(dir(context), m.file + ".part").delete();
        return file(context, m).delete();
    }

    /**
     * Downloads {@code m} if it is not installed, resuming a partial download. Reports 0..100 progress.
     * Returns the model file.
     */
    public static File ensure(Context context, Model m, BooleanSupplier cancelled, IntConsumer progress) throws Exception {
        File target = file(context, m);
        if (installed(context, m)) return target;
        File part = new File(dir(context), m.file + ".part");
        for (int attempt = 0; ; attempt++) {
            try { download(m.url(), part, cancelled, progress); break; }
            catch (IOException e) {
                if (cancelled.getAsBoolean() || attempt >= 2) throw new IOException("Model download failed: " + e.getMessage() + ". Check the internet connection and try again.", e);
                Thread.sleep(1500L * (attempt + 1));
            }
        }
        if (!validMagic(part)) { part.delete(); throw new IOException("The downloaded file is not a speech model"); }
        if (!part.renameTo(target)) throw new IOException("Could not save the speech model");
        return target;
    }

    private static void download(String address, File part, BooleanSupplier cancelled, IntConsumer progress) throws Exception {
        long have = part.isFile() ? part.length() : 0;
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(20_000); c.setReadTimeout(30_000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "BC-1 video editor");
        if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
        try {
            int code = c.getResponseCode();
            if (code == 416) return; // already complete
            if (code != 200 && code != 206) throw new IOException("HTTP " + code);
            boolean append = code == 206;
            if (!append) have = 0;
            long total = c.getContentLengthLong();
            if (total > 0) total += have;
            byte[] buffer = new byte[256 * 1024];
            long done = have; int lastPercent = -1;
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(part, append)) {
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
                    out.write(buffer, 0, n); done += n;
                    int percent = total > 0 ? (int) (done * 100 / total) : -1;
                    if (percent != lastPercent) { lastPercent = percent; progress.accept(percent); }
                }
            }
            if (total > 0 && done < total) throw new IOException("Connection closed early");
        } finally { c.disconnect(); }
    }

    static boolean validMagic(File f) {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            if (r.length() < 4) return false;
            int b0 = r.read(), b1 = r.read(), b2 = r.read(), b3 = r.read();
            return (b0 | b1 << 8 | b2 << 16 | b3 << 24) == MAGIC;
        } catch (IOException e) { return false; }
    }
}

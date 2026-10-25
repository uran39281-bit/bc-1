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
 * Verified speech models. Ice Voice 01 uses bundled pretrained Whisper Base English.
 * Optional multilingual models download on first use; audio is always processed locally.
 */
public final class SpeechModels {
    public enum Model {
        BASE_EN("Ice Voice 01 · English", "ggml-base.en-q5_1.bin", 59721011L, "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f", "included · Whisper Base English"),
        TINY("Tiny", "ggml-tiny-q5_1.bin", 32152673L, "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7", "fastest, rough"),
        BASE("Base", "ggml-base-q5_1.bin", 59707625L, "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", "recommended"),
        SMALL("Small", "ggml-small-q5_1.bin", 190085487L, "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", "most accurate, slower");

        public final String label, file, note, sha256; public final long approxBytes;
        Model(String label, String file, long approxBytes, String sha256, String note) { this.sha256 = sha256; this.label = label; this.file = file; this.approxBytes = approxBytes; this.note = note; }
        public boolean bundled() { return this == BASE_EN; }
        public boolean englishOnly() { return this == BASE_EN; }
        public String preparation() { return bundled() ? "Preparing included" : "Downloading"; }
        public String url() { return BASE_URL + file; }
        public String describe() { return label + " (" + Math.round(approxBytes / 1e6) + " MB, " + note + ")"; }
    }

    public static final String BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";
    /** whisper.cpp model files start with the ggml magic 0x67676d6c, stored little-endian. */
    private static final int MAGIC = 0x67676d6c;

    private SpeechModels() {}

    public static File dir(Context context) { File d = new File(context.getFilesDir(), "speech-models"); d.mkdirs(); return d; }
    public static File file(Context context, Model m) { return new File(dir(context), m.file); }
    public static boolean installed(Context context, Model m) { File f = file(context, m); return f.isFile() && f.length() == m.approxBytes && validMagic(f) && verified(f, m); }

    public static boolean delete(Context context, Model m) {
        new File(dir(context), m.file + ".part").delete();
        new File(dir(context), m.file + ".verified").delete();
        return file(context, m).delete();
    }

    /**
     * Prepares {@code m} from the APK or downloads an optional model, resuming partial downloads. Reports 0..100 progress.
     * Returns the model file.
     */
    public static synchronized File ensure(Context context, Model m, BooleanSupplier cancelled, IntConsumer progress) throws Exception {
        if(m!=Model.BASE_EN)throw new IllegalArgumentException("This editor includes English recognition only. Start a new English transcription.");
        File target = file(context, m);
        if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
        if (installed(context, m)) return target;
        if (target.isFile() && target.length() == m.approxBytes) {
            try { verify(target, m, cancelled); mark(target, m); return target; }
            catch (IOException invalid) { target.delete(); }
        }
        File part = new File(dir(context), m.file + ".part");
        if (m.bundled()) {
            // A cancelled/interrupted extraction is restarted from the APK, never from the network.
            try (InputStream in = context.getAssets().open("speech/" + m.file);
                 FileOutputStream out = new FileOutputStream(part)) {
                byte[] buffer = new byte[256 * 1024]; long done = 0; int n, last = -1;
                while ((n = in.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
                    out.write(buffer, 0, n); done += n;
                    int percent = (int)Math.min(99, done * 100 / m.approxBytes);
                    if (percent != last) { last = percent; progress.accept(percent); }
                }
                out.getFD().sync();
            } catch (Exception error) { part.delete(); throw error; }
            try {
                verify(part, m, cancelled);
                if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
                java.nio.file.Files.move(part.toPath(), target.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                mark(target, m); progress.accept(100); return target;
            } catch (Exception error) { part.delete(); throw error; }
        }
        for (int attempt = 0; ; attempt++) {
            try { download(m.url(), part, cancelled, progress); break; }
            catch (IOException e) {
                if (cancelled.getAsBoolean() || attempt >= 2) throw new IOException("Model download failed: " + e.getMessage() + ". Check the internet connection and try again.", e);
                Thread.sleep(1500L * (attempt + 1));
            }
        }
        try { verify(part, m, cancelled); } catch (IOException bad) { part.delete(); throw bad; }
        if (!part.renameTo(target)) throw new IOException("Could not save the speech model");
        mark(target, m);
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

    private static String stamp(File f, Model m) { return m.sha256 + ":" + f.length() + ":" + f.lastModified(); }
    private static boolean verified(File f, Model m) {
        try { return stamp(f, m).equals(new String(java.nio.file.Files.readAllBytes(new File(f.getPath()+".verified").toPath()), java.nio.charset.StandardCharsets.UTF_8)); }
        catch (IOException e) { return false; }
    }
    private static void mark(File f, Model m) throws IOException {
        java.nio.file.Files.write(new File(f.getPath()+".verified").toPath(), stamp(f,m).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void verify(File f, Model m, BooleanSupplier cancelled) throws Exception {
        if (f.length() != m.approxBytes || !validMagic(f)) throw new IOException("Incomplete or invalid speech model; prepare the model again.");
        java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] b = new byte[256 * 1024]; int n;
            while ((n = in.read(b)) != -1) { if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException(); hash.update(b, 0, n); }
        }
        StringBuilder hex = new StringBuilder(); for (byte b : hash.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        if (!m.sha256.equals(hex.toString())) throw new IOException("Speech model verification failed; prepare the model again.");
    }

    static boolean validMagic(File f) {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            if (r.length() < 4) return false;
            int b0 = r.read(), b1 = r.read(), b2 = r.read(), b3 = r.read();
            return (b0 | b1 << 8 | b2 << 16 | b3 << 24) == MAGIC;
        } catch (IOException e) { return false; }
    }
}

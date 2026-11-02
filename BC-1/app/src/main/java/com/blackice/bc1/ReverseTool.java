package com.blackice.bc1;

import android.media.MediaMetadataRetriever;
import android.net.Uri;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.ReverseTranscoder;
import java.io.File;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Layer Tools → Reverse. Producing the reversed copy is slow work, so it runs on its own thread with progress and
 * Cancel; the clip is only touched at the end, in one undoable edit, and only if it is still the same footage.
 * Copies are cached by source, trim and processing version, so reversing the same range again is instant.
 */
final class ReverseTool {
    private static final String VERSION = "rev1";
    private final EditorActivity a;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AtomicBoolean cancelled;
    private volatile boolean running;
    private volatile int progress;
    private long lastUi;

    ReverseTool(EditorActivity activity) { a = activity; }

    boolean running() { return running; }
    int progress() { return progress; }

    /** Why Reverse Footage cannot start right now, or null. */
    String unavailableReason() {
        EditProject.Clip c = a.clip();
        if (running) return "Reversing…";
        String unsupported = ReverseOps.unsupported(a.overlay() != null ? null : c);
        if (a.overlay() != null) return "Reverse is available for video footage only.";
        if (unsupported != null) return unsupported;
        if (a.loading || a.importing || a.captionApplying || a.uiLocked) return "Wait for the current operation to finish.";
        return null;
    }

    void cancel() { AtomicBoolean flag = cancelled; if (flag != null) flag.set(true); }

    void restore() {
        EditProject.Clip c = a.clip();
        if (c == null || !c.reversed || !a.layerEditable()) return;
        final String id = c.id;
        a.edit("Footage restored to forward", () -> {
            EditProject.Clip target = find(id);
            if (target == null) throw new IllegalArgumentException("The selected layer changed.");
            ReverseOps.restore(target);
        });
        a.renderInspector();
    }

    void start() {
        String reason = unavailableReason();
        if (reason != null) { a.tell(reason); a.renderInspector(); return; }
        EditProject.Clip c = a.clip();
        if (c.reversed || !a.layerEditable()) return;
        final String id = c.id, uri = c.uri, projectId = a.project.id;
        final long in = c.inMs, out = c.outMs, source = c.sourceDurationMs;
        final File file = cacheFile(uri, in, out, source);
        cancelled = new AtomicBoolean();
        final AtomicBoolean flag = cancelled;
        running = true; progress = 0;
        a.tell("Reversing…");
        a.renderInspector();
        worker.execute(() -> {
            long duration = 0; String failure = null; boolean wasCancelled = false;
            try {
                file.getParentFile().mkdirs();
                duration = cachedDuration(file);
                if (duration <= 0) {
                    if (new android.os.StatFs(file.getParentFile().getPath()).getAvailableBytes() < 256L * 1024 * 1024) throw new java.io.IOException("Not enough storage to reverse this footage.");
                    duration = ReverseTranscoder.reverse(a, uri, in, out, file, flag::get, this::report).durationMs;
                }
            } catch (java.util.concurrent.CancellationException e) {
                wasCancelled = true;
            } catch (Exception | OutOfMemoryError e) {
                CrashLogs.record("Reverse failed", e instanceof Exception ? (Exception) e : new IllegalStateException(e));
                failure = e instanceof OutOfMemoryError ? "Not enough memory to reverse this footage." : String.valueOf(e.getMessage());
            }
            final long measured = duration; final String error = failure; final boolean stopped = wasCancelled || flag.get();
            a.main.post(() -> finish(id, uri, in, out, projectId, file, measured, error, stopped));
        });
    }

    private void report(int percent) {
        progress = percent;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastUi < 400 && percent < 100) return;
        lastUi = now;
        a.main.post(a.layerTools::showProgress);
    }

    private void finish(String id, String uri, long in, long out, String projectId, File file, long duration, String error, boolean stopped) {
        running = false;
        if (a.isDestroyed()) return;
        if (stopped) { a.tell("Reverse cancelled. The footage is unchanged."); a.renderInspector(); return; }
        if (error != null || duration <= 0) { a.tell(error == null ? "Reversing failed." : error); a.renderInspector(); return; }
        EditProject.Clip c = a.project != null && projectId.equals(a.project.id) ? find(id) : null;
        if (c == null || c.reversed || !uri.equals(c.uri) || c.inMs != in || c.outMs != out) {
            a.tell("The footage changed while it was being reversed, so the result was not applied.");
            a.renderInspector();
            return;
        }
        final String derivative = Uri.fromFile(file).toString();
        a.edit("Footage reversed", () -> {
            EditProject.Clip target = find(id);
            if (target == null) throw new IllegalArgumentException("The selected layer changed.");
            ReverseOps.reverse(target, derivative, duration);
        });
        a.renderInspector();
    }

    private EditProject.Clip find(String id) {
        for (EditProject.Clip c : a.project.clips) if (c.id.equals(id)) return c;
        return null;
    }

    /** Key: source identity, trim and processing version; anything that changes the output changes the name. */
    private File cacheFile(String uri, long in, long out, long source) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((VERSION + "|" + uri + "|" + in + "|" + out + "|" + source).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder();
            for (int i = 0; i < 16; i++) name.append(String.format(java.util.Locale.US, "%02x", digest[i]));
            return new File(new File(a.getFilesDir(), "reversed"), name + ".mp4");
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    /** Duration of a finished cached copy, or 0 when absent or unreadable. */
    private long cachedDuration(File file) {
        if (!file.isFile() || file.length() == 0) return 0;
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getPath());
            String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return value == null ? 0 : Long.parseLong(value);
        } catch (Exception e) { file.delete(); return 0; }
        finally { try { retriever.release(); } catch (Exception ignored) { } }
    }
}

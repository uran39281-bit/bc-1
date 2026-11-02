package com.blackice.bc1;

import android.graphics.Bitmap;
import android.net.Uri;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.LayerCapture;
import java.io.File;
import java.io.FileOutputStream;
import java.util.UUID;

/**
 * Layer Tools → Screenshot: renders the selected layer alone, at the playhead, into a transparent PNG at the
 * canvas resolution and inserts it as a new independent image layer in one undoable edit.
 */
final class ScreenshotTool {
    /** Short edge of the captured frame; the canvas aspect ratio is kept. */
    static final int SHORT_EDGE = 1080;
    /** An image overlay is drawn 45% of the canvas width at scale 1, so this fills the canvas exactly. */
    static final float FULL_CANVAS_SCALE = 1f / .45f;

    private final EditorActivity a;
    private boolean running;

    ScreenshotTool(EditorActivity activity) { a = activity; }

    boolean running() { return running; }

    /** Why the action is unavailable right now, or null when it can run. */
    String unavailableReason() {
        EditProject.Clip c = a.clip();
        EditProject.Overlay o = a.overlay();
        if (running) return "Capturing…";
        if (c == null && o == null) return "Select a layer.";
        if (c != null && c.track >= EditProject.AUDIO_FIRST) return "Audio layers have no picture to capture.";
        if (o != null && o.visualizerClipId != null) return "This layer has no picture to capture.";
        long t = a.project.playheadMs;
        if (c != null) { EditProject.Placement p = a.project.placements().get(a.selected); if (t < p.startMs || t >= p.endMs) return "The playhead is outside the selected layer."; }
        if (o != null && (t < o.startMs || t >= o.endMs)) return "The playhead is outside the selected layer.";
        if (a.loading || a.importing || a.captionApplying || a.uiLocked) return "Wait for the current operation to finish.";
        return null;
    }

    void start() {
        String reason = unavailableReason();
        if (reason != null) { a.tell(reason); a.renderInspector(); return; }
        if (!a.editable()) return;
        EditProject.Clip c = a.clip();
        EditProject.Overlay o = a.overlay();
        final String layerId = c != null ? c.id : o.id;
        final String layerName = c != null ? c.name : o.label();
        final int blend = c != null ? c.blend : o.blend;
        final long time = a.project.playheadMs;
        final String projectId = a.project.id;
        final long revision = a.editRevision;
        final EditProject snapshot;
        try { snapshot = ProjectStore.snapshot(a.project); } catch (RuntimeException e) { a.tell(e.getMessage()); return; }
        running = true;
        a.tell("Capturing…");
        a.renderInspector();
        a.io.execute(() -> {
            File file = null;
            String failure = null;
            try {
                File directory = new File(a.getFilesDir(), "media");
                directory.mkdirs();
                if (new android.os.StatFs(directory.getPath()).getAvailableBytes() < 64L * 1024 * 1024) throw new java.io.IOException("Not enough storage to save the screenshot.");
                Bitmap frame = LayerCapture.render(a, snapshot, layerId, time, SHORT_EDGE);
                file = new File(directory, "screenshot-" + UUID.randomUUID() + ".png");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    if (!frame.compress(Bitmap.CompressFormat.PNG, 100, out)) throw new java.io.IOException("The screenshot could not be saved.");
                } finally { frame.recycle(); }
            } catch (LayerCapture.Unavailable e) {
                failure = e.getMessage();
            } catch (Exception | OutOfMemoryError e) {
                CrashLogs.record("Layer screenshot failed", e instanceof Exception ? (Exception) e : new IllegalStateException(e));
                failure = e instanceof OutOfMemoryError ? "Not enough memory to capture this frame." : String.valueOf(e.getMessage());
            }
            final File saved = file;
            final String error = failure;
            a.main.post(() -> finish(saved, error, layerId, layerName, blend, time, projectId, revision));
        });
    }

    /** UI thread. Inserts the new layer only if nothing it depends on changed while it rendered. */
    private void finish(File file, String error, String layerId, String layerName, int blend, long time, String projectId, long revision) {
        running = false;
        if (a.isDestroyed()) { discard(file); return; }
        if (error != null || file == null) { discard(file); a.tell(error == null ? "The screenshot failed." : error); a.renderInspector(); return; }
        boolean stale = a.project == null || !projectId.equals(a.project.id) || revision != a.editRevision || a.project.playheadMs != time
                || !layerStillExists(layerId);
        if (stale) { discard(file); a.tell("The project changed while capturing, so the screenshot was discarded."); a.renderInspector(); return; }
        String uri = Uri.fromFile(file).toString();
        a.edit("Screenshot added", () -> {
            EditProject.Overlay layer = new EditProject.Overlay();
            layer.imageUri = uri;
            layer.name = "Screenshot • " + layerName + " • " + EditorActivity.clock(time);
            layer.startMs = time;
            layer.endMs = time + 3000;
            layer.x = 0; layer.y = 0; layer.scale = FULL_CANVAS_SCALE; layer.opacity = 1f;
            layer.blend = blend; // applied once when this layer composites with the others
            a.project.overlays.add(layer);
            a.selected = -1;
            a.selectedOverlay = a.project.overlays.size() - 1;
        });
        if (a.editRevision == revision) { discard(file); } // the edit was rejected; do not leave an unreferenced file
        a.panels.reset();
        a.renderInspector();
    }

    private boolean layerStillExists(String id) {
        for (EditProject.Clip c : a.project.clips) if (c.id.equals(id)) return true;
        for (EditProject.Overlay o : a.project.overlays) if (o.id.equals(id)) return true;
        return false;
    }

    private static void discard(File file) { if (file != null) //noinspection ResultOfMethodCallIgnored
        file.delete(); }
}

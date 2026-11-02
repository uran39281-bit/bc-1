package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.HandlerThread;
import com.blackice.bc1.core.OutputSpec;
import com.blackice.bc1.core.RenderPlan;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.OverlayPainter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders ONE layer of a project at one instant into a transparent premultiplied bitmap at the project's canvas
 * resolution, through the same RenderPlan, transform, effect and opacity rules as the live preview and export.
 * Other layers, the backdrop and every editor overlay (handles, guides, outlines) are never drawn. Blend modes
 * are not applied: they need a backdrop, so callers keep them as metadata on the new layer.
 *
 * Blocks until done; never call it on the UI thread. The project must be a private copy.
 */
public final class LayerCapture {
    /** The layer cannot be rendered right now (not on screen, hidden, or outside its time range). */
    public static final class Unavailable extends Exception { public Unavailable(String message) { super(message); } }

    private static final long TIMEOUT_MS = 30_000;
    /** Frames larger than this many pixels are refused rather than risking an out-of-memory failure. */
    private static final long MAX_PIXELS = 3840L * 2160L;

    private LayerCapture() { }

    public static Bitmap render(Context context, EditProject project, String layerId, long timeMs, int shortEdge) throws Exception {
        OutputSpec spec = OutputSpec.of(project, shortEdge);
        if ((long) spec.width * spec.height > MAX_PIXELS) throw new IllegalArgumentException("The canvas is too large to capture.");
        RenderPlan plan = new RenderPlan(project, true);
        long time = Math.max(0, Math.min(timeMs, Math.max(0, project.durationMs() - 1)));
        for (EditProject.Overlay o : plan.overlaysAt(time)) if (o.id.equals(layerId)) return renderOverlay(context, spec, o, time, plan.overlayPose(o, time));
        for (RenderPlan.LayerDraw d : plan.layersAt(time)) if (d.clip.id.equals(layerId)) return renderClip(context, spec, d);
        throw new Unavailable("The selected layer is not visible at the playhead.");
    }

    private static Bitmap renderOverlay(Context context, OutputSpec spec, EditProject.Overlay source, long time, com.blackice.bc1.editor.TransformMath.Transform world) throws Exception {
        EditProject.Overlay layer = source.copy();
        layer.blend = 0; // the blend belongs to compositing with other layers, not to this layer's own pixels
        OverlayPainter painter = new OverlayPainter(context);
        Bitmap bitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888);
        try {
            bitmap.eraseColor(Color.TRANSPARENT);
            painter.draw(new Canvas(bitmap), spec.width, spec.height, layer, Math.max(0, time - source.startMs), world);
            return bitmap;
        } catch (Exception | Error failure) {
            bitmap.recycle();
            throw failure;
        } finally {
            painter.release();
        }
    }

    private static Bitmap renderClip(Context context, OutputSpec spec, RenderPlan.LayerDraw draw) throws Exception {
        HandlerThread thread = new HandlerThread("bc1-layer-capture");
        thread.start();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Bitmap> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        new Handler(thread.getLooper()).post(() -> {
            EglCore egl = null; Compositor compositor = null; FrameSource source = null;
            try {
                egl = EglCore.offscreen();
                compositor = new Compositor(context, spec.width, spec.height, true);
                compositor.setTransparentBackground(true);
                EditProject.Clip c = draw.clip;
                long sourceUs = draw.sourceTimeMs * 1000;
                source = c.image ? new ImageSource(context, c.uri, compositor)
                        : new VideoSource(context, c.uri, draw.sourceTimeMs, Math.max(c.outMs, draw.sourceTimeMs + 1), compositor, true);
                source.prepare(sourceUs);
                source.latch();
                compositor.beginPreviewFrame(false);
                source.draw(compositor, draw);
                compositor.endLayers();
                GLES30.glFinish();
                result.set(compositor.readPreview());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                try { if (source != null) source.release(); } catch (Throwable ignored) { }
                try { if (compositor != null) compositor.release(); } catch (Throwable ignored) { }
                try { if (egl != null) egl.close(); } catch (Throwable ignored) { }
                done.countDown();
            }
        });
        try {
            if (!done.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) throw new IllegalStateException("Capturing the frame took too long.");
            if (failure.get() instanceof Exception) throw (Exception) failure.get();
            if (failure.get() != null) throw new IllegalStateException(failure.get());
            return result.get();
        } finally {
            thread.quitSafely();
        }
    }
}

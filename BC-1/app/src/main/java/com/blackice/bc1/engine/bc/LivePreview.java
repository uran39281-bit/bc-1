package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import android.opengl.GLES30;
import com.blackice.bc1.core.OutputSpec;
import com.blackice.bc1.core.RenderPlan;
import com.blackice.bc1.editor.EditProject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ICE Lite live preview: draws the frame at the playhead straight to a surface with the same RenderPlan
 * and compositor as export, so effects, blend modes, transforms, layers and titles update the moment a
 * value changes. Decoders and pictures are kept between frames: a parameter change only redraws (no
 * decoding), forward scrubbing continues the open decoders, and only a backward jump reopens them.
 *
 * All GL work runs on one private thread. Requests are coalesced: while a frame is rendering, newer
 * requests replace older ones, so fast slider drags and scrubs never queue up.
 */
public final class LivePreview implements TextureView.SurfaceTextureListener {
    /** Called on the render thread after each frame is presented (tests and diagnostics). */
    public interface FrameListener { void onFrame(long timeMs, long renderMs); }

    private static final long REUSE_WINDOW_US = 3_000_000; // forward jumps up to 3 s reuse the open decoder

    private static final class Request {
        final EditProject project; final long timeMs; final CountDownLatch done; volatile boolean presented;
        Request(EditProject project, long timeMs, CountDownLatch done) { this.project = project; this.timeMs = timeMs; this.done = done; }
    }

    private static final class Slot {
        final FrameSource source; final String key; long lastUs;
        Slot(FrameSource source, String key, long lastUs) { this.source = source; this.key = key; this.lastUs = lastUs; }
    }

    private final Context context;
    private final HandlerThread thread = new HandlerThread("bc1-live-preview");
    private final Handler gl;
    private final AtomicReference<Request> pending = new AtomicReference<>();
    /** The newest frame asked for, redrawn when a new surface arrives (layout rebuilds, rotation). */
    private volatile Request latest;
    private volatile FrameListener listener;
    private volatile String lastError;
    /** Time of the last frame presented, or -1 before the first (tests and diagnostics). */
    private volatile long lastFrameMs = -1;
    // Render-thread state.
    private Surface surface;
    private SurfaceTexture surfaceTexture;
    private EglCore egl;
    private Compositor compositor;
    private OverlayLayer overlays;
    private int width, height;
    private long frames;
    private final Map<String, Slot> slots = new HashMap<>();

    public LivePreview(Context context) {
        this.context = context.getApplicationContext();
        thread.start();
        gl = new Handler(thread.getLooper());
    }

    public void setFrameListener(FrameListener l) { listener = l; }
    /** Last render error message, or null after a successful frame. */
    public String lastError() { return lastError; }
    public long lastFrameMs() { return lastFrameMs; }
    /** One-line state for bug reports and test failures. */
    public String debug() { Request r = latest; return "live last=" + lastFrameMs + " requested=" + (r == null ? -1 : r.timeMs) + " frames=" + frames + " window=" + (egl != null && egl.hasWindow()) + " error=" + lastError; }

    // ---- Surface ------------------------------------------------------------------------------

    /** Draws into an arbitrary surface (an ImageReader in tests). Blocks until attached. */
    public void attach(Surface target) {
        runOnGl(() -> bind(target, null));
        Request last = latest;
        if (last != null) request(last.project, last.timeMs);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        gl.post(() -> {
            bind(new Surface(st), st);
            // Request AFTER binding on the GL thread. An already queued drain can otherwise
            // consume the coalesced request before bind, leaving the new window black forever.
            Request last = latest;
            if (last != null) request(last.project, last.timeMs);
        }); // the UI thread never waits for surface attachment or rendering
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
        gl.post(() -> {
            if(surfaceTexture!=st)return;
            // TextureView resets its buffer size during layout. Keep the project-sized GL viewport
            // and buffer in agreement when the phone inspector changes the preview's display size.
            if(width>0&&height>0)st.setDefaultBufferSize(width,height);
            Request last=latest;if(last!=null)request(last.project,last.timeMs);
        });
    }
    /** Returns false: the render thread releases the SurfaceTexture after its last frame, so a frame in flight never draws into a dead surface. */
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (!gl.post(() -> { if(surfaceTexture==st)detachWindow();else st.release(); })) st.release();
        return false;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    private void bind(Surface target, SurfaceTexture st) {
        if(target==null||!target.isValid()){lastError="Preview surface is unavailable";com.blackice.bc1.CrashLogs.record("Preview surface unavailable",new IllegalStateException());return;}
        try { bindValid(target,st); }
        catch(RuntimeException error){lastError="Preview surface attachment failed: "+error.getClass().getSimpleName();com.blackice.bc1.CrashLogs.record("Preview surface attachment failed",error);unbind();}
    }
    private void bindValid(Surface target, SurfaceTexture st) {
        android.util.Log.d("BC1Live", "surface attached" + (st == null ? " (reader)" : ""));
        detachWindow();
        surface = target; surfaceTexture = st;
        if (egl != null) {
            // Same context: compiled shaders, open decoders and the last decoded frames carry over.
            try { egl.setWindow(target); }
            catch (RuntimeException e) {
                surface = null; surfaceTexture = null; // keep the new window out of the full teardown
                unbind();
                surface = target; surfaceTexture = st;
                egl = new EglCore(target);
            }
        } else egl = new EglCore(target);
        if (st != null && width > 0) st.setDefaultBufferSize(width, height);
    }

    /** Lets go of the current window (the view's surface is going away) but keeps the GL context when the driver allows. */
    private void detachWindow() {
        if (egl != null && egl.hasWindow() && !egl.dropWindow()) { unbind(); return; }
        if (surface != null) surface.release();
        if (surfaceTexture != null) surfaceTexture.release(); // handed over by onSurfaceTextureDestroyed
        surface = null; surfaceTexture = null;
    }

    /** Releases everything: decoders, programs, context and window. */
    private void unbind() {
        releaseSources();
        if (overlays != null) { overlays.release(); overlays = null; }
        if (compositor != null) { compositor.release(); compositor = null; }
        if (egl != null) { egl.close(); egl = null; }
        if (surface != null) surface.release();
        if (surfaceTexture != null) surfaceTexture.release();
        surface = null; surfaceTexture = null; width = height = 0;
    }

    // ---- Requests -----------------------------------------------------------------------------

    /** Shows the frame at {@code timeMs}. Returns at once; the newest request wins. The project must be a private copy. */
    public void request(EditProject snapshot, long timeMs) {
        Request r = new Request(snapshot, timeMs, null);
        latest = r;
        if (pending.getAndSet(r) == null) gl.post(this::drain);
    }

    /** Renders synchronously (tests). Returns false on timeout or error. */
    public boolean renderNow(EditProject snapshot, long timeMs, long timeoutMs) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Request r = new Request(snapshot, timeMs, done);
        latest = r;
        pending.set(r);
        gl.post(this::drain);
        return done.await(timeoutMs, TimeUnit.MILLISECONDS) && r.presented && lastError == null;
    }

    /** Closes decoders (frees hardware for playback or export); the next frame reopens what it needs. */
    public void releaseDecoders() { gl.post(this::releaseSources); }

    /** Stops the render thread. */
    public void release() { runOnGl(this::unbind); thread.quitSafely(); }

    private void drain() {
        Request r = pending.getAndSet(null);
        if (r == null) return;
        try {
            if (egl == null || !egl.hasWindow()) { android.util.Log.d("BC1Live", "frame at " + r.timeMs + " waits for a surface"); return; } // binding one redraws the latest request
            long start = System.nanoTime();
            render(r.project, r.timeMs);
            lastError = null;
            lastFrameMs = r.timeMs; r.presented=true;
            long took = (System.nanoTime() - start) / 1_000_000;
            if (++frames % 20 == 1 || took > 250) android.util.Log.d("BC1Live", "frame " + frames + " at " + r.timeMs + " ms took " + took + " ms, " + slots.size() + " sources open");
            FrameListener l = listener;
            if (l != null) l.onFrame(r.timeMs, (System.nanoTime() - start) / 1_000_000);
        } catch (Exception t) {
            lastError = String.valueOf(t.getMessage());
            android.util.Log.w("BC1Live", "Live preview frame failed", t);
            com.blackice.bc1.CrashLogs.record("Live renderer frame failed",t);
            releaseSources(); // reopen cleanly next time
        } finally {
            if (r.done != null) r.done.countDown();
        }
    }

    private void render(EditProject project, long timeMs) throws Exception {
        if ((project.clips.isEmpty() && project.overlays.isEmpty())) { egl.makeCurrent(); clearBlack(); egl.swap(); return; }
        long t0 = System.nanoTime();
        OutputSpec spec = OutputSpec.of(project, project.previewHeight);
        if (compositor == null || spec.width != width || spec.height != height) {
            releaseSources();
            if (overlays != null) { overlays.release(); overlays = null; }
            if (compositor != null) compositor.release();
            width = spec.width; height = spec.height;
            if (surfaceTexture != null) surfaceTexture.setDefaultBufferSize(width, height);
            compositor = new Compositor(context, width, height, true); // per-effect passes: effect changes compile in milliseconds
            overlays = new OverlayLayer(context, width, height);
        }
        long tCompositor = System.nanoTime();
        RenderPlan plan = new RenderPlan(project, true);
        long time = Math.max(0, Math.min(timeMs, Math.max(0, project.durationMs() - 1)));
        List<RenderPlan.LayerDraw> layers = plan.layersAt(time);
        FrameSource[] frame = new FrameSource[layers.size()];
        Set<String> used = new HashSet<>();
        for (int i = 0; i < layers.size(); i++) {
            RenderPlan.LayerDraw d = layers.get(i);
            EditProject.Clip c = d.clip;
            long sourceUs = d.sourceTimeMs * 1000;
            String key = c.id;
            String identity = c.uri + "|" + c.image + "|" + c.outMs;
            Slot slot = slots.get(key);
            boolean reusable = slot != null && slot.key.equals(identity)
                    && (c.image || (sourceUs >= slot.lastUs && sourceUs - slot.lastUs <= REUSE_WINDOW_US));
            if (!reusable) {
                if (slot != null) slot.source.release();
                FrameSource source = c.image ? new ImageSource(context, c.uri, compositor)
                        // Fitted copy, then the plain layer shader: the external-texture variant of the large effects
                        // shader can take many seconds to compile on some GPUs, and a preview-sized copy costs ~nothing.
                        : new VideoSource(context, c.uri, d.sourceTimeMs, Math.max(c.outMs, d.sourceTimeMs + 1), compositor, true);
                slot = new Slot(source, identity, sourceUs);
                slots.put(key, slot);
            }
            slot.lastUs = sourceUs;
            frame[i] = slot.source;
            used.add(key);
            slot.source.prepare(sourceUs);
        }
        long tPrepare = System.nanoTime();
        for (FrameSource s : frame) s.latch();
        long tLatch = System.nanoTime();
        // Decoders not on screen are closed so a few layers never hold every hardware decoder.
        for (String key : new ArrayList<>(slots.keySet())) if (!used.contains(key)) slots.remove(key).source.release();

        egl.makeCurrent();
        boolean blend = false;
        for (RenderPlan.LayerDraw d : layers) blend |= d.blend != 0;
        compositor.beginFrame(blend);
        for (int i = 0; i < layers.size(); i++) frame[i].draw(compositor, layers.get(i));
        compositor.endLayers();
        GLES30.glFinish();
        long tDraw = System.nanoTime();
        List<EditProject.Overlay> active = plan.overlaysAt(time);
        if (!active.isEmpty()) compositor.drawOverlay(overlays.texture(active, time));
        egl.swap();
        long tSwap = System.nanoTime();
        if (tSwap - t0 > 250_000_000L) android.util.Log.d("BC1Live", String.format(java.util.Locale.US,
                "slow frame: setup %d ms, open+prepare %d ms, latch %d ms, draw %d ms, swap %d ms",
                (tCompositor - t0) / 1_000_000, (tPrepare - tCompositor) / 1_000_000, (tLatch - tPrepare) / 1_000_000,
                (tDraw - tLatch) / 1_000_000, (tSwap - tDraw) / 1_000_000));
    }

    private static void clearBlack() {
        android.opengl.GLES30.glClearColor(0, 0, 0, 1);
        android.opengl.GLES30.glClear(android.opengl.GLES30.GL_COLOR_BUFFER_BIT);
    }

    private void releaseSources() {
        for (Slot s : slots.values()) try { s.source.release(); } catch (Exception ignored) { }
        slots.clear();
    }

    private void runOnGl(Runnable task) {
        if (Thread.currentThread() == thread) { task.run(); return; }
        CountDownLatch done = new CountDownLatch(1);
        if (!gl.post(() -> { try { task.run(); } finally { done.countDown(); } })) return;
        try { done.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

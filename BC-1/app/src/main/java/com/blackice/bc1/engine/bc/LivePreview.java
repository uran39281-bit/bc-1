package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
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

    /** Requests closer together than this are a scrub: newly opened decoders show their key frame at once. */
    private static final long SCRUB_GAP_NS = 120_000_000L;
    /** After a scrub stops, the exact frame replaces the key-frame approximation this much later. */
    private static final long SETTLE_MS = 150;
    /** Playback opens decoders this far ahead of a clip's first frame. */
    private static final long PREROLL_MS = 500;

    private static final class Request {
        final EditProject project; final long timeMs; final CountDownLatch done; final boolean exact;
        Request(EditProject project, long timeMs, CountDownLatch done, boolean exact) { this.project = project; this.timeMs = timeMs; this.done = done; this.exact = exact; }
    }

    /** Playback events, on the render thread. */
    public interface PlaybackListener { void onEnded(long positionMs); }

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
    // Render-thread state.
    private Surface surface;
    private SurfaceTexture surfaceTexture;
    private EglCore egl;
    private Compositor compositor;
    private OverlayLayer overlays;
    private int width, height;
    private long frames;
    private final Map<String, Slot> slots = new HashMap<>();
    private long lastRequestNs, lastLogMs;
    // Playback (render thread, except the volatile fields read by the editor).
    private volatile boolean playing;
    private volatile EditProject playProject;
    private volatile PlaybackAudio audio;
    private volatile long stoppedAtMs = -1;
    private PlaybackListener playbackListener;
    private android.view.Choreographer choreographer;
    private long playedFrames, droppedFrames;

    public LivePreview(Context context) {
        this.context = context.getApplicationContext();
        thread.start();
        gl = new Handler(thread.getLooper());
    }

    public void setFrameListener(FrameListener l) { listener = l; }
    /** Last render error message, or null after a successful frame. */
    public String lastError() { return lastError; }

    // ---- Surface ------------------------------------------------------------------------------

    /** Draws into an arbitrary surface (an ImageReader in tests). Blocks until attached. */
    public void attach(Surface target) {
        runOnGl(() -> bind(target, null));
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        gl.post(guarded("binding the preview surface", () -> bind(new Surface(st), st))); // the UI thread never waits for the render thread
    }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) { }
    /** Returns false: the render thread releases the SurfaceTexture after its last frame, so a frame in flight never draws into a dead surface. */
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        if (!gl.post(guarded("releasing the preview surface", this::detachWindow))) st.release();
        return false;
    }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture st) { }

    private void bind(Surface target, SurfaceTexture st) {
        android.util.Log.d("BC1Live", "bind window " + (st != null ? "texture view" : "surface") + (egl != null ? ", reusing context" : ""));
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
        // Redraw the newest frame asked for. Done here, on the render thread after binding, because a
        // request made just before may already have been drained (and dropped) without a window.
        Request last = latest;
        if (last != null) request(last.project, last.timeMs);
    }

    /** Lets go of the current window (the view's surface is going away) but keeps the GL context when the driver allows. */
    private void detachWindow() {
        if (surface != null) android.util.Log.d("BC1Live", "window detached");
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

    /**
     * Shows the frame at {@code timeMs}. Returns at once; the newest request wins. The project must be a
     * private copy. While playing, the project replaces the one being played (edits show live).
     */
    public void request(EditProject snapshot, long timeMs) {
        if (playing) { playProject = snapshot; return; }
        long now = System.nanoTime();
        boolean scrubbing = now - lastRequestNs < SCRUB_GAP_NS;
        lastRequestNs = now;
        Request r = new Request(snapshot, timeMs, null, !scrubbing);
        latest = r;
        if (pending.getAndSet(r) == null) gl.post(this::drain);
    }

    /** Renders synchronously and exactly (tests). Returns false on timeout or error. */
    public boolean renderNow(EditProject snapshot, long timeMs, long timeoutMs) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Request r = new Request(snapshot, timeMs, done, true);
        latest = r;
        pending.set(r);
        gl.post(this::drain);
        return done.await(timeoutMs, TimeUnit.MILLISECONDS) && lastError == null;
    }

    // ---- Playback ------------------------------------------------------------------------------

    /**
     * Plays {@code snapshot} from {@code fromMs} until {@code untilMs} with sound, rendering every frame
     * through ICE Lite (all layers, effects, blend modes, titles) on the audio clock. Frames that cannot
     * be drawn in time are skipped, never delayed, so picture and sound stay together.
     */
    public void play(EditProject snapshot, long fromMs, long untilMs, PlaybackListener l) {
        playProject = snapshot; stoppedAtMs = -1; playing = true;
        gl.post(guarded("starting playback", () -> startPlayback(snapshot, fromMs, untilMs, l)));
    }

    /** Stops playback; the picture stays on the last frame. Returns where it stopped. */
    public long stop() {
        long at = positionMs();
        playing = false;
        stoppedAtMs = at;
        gl.post(guarded("stopping playback", this::endPlayback));
        return at;
    }

    public boolean isPlaying() { return playing; }

    /** One-line state for diagnostics and test failures. */
    public String debugState() {
        Request l = latest;
        return "playing=" + playing + " position=" + positionMs() + " latest=" + (l == null ? "none" : l.timeMs + (l.exact ? " exact" : " approx"))
                + " frames=" + frames + " played=" + playedFrames + " lastError=" + lastError;
    }

    /** Where playback is now (or stopped). */
    public long positionMs() {
        PlaybackAudio a = audio;
        if (a != null && playing) return Math.min(a.positionMs(), a.untilMs());
        return stoppedAtMs;
    }

    /** Frames drawn and frames skipped during the last playback (diagnostics and tests). */
    public long[] playbackStats() { return new long[]{playedFrames, droppedFrames}; }

    private void startPlayback(EditProject snapshot, long fromMs, long untilMs, PlaybackListener l) {
        endPlayback();
        android.util.Log.d("BC1Live", "playback " + (playing ? "starts" : "was stopped before starting") + " at " + fromMs + " ms, until " + untilMs + " ms");
        if (!playing) return; // stopped before it started
        playbackListener = l; playedFrames = 0; droppedFrames = 0;
        PlaybackAudio a = new PlaybackAudio(context, com.blackice.bc1.core.AudioPlan.of(snapshot, true), fromMs, untilMs);
        audio = a;
        a.start();
        if (choreographer == null) choreographer = android.view.Choreographer.getInstance();
        choreographer.postFrameCallback(playbackFrame);
    }

    private long lastDrawnMs = -1;
    private final android.view.Choreographer.FrameCallback playbackFrame = new android.view.Choreographer.FrameCallback() {
        @Override public void doFrame(long frameTimeNanos) {
            PlaybackAudio a = audio;
            if (!playing || a == null) return;
            long t = Math.min(a.positionMs(), a.untilMs());
            if (t >= a.untilMs()) {
                long end = a.untilMs();
                endPlayback(); playing = false; stoppedAtMs = end;
                PlaybackListener l = playbackListener;
                if (l != null) l.onEnded(end);
                return;
            }
            EditProject p = playProject;
            int fps = Math.max(1, p.frameRate);
            long frameMs = t - t % Math.max(1, 1000 / fps); // project frame grid
            try {
                if (frameMs != lastDrawnMs && egl != null && egl.hasWindow()) {
                    long before = lastDrawnMs;
                    render(p, frameMs, true);
                    if (before >= 0 && frameMs - before > 1000 / fps * 3 / 2) droppedFrames += (frameMs - before) / Math.max(1, 1000 / fps) - 1;
                    lastDrawnMs = frameMs; playedFrames++;
                    FrameListener fl = listener;
                    if (fl != null) fl.onFrame(frameMs, 0);
                }
            } catch (Throwable error) {
                com.blackice.bc1.CrashLog.handled("playback frame", error);
                lastError = String.valueOf(error.getMessage());
                releaseSources();
            }
            if (playing) choreographer.postFrameCallback(this);
        }
    };

    private void endPlayback() {
        if (choreographer != null) choreographer.removeFrameCallback(playbackFrame);
        PlaybackAudio a = audio;
        if (a != null) android.util.Log.d("BC1Live", "playback ends: " + playedFrames + " frames drawn, " + droppedFrames + " skipped");
        audio = null;
        if (a != null) a.release();
        lastDrawnMs = -1;
    }

    /** Closes decoders (frees hardware for playback or export); the next frame reopens what it needs. */
    public void releaseDecoders() { gl.post(guarded("closing preview decoders", this::releaseSources)); }

    /** Stops the render thread. */
    public void release() { playing = false; runOnGl(() -> { endPlayback(); unbind(); }); thread.quitSafely(); }

    private void drain() {
        Request r = pending.getAndSet(null);
        if (r == null) return;
        try {
            if (egl == null || !egl.hasWindow()) { // no surface yet: binding one redraws the latest request
                android.util.Log.d("BC1Live", "frame at " + r.timeMs + " ms waits for a window");
                return;
            }
            if (playing) return; // playback owns the picture
            long start = System.nanoTime();
            render(r.project, r.timeMs, r.exact);
            lastError = null;
            // A scrub shows key frames; once it stops, draw the exact frame.
            if (!r.exact) gl.postDelayed(() -> {
                if (latest == r && !playing) { Request exact = new Request(r.project, r.timeMs, null, true); if (pending.getAndSet(exact) == null) drain(); }
            }, SETTLE_MS);
            long took = (System.nanoTime() - start) / 1_000_000;
            long nowMs = System.currentTimeMillis();
            if (++frames % 20 == 1 || took > 250 || nowMs - lastLogMs > 1000) { lastLogMs = nowMs; } else took = -1;
            if (took >= 0) android.util.Log.d("BC1Live", "frame " + frames + " at " + r.timeMs + " ms took " + took + " ms, " + slots.size() + " sources open");
            FrameListener l = listener;
            if (l != null) l.onFrame(r.timeMs, (System.nanoTime() - start) / 1_000_000);
        } catch (Throwable t) {
            lastError = String.valueOf(t.getMessage());
            android.util.Log.w("BC1Live", "Live preview frame failed", t);
            releaseSources(); // reopen cleanly next time
        } finally {
            if (r.done != null) r.done.countDown();
        }
    }

    /** @param exact false while scrubbing: a decoder opened for this frame shows its key frame instead of decoding up to the time */
    private void render(EditProject project, long timeMs, boolean exact) throws Exception {
        if (project.clips.isEmpty()) { egl.makeCurrent(); clearBlack(); egl.swap(); return; }
        long t0 = System.nanoTime();
        OutputSpec spec = OutputSpec.of(project, project.previewHeight);
        if (compositor == null || spec.width != width || spec.height != height) {
            releaseSources();
            if (overlays != null) { overlays.release(); overlays = null; }
            if (compositor != null) compositor.release();
            width = spec.width; height = spec.height;
            if (surfaceTexture != null) surfaceTexture.setDefaultBufferSize(width, height);
            compositor = new Compositor(context, width, height);
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
                // Scrubbing: the key frame is ready at once; decoding up to the exact time waits until the scrub stops.
                if (!exact && source instanceof VideoSource) sourceUs = Math.min(sourceUs, ((VideoSource) source).syncUs);
            }
            slot.lastUs = sourceUs;
            frame[i] = slot.source;
            used.add(key);
            slot.source.prepare(sourceUs);
        }
        long tPrepare = System.nanoTime();
        for (FrameSource s : frame) s.latch();
        long tLatch = System.nanoTime();
        // Playback: open decoders for clips starting within the next half second, so a cut never waits
        // for a decoder to start (they begin decoding in the background right away).
        Set<String> upcoming = new HashSet<>();
        if (playing) for (RenderPlan.LayerDraw d : plan.layersAt(Math.min(project.durationMs() - 1, time + PREROLL_MS))) {
            EditProject.Clip c = d.clip;
            if (c.image || used.contains(c.id)) continue;
            upcoming.add(c.id);
            if (!slots.containsKey(c.id)) {
                VideoSource next = new VideoSource(context, c.uri, c.inMs, Math.max(c.outMs, c.inMs + 1), compositor, true);
                slots.put(c.id, new Slot(next, c.uri + "|" + c.image + "|" + c.outMs, c.inMs * 1000));
            }
        }
        // Decoders not on screen (or about to be) are closed so a few layers never hold every hardware decoder.
        for (String key : new ArrayList<>(slots.keySet())) if (!used.contains(key) && !upcoming.contains(key)) slots.remove(key).source.release();

        egl.makeCurrent();
        boolean blend = false;
        for (RenderPlan.LayerDraw d : layers) blend |= d.blend != 0;
        compositor.beginFrame(blend);
        for (int i = 0; i < layers.size(); i++) frame[i].draw(compositor, layers.get(i));
        compositor.endLayers();
        long tDraw = System.nanoTime();
        List<EditProject.Overlay> active = plan.overlaysAt(time);
        if (!active.isEmpty()) compositor.drawOverlay(overlays.texture(active, time));
        egl.swap();
        long tSwap = System.nanoTime();
        if (!playing && tSwap - t0 > 250_000_000L) android.util.Log.d("BC1Live", String.format(java.util.Locale.US,
                "slow frame: setup %d ms, open+prepare %d ms, latch %d ms, draw %d ms, swap %d ms",
                (tCompositor - t0) / 1_000_000, (tPrepare - tCompositor) / 1_000_000, (tLatch - tPrepare) / 1_000_000,
                (tDraw - tLatch) / 1_000_000, (tSwap - tDraw) / 1_000_000)); // draw and swap overlap on the GPU
    }

    private static void clearBlack() {
        android.opengl.GLES30.glClearColor(0, 0, 0, 1);
        android.opengl.GLES30.glClear(android.opengl.GLES30.GL_COLOR_BUFFER_BIT);
    }

    private void releaseSources() {
        for (Slot s : slots.values()) try { s.source.release(); } catch (Exception ignored) { }
        slots.clear();
    }

    /**
     * Render-thread work that must never close the app: a failure (a surface the system already took
     * back, a driver error) is recorded and the preview starts over cleanly on the next frame.
     */
    private Runnable guarded(String what, Runnable task) {
        return () -> {
            try { task.run(); }
            catch (Throwable t) {
                com.blackice.bc1.CrashLog.handled("live preview: " + what, t);
                lastError = String.valueOf(t.getMessage());
                // Start over, but leave a TextureView's SurfaceTexture alone: the view still owns it.
                surfaceTexture = null;
                try { unbind(); } catch (Throwable ignored) { }
            }
        };
    }

    private void runOnGl(Runnable task) {
        if (Thread.currentThread() == thread) { task.run(); return; }
        CountDownLatch done = new CountDownLatch(1);
        Runnable safe = guarded("preview setup", task);
        if (!gl.post(() -> { try { safe.run(); } finally { done.countDown(); } })) return;
        try { done.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}

package com.blackice.bc1.engine.bc;

import com.blackice.bc1.core.RenderPlan;

/**
 * A visual input for one placement. GL thread only; source times must not decrease between frames.
 * Each output frame runs in two phases so that every layer's decoder works at the same time:
 * {@link #prepare} on all sources (hand the chosen frame to its surface, no waiting), then
 * {@link #latch} on all sources (wait for and take the frame), then {@link #draw}.
 */
interface FrameSource {
    /** Selects the frame for {@code sourceTimeUs} and starts its delivery to the GPU. */
    default void prepare(long sourceTimeUs) throws Exception { }
    /** Completes {@link #prepare}: the frame is now the source's current picture. */
    default void latch() throws Exception { }
    /** Draws the current picture as one composited layer. */
    void draw(Compositor compositor, RenderPlan.LayerDraw layer);
    /** Current picture as a frame-sized, letterboxed 2D texture (used for full-frame copies). */
    int texture();
    void release();
}

package com.blackice.bc1.engine.bc;

/** A visual input that yields a frame-sized, letterboxed texture for a source time. GL thread only. */
interface FrameSource {
    /** Texture holding the frame for {@code sourceTimeMs}. Times must not decrease between calls. */
    int textureAt(long sourceTimeMs) throws Exception;
    /** Same, in microseconds (frame-exact for BC Core's own intermediate passes). */
    default int textureAtUs(long sourceTimeUs) throws Exception { return textureAt(sourceTimeUs / 1000); }
    void release();
}

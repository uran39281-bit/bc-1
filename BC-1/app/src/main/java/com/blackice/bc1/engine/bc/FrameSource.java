package com.blackice.bc1.engine.bc;

/** A visual input that yields a frame-sized, letterboxed texture for a source time. GL thread only. */
interface FrameSource {
    /** Texture holding the frame for {@code sourceTimeMs}. Times must not decrease between calls. */
    int textureAt(long sourceTimeMs) throws Exception;
    void release();
}

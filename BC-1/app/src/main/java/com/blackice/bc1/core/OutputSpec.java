package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;

/** Output frame geometry and encoder targets. Pure Java: shared by every engine backend. */
public final class OutputSpec {
    public static final int EXPORT_SHORT_EDGE = 1080;
    public static final int VIDEO_BITRATE = 8_000_000;
    public static final int AUDIO_BITRATE = 128_000;
    public static final int AUDIO_RATE = 48_000;
    public static final int AUDIO_CHANNELS = 2;

    public final int width, height, frameRate;
    public final long durationMs;

    private OutputSpec(int width, int height, int frameRate, long durationMs) {
        this.width = width; this.height = height; this.frameRate = frameRate; this.durationMs = durationMs;
    }

    /** Same frame size rule as the Media3 composition: 16:9 with an even long edge. */
    public static OutputSpec of(EditProject project, int shortEdge) {
        if (shortEdge < 2 || shortEdge % 2 != 0) throw new IllegalArgumentException("Short edge must be a positive even number.");
        int longEdge = (shortEdge * 16 / 9 + 1) / 2 * 2;
        int w = project.portrait ? shortEdge : longEdge, h = project.portrait ? longEdge : shortEdge;
        return new OutputSpec(w, h, project.frameRate, project.durationMs());
    }

    public static OutputSpec export(EditProject project) { return of(project, EXPORT_SHORT_EDGE); }

    public float aspect() { return (float) width / height; }

    /** Number of video frames: every frame start strictly before the end of the timeline. */
    public int frameCount() { return FrameClock.frameCount(durationMs, frameRate); }
}

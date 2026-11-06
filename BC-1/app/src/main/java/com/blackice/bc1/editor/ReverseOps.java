package com.blackice.bc1.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Footage direction as a project edit. Reversing swaps the clip's source for a derived reversed file (made by
 * ReverseTranscoder) and keeps the forward source on the clip, so Restore Forward goes back to the same original
 * range without re-encoding anything. The clip's timeline placement, rate and duration never change.
 *
 * <p>Key times of the clip's own animation (position, rotation, effects…) are stored in source time, so they are
 * shifted by the same amount as the in-point: an authored animation keeps happening at the same project times
 * and is never itself reversed. Keys that would land before the start of the new source are dropped (they lie
 * outside the trimmed, visible range).
 */
public final class ReverseOps {
    private ReverseOps() { }

    /** Why reversing does not apply to this clip, or null when it does. */
    public static String unsupported(EditProject.Clip c) {
        if (c == null) return "Select a layer.";
        if (c.image) return "Reverse is available for video footage only.";
        if (EditProject.audioTrack(c.track)) return "Reverse is available for video footage only.";
        return null;
    }

    /** Swaps in the reversed derivative. {@code derivativeMs} is its measured duration. */
    public static void reverse(EditProject.Clip c, String derivativeUri, long derivativeMs) {
        String problem = unsupported(c);
        if (problem != null) throw new IllegalArgumentException(problem);
        if (c.reversed) throw new IllegalArgumentException("This footage is already reversed.");
        if (derivativeUri == null || derivativeMs <= 0) throw new IllegalArgumentException("The reversed footage is not ready.");
        long base = c.inMs, span = c.outMs - c.inMs;
        shift(c.motion,-base);for(EffectInstance e:c.effects())if(!c.primary(e.id))shift(e.motion,-base);
        c.forwardUri = c.uri; c.forwardName = c.name; c.forwardSourceDurationMs = c.sourceDurationMs;
        c.reverseBaseMs = base; c.reverseSpanMs = span;
        c.uri = derivativeUri;
        c.name = (c.name == null ? "Clip" : c.name) + " (reversed)";
        // The file reports a duration up to one frame short of the range it holds (the last frame's own length);
        // the clip keeps the full range so Restore Forward returns to exactly the original trim.
        c.sourceDurationMs = Math.max(derivativeMs, span);
        c.inMs = 0; c.outMs = span;
        c.reversed = true;
    }

    /** Goes back to the forward source, covering the same part of it that is currently shown. */
    public static void restore(EditProject.Clip c) {
        if (!c.reversed || c.forwardUri == null) throw new IllegalArgumentException("This footage is not reversed.");
        // Derivative time t shows forward time base + span - t.
        long in = c.reverseBaseMs + (c.reverseSpanMs - c.outMs), out = c.reverseBaseMs + (c.reverseSpanMs - c.inMs);
        in = Math.max(0, Math.min(in, c.forwardSourceDurationMs)); out = Math.max(in, Math.min(out, c.forwardSourceDurationMs));
        if (out <= in) throw new IllegalArgumentException("Nothing is left to restore.");
        shift(c.motion,in-c.inMs);for(EffectInstance e:c.effects())if(!c.primary(e.id))shift(e.motion,in-c.inMs);
        c.uri = c.forwardUri; c.name = c.forwardName == null ? c.name : c.forwardName; c.sourceDurationMs = c.forwardSourceDurationMs;
        c.inMs = in; c.outMs = out;
        c.reversed = false; c.forwardUri = null; c.forwardName = null; c.forwardSourceDurationMs = 0; c.reverseBaseMs = 0; c.reverseSpanMs = 0;
    }

    /** Moves every key of every property by {@code delta} ms, dropping keys that would fall before zero. */
    static void shift(Motion motion, long delta) {
        if (delta == 0) return;
        for (int property = 0; property < Motion.COUNT; property++) {
            List<Motion.Key> kept = new ArrayList<>();
            for (Motion.Key key : motion.curves.get(property)) {
                long t = key.timeMs + delta;
                if (t < 0) continue;
                key.timeMs = t; kept.add(key);
            }
            motion.curves.get(property).clear(); motion.curves.get(property).addAll(kept);
        }
    }
}

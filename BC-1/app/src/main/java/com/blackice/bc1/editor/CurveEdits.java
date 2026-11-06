package com.blackice.bc1.editor;

import com.blackice.bc1.menu.CubicBezier;
import java.util.List;

/**
 * Per-interval easing for the curve editor. Pure Java: the editor evaluates and writes exactly what
 * {@link Motion#ease} already evaluates for preview and both export engines, so the graph, the
 * preview and an exported file agree.
 *
 * <p>Presets 0–3 stay analytic quadratics until the person edits that interval. Opening a legacy
 * interval never rewrites it; {@link #seed} only chooses where the handles are drawn.
 */
public final class CurveEdits {
    private CurveEdits() {}

    /** Quadratic ease-in/out is piecewise, so it has no exact single cubic. This symmetric seed is the closest (max error about 0.5%). */
    public static final CubicBezier IN_OUT_SEED = new CubicBezier(0.45, 0.0, 0.55, 1.0);

    /** Index of the key whose outgoing interval is selected, or -1 when the id is missing. */
    public static int indexOf(List<Motion.Key> keys, String keyId) {
        if (keyId == null) return -1;
        for (int i = 0; i < keys.size(); i++) if (keyId.equals(keys.get(i).id)) return i;
        return -1;
    }

    /** True when the key at {@code index} has a later key on the same track. */
    public static boolean hasNext(List<Motion.Key> keys, int index) {
        return index >= 0 && index + 1 < keys.size() && keys.get(index + 1).timeMs > keys.get(index).timeMs;
    }

    /** Where to draw the two handles. Custom cubics reopen with exactly their stored coordinates. */
    public static CubicBezier seed(Motion.Key key) {
        if (key.easingToNext == -1) return stored(key);
        switch (Keyframes.easingOf(key)) {
            case 0: return CubicBezier.LINEAR;
            // Exact cubic forms of the analytic quadratics (linear x controls at 1/3 and 2/3).
            case 1: return new CubicBezier(1d / 3d, 0, 2d / 3d, 1d / 3d);
            case 2: return new CubicBezier(1d / 3d, 2d / 3d, 2d / 3d, 1);
            case 3: return IN_OUT_SEED;
            default: return stored(key);
        }
    }

    private static CubicBezier stored(Motion.Key key) {
        return new CubicBezier(clamp(key.x1), clamp(key.y1), clamp(key.x2), clamp(key.y2));
    }

    /** True while the plotted curve is the analytic ease-in/out and the handles are only an approximation of it. */
    public static boolean handlesApproximate(Motion.Key key) {
        return key.easingToNext != -1 && Keyframes.easingOf(key) == 3;
    }

    /** Eased progress the renderer will actually use for this outgoing interval. */
    public static double progress(Motion.Key key, double u) {
        if (!Double.isFinite(u)) throw new IllegalArgumentException("Invalid progress");
        return Motion.ease((float) Math.max(0, Math.min(1, u)), key);
    }

    /**
     * Writes a handle edit to this one key only. Never creates keys and never touches other tracks.
     * Returns false when nothing needs to change, so a no-op gesture adds no undo entry.
     * A request for the exact linear curve restores the preset-0 representation.
     */
    public static boolean write(Motion.Key key, CubicBezier curve) {
        if (curve.equals(CubicBezier.LINEAR)) {
            if (key.easingToNext != -1 && Keyframes.easingOf(key) == 0) return false;
            Keyframes.ease(key, 0);
            return true;
        }
        float x1 = (float) curve.x1, y1 = (float) curve.y1, x2 = (float) curve.x2, y2 = (float) curve.y2;
        if (key.easingToNext == -1 && key.x1 == x1 && key.y1 == y1 && key.x2 == x2 && key.y2 == y2) return false;
        // A handle move along the diagonal still changes editable geometry even though the timing stays linear.
        key.easingToNext = -1;
        key.x1 = x1; key.y1 = y1; key.x2 = x2; key.y2 = y2;
        return true;
    }

    private static double clamp(float v) { return Math.max(0, Math.min(1, v)); }
}

package com.blackice.bc1.editor;

import static org.junit.Assert.*;
import com.blackice.bc1.menu.CubicBezier;
import org.junit.Test;

/** The graph shows and writes exactly what Motion.ease evaluates for preview and export. */
public class CurveEditsTest {
    private static Motion.Key key(int preset) { Motion.Key k = new Motion.Key(0, 0); Keyframes.ease(k, preset); return k; }

    @Test public void legacyPresetsAreSeededWithoutBeingRewritten() {
        for (int preset = 0; preset < 4; preset++) {
            Motion.Key k = key(preset);
            float[] before = {k.x1, k.y1, k.x2, k.y2};
            CurveEdits.seed(k);
            CurveEdits.progress(k, .3);
            assertEquals(preset, k.easingToNext);
            assertArrayEquals(before, new float[]{k.x1, k.y1, k.x2, k.y2}, 0);
        }
    }

    @Test public void easeInAndEaseOutSeedsAreExactCubicForms() {
        for (int preset = 1; preset <= 2; preset++) {
            Motion.Key k = key(preset);
            CubicBezier seed = CurveEdits.seed(k);
            for (int i = 0; i <= 100; i++) {
                double u = i / 100d;
                assertEquals("preset " + preset + " at " + u, CurveEdits.progress(k, u), seed.valueAt(u), 2e-6);
            }
            assertFalse(CurveEdits.handlesApproximate(k));
        }
    }

    @Test public void easeInOutSeedIsFlaggedAsApproximateAndClose() {
        Motion.Key k = key(3);
        assertTrue(CurveEdits.handlesApproximate(k));
        CubicBezier seed = CurveEdits.seed(k);
        double worst = 0;
        for (int i = 0; i <= 1000; i++) worst = Math.max(worst, Math.abs(seed.valueAt(i / 1000d) - CurveEdits.progress(k, i / 1000d)));
        assertTrue("seed within 1% of the real curve: " + worst, worst < .01);
    }

    @Test public void customHandlesReopenWithExactlyTheirStoredCoordinates() {
        Motion.Key k = key(0);
        assertTrue(CurveEdits.write(k, new CubicBezier(.2, .6, .7, .1)));
        assertEquals(-1, k.easingToNext);
        CubicBezier seed = CurveEdits.seed(k);
        assertEquals(.2f, (float) seed.x1, 0);
        assertEquals(.6f, (float) seed.y1, 0);
        assertEquals(.7f, (float) seed.x2, 0);
        assertEquals(.1f, (float) seed.y2, 0);
        assertFalse("writing the same curve again is a no-op", CurveEdits.write(k, new CubicBezier(.2, .6, .7, .1)));
        assertFalse(CurveEdits.handlesApproximate(k));
    }

    @Test public void aDiagonalHandleMoveIsKeptEvenThoughTimingStaysLinear() {
        Motion.Key k = key(0);
        assertTrue(CurveEdits.write(k, new CubicBezier(.2, .2, .9, .9)));
        assertEquals(-1, k.easingToNext);
        assertEquals(.2f, (float) CurveEdits.seed(k).x1, 0);
        for (int i = 0; i <= 10; i++) assertEquals(i / 10d, CurveEdits.progress(k, i / 10d), 1e-5);
    }

    @Test public void resettingToLinearIsOneRealChangeThenANoOp() {
        Motion.Key k = key(3);
        assertTrue(CurveEdits.write(k, CubicBezier.LINEAR));
        assertEquals(0, k.easingToNext);
        assertFalse(CurveEdits.write(k, CubicBezier.LINEAR));
        Motion.Key custom = key(0);
        CurveEdits.write(custom, new CubicBezier(.5, .1, .5, .9));
        assertTrue(CurveEdits.write(custom, CubicBezier.LINEAR));
        assertEquals(0, custom.easingToNext);
    }

    @Test public void editingOneKeyNeverTouchesOtherKeysOrTracks() {
        Motion m = new Motion();
        m.put(Motion.X, new Motion.Key(0, 0));
        m.put(Motion.X, new Motion.Key(1000, 1));
        m.put(Motion.X, new Motion.Key(2000, 2));
        m.put(Motion.Y, new Motion.Key(0, 0));
        m.put(Motion.Y, new Motion.Key(1000, 5));
        Motion before = m.copy();
        Motion.Key first = m.curves.get(Motion.X).get(0);
        CurveEdits.write(first, new CubicBezier(.9, .1, .1, .9));
        assertEquals(3, m.curves.get(Motion.X).size());
        assertEquals(2, m.curves.get(Motion.Y).size());
        assertEquals(before.curves.get(Motion.Y).get(0).easingToNext, m.curves.get(Motion.Y).get(0).easingToNext);
        assertEquals(before.curves.get(Motion.X).get(1).easingToNext, m.curves.get(Motion.X).get(1).easingToNext);
        assertEquals(before.curves.get(Motion.X).get(2).easingToNext, m.curves.get(Motion.X).get(2).easingToNext);
        m.validate();
    }

    @Test public void progressFollowsThePropertyValueAcrossAnInterval() {
        Motion m = new Motion();
        m.put(Motion.ROTATION, new Motion.Key(0, 0));
        m.put(Motion.ROTATION, new Motion.Key(1000, 720));
        Motion.Key start = m.curves.get(Motion.ROTATION).get(0);
        CurveEdits.write(start, new CubicBezier(1d / 3, 0, 2d / 3, 1d / 3));
        assertEquals("quarter time of a quadratic ease in covers 6.25% of 720 degrees", 45, m.value(Motion.ROTATION, 250, 0), .01);
        assertEquals(720, m.value(Motion.ROTATION, 1000, 0), 0);
    }

    @Test public void selectionHelpersFindTheOutgoingInterval() {
        Motion m = new Motion();
        for (int i = 0; i < 3; i++) m.put(Motion.OPACITY, new Motion.Key(i * 1000L, i / 4f));
        java.util.List<Motion.Key> keys = m.curves.get(Motion.OPACITY);
        assertEquals(-1, CurveEdits.indexOf(keys, null));
        assertEquals(-1, CurveEdits.indexOf(keys, "missing"));
        assertEquals(1, CurveEdits.indexOf(keys, keys.get(1).id));
        assertTrue(CurveEdits.hasNext(keys, 1));
        assertFalse("the last key has no outgoing interval", CurveEdits.hasNext(keys, 2));
        assertFalse(CurveEdits.hasNext(keys, -1));
    }
}

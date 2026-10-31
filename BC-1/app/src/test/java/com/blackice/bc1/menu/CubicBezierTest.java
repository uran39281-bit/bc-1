package com.blackice.bc1.menu;

import static org.junit.Assert.*;
import java.util.Random;
import org.junit.Test;

/** Timing-curve math from the menu kit: x(t) must be inverted before reading y. */
public class CubicBezierTest {
    private static void close(String what, double expected, double actual, double tolerance) {
        assertTrue(what + ": got " + actual + ", expected " + expected, Math.abs(expected - actual) <= tolerance);
    }

    @Test public void linearAndQuadraticFormsAreExact() {
        CubicBezier squared = new CubicBezier(1d / 3, 0, 2d / 3, 1d / 3);
        CubicBezier root = new CubicBezier(1d / 3, 2d / 3, 2d / 3, 1);
        for (int i = 0; i <= 1000; i++) {
            double u = i / 1000d;
            close("linear", u, CubicBezier.LINEAR.valueAt(u), 1e-12);
            close("ease in is u squared", u * u, squared.valueAt(u), 1e-12);
            close("ease out is 1-(1-u)^2", 1 - (1 - u) * (1 - u), root.valueAt(u), 1e-12);
        }
    }

    @Test public void endpointsAreFixed() {
        CubicBezier c = new CubicBezier(.9, .1, .1, .9);
        assertEquals(0, c.valueAt(0), 0);
        assertEquals(1, c.valueAt(1), 0);
        assertEquals(0, c.valueAt(-5), 0);
        assertEquals(1, c.valueAt(7), 0);
    }

    @Test public void usingYOfTimeDirectlyWouldBeWrong() {
        CubicBezier c = new CubicBezier(0, 1d / 3, 0, 2d / 3);
        close("x is inverted: progress follows cbrt", Math.cbrt(.25), c.valueAt(.25), 1e-9);
        assertTrue(Math.abs(c.yAt(.25) - c.valueAt(.25)) > .05);
    }

    @Test public void crossedHandlesAndExtremesStayFinite() {
        for (double[] h : new double[][]{{1, 0, 0, 1}, {0, 0, 0, 1}, {1, 1, 1, 0}, {0, 1, 1, 0}}) {
            CubicBezier c = new CubicBezier(h[0], h[1], h[2], h[3]);
            double previous = -1;
            for (int i = 0; i <= 200; i++) {
                double v = c.valueAt(i / 200d);
                assertTrue(Double.isFinite(v) && v >= 0 && v <= 1);
            }
        }
    }

    @Test public void drawingAndRuntimeAgree() {
        Random r = new Random(0xB1AC1CE);
        for (int i = 0; i < 200; i++) {
            CubicBezier c = new CubicBezier(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble());
            for (int j = 0; j <= 50; j++) {
                double t = j / 50d;
                close("y at x(t)", c.yAt(t), c.valueAt(c.xAt(t)), 2e-9);
            }
        }
    }

    @Test public void handlesAreBoundedWithoutOvershoot() {
        for (double bad : new double[]{-.001, 1.001, Double.NaN, Double.POSITIVE_INFINITY}) {
            try { new CubicBezier(bad, 0, 0, 0); fail("accepted " + bad); } catch (IllegalArgumentException expected) { }
            try { new CubicBezier(0, 0, 0, bad); fail("accepted " + bad); } catch (IllegalArgumentException expected) { }
        }
    }
}

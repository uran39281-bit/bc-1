package com.blackice.bc1.menu;

/**
 * Immutable normalized timing curve with fixed endpoints (0,0) and (1,1).
 * All four handle coordinates are constrained to [0,1]: this editor has no
 * overshoot. Runtime sampling must invert x(t); y(timeProgress) is incorrect.
 * This class has no Android dependency and can be shared by preview and export.
 */
public final class CubicBezier {
    public static final CubicBezier LINEAR = new CubicBezier(1.0 / 3.0, 1.0 / 3.0,
            2.0 / 3.0, 2.0 / 3.0);
    /** A visual example only. New user intervals should retain the app's default. */
    public static final CubicBezier DEFAULT = new CubicBezier(0.25, 0.10, 0.75, 0.90);

    public final double x1;
    public final double y1;
    public final double x2;
    public final double y2;

    public CubicBezier(double x1, double y1, double x2, double y2) {
        this.x1 = coordinate(x1, "x1");
        this.y1 = coordinate(y1, "y1");
        this.x2 = coordinate(x2, "x2");
        this.y2 = coordinate(y2, "y2");
    }

    private static double coordinate(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and within [0,1]");
        }
        return value == 0.0 ? 0.0 : value; // Canonicalize negative zero for equality.
    }

    private static double parameter(double value) {
        return coordinate(value, "parameter");
    }

    private static double axis(double t, double h1, double h2) {
        double v = 1.0 - t;
        return 3.0 * v * v * t * h1 + 3.0 * v * t * t * h2 + t * t * t;
    }

    /** Parametric curve x for graph drawing; t must be in [0,1]. */
    public double xAt(double t) {
        return axis(parameter(t), x1, x2);
    }

    /** Parametric curve y for graph drawing; t must be in [0,1]. */
    public double yAt(double t) {
        return axis(parameter(t), y1, y2);
    }

    private double derivativeX(double t) {
        double v = 1.0 - t;
        return 3.0 * v * v * x1 + 6.0 * v * t * (x2 - x1)
                + 3.0 * t * t * (1.0 - x2);
    }

    /**
     * Solve x(t)=timeProgress with safeguarded Newton iteration and bisection.
     * Finite progress outside [0,1] is clamped to interval endpoints. Handles
     * may cross horizontally: x1 > x2 is still a valid monotonic timing curve.
     */
    public double parameterForX(double timeProgress) {
        if (!Double.isFinite(timeProgress)) {
            throw new IllegalArgumentException("timeProgress must be finite");
        }
        if (timeProgress <= 0.0) return 0.0;
        if (timeProgress >= 1.0) return 1.0;
        double lo = 0.0;
        double hi = 1.0;
        double t = timeProgress;
        for (int iteration = 0; iteration < 100; iteration++) {
            double error = axis(t, x1, x2) - timeProgress;
            if (error == 0.0) return t;
            if (error < 0.0) lo = t; else hi = t;
            if (hi - lo <= 1e-14) return (lo + hi) * 0.5;
            double slope = derivativeX(t);
            double candidate = slope > 1e-12 ? t - error / slope : Double.NaN;
            // A Newton step outside the bracket cannot be trusted.
            if (!Double.isFinite(candidate) || candidate <= lo || candidate >= hi) {
                candidate = (lo + hi) * 0.5;
            }
            t = candidate;
        }
        return (lo + hi) * 0.5;
    }

    /** Normalized eased progress for actual project time (not parametric t). */
    public double valueAt(double timeProgress) {
        double value = axis(parameterForX(timeProgress), y1, y2);
        return Math.max(0.0, Math.min(1.0, value));
    }

    public CubicBezier withFirstHandle(double x, double y) {
        return new CubicBezier(x, y, x2, y2);
    }

    public CubicBezier withSecondHandle(double x, double y) {
        return new CubicBezier(x1, y1, x, y);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CubicBezier)) return false;
        CubicBezier c = (CubicBezier) other;
        return Double.compare(x1, c.x1) == 0 && Double.compare(y1, c.y1) == 0
                && Double.compare(x2, c.x2) == 0 && Double.compare(y2, c.y2) == 0;
    }

    @Override public int hashCode() {
        int h = Double.hashCode(x1);
        h = 31 * h + Double.hashCode(y1);
        h = 31 * h + Double.hashCode(x2);
        return 31 * h + Double.hashCode(y2);
    }

    @Override public String toString() {
        return "CubicBezier(" + x1 + ", " + y1 + ", " + x2 + ", " + y2 + ")";
    }
}

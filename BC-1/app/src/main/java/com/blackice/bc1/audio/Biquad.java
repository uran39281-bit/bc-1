package com.blackice.bc1.audio;

/* loaded from: classes6.dex */
public final class Biquad {
    private final double a1;
    private final double a2;
    private final double b0;
    private final double b1;
    private final double b2;
    private double z1;
    private double z2;

    public Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
        this.b0 = b0 / a0;
        this.b1 = b1 / a0;
        this.b2 = b2 / a0;
        this.a1 = a1 / a0;
        this.a2 = a2 / a0;
    }

    public double apply(double x) {
        double y = (this.b0 * x) + this.z1;
        this.z1 = ((this.b1 * x) - (this.a1 * y)) + this.z2;
        this.z2 = (this.b2 * x) - (this.a2 * y);
        return y;
    }

    public static Biquad peak(int rate, double hz, double db) {
        double a = Math.pow(10.0d, db / 40.0d);
        double w = (Math.min(hz, rate * 0.45d) * 6.283185307179586d) / rate;
        double alpha = Math.sin(w) / 2.0d;
        double cs = Math.cos(w);
        return new Biquad((alpha * a) + 1.0d, cs * (-2.0d), 1.0d - (alpha * a), (alpha / a) + 1.0d, (-2.0d) * cs, 1.0d - (alpha / a));
    }

    public static Biquad highpass(int rate, double hz) {
        double w = (6.283185307179586d * hz) / rate;
        double cs = Math.cos(w);
        double alpha = Math.sin(w) / (Math.sqrt(0.5d) * 2.0d);
        return new Biquad((cs + 1.0d) / 2.0d, -(cs + 1.0d), (cs + 1.0d) / 2.0d, alpha + 1.0d, cs * (-2.0d), 1.0d - alpha);
    }
}

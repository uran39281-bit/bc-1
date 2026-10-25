package com.blackice.bc1.audio;

import java.util.ArrayList;
import java.util.Iterator;

/* loaded from: classes6.dex */
public final class LoudnessMeter {
    private int cursor;
    private long frames;
    public double peak;
    private double sum;
    private final Biquad[] shelf = new Biquad[2];
    private final Biquad[] high = new Biquad[2];
    private final double[] ring = new double[19200];
    private final ArrayList<Double> blocks = new ArrayList<>();

    public LoudnessMeter() {
        for (int c = 0; c < 2; c++) {
            this.shelf[c] = new Biquad(1.53512485958697d, -2.69169618940638d, 1.19839281085285d, 1.0d, -1.69065929318241d, 0.73248077421585d);
            this.high[c] = new Biquad(1.0d, -2.0d, 1.0d, 1.0d, -1.99004745483398d, 0.99007225036621d);
        }
    }

    public void add(float[] pcm, int n) {
        for (int f = 0; f < n; f++) {
            double power = 0.0d;
            for (int c = 0; c < 2; c++) {
                double x = pcm[(f * 2) + c];
                this.peak = Math.max(this.peak, Math.abs(x));
                double y = this.high[c].apply(this.shelf[c].apply(x));
                power += y * y;
            }
            this.sum += power - this.ring[this.cursor];
            this.ring[this.cursor] = power;
            this.cursor = (this.cursor + 1) % this.ring.length;
            this.frames++;
            if (this.frames >= 19200 && (this.frames - 19200) % 4800 == 0) {
                this.blocks.add(Double.valueOf(Math.max(0.0d, this.sum / 19200.0d)));
            }
        }
    }

    public double integrated() {
        double total = 0.0d;
        int count = 0;
        Iterator<Double> it = this.blocks.iterator();
        while (it.hasNext()) {
            double p = it.next().doubleValue();
            if (lufs(p) > -70.0d) {
                total += p;
                count++;
            }
        }
        if (count == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double gate = lufs(total / count) - 10.0d;
        double total2 = 0.0d;
        int count2 = 0;
        Iterator<Double> it2 = this.blocks.iterator();
        while (it2.hasNext()) {
            double p2 = it2.next().doubleValue();
            if (lufs(p2) > Math.max(-70.0d, gate)) {
                total2 += p2;
                count2++;
            }
        }
        if (count2 == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        return lufs(total2 / count2);
    }

    private static double lufs(double p) {
        if (p <= 0.0d) {
            return Double.NEGATIVE_INFINITY;
        }
        return (-0.691d) + (Math.log10(p) * 10.0d);
    }

    public double gainFor(double target) {
        double loud = integrated();
        if (!Double.isFinite(loud)) {
            throw new IllegalArgumentException("Need at least 0.4 seconds of audible sound to measure loudness.");
        }
        double requested = target - loud;
        double ceiling = this.peak > 0.0d ? 20.0d * Math.log10(Math.pow(10.0d, -0.05d) / this.peak) : 0.0d;
        return Math.max(-24.0d, Math.min(24.0d, Math.min(requested, ceiling)));
    }
}

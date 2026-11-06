package com.blackice.bc1.audio;

import java.lang.reflect.Array;

/* loaded from: classes6.dex */
public final class AudioDsp {
    private final double attack;
    private final int channels;
    private double envelope;
    private final Biquad[][] eq;
    private double gain = 1.0d;
    private final double makeup;
    private final double release;
    private final Biquad[] rumble;
    private final AudioSettings s;
    private final double threshold;

    public AudioDsp(AudioSettings settings, int rate, int channels) {
        if (rate <= 0 || channels < 1 || channels > 8) {
            throw new IllegalArgumentException("Invalid PCM format");
        }
        this.s = settings.copy();
        this.s.validate();
        this.channels = channels;
        this.eq = (Biquad[][]) Array.newInstance((Class<?>) Biquad.class, channels, 5);
        this.rumble = new Biquad[channels];
        for (int c = 0; c < channels; c++) {
            this.rumble[c] = Biquad.highpass(rate, 70.0d);
            for (int i = 0; i < 5; i++) {
                this.eq[c][i] = Biquad.peak(rate, AudioSettings.FREQUENCIES[i], this.s.eqDb[i]);
            }
        }
        this.attack = Math.exp((-1.0d) / (rate * 0.002d));
        this.release = Math.exp((-1.0d) / (rate * 0.15d));
        this.threshold = Math.pow(10.0d, ((this.s.noise * 8) - 60) / 20.0d);
        this.makeup = Math.pow(10.0d, this.s.preampDb / 20.0d);
    }

    public void process(float[] pcm, int frames) {
        AudioDsp audioDsp = this;
        if (audioDsp.s.active()) {
            int f = 0;
            while (f < frames) {
                double level = 0.0d;
                for (int c = 0; c < audioDsp.channels; c++) {
                    level = Math.max(level, Math.abs(pcm[(audioDsp.channels * f) + c]));
                }
                audioDsp.envelope = ((level > audioDsp.envelope ? audioDsp.attack : audioDsp.release) * audioDsp.envelope) + ((1.0d - (level > audioDsp.envelope ? audioDsp.attack : audioDsp.release)) * level);
                double desired = audioDsp.s.noise == 0 ? 1.0d : Math.max(Math.pow(10.0d, ((-audioDsp.s.noise) * 8) / 20.0d), Math.min(1.0d, Math.pow(audioDsp.envelope / audioDsp.threshold, 1.5d)));
                double smoothing = desired > audioDsp.gain ? audioDsp.attack : audioDsp.release;
                audioDsp.gain = (audioDsp.gain * smoothing) + ((1.0d - smoothing) * desired);
                int c2 = 0;
                while (c2 < audioDsp.channels) {
                    double v = pcm[(audioDsp.channels * f) + c2];
                    if (audioDsp.s.noise > 0) {
                        v = audioDsp.rumble[c2].apply(v) * audioDsp.gain;
                    }
                    for (int i = 0; i < 5; i++) {
                        if (audioDsp.s.eqDb[i] != 0.0f) {
                            v = audioDsp.eq[c2][i].apply(v);
                        }
                    }
                    double v2 = v * audioDsp.makeup;
                    pcm[(audioDsp.channels * f) + c2] = (float) Math.max(-1.0d, Math.min(1.0d, Double.isFinite(v2) ? v2 : 0.0d));
                    c2++;
                    audioDsp = this;
                    f = f;
                }
                f++;
                audioDsp = this;
            }
        }
    }
}

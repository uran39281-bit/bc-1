package com.blackice.bc1.engine.bc;

import android.content.Context;
import com.blackice.bc1.core.AudioMixer;
import com.blackice.bc1.core.AudioPlan;

/**
 * The project's audio as 16 kHz mono for speech recognition, mixed by the same AudioMixer as export
 * (trims, speed, volume and fades included). Read sequentially from {@code fromMs} to {@code toMs}.
 */
public final class SpeechAudio implements AutoCloseable {
    public static final int RATE = 16_000;
    private final AudioMixer mixer;
    private final long fromFrame, toFrame;
    private float[] stereo = new float[0];

    public SpeechAudio(Context context, AudioPlan plan, long fromMs, long toMs) {
        Context app = context.getApplicationContext();
        mixer = new AudioMixer(plan, s -> AudioDecoderSource.open(app, s), RATE);
        fromFrame = AudioMixer.frameOf(Math.max(0, fromMs), RATE);
        toFrame = Math.min(mixer.totalFrames(), AudioMixer.frameOf(toMs, RATE));
    }

    /** Frames this audio has in total. */
    public long frames() { return Math.max(0, toFrame - fromFrame); }

    /** Fills {@code mono} from {@code offset}; returns frames written, 0 at the end. */
    public int read(float[] mono, int offset, int maxFrames) throws Exception {
        // The mixer has no seek; frames before the range are mixed and discarded (silence costs nothing).
        while (mixer.position() < fromFrame) {
            int skip = (int) Math.min(8192, fromFrame - mixer.position());
            ensure(skip);
            if (mixer.render(stereo, skip) <= 0) return 0;
        }
        int frames = (int) Math.min(maxFrames, toFrame - mixer.position());
        if (frames <= 0) return 0;
        ensure(frames);
        int n = mixer.render(stereo, frames);
        for (int i = 0; i < n; i++) mono[offset + i] = (stereo[2 * i] + stereo[2 * i + 1]) * .5f;
        return n;
    }

    private void ensure(int frames) { if (stereo.length < frames * 2) stereo = new float[frames * 2]; }

    @Override public void close() { mixer.close(); }
}

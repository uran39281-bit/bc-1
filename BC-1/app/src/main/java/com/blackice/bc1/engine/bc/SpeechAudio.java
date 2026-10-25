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
    private final java.util.function.BooleanSupplier cancelled;
    private float[] stereo = new float[0];

    public SpeechAudio(Context context, AudioPlan plan, long fromMs, long toMs) {
        this(context,plan,fromMs,toMs,0,()->false);
    }
    public SpeechAudio(Context context, AudioPlan plan, long fromMs, long toMs, long skipFrames, java.util.function.BooleanSupplier cancelled) {
        this.cancelled=cancelled;
        Context app = context.getApplicationContext();
        mixer = new AudioMixer(plan, new AudioMixer.Opener() {
            @Override public AudioMixer.Opened open(AudioPlan.Segment s)throws Exception{return AudioDecoderSource.open(app,s);}
            @Override public AudioMixer.Opened openAt(AudioPlan.Segment s,long localMs)throws Exception{return AudioDecoderSource.openAt(app,s,localMs);}
            @Override public boolean seeks(){return true;}
        }, RATE);
        fromFrame = AudioMixer.frameOf(Math.max(0, fromMs), RATE) + Math.max(0,skipFrames);
        toFrame = Math.min(mixer.totalFrames(), AudioMixer.frameOf(toMs, RATE));
        mixer.seek(fromFrame * 1000 / RATE);
    }

    /** Frames this audio has in total. */
    public long frames() { return Math.max(0, toFrame - fromFrame); }

    /** Fills {@code mono} from {@code offset}; returns frames written, 0 at the end. */
    public int read(float[] mono, int offset, int maxFrames) throws Exception {
        // Checkpoints are sample-exact; discard any sub-millisecond remainder after seeking.
        while (mixer.position() < fromFrame) {
            if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();
            int skip = (int) Math.min(8192, fromFrame - mixer.position());
            ensure(skip);
            if (mixer.render(stereo, skip) <= 0) return 0;
        }
        if(cancelled.getAsBoolean())throw new java.util.concurrent.CancellationException();
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

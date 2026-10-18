package com.blackice.bc1.core;

/**
 * Streaming linear-interpolation resampler for interleaved stereo float PCM. Pure Java.
 * Playback speed is folded into the ratio, so 2× speed reads source samples twice as fast
 * (pitch follows speed; a pitch-preserving stretch is a later engine stage).
 */
public final class Resampler {
    /** Pulls interleaved stereo frames at the source rate. Returns frames read, or −1 at the end. */
    public interface Source { int read(float[] stereo, int maxFrames) throws Exception; }

    private final Source source;
    private final double step;
    private float[] buffer = new float[2 * 4096];
    private int frames;          // valid frames in buffer
    private double position;     // fractional read position in buffer frames
    private boolean ended;

    public Resampler(Source source, int sourceRate, int outputRate, double speed) {
        if (sourceRate <= 0 || outputRate <= 0 || !(speed > 0)) throw new IllegalArgumentException("Invalid resampling rates.");
        this.source = source;
        step = sourceRate * speed / outputRate;
    }

    public double step() { return step; }

    /** Fills up to {@code maxFrames} output frames. Returns the frames written; fewer only at the end. */
    public int read(float[] out, int maxFrames) throws Exception {
        int written = 0;
        while (written < maxFrames) {
            int i = (int) position;
            if (i + 1 >= frames) {
                if (!refill()) {
                    // The final source frame is emitted only when a step lands on it exactly.
                    int j = (int) position; // refill may have shifted the buffer
                    if (j == frames - 1 && position == j) { out[written * 2] = buffer[j * 2]; out[written * 2 + 1] = buffer[j * 2 + 1]; written++; position += step; continue; }
                    break;
                }
                continue;
            }
            float t = (float) (position - i);
            out[written * 2] = buffer[i * 2] + (buffer[i * 2 + 2] - buffer[i * 2]) * t;
            out[written * 2 + 1] = buffer[i * 2 + 1] + (buffer[i * 2 + 3] - buffer[i * 2 + 1]) * t;
            written++;
            position += step;
        }
        return written;
    }

    /** Discards consumed frames and appends more source frames. False once nothing more can be read. */
    private boolean refill() throws Exception {
        if (ended) return false;
        int keep = Math.max(0, Math.min(frames, frames - (int) position));
        int drop = frames - keep;
        if (drop > 0) {
            System.arraycopy(buffer, drop * 2, buffer, 0, keep * 2);
            frames = keep; position -= drop;
        }
        if (frames * 2 + 2 * 1024 > buffer.length) {
            float[] grown = new float[buffer.length * 2];
            System.arraycopy(buffer, 0, grown, 0, frames * 2);
            buffer = grown;
        }
        int room = (buffer.length / 2) - frames;
        float[] chunk = new float[room * 2];
        int n;
        do { n = source.read(chunk, room); } while (n == 0);
        if (n < 0) { ended = true; return false; }
        System.arraycopy(chunk, 0, buffer, frames * 2, n * 2);
        frames += n;
        return true;
    }

    /** Converts interleaved PCM with any channel count to stereo (mono is duplicated, extra channels dropped). */
    public static int toStereo(float[] in, int frames, int channels, float[] out) {
        if (channels <= 0) throw new IllegalArgumentException("No audio channels.");
        for (int f = 0; f < frames; f++) {
            float l = in[f * channels], r = channels > 1 ? in[f * channels + 1] : l;
            out[f * 2] = l; out[f * 2 + 1] = r;
        }
        return frames;
    }

    /** Hard limiter so summed sources never wrap when converted to 16-bit. */
    public static float clamp(float v) { return v > 1f ? 1f : v < -1f ? -1f : v; }
}

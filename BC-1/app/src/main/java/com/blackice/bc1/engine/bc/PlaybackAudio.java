package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import com.blackice.bc1.core.AudioMixer;
import com.blackice.bc1.core.AudioPlan;

/**
 * Sound and clock for ICE Lite playback. The project's audio (the export mixer: trims, speed, volume,
 * fades, music) plays through an AudioTrack, and its playback position is the clock video frames follow,
 * so picture and sound stay in sync. A project with no sound runs on the system clock instead.
 */
final class PlaybackAudio {
    static final int RATE = 48_000;
    private static final int BLOCK = 1024;

    private final long fromMs, untilMs;
    private final AudioTrack track;
    private final Thread writer;
    private volatile boolean running = true;
    private volatile Throwable failure;
    private final long startedNs = System.nanoTime();
    { lastMoveNs = startedNs; }
    private final AudioTimestamp stamp = new AudioTimestamp();

    PlaybackAudio(Context context, AudioPlan plan, long fromMs, long untilMs) {
        this.fromMs = fromMs; this.untilMs = untilMs;
        AudioTrack created = null;
        if (!plan.segments.isEmpty()) try { created = buildTrack(); }
        catch (RuntimeException e) { com.blackice.bc1.CrashLog.handled("audio output (playing silently)", e); }
        track = created;
        if (track == null) { writer = null; return; }
        Context app = context.getApplicationContext();
        AudioMixer.Opener opener = new AudioMixer.Opener() {
            @Override public AudioMixer.Opened open(AudioPlan.Segment s) throws Exception { return AudioDecoderSource.open(app, s); }
            @Override public AudioMixer.Opened openAt(AudioPlan.Segment s, long localMs) throws Exception { return AudioDecoderSource.openAt(app, s, localMs); }
            @Override public boolean seeks() { return true; }
        };
        writer = new Thread(() -> {
            float[] block = new float[BLOCK * 2];
            boolean started = false;
            try (AudioMixer mixer = new AudioMixer(plan, opener, RATE)) {
                mixer.seek(fromMs);
                long limit = AudioMixer.frameOf(untilMs, RATE);
                while (running) {
                    int n = (int) Math.min(BLOCK, limit - mixer.position());
                    n = n <= 0 ? 0 : mixer.render(block, n);
                    if (n <= 0) java.util.Arrays.fill(block, 0f); // past the end: silence while video finishes
                    int frames = n > 0 ? n : BLOCK;
                    int written = 0;
                    while (running && written < frames * 2) {
                        int w = track.write(block, written, frames * 2 - written, AudioTrack.WRITE_BLOCKING);
                        if (w < 0) throw new IllegalStateException("Audio output error " + w);
                        written += w;
                        if (!started) { track.play(); started = true; } // first block queued: start without an underrun
                    }
                }
            } catch (Throwable t) {
                failure = t;
                com.blackice.bc1.CrashLog.handled("playback audio", t);
            }
        }, "bc1-playback-audio");
        writer.setPriority(Thread.MAX_PRIORITY);
    }

    private static AudioTrack buildTrack() {
        int min = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT);
        return new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
                .setBufferSizeInBytes(Math.max(min * 2, BLOCK * 8 * 4 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM).build();
    }

    void start() { if (writer != null) writer.start(); }

    // Clock watchdog: if the audio position stops moving for a long time (no audio output, a stalled
    // device), the picture continues on the system clock from where the sound was.
    private static final long STALL_NS = 1_500_000_000L;
    private long lastAudioMs = -1, lastMoveNs, wallBaseMs = -1, wallBaseNs;

    /** Timeline position being heard now. */
    synchronized long positionMs() {
        long now = System.nanoTime();
        if (wallBaseMs >= 0) return wallBaseMs + (now - wallBaseNs) / 1_000_000;
        if (track == null || failure != null) { wallBaseMs = lastAudioMs >= 0 ? lastAudioMs : fromMs; wallBaseNs = lastAudioMs >= 0 ? lastMoveNs : startedNs; return positionMs(); }
        long frames;
        if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING && track.getTimestamp(stamp)) {
            frames = stamp.framePosition + (now - stamp.nanoTime) * RATE / 1_000_000_000L;
        } else frames = track.getPlaybackHeadPosition() & 0xFFFFFFFFL;
        long ms = fromMs + Math.max(0, frames) * 1000 / RATE;
        if (ms != lastAudioMs) { lastAudioMs = ms; lastMoveNs = now; }
        else if (now - lastMoveNs > STALL_NS) { wallBaseMs = ms; wallBaseNs = lastMoveNs; return positionMs(); }
        return ms;
    }

    long untilMs() { return untilMs; }

    void release() {
        running = false;
        if (track != null) {
            try { track.pause(); track.flush(); } catch (IllegalStateException ignored) { }
            if (writer != null) { writer.interrupt(); try { writer.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
            track.release();
        }
    }
}

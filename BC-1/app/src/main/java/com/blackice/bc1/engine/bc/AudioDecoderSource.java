package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import com.blackice.bc1.core.AudioMixer;
import com.blackice.bc1.core.AudioPlan;
import com.blackice.bc1.core.Resampler;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Streams a media file's audio as stereo float PCM from a segment's in-point. */
final class AudioDecoderSource implements Resampler.Source, AutoCloseable {
    private static final long WAIT_MS = 5_000;
    private final MediaExtractor extractor = new MediaExtractor();
    private MediaCodec codec;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private int channels, rate, encoding = AudioFormat.ENCODING_PCM_16BIT;
    private final long startUs;
    private boolean inputEos, outputEos;
    private float[] pending = new float[0];
    private int pendingFrames, pendingOffset;

    /** Opens a segment, or returns null when the media has no audio track. */
    static AudioMixer.Opened open(Context context, AudioPlan.Segment segment) throws Exception {
        AudioDecoderSource source = new AudioDecoderSource(context, segment.uri, segment.sourceInMs * 1000);
        if (source.codec == null) { source.close(); return null; }
        return new AudioMixer.Opened(source, source.rate, source);
    }

    private AudioDecoderSource(Context context, String uri, long startUs) throws Exception {
        this.startUs = startUs;
        extractor.setDataSource(context, Uri.parse(uri), null);
        MediaFormat format = null; int track = -1;
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat f = extractor.getTrackFormat(i);
            String mime = f.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) { format = f; track = i; break; }
        }
        if (track < 0) return;
        extractor.selectTrack(track);
        channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
        try {
            codec.configure(format, null, null, 0);
            codec.start();
        } catch (Exception e) { codec.release(); codec = null; throw e; }
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
    }

    @Override public int read(float[] stereo, int maxFrames) throws Exception {
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (pendingFrames == 0) {
            if (outputEos) return -1;
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Audio decoder stalled");
            decodeOnce();
        }
        int n = Math.min(maxFrames, pendingFrames);
        System.arraycopy(pending, pendingOffset * 2, stereo, 0, n * 2);
        pendingOffset += n; pendingFrames -= n;
        return n;
    }

    private void decodeOnce() {
        if (!inputEos) {
            int index = codec.dequeueInputBuffer(2_000);
            if (index >= 0) {
                int size = extractor.readSampleData(codec.getInputBuffer(index), 0);
                if (size < 0) { codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputEos = true; }
                else { codec.queueInputBuffer(index, 0, size, extractor.getSampleTime(), 0); extractor.advance(); }
            }
        }
        int index = codec.dequeueOutputBuffer(info, 2_000);
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            MediaFormat f = codec.getOutputFormat();
            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            encoding = f.containsKey(MediaFormat.KEY_PCM_ENCODING) ? f.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
            return;
        }
        if (index < 0) return;
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true;
        if (info.size > 0) {
            ByteBuffer b = codec.getOutputBuffer(index).order(ByteOrder.LITTLE_ENDIAN);
            b.position(info.offset); b.limit(info.offset + info.size);
            boolean floats = encoding == AudioFormat.ENCODING_PCM_FLOAT;
            int frames = info.size / ((floats ? 4 : 2) * channels);
            float[] interleaved = new float[frames * channels];
            for (int i = 0; i < interleaved.length; i++) interleaved[i] = floats ? b.getFloat() : b.getShort() / 32768f;
            // Skip decoded samples before the in-point (seeks land on the previous sync sample).
            int skip = 0;
            if (info.presentationTimeUs < startUs) skip = (int) Math.min(frames, (startUs - info.presentationTimeUs) * rate / 1_000_000L);
            int keep = frames - skip;
            if (keep > 0) {
                if (pending.length < keep * 2) pending = new float[keep * 2];
                float[] stereo = new float[frames * 2];
                Resampler.toStereo(interleaved, frames, channels, stereo);
                System.arraycopy(stereo, skip * 2, pending, 0, keep * 2);
                pendingFrames = keep; pendingOffset = 0;
            }
        }
        codec.releaseOutputBuffer(index, false);
    }

    /** Sample rate of the decoded PCM (valid before the first read; refined on the first format change). */
    int sampleRate() { return rate; }

    @Override public void close() {
        if (codec != null) { try { codec.stop(); } catch (Exception ignored) { } codec.release(); codec = null; }
        extractor.release();
    }
}

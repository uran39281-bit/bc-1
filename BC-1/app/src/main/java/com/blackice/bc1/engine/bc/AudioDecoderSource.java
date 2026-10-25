package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.view.Surface;
import com.blackice.bc1.audio.PcmTimestampCursor;
import com.blackice.bc1.core.AudioMixer;
import com.blackice.bc1.core.AudioPlan;
import com.blackice.bc1.core.Resampler;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;

/** Recovered from the saved Black Ice Audio build. */
final class AudioDecoderSource implements Resampler.Source, AutoCloseable {
    private static final long WAIT_MS = 5000;
    private int channels;
    private MediaCodec codec;
    private boolean inputEos;
    private boolean outputEos;
    private int pendingFrames;
    private int pendingOffset;
    private long pendingSilence;
    private int rate;
    private final long startUs;
    private final MediaExtractor extractor = new MediaExtractor();
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private int encoding = 2;
    private float[] pending = new float[0];
    private final PcmTimestampCursor timestamps = new PcmTimestampCursor();

    static AudioMixer.Opened open(Context context, AudioPlan.Segment segment) throws Exception {
        return openAt(context, segment, 0L);
    }

    static AudioMixer.Opened openAt(Context context, AudioPlan.Segment segment, long localMs) throws Exception {
        long sourceUs = (segment.sourceInMs * 1000) + Math.round(localMs * 1000.0d * segment.speed);
        AudioDecoderSource source = new AudioDecoderSource(context, segment.uri, sourceUs);
        if (source.codec == null) {
            source.close();
            return null;
        }
        try {
            source.prime();
            return new AudioMixer.Opened(source, source.rate, source);
        } catch (Exception e) {
            source.close();
            throw e;
        }
    }

    private AudioDecoderSource(Context context, String uri, long startUs) throws Exception {
        this.startUs = startUs;
        this.extractor.setDataSource(context, Uri.parse(uri), (Map<String, String>) null);
        MediaFormat format = null;
        int track = -1;
        int i = 0;
        while (true) {
            if (i < this.extractor.getTrackCount()) {
                MediaFormat f = this.extractor.getTrackFormat(i);
                String mime = f.getString("mime");
                if (mime != null && mime.startsWith("audio/")) {
                    format = f;
                    track = i;
                    break;
                }
                i++;
            } else {
                break;
            }
        }
        if (track < 0) {
            return;
        }
        this.extractor.selectTrack(track);
        this.channels = format.getInteger("channel-count");
        this.rate = format.getInteger("sample-rate");
        this.codec = MediaCodec.createDecoderByType(format.getString("mime"));
        try {
            this.codec.configure(format, (Surface) null, (MediaCrypto) null, 0);
            this.codec.start();
            this.extractor.seekTo(startUs, 0);
        } catch (Exception e) {
            this.codec.release();
            this.codec = null;
            throw e;
        }
    }

    private void prime() throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (this.pendingFrames == 0 && this.pendingSilence == 0 && !this.outputEos) {
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Audio decoder stalled");
            }
            decodeOnce();
        }
    }

    @Override // com.blackice.bc1.core.Resampler.Source
    public int read(float[] stereo, int maxFrames) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (this.pendingFrames == 0 && this.pendingSilence == 0) {
            if (this.outputEos) {
                return -1;
            }
            if (System.currentTimeMillis() > deadline) {
                throw new IllegalStateException("Audio decoder stalled");
            }
            decodeOnce();
        }
        if (this.pendingSilence > 0) {
            int n = (int) Math.min(maxFrames, this.pendingSilence);
            Arrays.fill(stereo, 0, n * 2, 0.0f);
            this.pendingSilence -= n;
            return n;
        }
        int n2 = Math.min(maxFrames, this.pendingFrames);
        System.arraycopy(this.pending, this.pendingOffset * 2, stereo, 0, n2 * 2);
        this.pendingOffset += n2;
        this.pendingFrames -= n2;
        return n2;
    }

    private void decodeOnce() {
        int index;
        if (!this.inputEos && (index = this.codec.dequeueInputBuffer(2000L)) >= 0) {
            int size = this.extractor.readSampleData(this.codec.getInputBuffer(index), 0);
            if (size < 0) {
                this.codec.queueInputBuffer(index, 0, 0, 0L, 4);
                this.inputEos = true;
            } else {
                this.codec.queueInputBuffer(index, 0, size, this.extractor.getSampleTime(), 0);
                this.extractor.advance();
            }
        }
        int index2 = this.codec.dequeueOutputBuffer(this.info, 2000L);
        if (index2 == -2) {
            MediaFormat f = this.codec.getOutputFormat();
            this.channels = f.getInteger("channel-count");
            this.rate = f.getInteger("sample-rate");
            this.encoding = f.containsKey("pcm-encoding") ? f.getInteger("pcm-encoding") : 2;
            return;
        }
        if (index2 < 0) {
            return;
        }
        if ((this.info.flags & 4) != 0) {
            this.outputEos = true;
        }
        if (this.info.size > 0) {
            ByteBuffer b = this.codec.getOutputBuffer(index2).order(ByteOrder.LITTLE_ENDIAN);
            b.position(this.info.offset);
            b.limit(this.info.offset + this.info.size);
            boolean floats = this.encoding == 4;
            int frames = this.info.size / ((floats ? 4 : 2) * this.channels);
            float[] interleaved = new float[this.channels * frames];
            for (int i = 0; i < interleaved.length; i++) {
                interleaved[i] = floats ? b.getFloat() : b.getShort() / 32768.0f;
            }
            PcmTimestampCursor.Span span = this.timestamps.accept(this.info.presentationTimeUs, this.startUs, this.rate, frames);
            int skip = span.skip;
            int keep = span.keep;
            this.pendingSilence = span.silence;
            if (keep > 0) {
                if (this.pending.length < keep * 2) {
                    this.pending = new float[keep * 2];
                }
                float[] stereo = new float[frames * 2];
                Resampler.toStereo(interleaved, frames, this.channels, stereo);
                System.arraycopy(stereo, skip * 2, this.pending, 0, keep * 2);
                this.pendingFrames = keep;
                this.pendingOffset = 0;
            }
        }
        this.codec.releaseOutputBuffer(index2, false);
    }

    int sampleRate() {
        return this.rate;
    }

    @Override // java.lang.AutoCloseable
    public void close() {
        if (this.codec != null) {
            try {
                this.codec.stop();
            } catch (Exception e) {
            }
            this.codec.release();
            this.codec = null;
        }
        this.extractor.release();
    }
}

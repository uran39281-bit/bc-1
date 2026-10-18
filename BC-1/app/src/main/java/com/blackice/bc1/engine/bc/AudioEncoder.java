package com.blackice.bc1.engine.bc;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import com.blackice.bc1.core.AudioMixer;
import com.blackice.bc1.core.OutputSpec;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Mixes the whole timeline and encodes it to AAC before any video is written. The encoded samples
 * wait in a private temp file, so the muxer starts with both track formats known and can interleave
 * audio with video deterministically: no race between two encoders feeding one muxer.
 */
final class AudioEncoder {
    static final class Encoded implements AutoCloseable {
        final MediaFormat format;
        final File file;
        /** {presentationTimeUs, fileOffset, size, flags} per AAC access unit. */
        final List<long[]> samples;
        private RandomAccessFile reader;
        private int next;
        Encoded(MediaFormat format, File file, List<long[]> samples) { this.format = format; this.file = file; this.samples = samples; }

        boolean hasNext() { return next < samples.size(); }
        long nextTimeUs() { return samples.get(next)[0]; }

        /** Reads the next sample into {@code buffer} and fills {@code info}. */
        void read(ByteBuffer buffer, MediaCodec.BufferInfo info) throws IOException {
            if (reader == null) reader = new RandomAccessFile(file, "r");
            long[] s = samples.get(next++);
            byte[] bytes = new byte[(int) s[2]];
            reader.seek(s[1]); reader.readFully(bytes);
            buffer.clear(); buffer.put(bytes); buffer.flip();
            info.set(0, bytes.length, s[0], (int) s[3]);
        }

        @Override public void close() {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
            file.delete();
        }
    }

    private AudioEncoder() {}

    static Encoded encode(AudioMixer mixer, File temp, BooleanSupplier cancelled, IntConsumer progress) throws Exception {
        int rate = OutputSpec.AUDIO_RATE, channels = OutputSpec.AUDIO_CHANNELS;
        MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, OutputSpec.AUDIO_BITRATE);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024);
        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        List<long[]> samples = new ArrayList<>();
        MediaFormat outputFormat = null;
        long offset = 0;
        try (FileOutputStream out = new FileOutputStream(temp)) {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            float[] block = new float[2 * 1024];
            long framesQueued = 0, total = Math.max(1, mixer.totalFrames());
            boolean inputDone = false, outputDone = false;
            long deadline = System.currentTimeMillis() + 10_000;
            while (!outputDone) {
                if (cancelled.getAsBoolean()) throw new java.util.concurrent.CancellationException();
                if (System.currentTimeMillis() > deadline) throw new IllegalStateException("AAC encoder stalled");
                if (!inputDone) {
                    int index = codec.dequeueInputBuffer(2_000);
                    if (index >= 0) {
                        ByteBuffer in = codec.getInputBuffer(index).order(ByteOrder.LITTLE_ENDIAN);
                        in.clear();
                        int capacityFrames = Math.min(1024, in.remaining() / (2 * channels));
                        int n = mixer.render(block, capacityFrames);
                        long pts = framesQueued * 1_000_000L / rate;
                        if (n <= 0) {
                            codec.queueInputBuffer(index, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            for (int i = 0; i < n * 2; i++) in.putShort((short) Math.round(block[i] * 32767f));
                            codec.queueInputBuffer(index, 0, n * 2 * channels, pts, 0);
                            framesQueued += n;
                            progress.accept((int) (framesQueued * 100 / total));
                        }
                    }
                }
                int index = codec.dequeueOutputBuffer(info, 2_000);
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { outputFormat = codec.getOutputFormat(); continue; }
                if (index < 0) continue;
                deadline = System.currentTimeMillis() + 10_000;
                boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
                if (!config && info.size > 0) {
                    ByteBuffer data = codec.getOutputBuffer(index);
                    data.position(info.offset); data.limit(info.offset + info.size);
                    byte[] bytes = new byte[info.size]; data.get(bytes);
                    out.write(bytes);
                    samples.add(new long[]{info.presentationTimeUs, offset, info.size, info.flags & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM});
                    offset += info.size;
                }
                codec.releaseOutputBuffer(index, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
            }
        } catch (Exception e) {
            temp.delete();
            throw e;
        } finally {
            try { codec.stop(); } catch (Exception ignored) { }
            codec.release();
        }
        if (outputFormat == null || samples.isEmpty()) { temp.delete(); throw new IllegalStateException("AAC encoder produced no audio"); }
        return new Encoded(outputFormat, temp, samples);
    }
}

package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.view.Surface;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Writes a reversed copy of a trimmed video range as a new MP4 (H.264 + AAC) in app storage. The source file is
 * never touched.
 *
 * <p>Video: the actual presentation timestamps of the N frames in the range are read from the container, the
 * frames are fetched one by one (frame-accurate) and re-encoded in reverse order, frame i of the output being
 * source frame N-1-i, with the original spacing between frames mirrored, so variable-frame-rate sources keep their
 * rhythm and no endpoint is skipped or repeated. Audio: the same range is decoded to PCM, reversed sample by
 * sample and re-encoded, so it stays in step with the picture. If the source has audio and it cannot be
 * reversed, the whole job fails rather than silently dropping or leaving it forward.
 *
 * <p>Blocks; run off the UI thread. {@code cancelled} is polled between frames.
 */
public final class ReverseTranscoder {
    public static final class Result {
        public final long durationMs; public final int frames; public final boolean hasAudio;
        Result(long durationMs, int frames, boolean hasAudio) { this.durationMs = durationMs; this.frames = frames; this.hasAudio = hasAudio; }
    }

    /** Longest range that will be reversed (frames); longer ranges are refused with a message. */
    public static final int MAX_FRAMES = 3600;
    private static final int AUDIO_SHARE = 10;

    private ReverseTranscoder() { }

    public static Result reverse(Context context, String uri, long inMs, long outMs, File output, BooleanSupplier cancelled, IntConsumer progress) throws Exception {
        if (outMs <= inMs) throw new IllegalArgumentException("Nothing to reverse.");
        long inUs = inMs * 1000, outUs = outMs * 1000;
        List<Long> times = framesIn(context, uri, inUs, outUs);
        if (times.isEmpty()) throw new IllegalStateException("No video frames in the trimmed range.");
        if (times.size() > MAX_FRAMES) throw new IllegalArgumentException("Reversing supports up to " + MAX_FRAMES + " frames (about " + (MAX_FRAMES / 30) + " s at 30 fps). Trim the clip first.");
        File temp = new File(output.getParentFile(), output.getName() + ".part");
        boolean done = false;
        try {
            AudioPart audio = reverseAudio(context, uri, inUs, outUs, cancelled, p -> progress.accept(p * AUDIO_SHARE / 100));
            int frames = times.size();
            long spanUs = times.get(frames - 1) - times.get(0);
            long frameUs = frames > 1 ? Math.max(1, spanUs / (frames - 1)) : 33_333;
            progress.accept(AUDIO_SHARE);
            writeVideo(context, uri, times, frameUs, audio, temp, null, cancelled, p -> progress.accept(AUDIO_SHARE + p * (100 - AUDIO_SHARE) / 100));
            if (!temp.renameTo(output)) throw new java.io.IOException("Could not save the reversed footage.");
            done = true;
            progress.accept(100);
            return new Result((spanUs + frameUs) / 1000, frames, audio != null);
        } finally {
            if (!done) { temp.delete(); output.delete(); }
        }
    }

    /** Presentation times (µs) of the video frames in [inUs, outUs), oldest first. */
    static List<Long> framesIn(Context context, String uri, long inUs, long outUs) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(context, Uri.parse(uri), null);
            int track = trackOf(extractor, "video/");
            if (track < 0) throw new IllegalArgumentException("This file has no video track.");
            extractor.selectTrack(track);
            extractor.seekTo(inUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            List<Long> times = new ArrayList<>();
            while (true) {
                long t = extractor.getSampleTime();
                if (t < 0 || t > outUs + 2_000_000L) break; // B-frames reorder by at most a couple of seconds
                if (t >= inUs && t < outUs) times.add(t);
                if (!extractor.advance()) break;
            }
            Collections.sort(times);
            return times;
        } finally { extractor.release(); }
    }

    private static int trackOf(MediaExtractor extractor, String prefix) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) return i;
        }
        return -1;
    }

    // ---- Audio ----------------------------------------------------------------------------------------

    private static final class AudioPart {
        final MediaFormat format; final List<byte[]> data = new ArrayList<>(); final List<long[]> info = new ArrayList<>();
        AudioPart(MediaFormat format) { this.format = format; }
    }

    /** Decodes the range to PCM, reverses it by sample and encodes AAC. Null when the file has no audio track. */
    private static AudioPart reverseAudio(Context context, String uri, long inUs, long outUs, BooleanSupplier cancelled, IntConsumer progress) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null, encoder = null;
        try {
            extractor.setDataSource(context, Uri.parse(uri), null);
            int track = trackOf(extractor, "audio/");
            if (track < 0) return null;
            extractor.selectTrack(track);
            MediaFormat source = extractor.getTrackFormat(track);
            decoder = MediaCodec.createDecoderByType(source.getString(MediaFormat.KEY_MIME));
            decoder.configure(source, null, null, 0);
            decoder.start();
            extractor.seekTo(inUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            ByteArrayOutputStream pcm = new ByteArrayOutputStream();
            int rate = source.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels = source.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            long deadline = System.currentTimeMillis() + 120_000;
            while (!outputDone) {
                if (cancelled.getAsBoolean()) throw new CancellationException("Reverse cancelled");
                if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Audio decoder stalled.");
                if (!inputDone) {
                    int index = decoder.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        long t = extractor.getSampleTime();
                        if (t < 0 || t >= outUs) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; }
                        else {
                            ByteBuffer buffer = decoder.getInputBuffer(index);
                            int size = extractor.readSampleData(buffer, 0);
                            decoder.queueInputBuffer(index, 0, Math.max(0, size), t, 0);
                            extractor.advance();
                        }
                    }
                }
                int out = decoder.dequeueOutputBuffer(info, 10_000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat f = decoder.getOutputFormat();
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) != android.media.AudioFormat.ENCODING_PCM_16BIT)
                        throw new IllegalStateException("This audio format cannot be reversed yet.");
                } else if (out >= 0) {
                    if (info.size > 0) {
                        ByteBuffer data = decoder.getOutputBuffer(out);
                        data.position(info.offset); data.limit(info.offset + info.size);
                        // Keep only the samples inside [inUs, outUs).
                        int bytesPerFrame = 2 * channels;
                        long first = Math.round((inUs - info.presentationTimeUs) * (double) rate / 1_000_000L);
                        long last = Math.round((outUs - info.presentationTimeUs) * (double) rate / 1_000_000L);
                        int frames = info.size / bytesPerFrame;
                        int from = (int) Math.max(0, Math.min(frames, first)), to = (int) Math.max(from, Math.min(frames, last));
                        if (to > from) { byte[] chunk = new byte[(to - from) * bytesPerFrame]; data.position(info.offset + from * bytesPerFrame); data.get(chunk); pcm.write(chunk, 0, chunk.length); }
                    }
                    decoder.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            byte[] forward = pcm.toByteArray();
            int bytesPerFrame = 2 * channels;
            int frames = forward.length / bytesPerFrame;
            if (frames == 0) return null;
            byte[] reversed = new byte[frames * bytesPerFrame];
            for (int i = 0; i < frames; i++) System.arraycopy(forward, (frames - 1 - i) * bytesPerFrame, reversed, i * bytesPerFrame, bytesPerFrame);
            forward = null;
            // Encode AAC.
            MediaFormat target = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels);
            target.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            target.setInteger(MediaFormat.KEY_BIT_RATE, channels > 1 ? 128_000 : 96_000);
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            encoder.configure(target, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            AudioPart part = null;
            int offset = 0; boolean sentEnd = false, finished = false;
            long fed = 0;
            deadline = System.currentTimeMillis() + 120_000;
            while (!finished) {
                if (cancelled.getAsBoolean()) throw new CancellationException("Reverse cancelled");
                if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Audio encoder stalled.");
                if (!sentEnd) {
                    int index = encoder.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        ByteBuffer buffer = encoder.getInputBuffer(index);
                        int count = Math.min(buffer.capacity(), reversed.length - offset);
                        count -= count % bytesPerFrame;
                        if (count <= 0) { encoder.queueInputBuffer(index, 0, 0, fed * 1_000_000L / rate, MediaCodec.BUFFER_FLAG_END_OF_STREAM); sentEnd = true; }
                        else {
                            buffer.clear(); buffer.put(reversed, offset, count);
                            encoder.queueInputBuffer(index, 0, count, fed * 1_000_000L / rate, 0);
                            offset += count; fed += count / bytesPerFrame;
                            progress.accept((int) (offset * 100L / reversed.length));
                        }
                    }
                }
                int out = encoder.dequeueOutputBuffer(info, 10_000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) part = new AudioPart(encoder.getOutputFormat());
                else if (out >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                        if (part == null) part = new AudioPart(target);
                        ByteBuffer data = encoder.getOutputBuffer(out);
                        data.position(info.offset); data.limit(info.offset + info.size);
                        byte[] bytes = new byte[info.size]; data.get(bytes);
                        part.data.add(bytes); part.info.add(new long[]{info.presentationTimeUs, info.flags});
                    }
                    encoder.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) finished = true;
                }
            }
            if (part == null || part.data.isEmpty()) throw new IllegalStateException("The audio could not be reversed.");
            return part;
        } finally {
            if (decoder != null) try { decoder.stop(); } catch (Exception ignored) { }
            if (decoder != null) decoder.release();
            if (encoder != null) try { encoder.stop(); } catch (Exception ignored) { }
            if (encoder != null) encoder.release();
            extractor.release();
        }
    }

    // ---- Video ----------------------------------------------------------------------------------------

    /** Returned bitmap is owned by the writer; the filter must not recycle its input. */
    public interface FrameFilter { Bitmap apply(Bitmap frame, long ptsUs) throws Exception; }

    /** Experimental, bounded forward render. Retains source PTS and copies the original AAC packets. */
    public static void replacePerson(Context context, String uri, long durationMs, File output,
                                     FrameFilter filter, BooleanSupplier cancelled, IntConsumer progress) throws Exception {
        if (filter == null || durationMs < 1 || durationMs > 12000) throw new IllegalArgumentException("Choose a source video up to 12 seconds.");
        List<Long> times = framesIn(context, uri, 0, durationMs * 1000L + 1000);
        if (times.size() < 2 || times.size() > 360) throw new IllegalArgumentException("Choose a clip with 2 to 360 frames.");
        MediaExtractor e = new MediaExtractor();
        AudioPart audio = null;
        try {
            e.setDataSource(context, Uri.parse(uri), null);
            int track = trackOf(e, "audio/");
            if (track >= 0) {
                MediaFormat f = e.getTrackFormat(track);
                if (!MediaFormat.MIMETYPE_AUDIO_AAC.equals(f.getString(MediaFormat.KEY_MIME)))
                    throw new IllegalArgumentException("This preview supports AAC audio or silent video.");
                audio = new AudioPart(f);
                e.selectTrack(track);
                ByteBuffer buf = ByteBuffer.allocate(256 * 1024);
                long bytes = 0;
                while (e.getSampleTime() >= 0) {
                    if (cancelled.getAsBoolean()) throw new CancellationException();
                    buf.clear(); int n = e.readSampleData(buf, 0);
                    if (n < 0) break;
                    if ((bytes += n) > 16 * 1024 * 1024 || n > buf.capacity()) throw new IllegalArgumentException("Unsupported audio size.");
                    if ((e.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0) throw new IllegalArgumentException("Encrypted footage is unsupported.");
                    byte[] chunk = new byte[n]; buf.position(0); buf.get(chunk);
                    audio.data.add(chunk); audio.info.add(new long[]{e.getSampleTime(), MediaCodec.BUFFER_FLAG_KEY_FRAME});
                    if (!e.advance()) break;
                }
            }
        } finally { e.release(); }
        long frameUs = Math.max(1, (times.get(times.size()-1)-times.get(0))/(times.size()-1));
        writeVideo(context, uri, times, frameUs, audio, output, filter, cancelled, progress);
    }

    private static void writeVideo(Context context, String uri, List<Long> times, long frameUs, AudioPart audio, File output, FrameFilter filter,
                                   BooleanSupplier cancelled, IntConsumer progress) throws Exception {
        int frames = times.size();
        long lastUs = times.get(frames - 1);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        MediaCodec encoder = null; Surface input = null; EglCore egl = null; Compositor compositor = null; MediaMuxer muxer = null;
        Bitmap first = null; boolean succeeded = false;
        try {
            retriever.setDataSource(context, Uri.parse(uri));
            if (filter != null) {
                int w = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                int h = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                if ((long) w * h > 1280L * 720 || Math.min(w,h) < 16 || (w%2+h%2)!=0)
                    throw new IllegalArgumentException("Use an even-sized video up to 720p for this preview.");
            }
            first = retriever.getFrameAtTime(filter == null ? lastUs : times.get(0), MediaMetadataRetriever.OPTION_CLOSEST);
            if (first == null) throw new IllegalStateException("The video frames cannot be read on this device.");
            int width = first.getWidth() & ~1, height = first.getHeight() & ~1;
            if (width < 16 || height < 16) throw new IllegalStateException("Unsupported video size.");
            int fps = (int) Math.max(1, Math.min(120, Math.round(1_000_000.0 / frameUs)));
            MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, (int) Math.max(2_000_000L, Math.min(60_000_000L, (long) (width * (double) height * fps * 0.2))));
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            input = encoder.createInputSurface();
            encoder.start();
            egl = new EglCore(input);
            compositor = new Compositor(context, width, height);
            int texture = GlKit.texture2d();
            muxer = new MediaMuxer(output.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            Mux mux = new Mux(muxer, audio);
            Matrix flip = new Matrix();
            for (int i = 0; i < frames; i++) {
                if (cancelled.getAsBoolean()) throw new CancellationException("Reverse cancelled");
                long sourceTime = times.get(filter == null ? frames - 1 - i : i);
                Bitmap frame = i == 0 ? first : retriever.getFrameAtTime(sourceTime, MediaMetadataRetriever.OPTION_CLOSEST);
                if (frame == null) throw new IllegalStateException("A video frame could not be read (" + (i + 1) + " of " + frames + ").");
                if (filter != null) {
                    Bitmap original = frame;
                    frame = filter.apply(original, sourceTime);
                    if (frame != original && original != first) original.recycle();
                }
                // GL's origin is the bottom-left; flip so the picture ends up upright in the encoder.
                flip.reset(); flip.setScale(1, -1, frame.getWidth() / 2f, frame.getHeight() / 2f);
                Bitmap upright = Bitmap.createBitmap(frame, 0, 0, Math.min(frame.getWidth(), width), Math.min(frame.getHeight(), height), flip, false);
                if (frame != first) frame.recycle();
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, upright, 0);
                upright.recycle();
                compositor.beginFrame(false);
                compositor.drawPlain(texture);
                egl.setPresentationTime((filter == null ? lastUs - sourceTime : sourceTime) * 1000L);
                egl.swap();
                drain(encoder, mux, false);
                progress.accept((int) ((i + 1L) * 100 / frames));
            }
            encoder.signalEndOfInputStream();
            drain(encoder, mux, true);
            mux.finish();
            muxer.stop();
            succeeded = true;
        } finally {
            if (first != null && !first.isRecycled()) first.recycle();
            retriever.release();
            if (compositor != null) try { compositor.release(); } catch (Exception ignored) { }
            if (egl != null) try { egl.close(); } catch (Exception ignored) { }
            if (input != null) input.release();
            if (encoder != null) { try { encoder.stop(); } catch (Exception ignored) { } encoder.release(); }
            if (muxer != null) try { muxer.release(); } catch (Exception ignored) { }
            if (!succeeded) output.delete();
        }
    }

    private static void drain(MediaCodec encoder, Mux mux, boolean endOfStream) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        long deadline = System.currentTimeMillis() + 30_000;
        while (true) {
            int index = encoder.dequeueOutputBuffer(info, endOfStream ? 10_000 : 0);
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) return;
                if (System.currentTimeMillis() > deadline) throw new IllegalStateException("Video encoder did not finish");
                continue;
            }
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { mux.start(encoder.getOutputFormat()); continue; }
            if (index < 0) continue;
            deadline = System.currentTimeMillis() + 30_000;
            boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            if (!config && info.size > 0) {
                if (!mux.started) throw new IllegalStateException("Video sample before the encoder format");
                ByteBuffer data = encoder.getOutputBuffer(index);
                data.position(info.offset); data.limit(info.offset + info.size);
                mux.writeVideo(data, info);
            }
            encoder.releaseOutputBuffer(index, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return;
        }
    }

    /** One MP4 with a video track and an optional pre-encoded audio track, interleaved by timestamp. */
    private static final class Mux {
        final MediaMuxer muxer; final AudioPart audio; boolean started; int videoTrack, audioTrack, nextAudio;
        Mux(MediaMuxer muxer, AudioPart audio) { this.muxer = muxer; this.audio = audio; }
        void start(MediaFormat videoFormat) {
            if (started) throw new IllegalStateException("Video format changed twice");
            videoTrack = muxer.addTrack(videoFormat);
            if (audio != null) audioTrack = muxer.addTrack(audio.format);
            muxer.start(); started = true;
        }
        void writeVideo(ByteBuffer data, MediaCodec.BufferInfo info) {
            writeAudioUntil(info.presentationTimeUs);
            muxer.writeSampleData(videoTrack, data, info);
        }
        void writeAudioUntil(long timeUs) {
            if (audio == null) return;
            while (nextAudio < audio.data.size() && audio.info.get(nextAudio)[0] <= timeUs) {
                byte[] bytes = audio.data.get(nextAudio);
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                info.set(0, bytes.length, audio.info.get(nextAudio)[0], (int) audio.info.get(nextAudio)[1] & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                muxer.writeSampleData(audioTrack, ByteBuffer.wrap(bytes), info);
                nextAudio++;
            }
        }
        void finish() {
            if (!started) throw new IllegalStateException("Video encoder produced no output");
            writeAudioUntil(Long.MAX_VALUE);
        }
    }
}

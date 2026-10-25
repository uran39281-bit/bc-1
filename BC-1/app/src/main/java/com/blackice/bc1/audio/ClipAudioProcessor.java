package com.blackice.bc1.audio;

import androidx.media3.common.audio.AudioProcessor;
import androidx.media3.common.audio.BaseAudioProcessor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/* loaded from: classes6.dex */
public final class ClipAudioProcessor extends BaseAudioProcessor {
    private AudioDsp dsp;
    private float[] scratch = new float[0];
    private final AudioSettings settings;

    public ClipAudioProcessor(AudioSettings s) {
        this.settings = s.copy();
    }

    @Override // androidx.media3.common.audio.BaseAudioProcessor
    protected AudioProcessor.AudioFormat onConfigure(AudioProcessor.AudioFormat input) throws AudioProcessor.UnhandledAudioFormatException {
        if (input.encoding == 2 || input.encoding == 4) {
            return this.settings.active() ? input : AudioProcessor.AudioFormat.NOT_SET;
        }
        throw new AudioProcessor.UnhandledAudioFormatException(input);
    }

    @Override // androidx.media3.common.audio.BaseAudioProcessor
    protected void onFlush() {
        if (this.inputAudioFormat.channelCount > 0) {
            this.dsp = new AudioDsp(this.settings, this.inputAudioFormat.sampleRate, this.inputAudioFormat.channelCount);
        }
    }

    @Override // androidx.media3.common.audio.AudioProcessor
    public void queueInput(ByteBuffer input) {
        int channels = this.inputAudioFormat.channelCount;
        int bytes = this.inputAudioFormat.encoding == 4 ? 4 : 2;
        int samples = input.remaining() / bytes;
        int frames = samples / channels;
        if (this.scratch.length < samples) {
            this.scratch = new float[samples];
        }
        input.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < samples; i++) {
            this.scratch[i] = bytes == 4 ? input.getFloat() : input.getShort() / 32768.0f;
        }
        this.dsp.process(this.scratch, frames);
        ByteBuffer out = replaceOutputBuffer(samples * bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int i2 = 0; i2 < samples; i2++) {
            float[] fArr = this.scratch;
            if (bytes == 4) {
                out.putFloat(fArr[i2]);
            } else {
                out.putShort((short) Math.max(-32768, Math.min(32767, Math.round(fArr[i2] * 32768.0f))));
            }
        }
        out.flip();
    }
}

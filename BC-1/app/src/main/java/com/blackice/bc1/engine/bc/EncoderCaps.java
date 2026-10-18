package com.blackice.bc1.engine.bc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import com.blackice.bc1.core.ExportPlanner;

/** Asks the device's H.264 encoders whether a frame size and rate can be encoded. */
public final class EncoderCaps {
    private EncoderCaps() {}

    /** True when any H.264 encoder reports support for this exact size and frame rate. */
    public static boolean supports(int width, int height, int fps) {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                if (!type.equalsIgnoreCase(MediaFormat.MIMETYPE_VIDEO_AVC)) continue;
                try {
                    MediaCodecInfo.VideoCapabilities video = info.getCapabilitiesForType(type).getVideoCapabilities();
                    if (video != null && video.areSizeAndRateSupported(width, height, fps)) return true;
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return false;
    }

    public static ExportPlanner.Plan plan(int shortEdge, int fps, boolean portrait) {
        return ExportPlanner.choose(shortEdge, fps, portrait, EncoderCaps::supports);
    }
}

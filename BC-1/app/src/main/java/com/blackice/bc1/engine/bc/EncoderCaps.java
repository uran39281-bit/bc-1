package com.blackice.bc1.engine.bc;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import com.blackice.bc1.core.ExportPlanner;
import com.blackice.bc1.core.VideoCodec;

/** Asks the device's video encoders whether a codec, frame size and rate can be encoded. */
public final class EncoderCaps {
    private EncoderCaps() {}

    /** True when any encoder for the codec reports support for this exact size and frame rate. */
    public static boolean supports(VideoCodec codec, int width, int height, int fps) {
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            if (!info.isEncoder()) continue;
            for (String type : info.getSupportedTypes()) {
                if (!type.equalsIgnoreCase(codec.mime)) continue;
                try {
                    MediaCodecInfo.VideoCapabilities video = info.getCapabilitiesForType(type).getVideoCapabilities();
                    if (video != null && video.areSizeAndRateSupported(width, height, fps)) return true;
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return false;
    }

    public static boolean supportsBitrate(com.blackice.bc1.core.ExportPlanner.Plan plan){
        for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
            if(!info.isEncoder())continue;for(String type:info.getSupportedTypes())if(type.equalsIgnoreCase(plan.codec.mime))try{
                MediaCodecInfo.VideoCapabilities video=info.getCapabilitiesForType(type).getVideoCapabilities();
                if(video!=null&&video.areSizeAndRateSupported(plan.width,plan.height,plan.frameRate)&&video.getBitrateRange().contains(plan.bitrate))return true;
            }catch(IllegalArgumentException ignored){}
        }return false;
    }
    public static boolean supports(int width, int height, int fps) { return supports(VideoCodec.H264, width, height, fps); }

    public static ExportPlanner.Plan plan(int shortEdge, int fps, boolean portrait, VideoCodec.Choice choice) {
        return ExportPlanner.choose(shortEdge, fps, portrait, choice, EncoderCaps::supports);
    }

    public static ExportPlanner.Plan plan(int shortEdge, int fps, boolean portrait) {
        return plan(shortEdge, fps, portrait, VideoCodec.Choice.AUTO);
    }
}

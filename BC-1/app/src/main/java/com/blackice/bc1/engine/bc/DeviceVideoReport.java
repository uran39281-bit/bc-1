package com.blackice.bc1.engine.bc;

import android.app.ActivityManager;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import com.blackice.bc1.core.ExportPlanner;
import com.blackice.bc1.core.VideoCodec;
import java.util.Locale;

/** Plain-text report of what this device's video codecs can do, for Setup → Device video capabilities. */
public final class DeviceVideoReport {
    private DeviceVideoReport() {}

    private static final int[][] PRESETS = {{1920, 1080, 60}, {1920, 1080, 120}, {2560, 1440, 120}, {3840, 2160, 60}, {3840, 2160, 120}};

    public static String build(Context context) {
        StringBuilder b = new StringBuilder();
        b.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" • Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        if (Build.VERSION.SDK_INT >= 31) b.append("Chip: ").append(Build.SOC_MANUFACTURER).append(' ').append(Build.SOC_MODEL).append('\n');
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        b.append(String.format(Locale.US, "Memory: %.1f GB\n", memory.totalMem / 1e9));

        b.append("\nExport presets (encoder support):\n");
        for (int[] p : PRESETS) {
            b.append(String.format(Locale.US, "  %dp%d  ", p[1], p[2]));
            for (VideoCodec codec : VideoCodec.values())
                b.append(codec == VideoCodec.H264 ? "H.264 " : "  HEVC ").append(EncoderCaps.supports(codec, p[0], p[1], p[2]) ? "yes" : "no");
            b.append('\n');
        }
        ExportPlanner.Plan best = EncoderCaps.plan(2160, 120, false);
        b.append("A 4K 120 fps request exports as: ").append(best.describe()).append('\n');

        b.append("\nVideo codecs:\n");
        for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
            for (String type : info.getSupportedTypes()) {
                if (!type.equalsIgnoreCase(VideoCodec.H264.mime) && !type.equalsIgnoreCase(VideoCodec.HEVC.mime)) continue;
                try {
                    MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(type);
                    MediaCodecInfo.VideoCapabilities video = caps.getVideoCapabilities();
                    boolean hardware = Build.VERSION.SDK_INT >= 29 ? info.isHardwareAccelerated() : !info.getName().startsWith("c2.android") && !info.getName().startsWith("OMX.google");
                    b.append("  ").append(info.isEncoder() ? "Encoder " : "Decoder ").append(info.getName())
                            .append(hardware ? " (hardware)" : " (software)").append('\n');
                    b.append(String.format(Locale.US, "    %s up to %dx%d • %d instances • 4K60 %s • 4K120 %s\n",
                            type.equalsIgnoreCase(VideoCodec.HEVC.mime) ? "HEVC" : "H.264",
                            video.getSupportedWidths().getUpper(), video.getSupportedHeights().getUpper(), caps.getMaxSupportedInstances(),
                            video.areSizeAndRateSupported(3840, 2160, 60) ? "yes" : "no",
                            video.areSizeAndRateSupported(3840, 2160, 120) ? "yes" : "no"));
                } catch (RuntimeException ignored) { }
            }
        }
        b.append("\nReported limits are what the codecs advertise; real throughput with many 4K layers is measured by an export.");
        return b.toString();
    }
}

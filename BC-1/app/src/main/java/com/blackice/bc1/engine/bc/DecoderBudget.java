package com.blackice.bc1.engine.bc;

import android.app.ActivityManager;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import com.blackice.bc1.core.OutputSpec;
import com.blackice.bc1.editor.EditProject;
import java.util.HashSet;
import java.util.Set;

/**
 * How many video decoders an export may hold open at once: the device's reported decoder instance
 * limit for the project's codecs, bounded by a memory estimate for the largest source (each decoder
 * queues roughly a dozen YUV frames, plus BC Core's two frame-sized RGBA textures per layer).
 */
public final class DecoderBudget {
    public static final int MIN = 2, MAX = 16;
    private DecoderBudget() {}

    public static final class Estimate {
        public final int budget, instances, memoryBudget, largestWidth, largestHeight;
        Estimate(int instances, int memoryBudget, int w, int h) {
            this.instances = instances; this.memoryBudget = memoryBudget; largestWidth = w; largestHeight = h;
            budget = Math.max(MIN, Math.min(MAX, Math.min(instances, memoryBudget)));
        }
    }

    public static Estimate estimate(Context context, EditProject project, OutputSpec spec) {
        Set<String> mimes = new HashSet<>(); Set<String> seen = new HashSet<>();
        int largestW = 0, largestH = 0;
        for (EditProject.Clip c : project.clips) {
            if (c.image || EditProject.audioTrack(c.track) || !seen.add(c.uri)) continue;
            MediaExtractor e = new MediaExtractor();
            try {
                e.setDataSource(context, Uri.parse(c.uri), null);
                for (int i = 0; i < e.getTrackCount(); i++) {
                    MediaFormat f = e.getTrackFormat(i); String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime == null || !mime.startsWith("video/")) continue;
                    mimes.add(mime);
                    int w = f.getInteger(MediaFormat.KEY_WIDTH), h = f.getInteger(MediaFormat.KEY_HEIGHT);
                    if ((long) w * h > (long) largestW * largestH) { largestW = w; largestH = h; }
                    break;
                }
            } catch (Exception ignored) {
            } finally { e.release(); }
        }
        if (largestW == 0) { largestW = spec.width; largestH = spec.height; }
        int instances = MAX;
        for (String mime : mimes) instances = Math.min(instances, instancesFor(mime));
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        long perDecoder = (long) (largestW * (double) largestH * 1.5 * 12) + 2L * spec.width * spec.height * 4;
        int memoryBudget = (int) Math.max(1, Math.min(Integer.MAX_VALUE, memory.totalMem / 3 / Math.max(1, perDecoder)));
        return new Estimate(instances, memoryBudget, largestW, largestH);
    }

    /** Largest instance count reported by the decoders BC Core would pick for this codec. */
    static int instancesFor(String mime) {
        int best = 0;
        for (String name : VideoSource.decoders(mime)) {
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (!info.getName().equals(name)) continue;
                try { best = Math.max(best, info.getCapabilitiesForType(mime).getMaxSupportedInstances()); } catch (IllegalArgumentException ignored) { }
            }
            if (best > 0) break; // the first (preferred) decoder decides
        }
        return best > 0 ? best : MIN;
    }
}

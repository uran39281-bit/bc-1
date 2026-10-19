package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import com.blackice.bc1.editor.EditProject;
import java.util.HashSet;
import java.util.Set;

/** Detects HDR (PQ / HLG) video clips, which ICE Lite does not tone-map yet. */
public final class HdrCheck {
    private HdrCheck() {}

    public static boolean anyHdr(Context context, EditProject project) {
        Set<String> seen = new HashSet<>();
        for (EditProject.Clip c : project.clips) {
            if (c.image || EditProject.audioTrack(c.track) || !seen.add(c.uri)) continue;
            if (isHdr(context, c.uri)) return true;
        }
        return false;
    }

    static boolean isHdr(Context context, String uri) {
        MediaExtractor e = new MediaExtractor();
        try {
            e.setDataSource(context, Uri.parse(uri), null);
            for (int i = 0; i < e.getTrackCount(); i++) {
                MediaFormat f = e.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("video/")) continue;
                if (f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
                    int transfer = f.getInteger(MediaFormat.KEY_COLOR_TRANSFER);
                    if (transfer == MediaFormat.COLOR_TRANSFER_ST2084 || transfer == MediaFormat.COLOR_TRANSFER_HLG) return true;
                }
                return false;
            }
        } catch (Exception ignored) {
        } finally { e.release(); }
        return false;
    }
}

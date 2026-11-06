package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** UTF-8 subtitle serialization from the edited timeline, not the original AI transcript. */
public final class SubtitleFiles {
    private SubtitleFiles() { }
    public static List<EditProject.Overlay> captions(EditProject p) {
        List<EditProject.Overlay> out = new ArrayList<>();
        for (EditProject.Overlay o : p.overlays)
            if (CaptionBuilder.GROUP.equals(o.group) && o.imageUri == null && o.shape == 0) out.add(o);
        out.sort(Comparator.comparingLong(o -> o.startMs));
        return out;
    }
    public static String encode(EditProject p, boolean vtt) {
        StringBuilder out = new StringBuilder(vtt ? "WEBVTT\n\n" : ""); int index = 0;
        for (EditProject.Overlay o : captions(p)) {
            if (o.hidden || o.text.trim().isEmpty()) continue;
            if (o.startMs < 0 || o.endMs <= o.startMs) throw new IllegalArgumentException("Invalid caption timing");
            if (!vtt) out.append(++index).append('\n');
            out.append(time(o.startMs, vtt)).append(" --> ").append(time(o.endMs, vtt)).append('\n');
            // Treat text as literal text, even if the transcript contains subtitle markup.
            String text = o.text.replace("\r", "").trim().replaceAll("\n[ \\t]*\n+", "\n")
                    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            out.append(text).append("\n\n");
        }
        return out.toString();
    }
    public static String time(long ms, boolean vtt) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d%s%03d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, vtt ? "." : ",", ms % 1000);
    }
    public static long seconds(String value) {
        double s = Double.parseDouble(value.trim());
        if (!Double.isFinite(s) || s < 0 || s > 7_200_000) throw new IllegalArgumentException("Enter valid seconds, such as 12.350");
        return Math.round(s * 1000);
    }
}

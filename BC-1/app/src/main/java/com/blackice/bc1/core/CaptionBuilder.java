package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/* loaded from: classes6.dex */
public final class CaptionBuilder {
    public static final String GROUP = "Captions";
    static final long MAX_CUE_MS = 6000;
    static final long MIN_CUE_MS = 0;
    private static final Pattern NON_SPEECH = Pattern.compile("^[\\[(*♪].*[\\])*♪]$");

    public static final class Cue {
        public final long endMs;
        public final long startMs;
        public final String text;

        public Cue(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }

        public String toString() {
            return this.startMs + "-" + this.endMs + " " + this.text.replace('\n', '|');
        }
    }

    private CaptionBuilder() {
    }

    public static int lineChars(int width, int height) {
        return height > width ? 24 : 38;
    }

    public static List<Cue> cues(long[] starts, long[] ends, String[] texts, long offsetMs, long endLimitMs, int lineChars) {
        List<Cue> raw = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            String text = clean(texts[i]);
            if (!text.isEmpty()) {
                raw.addAll(split(Math.max(0L, starts[i]) + offsetMs, Math.max(starts[i], ends[i]) + offsetMs, text, lineChars));
            }
        }
        List<Cue> out = new ArrayList<>(raw.size());
        for (int i2 = 0; i2 < raw.size(); i2++) {
            Cue c = raw.get(i2);
            long nextStart = i2 + 1 < raw.size() ? raw.get(i2 + 1).startMs : Long.MAX_VALUE;
            long start = Math.min(c.startMs, endLimitMs);
            long end = Math.min(c.endMs, Math.min(nextStart, endLimitMs));
            long start2 = (out.isEmpty() || start >= out.get(out.size() + (-1)).endMs) ? start : out.get(out.size() - 1).endMs;
            if (end - start2 >= 100) {
                out.add(new Cue(start2, end, c.text));
            }
        }
        return out;
    }

    static String clean(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (t.isEmpty() || NON_SPEECH.matcher(t).matches()) {
            return "";
        }
        String t2 = t.replaceAll("\\[[^\\]]*]", "").replaceAll("\\s+", " ").trim();
        return t2.matches("[\\p{Punct}\\s]*") ? "" : t2;
    }

    static List<String> wrap(String text, int lineChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        String[] split = text.split(" ");
        int length = split.length;
        for (int i = 0; i < length; i++) {
            String word = split[i];
            if (!word.isEmpty()) {
                if (word.codePointCount(0, word.length()) > lineChars) {
                    if (line.length() > 0) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    while (!word.isEmpty()) {
                        int n = Math.min(lineChars, word.codePointCount(0, word.length()));
                        int cut = word.offsetByCodePoints(0, n);
                        lines.add(word.substring(0, cut));
                        word = word.substring(cut);
                    }
                } else {
                    if (line.length() > 0 && line.length() + 1 + word.length() > lineChars) {
                        lines.add(line.toString());
                        line.setLength(0);
                    }
                    if (line.length() > 0) {
                        line.append(' ');
                    }
                    line.append(word);
                }
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    static List<Cue> split(long start, long end, String text, int lineChars) {
        List<Cue> out = new ArrayList<>();
        if (end <= start) {
            return out;
        }
        List<String> lines = wrap(text, lineChars);
        String wrapped = String.join("\n", lines);
        if (wrapped.length() > 200) {
            throw new IllegalArgumentException("Speech segment is too long. Regenerate captions with the updated timing model.");
        }
        out.add(new Cue(start, end, wrapped));
        return out;
    }

    public static int apply(EditProject project, List<Cue> cues) {
        return apply(project, cues, 0L, Long.MAX_VALUE);
    }

    public static int apply(EditProject project, List<Cue> cues, long fromMs, long toMs) {
        int retained = 0;
        for (EditProject.Overlay o : project.overlays) {
            if ("Captions".equals(o.group) && (o.startMs >= toMs || o.endMs <= fromMs)) {
                retained++;
            }
        }
        if (cues.size() + retained > 2000) {
            throw new IllegalArgumentException("Use at most 2000 captions. Caption a shorter selection.");
        }
        for (Cue c : cues) {
            if (c.startMs < fromMs || c.endMs > toMs || c.endMs <= c.startMs || c.text == null || c.text.length() > 200) {
                throw new IllegalArgumentException("Invalid caption cue");
            }
        }
        Iterator<EditProject.Overlay> it = project.overlays.iterator();
        while (it.hasNext()) {
            EditProject.Overlay o2 = it.next();
            if ("Captions".equals(o2.group) && o2.startMs < toMs && o2.endMs > fromMs) {
                it.remove();
            }
        }
        int n = 0;
        for (Cue c2 : cues) {
            EditProject.Overlay o3 = new EditProject.Overlay();
            o3.text = c2.text;
            o3.startMs = c2.startMs;
            o3.endMs = c2.endMs;
            o3.group = "Captions";
            n++;
            o3.name = "Caption " + n;
            o3.x = 0.0f;
            o3.y = -0.72f;
            o3.scale = 0.62f;
            o3.alignment = 1;
            o3.color = -1;
            o3.borderColor = -16777216;
            o3.borderWidth = 0.05f;
            o3.shadow = 0.6f;
            o3.font = "sans-serif-medium";
            project.overlays.add(o3);
        }
        return n;
    }

    public static int remove(EditProject project) {
        int before = project.overlays.size();
        project.overlays.removeIf(new Predicate() { // from class: com.blackice.bc1.core.CaptionBuilder$$ExternalSyntheticLambda0
            @Override // java.util.function.Predicate
            public final boolean test(Object obj) {
                boolean equals;
                equals = "Captions".equals(((EditProject.Overlay) obj).group);
                return equals;
            }
        });
        return before - project.overlays.size();
    }
}

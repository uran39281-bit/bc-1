package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns speech-recognition segments into caption overlays: cleans the text, splits long phrases into
 * readable cues of at most two lines, keeps cues on screen long enough to read without overlapping,
 * and places them on the project's text layer as the "Captions" group (re-running replaces that group).
 */
public final class CaptionBuilder {
    public static final String GROUP = "Captions";
    /** Shortest time a cue stays up, if the next cue allows. */
    static final long MIN_CUE_MS = 900;
    /** Longest single cue before it is split by words. */
    static final long MAX_CUE_MS = 6000;

    private static final Pattern NON_SPEECH = Pattern.compile("^[\\[(*♪].*[\\])*♪]$");

    public static final class Cue {
        public final long startMs, endMs; public final String text;
        public Cue(long startMs, long endMs, String text) { this.startMs = startMs; this.endMs = endMs; this.text = text; }
        @Override public String toString() { return startMs + "-" + endMs + " " + text.replace('\n', '|'); }
    }

    private CaptionBuilder() {}

    /** Characters per caption line: shorter on tall (portrait) frames. */
    public static int lineChars(int width, int height) { return height > width ? 24 : 38; }

    /**
     * @param starts segment starts in ms relative to the audio; {@code ends}, {@code texts} parallel to it
     * @param offsetMs timeline position of the audio's start
     * @param endLimitMs no cue extends past this timeline time
     */
    public static List<Cue> cues(long[] starts, long[] ends, String[] texts, long offsetMs, long endLimitMs, int lineChars) {
        List<Cue> raw = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            String text = clean(texts[i]);
            if (text.isEmpty()) continue;
            long start = Math.max(0, starts[i]) + offsetMs, end = Math.max(starts[i], ends[i]) + offsetMs;
            raw.addAll(split(start, end, text, lineChars));
        }
        // Readable durations: extend short cues into the gap after them, never into the next cue.
        List<Cue> out = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            Cue c = raw.get(i);
            long nextStart = i + 1 < raw.size() ? raw.get(i + 1).startMs : Long.MAX_VALUE;
            long start = Math.min(c.startMs, endLimitMs);
            long end = Math.max(c.endMs, start + MIN_CUE_MS);
            end = Math.min(end, Math.min(nextStart, endLimitMs));
            if (!out.isEmpty() && start < out.get(out.size() - 1).endMs) start = out.get(out.size() - 1).endMs;
            if (end - start < 100) continue;
            out.add(new Cue(start, end, c.text));
        }
        return out;
    }

    /** Removes whisper's non-speech markers ("[BLANK_AUDIO]", "(music)", "♪ ... ♪") and extra spaces. */
    static String clean(String text) {
        if (text == null) return "";
        String t = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (t.isEmpty() || NON_SPEECH.matcher(t).matches()) return "";
        t = t.replaceAll("\\[[^\\]]*]", "").replaceAll("\\s+", " ").trim();
        return t.matches("[\\p{Punct}\\s]*") ? "" : t;
    }

    /** Greedy word wrap to lines of at most {@code lineChars} (a longer single word keeps its own line). */
    static List<String> wrap(String text, int lineChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (line.length() > 0 && line.length() + 1 + word.length() > lineChars) { lines.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /** Splits text into cues of at most two lines, timed in proportion to their length; long cues split by time too. */
    static List<Cue> split(long start, long end, String text, int lineChars) {
        long duration = Math.max(1, end - start);
        String[] words = text.split(" ");
        if (duration > MAX_CUE_MS && words.length > 1) {
            int half = words.length / 2;
            String first = String.join(" ", java.util.Arrays.copyOfRange(words, 0, half));
            String second = String.join(" ", java.util.Arrays.copyOfRange(words, half, words.length));
            long mid = start + duration * first.length() / Math.max(1, first.length() + second.length());
            List<Cue> parts = new ArrayList<>(split(start, mid, first, lineChars));
            parts.addAll(split(mid, end, second, lineChars));
            return parts;
        }
        List<String> lines = wrap(text, lineChars), chunks = new ArrayList<>();
        for (int i = 0; i < lines.size(); i += 2) chunks.add(i + 1 < lines.size() ? lines.get(i) + "\n" + lines.get(i + 1) : lines.get(i));
        int total = 0;
        for (String c : chunks) total += c.length();
        List<Cue> out = new ArrayList<>();
        long cursor = start;
        for (int i = 0; i < chunks.size(); i++) {
            long next = i == chunks.size() - 1 ? end : cursor + duration * chunks.get(i).length() / Math.max(1, total);
            out.add(new Cue(cursor, next, chunks.get(i)));
            cursor = next;
        }
        return out;
    }

    /**
     * Replaces the project's caption group with {@code cues}, styled as readable bottom captions.
     * Returns the number of captions added. Overlays outside the group are untouched.
     */
    public static int apply(EditProject project, List<Cue> cues) {
        for (Iterator<EditProject.Overlay> it = project.overlays.iterator(); it.hasNext(); )
            if (GROUP.equals(it.next().group)) it.remove();
        int n = 0;
        for (Cue c : cues) {
            EditProject.Overlay o = new EditProject.Overlay();
            o.text = c.text; o.startMs = c.startMs; o.endMs = c.endMs;
            o.group = GROUP; o.name = "Caption " + (++n);
            o.x = 0; o.y = -.72f; o.scale = .62f; o.alignment = 1;
            o.color = 0xFFFFFFFF; o.borderColor = 0xFF000000; o.borderWidth = .05f; o.shadow = .6f;
            o.font = "sans-serif-medium";
            project.overlays.add(o);
        }
        return n;
    }

    /** Removes every caption made by {@link #apply}. */
    public static int remove(EditProject project) {
        int before = project.overlays.size();
        project.overlays.removeIf(o -> GROUP.equals(o.group));
        return before - project.overlays.size();
    }
}

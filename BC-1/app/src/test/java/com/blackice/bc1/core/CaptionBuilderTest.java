package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaptionBuilderTest {
    private static List<CaptionBuilder.Cue> cues(long[] s, long[] e, String[] t) { return CaptionBuilder.cues(s, e, t, 0, 60_000, 38); }

    @Test public void nonSpeechMarkersAreDropped() {
        assertEquals("", CaptionBuilder.clean(" [BLANK_AUDIO]"));
        assertEquals("", CaptionBuilder.clean("(music)"));
        assertEquals("", CaptionBuilder.clean("♪ la la ♪"));
        assertEquals("", CaptionBuilder.clean(" ... "));
        assertEquals("Hello there", CaptionBuilder.clean("  Hello [laughs]  there "));
        List<CaptionBuilder.Cue> c = cues(new long[]{0, 1000}, new long[]{1000, 2000}, new String[]{"[BLANK_AUDIO]", " Hi."});
        assertEquals(1, c.size());
        assertEquals("Hi.", c.get(0).text);
    }

    @Test public void longPhrasesWrapToTwoLinesAndSplitInOrder() {
        String text = "And so my fellow Americans ask not what your country can do for you ask what you can do for your country";
        List<CaptionBuilder.Cue> c = cues(new long[]{300}, new long[]{5800}, new String[]{text});
        assertTrue(c.size() >= 2);
        StringBuilder joined = new StringBuilder();
        long cursor = 300;
        for (CaptionBuilder.Cue cue : c) {
            String[] lines = cue.text.split("\n");
            assertTrue("At most two lines: " + cue, lines.length <= 2);
            for (String line : lines) assertTrue("Line fits: " + line, line.length() <= 38);
            assertEquals("Cues are contiguous", cursor, cue.startMs);
            cursor = cue.endMs;
            joined.append(joined.length() > 0 ? " " : "").append(cue.text.replace('\n', ' '));
        }
        assertEquals(5800, cursor);
        assertEquals("No words lost or reordered", text, joined.toString());
    }

    @Test public void longDurationsSplitByTimeAndShortCuesStayReadable() {
        List<CaptionBuilder.Cue> c = cues(new long[]{0}, new long[]{12_000}, new String[]{"one two three four"});
        assertTrue(c.size() >= 2);
        for (CaptionBuilder.Cue cue : c) assertTrue("Cue too long: " + cue, cue.endMs - cue.startMs <= CaptionBuilder.MAX_CUE_MS);
        assertEquals(12_000, c.get(c.size() - 1).endMs);
        // A 200 ms word is held for the minimum time, but never over the next cue.
        c = cues(new long[]{0, 500}, new long[]{200, 3000}, new String[]{"Yes.", "Then the rest."});
        assertEquals(500, c.get(0).endMs);
        c = cues(new long[]{0, 5000}, new long[]{200, 6000}, new String[]{"Yes.", "Later."});
        assertEquals(CaptionBuilder.MIN_CUE_MS, c.get(0).endMs);
    }

    @Test public void offsetAndEndLimitApply() {
        List<CaptionBuilder.Cue> c = CaptionBuilder.cues(new long[]{0, 4000}, new long[]{1500, 9000}, new String[]{"A", "B"}, 2000, 7000, 38);
        assertEquals(2000, c.get(0).startMs);
        assertEquals(7000, c.get(1).endMs);
        assertTrue(CaptionBuilder.cues(new long[]{8000}, new long[]{9000}, new String[]{"late"}, 0, 7000, 38).isEmpty());
    }

    @Test public void applyReplacesOnlyTheCaptionGroup() {
        EditProject p = new EditProject();
        EditProject.Overlay title = new EditProject.Overlay(); title.text = "TITLE"; p.overlays.add(title);
        CaptionBuilder.apply(p, cues(new long[]{0}, new long[]{1000}, new String[]{"first"}));
        assertEquals(2, p.overlays.size());
        int n = CaptionBuilder.apply(p, cues(new long[]{0, 2000}, new long[]{1000, 3000}, new String[]{"one", "two"}));
        assertEquals(2, n);
        assertEquals(3, p.overlays.size());
        assertEquals("TITLE", p.overlays.get(0).text);
        EditProject.Overlay cap = p.overlays.get(1);
        assertEquals(CaptionBuilder.GROUP, cap.group);
        assertTrue("Captions sit near the bottom", cap.y < -.5f);
        assertEquals(2, CaptionBuilder.remove(p));
        assertEquals(1, p.overlays.size());
    }

    @Test public void portraitFramesUseShorterLines() {
        assertTrue(CaptionBuilder.lineChars(1080, 1920) < CaptionBuilder.lineChars(1920, 1080));
    }

    @Test public void longAudioIsCutAtThePauseNearTheChunkEnd() {
        int rate = 16_000; float[] a = new float[rate * 20];
        for (int i = 0; i < a.length; i++) a[i] = (float) Math.sin(i * .07) * .5f;     // continuous "speech"
        for (int i = rate * 17; i < rate * 17 + rate / 5; i++) a[i] = 0;               // a 200 ms pause at 17 s
        int cut = SpeechChunks.quietestCut(a, a.length, rate, rate * 5);
        assertTrue("Cut inside the pause: " + cut, cut >= rate * 17 && cut <= rate * 17 + rate / 5);
        assertTrue(SpeechChunks.quietestCut(a, 100, rate, rate) >= 1);
    }

    @Test public void segmentsTightenToTheActualSpeech() {
        int rate = 16_000; float[] a = new float[rate * 6];
        for (int i = rate * 3; i < rate * 5; i++) a[i] = (float) Math.sin(i * .07) * .4f; // speech 3-5 s after silence
        long[] t = SpeechChunks.tighten(a, rate, 0, 6000);
        assertTrue("Starts at the speech, not at 0: " + t[0], t[0] >= 2800 && t[0] <= 3000);
        assertTrue("Ends just after the speech: " + t[1], t[1] >= 5000 && t[1] <= 5300);
        long[] silent = SpeechChunks.tighten(new float[rate * 2], rate, 100, 1500);
        assertArrayEquals("Silence keeps whisper's times", new long[]{100, 1500}, silent);
    }
}

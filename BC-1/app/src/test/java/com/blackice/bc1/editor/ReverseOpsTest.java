package com.blackice.bc1.editor;

import static org.junit.Assert.*;
import org.junit.Test;

/** Footage direction as a model edit: nothing about timing, rate or authored animation changes. */
public class ReverseOpsTest {
    private static EditProject.Clip clip() {
        EditProject.Clip c = new EditProject.Clip();
        c.uri = "file:///forward.mp4"; c.name = "Beach"; c.sourceDurationMs = 10_000; c.inMs = 2000; c.outMs = 6000; c.speed = 2f;
        c.motion.put(Motion.X, new Motion.Key(2500, .1f)); c.motion.put(Motion.X, new Motion.Key(5000, .6f));
        c.motion.put(Motion.ROTATION, new Motion.Key(1000, 5f)); // before the trim: outside what is shown
        return c;
    }

    @Test public void reversingKeepsDurationRateAndTheAuthoredAnimationTimes() {
        EditProject.Clip c = clip(); long duration = c.durationMs();
        ReverseOps.reverse(c, "file:///reversed.mp4", 4000);
        assertTrue(c.reversed); assertEquals("file:///reversed.mp4", c.uri); assertEquals(0, c.inMs); assertEquals(4000, c.outMs);
        assertEquals("Same timeline duration", duration, c.durationMs()); assertEquals("Rate magnitude kept", 2f, c.speed, 0);
        assertEquals("Key stays at the same project time: 500 ms in", 500, c.motion.curves.get(Motion.X).get(0).timeMs);
        assertEquals(3000, c.motion.curves.get(Motion.X).get(1).timeMs);
        assertEquals("Values are not reversed", .1f, c.motion.curves.get(Motion.X).get(0).value, 0);
        assertTrue("A key outside the trimmed range is dropped, not wrapped", c.motion.curves.get(Motion.ROTATION).isEmpty());
        assertEquals("Beach (reversed)", c.name);
    }

    @Test public void restoreForwardReturnsTheSameOriginalRange() {
        EditProject.Clip c = clip();
        ReverseOps.reverse(c, "file:///reversed.mp4", 4000);
        ReverseOps.restore(c);
        assertFalse(c.reversed); assertEquals("file:///forward.mp4", c.uri); assertEquals(2000, c.inMs); assertEquals(6000, c.outMs);
        assertEquals(10_000, c.sourceDurationMs); assertEquals("Beach", c.name);
        assertEquals(2500, c.motion.curves.get(Motion.X).get(0).timeMs); assertEquals(5000, c.motion.curves.get(Motion.X).get(1).timeMs);
        assertNull(c.forwardUri);
    }

    @Test public void aDerivativeReportedOneFrameShortStillRestoresTheExactOriginalRange() {
        EditProject.Clip c = clip(); long duration = c.durationMs();
        ReverseOps.reverse(c, "file:///reversed.mp4", 3967);
        assertEquals("The clip keeps the whole trimmed range", 4000, c.outMs); assertEquals(duration, c.durationMs());
        ReverseOps.restore(c);
        assertEquals(2000, c.inMs); assertEquals(6000, c.outMs);
    }

    @Test public void trimmingTheReversedClipMapsBackToTheMirroredForwardRange() {
        EditProject.Clip c = clip();
        ReverseOps.reverse(c, "file:///reversed.mp4", 4000);
        c.inMs = 500; c.outMs = 3000; // trimmed 500 ms off the start and 1000 ms off the end of the reversed footage
        ReverseOps.restore(c);
        // Reversed t=500..3000 shows forward 6000-500=5500 down to 6000-3000=3000.
        assertEquals(3000, c.inMs); assertEquals(5500, c.outMs);
    }

    @Test public void unsupportedAndRepeatedRequestsAreRejected() {
        EditProject.Clip picture = clip(); picture.image = true;
        assertNotNull(ReverseOps.unsupported(picture));
        EditProject.Clip audio = clip(); audio.track = EditProject.AUDIO_FIRST;
        assertNotNull(ReverseOps.unsupported(audio));
        EditProject.Clip c = clip(); ReverseOps.reverse(c, "file:///r.mp4", 4000);
        try { ReverseOps.reverse(c, "file:///r2.mp4", 4000); fail(); } catch (IllegalArgumentException expected) { }
        try { ReverseOps.restore(clip()); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void reversedStateSurvivesSaveAndReopen() throws Exception {
        EditProject p = new EditProject(); EditProject.Clip c = clip(); p.clips.add(c);
        ReverseOps.reverse(c, "file:///reversed.mp4", 4000);
        EditProject back = ProjectStore.decode(ProjectStore.encode(p));
        EditProject.Clip r = back.clips.get(0);
        assertTrue(r.reversed); assertEquals("file:///forward.mp4", r.forwardUri); assertEquals(2000, r.reverseBaseMs); assertEquals(4000, r.reverseSpanMs);
        ReverseOps.restore(r); assertEquals(2000, r.inMs); assertEquals(6000, r.outMs);
        assertFalse("Forward projects write nothing extra", ProjectStore.encode(back).contains("reverseBaseMs"));
    }
}

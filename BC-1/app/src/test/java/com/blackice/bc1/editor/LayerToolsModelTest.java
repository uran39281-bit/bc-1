package com.blackice.bc1.editor;

import static org.junit.Assert.*;
import org.junit.Test;

/** The model rules behind Layer Tools → Speed and Copy All. */
public class LayerToolsModelTest {
    private static EditProject.Clip clip() {
        EditProject.Clip c = new EditProject.Clip();
        c.uri = "file:///fixture.mp4"; c.name = "fixture"; c.sourceDurationMs = 8000; c.inMs = 1000; c.outMs = 5000;
        return c;
    }
    private static EditProject project(EditProject.Clip c) { EditProject p = new EditProject(); p.clips.add(c); return p; }

    @Test public void slowerAndFasterRatesScaleTheTimelineDurationOnly() {
        EditProject.Clip c = clip(); EditProject p = project(c);
        assertEquals(4000, c.durationMs());
        TimelineEdits.speed(p, 0, .5f);
        assertEquals("Half rate doubles the duration", 8000, c.durationMs());
        assertEquals("Source trim is untouched", 1000, c.inMs); assertEquals(5000, c.outMs);
        TimelineEdits.speed(p, 0, 2f);
        assertEquals("Double rate halves the duration", 2000, c.durationMs());
        TimelineEdits.speed(p, 0, 1f);
        assertEquals(4000, c.durationMs());
    }

    @Test public void invalidRatesAreRejectedWithoutChangingTheClip() {
        for (float bad : new float[]{0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, .2f, 4.5f}) {
            EditProject.Clip c = clip(); EditProject p = project(c);
            try { TimelineEdits.speed(p, 0, bad); fail("Rate " + bad + " must be rejected"); } catch (IllegalArgumentException expected) { }
            assertEquals(1f, c.speed, 0); assertEquals(4000, c.durationMs());
        }
    }

    @Test public void copyAllCarriesTransformsAndEffectsButNoMediaTimingOrSpeed() throws Exception {
        EditProject.Clip source = clip(); source.speed = 2f; source.x = .25f; source.rotation = 30; source.scale = 1.5f; source.opacity = .6f;
        source.motion.put(Motion.ROTATION, new Motion.Key(1000, 0)); source.motion.put(Motion.ROTATION, new Motion.Key(3000, 90));
        Attributes snapshot = Attributes.of(source, Attributes.TRANSFORM | Attributes.EFFECTS | Attributes.ANIMATION);
        String payload = snapshot.encode();
        assertFalse("No source URI", payload.contains("fixture"));
        assertFalse("No playback speed", payload.contains("\"speed\""));
        assertFalse("No trim or placement", payload.contains("inMs") || payload.contains("startMs"));
        // Later edits to the source never reach the snapshot.
        source.x = -.5f; source.motion.curves.get(Motion.ROTATION).clear();
        EditProject.Clip target = clip(); target.speed = .5f; target.inMs = 2000; target.outMs = 4000;
        int applied = snapshot.applyTo(target, Attributes.TRANSFORM | Attributes.EFFECTS | Attributes.ANIMATION);
        assertEquals(Attributes.TRANSFORM | Attributes.EFFECTS | Attributes.ANIMATION, applied);
        assertEquals(.25f, target.x, 0); assertEquals(30f, target.rotation, 0); assertEquals(1.5f, target.scale, 0);
        assertEquals("Target keeps its own rate", .5f, target.speed, 0);
        assertEquals("Target keeps its own trim", 2000, target.inMs); assertEquals(4000, target.outMs);
        assertEquals("Animation keys are retimed to the target, not pasted as source times", 2, target.motion.curves.get(Motion.ROTATION).size());
        assertTrue(target.motion.curves.get(Motion.ROTATION).get(0).timeMs >= target.inMs);
    }
}

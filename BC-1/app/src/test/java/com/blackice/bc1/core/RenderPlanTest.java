package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.Motion;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RenderPlanTest {
    static EditProject.Clip clip(String name, long duration, int track, long start) {
        EditProject.Clip c = new EditProject.Clip();
        c.uri = "file:///" + name; c.name = name; c.sourceDurationMs = duration; c.outMs = duration; c.track = track; c.startMs = start;
        return c;
    }

    /** Three 2 s main clips: a → b dissolve (500 ms), b → c none. */
    static EditProject mainLane() {
        EditProject p = new EditProject();
        EditProject.Clip a = clip("a", 2000, 0, 0), b = clip("b", 2000, 0, 0), c = clip("c", 2000, 0, 0);
        a.transition = 0; a.transitionMs = 500; a.easing = 0;
        p.clips.add(a); p.clips.add(b); p.clips.add(c);
        return p;
    }

    @Test public void outputSpecMatchesMedia3FrameSize() {
        EditProject p = mainLane();
        OutputSpec landscape = OutputSpec.export(p);
        assertEquals(1920, landscape.width); assertEquals(1080, landscape.height);
        p.portrait = true;
        OutputSpec portrait = OutputSpec.export(p);
        assertEquals(1080, portrait.width); assertEquals(1920, portrait.height);
        assertEquals(640, OutputSpec.of(p, 360).height);
        assertEquals(5500, landscape.durationMs);
    }

    @Test public void frameClockHasNoDrift() {
        assertEquals(0, FrameClock.frameTimeUs(0, 30));
        assertEquals(33_333, FrameClock.frameTimeUs(1, 30));
        assertEquals(1_000_000, FrameClock.frameTimeUs(30, 30));
        assertEquals(3_600_000_000L, FrameClock.frameTimeUs(30L * 3600, 30));
        assertEquals(165, FrameClock.frameCount(5500, 30));
        assertEquals(1, FrameClock.frameCount(1, 24));
        assertEquals(0, FrameClock.frameCount(0, 25));
        assertEquals(29, FrameClock.frameAt(999, 30));
        try { FrameClock.frameCount(1000, 0); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void dissolveDrawsBothStreamsWithMaskOnTheUpperOne() {
        EditProject p = mainLane();
        RenderPlan plan = new RenderPlan(p, false);
        // b starts at 1500 (2000 − 500 overlap).
        List<RenderPlan.LayerDraw> before = plan.layersAt(1000);
        assertEquals(1, before.size()); assertEquals("a", before.get(0).clip.name); assertEquals(0, before.get(0).role);

        List<RenderPlan.LayerDraw> mid = plan.layersAt(1750);
        assertEquals(2, mid.size());
        // b is ordinal 1 (odd → lower stream) and draws first; a is ordinal 0 (upper) and fades out.
        assertEquals("b", mid.get(0).clip.name); assertFalse(mid.get(0).top); assertEquals(1, mid.get(0).role);
        assertEquals("a", mid.get(1).clip.name); assertTrue(mid.get(1).top); assertEquals(2, mid.get(1).role);
        assertEquals(.5f, mid.get(1).progress, 1e-4); assertEquals(.5f, mid.get(0).progress, 1e-4);
        assertEquals(0, mid.get(1).transition); assertEquals(0, mid.get(0).transition);

        List<RenderPlan.LayerDraw> after = plan.layersAt(2000);
        assertEquals(1, after.size()); assertEquals("b", after.get(0).clip.name);
        assertEquals("No transition into c", 0, plan.layersAt(3600).get(0).role);
        assertEquals("c", plan.layersAt(3600).get(0).clip.name);
        assertTrue("End is exclusive", plan.layersAt(5500).isEmpty());
    }

    @Test public void smoothEasingAndSourceTimeFollowSpeed() {
        EditProject p = mainLane();
        p.clips.get(0).easing = 1;
        p.clips.get(1).inMs = 400; p.clips.get(1).speed = 2f; p.clips.get(1).outMs = 2000; // 800 ms long
        RenderPlan plan = new RenderPlan(p, false);
        EditProject.Placement b = p.placements().get(1);
        assertEquals(400, RenderPlan.sourceTime(b, b.startMs));
        assertEquals(400 + 200, RenderPlan.sourceTime(b, b.startMs + 100));
        RenderPlan.Transition t = RenderPlan.transition(b, p.clips.get(0), b.startMs + b.incomingMs / 4);
        float x = .25f;
        assertEquals(x * x * (3 - 2 * x), t.progress, 1e-4);
    }

    @Test public void layersStackAboveMainAndHiddenLayersAreSkipped() {
        EditProject p = mainLane();
        p.clips.add(clip("L2", 1000, 2, 0)); p.clips.add(clip("L1", 1000, 1, 0)); p.clips.add(clip("L3", 1000, 3, 0));
        p.clips.add(clip("music-ish", 1000, 4, 0));
        RenderPlan plan = new RenderPlan(p, false);
        List<RenderPlan.LayerDraw> at = plan.layersAt(100);
        String[] order = new String[at.size()];
        for (int i = 0; i < order.length; i++) order[i] = at.get(i).clip.name;
        assertArrayEquals(new String[]{"a", "L1", "L2", "L3"}, order);
        p.tracks[2].hidden = true;
        assertEquals(3, new RenderPlan(p, false).layersAt(100).size());
        p.tracks[1].solo = true;
        assertEquals("Export ignores solo", 3, new RenderPlan(p, false).layersAt(100).size());
        assertEquals("Preview isolates solo", 1, new RenderPlan(p, true).layersAt(100).size());
    }

    @Test public void keyframedTransformAndOpacityUseSourceTime() {
        EditProject p = mainLane();
        EditProject.Clip a = p.clips.get(0);
        a.inMs = 1000; a.outMs = 2000; a.sourceDurationMs = 3000; a.transition = -1;
        a.motion.put(Motion.X, new Motion.Key(1000, 0)); a.motion.put(Motion.X, new Motion.Key(2000, 1));
        Motion.Key k = a.motion.curves.get(Motion.X).get(0); k.x1 = 0; k.y1 = 0; k.x2 = 1; k.y2 = 1; // linear
        a.motion.put(Motion.OPACITY, new Motion.Key(1000, .5f));
        RenderPlan.LayerDraw d = new RenderPlan(p, false).layersAt(500).get(0);
        assertEquals(1500, d.sourceTimeMs);
        assertEquals(.5f, d.transform.x, .02f);
        assertEquals(.5f, d.opacity, 1e-6);
    }

    @Test public void overlaysAreTimedAndHonourHidden() {
        EditProject p = mainLane();
        EditProject.Overlay o = new EditProject.Overlay(); o.startMs = 100; o.endMs = 200; p.overlays.add(o);
        RenderPlan plan = new RenderPlan(p, false);
        assertTrue(plan.overlaysAt(99).isEmpty()); assertEquals(1, plan.overlaysAt(100).size()); assertTrue(plan.overlaysAt(200).isEmpty());
        o.hidden = true;
        assertTrue(new RenderPlan(p, false).overlaysAt(150).isEmpty());
    }

    @Test public void audioOnlyProjectIsRejected() {
        EditProject p = new EditProject();
        EditProject.Clip c = clip("sound", 1000, 4, 0); c.audioOnly = true; p.clips.add(c);
        try { new RenderPlan(p, false); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void engineChoiceParsesSafely() {
        assertEquals(ExportEngine.MEDIA3, ExportEngine.parse(null));
        assertEquals(ExportEngine.MEDIA3, ExportEngine.parse("nonsense"));
        assertEquals(ExportEngine.BC_CORE, ExportEngine.parse("BC_CORE"));
        assertEquals(2, ExportEngine.labels().length);
    }
}

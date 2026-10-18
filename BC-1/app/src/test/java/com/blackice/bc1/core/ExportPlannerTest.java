package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExportPlannerTest {
    private static String labels(List<int[]> list, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(i == 0 ? "" : " ").append(list.get(i)[0]).append('p').append(list.get(i)[1]);
        return b.toString();
    }

    @Test public void frameSizes() {
        assertArrayEquals(new int[]{1920, 1080}, ExportPlanner.size(1080, false));
        assertArrayEquals(new int[]{2560, 1440}, ExportPlanner.size(1440, false));
        assertArrayEquals(new int[]{3840, 2160}, ExportPlanner.size(2160, false));
        assertArrayEquals(new int[]{2160, 3840}, ExportPlanner.size(2160, true));
    }

    @Test public void bitrateScalesWithSizeAndRate() {
        assertEquals("Shipping rate unchanged", 8_000_000, ExportPlanner.videoBitrate(1920, 1080, 30));
        assertEquals(8_000_000, ExportPlanner.videoBitrate(1080, 1920, 30));
        int q60 = ExportPlanner.videoBitrate(2560, 1440, 60), k60 = ExportPlanner.videoBitrate(3840, 2160, 60), k120 = ExportPlanner.videoBitrate(3840, 2160, 120);
        assertTrue("1440p60 " + q60, q60 > 22_000_000 && q60 < 28_000_000);
        assertTrue("2160p60 " + k60, k60 > 50_000_000 && k60 < 62_000_000);
        assertTrue("2160p120 " + k120, k120 > 85_000_000 && k120 <= 110_000_000);
        assertTrue(ExportPlanner.videoBitrate(640, 360, 24) >= 4_000_000);
    }

    @Test public void fallbackOrderKeepsSmoothnessThenSize() {
        List<int[]> c = ExportPlanner.candidates(2160, 120);
        assertEquals("2160p120 2160p60 1440p120 1080p120", labels(c, 4));
        assertEquals("Every size × rate offered once", 3 * 6, c.size());
        assertEquals("1080p30 1080p25 1080p24", labels(ExportPlanner.candidates(1080, 30), 3));
        assertEquals("2160p60 2160p50 2160p30 1440p60 1080p60", labels(ExportPlanner.candidates(2160, 60), 5));
    }

    @Test public void chooseUsesTheFirstEncodableCandidate() {
        // A phone that encodes 4K up to 60 fps and anything smaller up to 120 fps.
        ExportPlanner.Capability phone = (codec, w, h, fps) -> (long) w * h <= 3840L * 2160 && (fps <= 60 || (long) w * h <= 2560L * 1440);
        ExportPlanner.Plan k = ExportPlanner.choose(2160, 120, false, phone);
        assertEquals("2160p60", k.label());
        assertTrue(k.reduced);
        assertEquals("2160p60 H.264 (2160p120 is not supported on this device)", k.describe());
        ExportPlanner.Plan exact = ExportPlanner.choose(1440, 120, true, phone);
        assertFalse(exact.reduced);
        assertEquals(1440, exact.width); assertEquals(2560, exact.height);
        // A 1080p-only encoder.
        ExportPlanner.Plan small = ExportPlanner.choose(2160, 60, false, (codec, w, h, fps) -> h <= 1080 && w <= 1920 && fps <= 60);
        assertEquals("1080p60", small.label());
    }

    /** A device whose H.264 encoder stops at 4K60 while HEVC reaches 4K120. */
    static final ExportPlanner.Capability SNAPDRAGON = (codec, w, h, fps) -> (long) w * h <= 3840L * 2160 && (codec == VideoCodec.HEVC ? fps <= 120 : fps <= 60);

    @Test public void autoUsesHevcOnlyWhenH264CannotDeliver() {
        ExportPlanner.Plan k120 = ExportPlanner.choose(2160, 120, false, VideoCodec.Choice.AUTO, SNAPDRAGON);
        assertEquals("2160p120", k120.label()); assertEquals(VideoCodec.HEVC, k120.codec); assertFalse(k120.reduced);
        assertEquals("2160p120 HEVC (H.265)", k120.describe());
        ExportPlanner.Plan k60 = ExportPlanner.choose(2160, 60, false, VideoCodec.Choice.AUTO, SNAPDRAGON);
        assertEquals("H.264 kept for compatibility", VideoCodec.H264, k60.codec);
        ExportPlanner.Plan forced = ExportPlanner.choose(2160, 120, false, VideoCodec.Choice.H264_ONLY, SNAPDRAGON);
        assertEquals("2160p60", forced.label()); assertTrue(forced.reduced);
        assertEquals(VideoCodec.HEVC, ExportPlanner.choose(1080, 30, false, VideoCodec.Choice.HEVC_ONLY, SNAPDRAGON).codec);
    }

    @Test public void hevcNeedsLessBitrateAndSizeEstimateIsSane() {
        ExportPlanner.Plan k120 = ExportPlanner.fixed(2160, 120, VideoCodec.HEVC, false);
        assertTrue("HEVC 4K120 " + k120.bitrate, k120.bitrate > 50_000_000 && k120.bitrate < 65_000_000);
        long fourMinutes = k120.estimatedBytes(4 * 60_000);
        assertTrue("4 min 4K120 HEVC ≈ 1.7 GB: " + fourMinutes, fourMinutes > 1_500_000_000L && fourMinutes < 2_000_000_000L);
        assertEquals(VideoCodec.Choice.AUTO, VideoCodec.Choice.parse("bogus"));
        assertEquals(VideoCodec.Choice.HEVC_ONLY, VideoCodec.Choice.parse("HEVC_ONLY"));
    }

    @Test public void brokenCapabilityQueryKeepsTheRequest() {
        ExportPlanner.Plan plan = ExportPlanner.choose(1080, 30, false, (codec, w, h, fps) -> false);
        assertEquals("1080p30", plan.label());
        assertFalse(plan.reduced);
        assertEquals("Unknown edges fall back to 1080p", 1080, ExportPlanner.choose(999, 30, false, (codec, w, h, f) -> true).shortEdge);
    }

    @Test public void projectsAcceptHighFrameRates() {
        EditProject p = RenderPlanTest.mainLane();
        for (int fps : ExportPlanner.FRAME_RATES) { p.frameRate = fps; p.validate(); }
        p.frameRate = 120;
        OutputSpec spec = OutputSpec.of(p, 2160);
        assertEquals(3840, spec.width);
        assertEquals("5.5 s at 120 fps", 660, spec.frameCount());
        assertEquals(ExportPlanner.videoBitrate(3840, 2160, 120), spec.videoBitrate());
        assertEquals(8_333, FrameClock.frameTimeUs(1, 120));
        p.frameRate = 48;
        try { p.validate(); fail(); } catch (IllegalArgumentException expected) { }
    }
}

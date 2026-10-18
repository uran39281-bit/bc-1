package com.blackice.bc1.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Chooses the export resolution and frame rate the device can actually encode. Pure Java: the
 * backend supplies the capability test. Frame rate is kept before resolution is lowered, but never
 * below 30 fps while a lower resolution could still deliver the requested rate.
 */
public final class ExportPlanner {
    /** Short edges offered for export, smallest first. */
    public static final int[] SHORT_EDGES = {1080, 1440, 2160};
    public static final String[] RESOLUTION_LABELS = {"1080p (Full HD)", "1440p (QHD)", "2160p (4K)"};
    /** Project frame rates, lowest first. */
    public static final int[] FRAME_RATES = {24, 25, 30, 50, 60, 120};
    public static final String PREF_RESOLUTION = "shortEdge";

    /** Can the device encode H.264 at this exact frame size and rate? */
    public interface Capability { boolean supports(int width, int height, int fps); }

    public static final class Plan {
        public final int shortEdge, frameRate, width, height, bitrate;
        /** True when the device could not encode what was asked for. */
        public final boolean reduced;
        public final int requestedShortEdge, requestedFrameRate;
        Plan(int shortEdge, int frameRate, boolean portrait, int requestedShortEdge, int requestedFrameRate) {
            this.shortEdge = shortEdge; this.frameRate = frameRate;
            int[] size = size(shortEdge, portrait);
            width = size[0]; height = size[1];
            bitrate = videoBitrate(width, height, frameRate);
            this.requestedShortEdge = requestedShortEdge; this.requestedFrameRate = requestedFrameRate;
            reduced = shortEdge != requestedShortEdge || frameRate != requestedFrameRate;
        }
        public String label() { return shortEdge + "p" + frameRate; }
        /** One line for the export status, e.g. "2160p60 (2160p120 is not supported on this device)". */
        public String describe() {
            return reduced ? label() + " (" + requestedShortEdge + "p" + requestedFrameRate + " is not supported on this device)" : label();
        }
    }

    private ExportPlanner() {}

    public static boolean validShortEdge(int edge) { for (int e : SHORT_EDGES) if (e == edge) return true; return false; }
    public static boolean validFrameRate(int fps) { for (int f : FRAME_RATES) if (f == fps) return true; return false; }

    public static int resolutionIndex(int shortEdge) {
        for (int i = 0; i < SHORT_EDGES.length; i++) if (SHORT_EDGES[i] == shortEdge) return i;
        return 0;
    }

    /** 16:9 frame with an even long edge; same rule as the Media3 composition. */
    public static int[] size(int shortEdge, boolean portrait) {
        int longEdge = (shortEdge * 16 / 9 + 1) / 2 * 2;
        return portrait ? new int[]{shortEdge, longEdge} : new int[]{longEdge, shortEdge};
    }

    /**
     * H.264 bitrate: 8 Mb/s at 1080p30 (the shipping rate), scaled by pixel count, with
     * sub-linear growth for higher frame rates because consecutive frames are more alike.
     * 1440p60 ≈ 25 Mb/s, 2160p60 ≈ 56 Mb/s, 2160p120 ≈ 97 Mb/s.
     */
    public static int videoBitrate(int width, int height, int fps) {
        double rate = 8_000_000.0 * (width * (double) height) / (1920.0 * 1080.0) * Math.pow(fps / 30.0, 0.8);
        return (int) Math.max(4_000_000, Math.min(150_000_000, Math.round(rate / 100_000.0) * 100_000));
    }

    /**
     * Candidates in preference order. First the requested size at rates down to half the requested
     * rate (never below 30 fps), then smaller sizes at the full requested rate, then everything else
     * by pixel throughput. 2160p120 → 2160p60 → 1440p120 → 1080p120 → 2160p50 → …
     */
    public static List<int[]> candidates(int shortEdge, int fps) {
        List<int[]> list = new ArrayList<>();
        List<Integer> edges = new ArrayList<>();
        for (int i = SHORT_EDGES.length - 1; i >= 0; i--) if (SHORT_EDGES[i] <= shortEdge) edges.add(SHORT_EDGES[i]);
        if (edges.isEmpty()) edges.add(SHORT_EDGES[0]);
        List<Integer> rates = new ArrayList<>();
        for (int i = FRAME_RATES.length - 1; i >= 0; i--) if (FRAME_RATES[i] <= fps) rates.add(FRAME_RATES[i]);
        if (rates.isEmpty()) rates.add(fps);
        int floor = Math.min(fps, Math.max(30, fps / 2));
        int top = edges.get(0);
        for (int rate : rates) if (rate >= floor) list.add(new int[]{top, rate});
        for (int edge : edges) if (edge != top) list.add(new int[]{edge, fps});
        List<int[]> rest = new ArrayList<>();
        for (int edge : edges) for (int rate : rates) if (!contains(list, edge, rate)) rest.add(new int[]{edge, rate});
        rest.sort((x, y) -> Long.compare(throughput(y), throughput(x)));
        list.addAll(rest);
        return list;
    }

    private static long throughput(int[] c) { int[] s = size(c[0], false); return (long) s[0] * s[1] * c[1]; }

    private static boolean contains(List<int[]> list, int edge, int rate) {
        for (int[] c : list) if (c[0] == edge && c[1] == rate) return true;
        return false;
    }

    /** First candidate the device supports; the request itself when the query supports nothing. */
    public static Plan choose(int shortEdge, int fps, boolean portrait, Capability capability) {
        if (!validShortEdge(shortEdge)) shortEdge = SHORT_EDGES[0];
        for (int[] c : candidates(shortEdge, fps)) {
            int[] size = size(c[0], portrait);
            if (capability.supports(size[0], size[1], c[1])) return new Plan(c[0], c[1], portrait, shortEdge, fps);
        }
        // The capability query reported nothing usable (seen on some emulators): keep the request and
        // let the encoder decide, exactly as exports behaved before planning existed.
        return new Plan(shortEdge, fps, portrait, shortEdge, fps);
    }
}

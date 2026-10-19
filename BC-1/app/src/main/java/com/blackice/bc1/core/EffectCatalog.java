package com.blackice.bc1.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Every clip effect the layer shader (assets/shaders/video.frag) implements, by stored id. Ids 0-11 are
 * the original BC-1 effects; 12+ are the Black Ice effect set, written for BC-1. Append only: ids are
 * saved in projects. Each effect has one control, the clip's animatable intensity (0..1).
 */
public final class EffectCatalog {
    public static final String[] NAMES = {
            // 0-11: original effects
            "None", "Gaussian Blur", "Glow / Bloom", "Chroma Key (Green)", "Grayscale", "Opacity", "Vignette", "Sharpen", "Pixelate", "Exposure", "Saturation", "Ice Grade",
            // 12-16: blur & sharpen
            "Box Blur", "Motion Blur", "Spin Blur", "Zoom Blur", "Unsharp Mask",
            // 17-28: colour & light
            "Contrast", "Brightness", "Gamma", "Levels", "Hue Shift", "Warm", "Cool", "Posterize", "Invert", "Sepia", "Ice Duotone", "Light Leak",
            // 29-37: distortion
            "Swirl", "Bulge", "Pinch", "Ripple", "Wave", "Fisheye", "Mirror", "Kaleidoscope", "Shake",
            // 38-41: edges & keying
            "Threshold", "Edge Detect", "Emboss", "Luma Key",
            // 42-47: stylize
            "Film Grain", "Scanlines", "Glitch", "VHS", "RGB Split", "Halftone",
    };

    public static final String[] CATEGORIES = {"Basics", "Blur & Sharpen", "Color & Light", "Distortion", "Edges & Keying", "Stylize"};

    /** Category index of each effect id. */
    private static final int[] CATEGORY = new int[NAMES.length];
    static {
        int[][] ranges = {{0, 0}, {1, 1}, {2, 2}, {3, 4}, {4, 2}, {5, 0}, {6, 2}, {7, 1}, {8, 5}, {9, 2}, {10, 2}, {11, 2}};
        for (int[] r : ranges) CATEGORY[r[0]] = r[1];
        for (int id = 12; id <= 16; id++) CATEGORY[id] = 1;
        for (int id = 17; id <= 28; id++) CATEGORY[id] = 2;
        for (int id = 29; id <= 37; id++) CATEGORY[id] = 3;
        for (int id = 38; id <= 41; id++) CATEGORY[id] = 4;
        for (int id = 42; id <= 47; id++) CATEGORY[id] = 5;
    }

    /** Effects whose intensity is neutral at 50% (lower reduces, higher increases). */
    private static final int[] CENTERED = {9, 10, 17, 18, 19};

    private EffectCatalog() {}

    public static int count() { return NAMES.length; }
    public static boolean valid(int id) { return id >= 0 && id < NAMES.length; }
    public static String name(int id) { return valid(id) ? NAMES[id] : NAMES[0]; }
    public static int category(int id) { return valid(id) ? CATEGORY[id] : 0; }

    public static boolean centered(int id) {
        for (int c : CENTERED) if (c == id) return true;
        return false;
    }

    /** Effect ids in a category, in id order. */
    public static List<Integer> inCategory(int category) {
        List<Integer> ids = new ArrayList<>();
        for (int id = 0; id < NAMES.length; id++) if (CATEGORY[id] == category) ids.add(id);
        return ids;
    }

    /** Effects that move pixels (the shader applies these before sampling). */
    public static boolean distortion(int id) { return id >= 29 && id <= 37; }
}

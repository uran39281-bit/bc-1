package com.blackice.bc1.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Every clip effect the layer shader (assets/shaders/video.frag) implements, by stored id. Ids 0-11 are
 * the original BC-1 effects; 12+ are the Black Ice effect set, written for BC-1. Append only: ids are
 * saved in projects. Every effect has its animatable strength (0..1); most also have up to four named controls,
 * stored per clip, whose defaults reproduce the effect's original look exactly.
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
            // 48-65: grading, detail and look
            "Highlights", "Shadows", "Fade", "Temperature", "Tint", "Vibrance", "Clarity", "Dehaze", "S-Curve", "Color Balance",
            "Selective Color", "Night Boost", "Auto Enhance", "B&W Mix", "Particles", "Tinted Vignette", "Filmic Tone", "Chroma Key (Blue)",
    };

    public static final String[] CATEGORIES = {"Basics", "Blur & Sharpen", "Color & Light", "Distortion", "Edges & Keying", "Stylize", "Grade & Detail"};

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
        for (int id = 48; id <= 61; id++) CATEGORY[id] = 6;
        CATEGORY[62] = 5; CATEGORY[63] = 5; CATEGORY[64] = 6; CATEGORY[65] = 4;
    }

    /** Effects whose intensity is neutral at 50% (lower reduces, higher increases). */
    private static final int[] CENTERED = {9, 10, 17, 18, 19, 48, 49, 51, 52};

    /** Maximum named controls per effect (the shader reads them as one vec4). */
    public static final int MAX_PARAMS = 4;

    /** A named control: its label, default (0..1) and whether the label is shown as an angle (value x 360°). */
    public static final class Param {
        public final String name; public final float def; public final boolean angle;
        Param(String name, float def, boolean angle) { this.name = name; this.def = def; this.angle = angle; }
    }

    private static final Param[][] PARAMS = new Param[NAMES.length][];
    private static Param p(String name, float def) { return new Param(name, def, false); }
    private static Param angle(String name, float def) { return new Param(name, def, true); }
    static {
        for (int id = 0; id < PARAMS.length; id++) PARAMS[id] = new Param[0];
        PARAMS[1] = new Param[]{p("Radius", .5f)};
        PARAMS[2] = new Param[]{p("Radius", .5f), p("Threshold", .32f)};
        PARAMS[3] = new Param[]{p("Softness", .5f)};
        PARAMS[4] = new Param[]{p("Contrast", 0f)};
        PARAMS[6] = new Param[]{p("Size", .5f), p("Softness", .5f)};
        PARAMS[7] = new Param[]{p("Radius", .5f)};
        PARAMS[8] = new Param[]{p("Cell size", .5f)};
        PARAMS[11] = new Param[]{p("Tint", .5f)};
        PARAMS[12] = new Param[]{p("Radius", .5f)};
        PARAMS[13] = new Param[]{angle("Angle", 0f), p("Length", .5f)};
        PARAMS[14] = new Param[]{p("Center X", .5f), p("Center Y", .5f)};
        PARAMS[15] = new Param[]{p("Center X", .5f), p("Center Y", .5f)};
        PARAMS[16] = new Param[]{p("Radius", .5f)};
        PARAMS[20] = new Param[]{p("White point", .5f)};
        PARAMS[28] = new Param[]{p("Position X", 0f), p("Position Y", 1f), p("Size", .5f)};
        PARAMS[29] = new Param[]{p("Radius", .5f), p("Center X", .5f), p("Center Y", .5f)};
        PARAMS[30] = new Param[]{p("Radius", .5f), p("Center X", .5f), p("Center Y", .5f)};
        PARAMS[31] = new Param[]{p("Radius", .5f), p("Center X", .5f), p("Center Y", .5f)};
        PARAMS[32] = new Param[]{p("Frequency", .5f), p("Speed", .5f)};
        PARAMS[33] = new Param[]{p("Frequency", .5f), p("Speed", .5f)};
        PARAMS[34] = new Param[]{p("Curvature", .5f), p("Center X", .5f), p("Center Y", .5f)};
        PARAMS[35] = new Param[]{p("Mirror line", .5f)};
        PARAMS[36] = new Param[]{angle("Rotation", 0f), p("Center X", .5f), p("Center Y", .5f)};
        PARAMS[37] = new Param[]{p("Speed", .5f)};
        PARAMS[39] = new Param[]{p("Thickness", .5f)};
        PARAMS[40] = new Param[]{p("Depth", .5f), angle("Light angle", .125f)};
        PARAMS[41] = new Param[]{p("Softness", .5f)};
        PARAMS[42] = new Param[]{p("Grain size", .5f), p("Speed", .5f)};
        PARAMS[43] = new Param[]{p("Line density", .5f)};
        PARAMS[44] = new Param[]{p("Speed", .5f), p("Blocks", .5f)};
        PARAMS[45] = new Param[]{p("Wobble", .5f), p("Noise", .5f)};
        PARAMS[46] = new Param[]{angle("Angle", 0f)};
        PARAMS[47] = new Param[]{angle("Dot angle", .125f), p("Dot size", .5f)};
        PARAMS[48] = new Param[]{p("Range", .5f)};
        PARAMS[49] = new Param[]{p("Range", .5f)};
        PARAMS[50] = new Param[]{p("Black lift", .5f), p("Desaturate", .5f)};
        PARAMS[54] = new Param[]{p("Radius", .5f)};
        PARAMS[55] = new Param[]{p("Recovery", .5f)};
        PARAMS[56] = new Param[]{p("Midpoint", .5f)};
        PARAMS[57] = new Param[]{angle("Shadow hue", .6f), angle("Highlight hue", .08f), p("Balance", .5f)};
        PARAMS[58] = new Param[]{angle("Target hue", 0f), p("Hue shift", .65f), p("Saturation", .6f), p("Brightness", .5f)};
        PARAMS[59] = new Param[]{p("Blue tint", .5f)};
        PARAMS[60] = new Param[]{p("Pop", .5f)};
        PARAMS[61] = new Param[]{p("Reds", .5f), p("Greens", .5f), p("Blues", .5f)};
        PARAMS[62] = new Param[]{p("Size", .5f), p("Speed", .5f), p("Density", .5f)};
        PARAMS[63] = new Param[]{angle("Tint hue", .62f), p("Size", .5f), p("Softness", .5f)};
        PARAMS[64] = new Param[]{p("Exposure", .5f)};
        PARAMS[65] = new Param[]{p("Softness", .5f)};
    }

    private EffectCatalog() {}

    /** The named controls of an effect (none for effects that are fully described by their strength). */
    public static Param[] params(int id) { return valid(id) ? PARAMS[id] : PARAMS[0]; }
    public static int paramCount(int id) { return params(id).length; }
    /** The four values the shader reads: stored values where this clip has them for this effect, else the defaults. */
    public static float[] resolve(int id, float[] stored) {
        float[] out = new float[MAX_PARAMS];
        Param[] defs = params(id);
        for (int i = 0; i < MAX_PARAMS; i++) {
            float v = i < defs.length ? defs[i].def : 0f;
            if (stored != null && i < stored.length && i < defs.length && Float.isFinite(stored[i])) v = Math.max(0f, Math.min(1f, stored[i]));
            out[i] = v;
        }
        return out;
    }

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

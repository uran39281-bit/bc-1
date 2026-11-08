package com.blackice.bc1.core;

import java.util.*;

/**
 * The Effect Catalog as people browse it: 34 entries in five categories. Each entry's modes point at existing
 * {@link EffectCatalog} ids, so projects keep storing the same ids and nothing is migrated. Legacy modes are
 * ids that older projects still use but new clips no longer get (for example Exposure, now part of Light);
 * they show only on a clip that already has them, next to the mode that replaces them.
 */
public final class EffectGroups {
    public static final String[] CATEGORIES = {"Color & Light", "Blur & Stylize", "Distortion, Glitch & Motion", "Patterns & Texture", "Mask & Reveal"};

    /** One way an entry can work, backed by a catalog effect id. */
    public static final class Mode {
        public final String label; public final int id; public final boolean legacy;
        Mode(String label, int id, boolean legacy) { this.label = label; this.id = id; this.legacy = legacy; }
    }

    /** A named starting point for one mode's controls (Warm, Cool, Green, Blue). */
    public static final class Preset {
        public final String label; public final int id; private final float[] params;
        Preset(String label, int id, float... params) { this.label = label; this.id = id; this.params = params; }
        public float[] params() { return params.clone(); }
    }

    public static final class Entry {
        public final String name; public final int category;
        private final Mode[] modes; private final Preset[] presets; private final String[] aliases;
        Entry(String name, int category, Mode[] modes, Preset[] presets, String[] aliases) {
            this.name = name; this.category = category; this.modes = modes; this.presets = presets; this.aliases = aliases;
        }
        /** The id a new clip gets: the first non-legacy mode. */
        public int defaultId() { for (Mode m : modes) if (!m.legacy) return m.id; return modes[0].id; }
        public boolean has(int id) { return mode(id) != null; }
        public Mode mode(int id) { for (Mode m : modes) if (m.id == id) return m; return null; }
        public List<Mode> allModes() { return Collections.unmodifiableList(Arrays.asList(modes)); }
        /** Modes offered for a clip whose effect is {@code currentId}: every current mode, plus its own if legacy. */
        public List<Mode> choices(int currentId) {
            List<Mode> out = new ArrayList<>();
            for (Mode m : modes) if (!m.legacy || m.id == currentId) out.add(m);
            return out;
        }
        public List<Preset> presets(int currentId) {
            List<Preset> out = new ArrayList<>();
            for (Preset p : presets) if (p.id == currentId) out.add(p);
            return out;
        }
        /** Matches the entry name, any mode or old effect name, and extra search words. */
        public boolean matches(String query) {
            String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
            if (q.isEmpty() || name.toLowerCase(Locale.ROOT).contains(q)) return true;
            for (Mode m : modes) if (m.label.toLowerCase(Locale.ROOT).contains(q) || EffectCatalog.name(m.id).toLowerCase(Locale.ROOT).contains(q)) return true;
            for (String a : aliases) if (a.contains(q)) return true;
            return false;
        }
    }

    private static Mode m(String label, int id) { return new Mode(label, id, false); }
    private static Mode legacy(String label, int id) { return new Mode(label, id, true); }
    private static Mode[] modes(Mode... m) { return m; }
    private static Preset[] presets(Preset... p) { return p; }
    private static String[] words(String... w) { return w; }
    private static final Preset[] NONE = {};
    private static final String[] NO_WORDS = {};

    private static final Entry[] ENTRIES = {
        // Color & Light · 12
        new Entry("Light", 0, modes(m("Adjust", 66), legacy("Exposure", 9), legacy("Brightness", 18), legacy("Highlights", 48), legacy("Shadows", 49)), NONE, NO_WORDS),
        new Entry("Tone Curve", 0, modes(m("Contrast", 17), m("Gamma", 19), m("Levels", 20), m("S-Curve", 56)), NONE, NO_WORDS),
        new Entry("White Balance", 0, modes(m("Adjust", 67), legacy("Temperature", 51), legacy("Tint", 52), legacy("Warm", 22), legacy("Cool", 23)),
                presets(new Preset("Warm", 67, .7f, .5f), new Preset("Cool", 67, .3f, .5f), new Preset("Neutral", 67, .5f, .5f)), NO_WORDS),
        new Entry("Color Intensity", 0, modes(m("Adjust", 68), legacy("Saturation", 10), legacy("Vibrance", 53)), NONE, NO_WORDS),
        new Entry("Hue & Selective Color", 0, modes(m("All Colors", 21), m("Selected Color", 58)), NONE, words("hue")),
        new Entry("Color Balance", 0, modes(m("Color Balance", 57)), NONE, words("split tone")),
        new Entry("Black & White", 0, modes(m("Standard", 4), m("Channel Mixer", 61)), NONE, words("black and white", "monochrome", "b&w")),
        new Entry("Color Looks", 0, modes(m("Ice Grade", 11), m("Ice Duotone", 27), m("Sepia", 26), m("Fade", 50), m("Filmic Tone", 64)), NONE, words("look", "grade", "lut")),
        new Entry("Enhance", 0, modes(m("Auto Enhance", 60), m("Night Boost", 59), m("Dehaze", 55)), NONE, words("auto")),
        new Entry("Glow / Bloom", 0, modes(m("Glow / Bloom", 2)), NONE, NO_WORDS),
        new Entry("Vignette", 0, modes(m("Plain", 6), m("Tinted", 63)), NONE, NO_WORDS),
        new Entry("Light Leak", 0, modes(m("Light Leak", 28)), NONE, words("flare")),
        // Blur & Stylize · 6
        new Entry("Blur", 1, modes(m("Gaussian", 1), m("Box", 12), m("Motion", 13), m("Spin", 14), m("Zoom", 15)), NONE, NO_WORDS),
        new Entry("Detail", 1, modes(m("Sharpen", 7), m("Clarity", 54), legacy("Unsharp Mask", 16)), NONE, words("sharp")),
        new Entry("Edge Stylize", 1, modes(m("Edge Detect", 39), m("Emboss", 40)), NONE, words("outline")),
        new Entry("Posterize", 1, modes(m("Posterize", 24)), NONE, NO_WORDS),
        new Entry("Invert", 1, modes(m("Invert", 25)), NONE, words("negative")),
        new Entry("Threshold", 1, modes(m("Threshold", 38)), NONE, NO_WORDS),
        // Distortion, Glitch & Motion · 8
        new Entry("Lens Warp", 2, modes(m("Bulge", 30), m("Pinch", 31), m("Fisheye", 34)), NONE, NO_WORDS),
        new Entry("Swirl", 2, modes(m("Swirl", 29)), NONE, words("twirl")),
        new Entry("Wave Distortion", 2, modes(m("Ripple", 32), m("Wave", 33)), NONE, NO_WORDS),
        new Entry("Mirror & Kaleidoscope", 2, modes(m("Mirror", 35), m("Kaleidoscope", 36)), NONE, NO_WORDS),
        new Entry("Glitch", 2, modes(m("Glitch", 44)), NONE, NO_WORDS),
        new Entry("VHS", 2, modes(m("VHS", 45)), NONE, words("tape", "retro")),
        new Entry("RGB Split", 2, modes(m("RGB Split", 46)), NONE, words("chromatic aberration")),
        new Entry("Shake", 2, modes(m("Shake", 37)), NONE, words("camera shake", "jitter")),
        // Patterns & Texture · 5
        new Entry("Pixelate", 3, modes(m("Pixelate", 8)), NONE, words("mosaic")),
        new Entry("Film Grain", 3, modes(m("Film Grain", 42)), NONE, words("noise")),
        new Entry("Scanlines", 3, modes(m("Scanlines", 43)), NONE, NO_WORDS),
        new Entry("Halftone", 3, modes(m("Halftone", 47)), NONE, NO_WORDS),
        new Entry("Particles", 3, modes(m("Particles", 62)), NONE, words("dust", "specks")),
        // Mask & Reveal · 3
        new Entry("Chroma Key", 4, modes(m("Any Color", 69), legacy("Green", 3), legacy("Blue", 65)),
                presets(new Preset("Green", 69, 1f / 3f, .5f, .5f, .5f), new Preset("Blue", 69, 2f / 3f, .5f, .5f, .5f)), words("green screen", "blue screen", "keying")),
        new Entry("Luma Key", 4, modes(m("Luma Key", 41)), NONE, NO_WORDS),
        new Entry("Opacity", 4, modes(m("Opacity", 5)), NONE, words("transparency")),
    };

    private EffectGroups() {}

    public static List<Entry> all() { return Collections.unmodifiableList(Arrays.asList(ENTRIES)); }

    /** Entries in a category (all categories when negative) whose names, modes or old names match the query. */
    public static List<Entry> entries(int category, String query) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) if ((category < 0 || e.category == category) && e.matches(query)) out.add(e);
        return out;
    }

    /** The entry an effect id belongs to, or null for None and unknown ids. */
    public static Entry entry(int id) { for (Entry e : ENTRIES) if (e.has(id)) return e; return null; }

    /** "Blur · Motion" for a mode of a multi-mode entry; the entry name when the effect is its only current mode. */
    public static String title(int id) {
        Entry e = entry(id); if (e == null) return EffectCatalog.name(id);
        Mode mode = e.mode(id);
        return e.choices(id).size() > 1 && !mode.label.equals(e.name) ? e.name + " · " + mode.label : e.name;
    }
}

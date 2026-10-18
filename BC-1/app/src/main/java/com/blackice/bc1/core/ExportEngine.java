package com.blackice.bc1.core;

/** Which backend renders an export. Stored by name, so new engines can be appended safely. */
public enum ExportEngine {
    /** Google Media3 Transformer with BC-1 GLES effects (the shipping pipeline). */
    MEDIA3("Media3 (stable)"),
    /** ICE Lite: BC-1's own frame scheduler, compositor, mixer and muxer. The constant keeps its original
     * name because it is stored in preferences; only the label is user-visible. */
    BC_CORE("ICE Lite (experimental)");

    public static final String PREF_KEY = "engine";
    public final String label;

    ExportEngine(String label) { this.label = label; }

    public static ExportEngine parse(String name) {
        if (name != null) for (ExportEngine e : values()) if (e.name().equals(name)) return e;
        return MEDIA3;
    }

    /** The stored choice, or the build's default: ICE Lite test builds (package *.icelite) default to ICE Lite. */
    public static ExportEngine resolve(String stored, String packageName) {
        if (stored == null) return packageName != null && packageName.endsWith(".icelite") ? BC_CORE : MEDIA3;
        return parse(stored);
    }

    public static String[] labels() {
        ExportEngine[] all = values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) labels[i] = all[i].label;
        return labels;
    }
}

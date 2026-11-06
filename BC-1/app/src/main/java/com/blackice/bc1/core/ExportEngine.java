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

    /** Most video layers the Media3 path decodes at once without risking decoder exhaustion (it cannot stage). */
    public static final int MEDIA3_MAX_DECODERS = 4;

    /** The engine an export actually uses, and why it differs from the setting (null when it does not). */
    public static final class Route {
        public final ExportEngine engine; public final String reason;
        Route(ExportEngine engine, String reason) { this.engine = engine; this.reason = reason; }
    }

    /**
     * Picks the engine for one export. Projects with more simultaneous video layers than Media3 can open
     * go to ICE Lite (which renders in passes when needed); projects with HDR clips go to Media3 (which
     * tone-maps HDR to SDR) unless they are too heavy for it.
     */
    public static Route route(ExportEngine requested, int videoDecoders, boolean hdr) { return route(requested, videoDecoders, hdr, false); }

    /** Same, where {@code blends} means a clip uses a blend mode only ICE Lite renders. */
    public static Route route(ExportEngine requested, int videoDecoders, boolean hdr, boolean blends) {
        if (requested == MEDIA3 && videoDecoders > MEDIA3_MAX_DECODERS)
            return new Route(BC_CORE, "ICE Lite used: " + videoDecoders + " video layers at once");
        if (requested == MEDIA3 && blends)
            return new Route(BC_CORE, "ICE Lite used: blend modes");
        if (requested == BC_CORE && blends && hdr)
            return new Route(BC_CORE, "HDR clips exported without tone mapping (blend modes need ICE Lite)");
        if (requested == BC_CORE && hdr && videoDecoders <= MEDIA3_MAX_DECODERS)
            return new Route(MEDIA3, "Media3 used: HDR clips need tone mapping");
        if (requested == BC_CORE && hdr)
            return new Route(BC_CORE, "HDR clips exported without tone mapping (too many layers for Media3)");
        return new Route(requested, null);
    }

    public static String[] labels() {
        ExportEngine[] all = values();
        String[] labels = new String[all.length];
        for (int i = 0; i < all.length; i++) labels[i] = all[i].label;
        return labels;
    }
}

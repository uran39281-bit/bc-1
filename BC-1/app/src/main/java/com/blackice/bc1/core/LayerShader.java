package com.blackice.bc1.core;

/**
 * Specialises the shared layer shader (assets/shaders/video.frag) for one clip effect. Each effect's code
 * is guarded by {@code BC_EFFECT}; defining it compiles only that effect, which keeps compile time near the
 * size of the base shader instead of all {@link EffectCatalog} effects (some GPU compilers, notably the
 * software GL in emulators, take many seconds for the full set).
 */
public final class LayerShader {
    private LayerShader() {}

    /** The fragment source with {@code #define BC_EFFECT effect} after its {@code #version} line. */
    public static String forEffect(String fragment, int effect) {
        int id = EffectCatalog.valid(effect) ? effect : 0;
        if (!fragment.startsWith("#version")) throw new IllegalArgumentException("Shader must start with #version");
        int line = fragment.indexOf('\n');
        return fragment.substring(0, line + 1) + "#define BC_EFFECT " + id + "\n" + fragment.substring(line + 1);
    }
}

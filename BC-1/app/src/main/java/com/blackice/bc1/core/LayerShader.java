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

    /** Effects that {@link #effectPass} applies: everything compiled per effect (12 and up); 1-11 stay in the base shader. */
    public static boolean separable(int effect) { return effect >= 12 && EffectCatalog.valid(effect); }

    /**
     * A small program that applies one effect to a frame-sized texture, in the layer's own coordinates: the
     * shared helpers and that effect's code, with a main that samples, distorts and filters exactly as the
     * layer shader does but leaves transform, keying, transitions and opacity to the base layer program.
     * Changing a clip's effect then compiles only this, not another copy of the whole layer shader.
     */
    public static String effectPass(String fragment, int effect) {
        if (effect<=0||!EffectCatalog.valid(effect)) throw new IllegalArgumentException("Unsupported effect "+effect);
        String source=forEffect(fragment,effect);
        int main=source.indexOf("void main()");
        int begin=source.indexOf("    if(uEffect>=29",main),end=source.indexOf("    if(uChroma.x",begin);
        if(main<0||begin<0||end<0)throw new IllegalArgumentException("Shader effect stage not found");
        // Reuse the shipping effect stage byte for byte, including the original 1–11 filters.
        return source.substring(0,main)+"void main() {\nfloat k=uIntensity; vec2 uv=vUV; float inside=1.0;\n"+
            source.substring(begin,end)+"fragColor=vec4(clamp(a.rgb,0.0,1.0),a.a*inside);\n}\n";
    }
}

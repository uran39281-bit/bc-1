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
        if (!separable(effect)) throw new IllegalArgumentException("Effect " + effect + " is drawn by the base layer shader");
        String source = forEffect(fragment, effect);
        int main = source.indexOf("void main()");
        if (main < 0) throw new IllegalArgumentException("Shader has no main");
        return source.substring(0, main) +
                "void main() {\n" +
                "    float k=uIntensity; vec2 uv=vUV; float inside=1.0;\n" +
                "    if(uEffect>=29 && uEffect<=37 && k>0.0) {\n" +
                "        uv=bcDistort(uEffect,uv,k);\n" +
                "        if(uEffect==32||uEffect==33||uEffect==36||uEffect==37) uv=clamp(uv,uTexel*.5,1.0-uTexel*.5);\n" +
                "        else inside*=step(0.0,uv.x)*step(uv.x,1.0)*step(0.0,uv.y)*step(uv.y,1.0);\n" +
                "    }\n" +
                "    vec4 a=texture(uInput,uv);\n" +
                "    if(uEffect>=12 && !(uEffect>=29 && uEffect<=37)) a=bcEffect(uEffect,uv,a,k);\n" +
                "    fragColor=vec4(clamp(a.rgb,0.0,1.0),a.a*inside);\n" +
                "}\n";
    }
}

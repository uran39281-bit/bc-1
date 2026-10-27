package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import com.blackice.bc1.core.RenderPlan;
import com.blackice.bc1.editor.EditProject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * ICE Lite GLES compositor. Each source is first fitted (rotation, letterbox) into a frame-sized texture,
 * then drawn with the shipping layer shader (assets/shaders/video.frag), so effects, chroma key,
 * transforms and transition masks are pixel-for-pixel the same code as the Media3 path.
 */
final class Compositor {
    private static final String FIT_VERTEX =
            "attribute vec4 aPosition;\n" +
            "uniform mat4 uSt;\n" +
            "uniform int uRotation;\n" +
            "uniform float uFlipY;\n" +
            "varying vec2 vTex;\n" +
            "void main() {\n" +
            "  gl_Position = aPosition;\n" +
            "  vec2 d = aPosition.xy * 0.5 + 0.5;\n" +
            "  vec2 c = d;\n" +
            "  if (uRotation == 90) c = vec2(1.0 - d.y, d.x);\n" +
            "  else if (uRotation == 180) c = vec2(1.0 - d.x, 1.0 - d.y);\n" +
            "  else if (uRotation == 270) c = vec2(d.y, 1.0 - d.x);\n" +
            "  if (uFlipY > 0.5) c.y = 1.0 - c.y;\n" +
            "  vTex = (uSt * vec4(c, 0.0, 1.0)).xy;\n" +
            "}\n";
    private static final String FIT_EXTERNAL =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTex;\n" +
            "uniform samplerExternalOES uTex;\n" +
            "void main() { gl_FragColor = texture2D(uTex, vTex); }\n";
    private static final String FIT_2D =
            "precision mediump float;\n" +
            "varying vec2 vTex;\n" +
            "uniform sampler2D uTex;\n" +
            "void main() { gl_FragColor = texture2D(uTex, vTex); }\n";
    /** Overlay bitmaps are premultiplied and stored top row first. */
    private static final String OVERLAY_VERTEX =
            "attribute vec4 aPosition;\n" +
            "varying vec2 vTex;\n" +
            "void main() { gl_Position = aPosition; vTex = vec2(aPosition.x * 0.5 + 0.5, 0.5 - aPosition.y * 0.5); }\n";
    private static final float[] IDENTITY = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};

    final int width, height;
    private final GlKit.Quad quad;
    private final GlKit.Program fitExternal, fit2d, overlay;
    /**
     * Layer programs, compiled on first use: the shipping shader is large, and a software GL (emulators)
     * takes seconds per compile, so a frame only pays for the variants it draws with.
     * External: reads the decoder's texture directly (null if the GPU lacks ESSL 3 external sampling).
     * Blend: reads the composite so far as a backdrop and writes the blended result.
     */
    private final String vertex, fragment;
    /** Per effect id (each program compiles only that effect's code; see LayerShader). */
    private final java.util.Map<Integer, GlKit.Program> layer = new java.util.HashMap<>(), layerBlend = new java.util.HashMap<>(),
            layerExternal = new java.util.HashMap<>(), layerExternalBlend = new java.util.HashMap<>();
    /** Null when the GPU cannot sample external textures in ESSL 3; layers are then copied first. */
    private Boolean externalSupported;
    /** Ping-pong composite targets, used only in frames that contain a blend-mode layer. */
    private GlKit.Target[] accum;
    private boolean accumulating;
    private int current;
    /**
     * Live preview: effects 12+ are applied by a small per-effect pass into {@link #effected}, and the layer is
     * then drawn with the base program. Switching a clip's effect compiles only that pass (milliseconds even
     * on software GL) instead of a new copy of the whole layer shader. Export keeps the single-pass programs.
     */
    private final boolean effectPasses;
    private final java.util.Map<Integer, GlKit.Program> effect = new java.util.HashMap<>();
    private GlKit.Target effected;
    private GlKit.Target previewOutput;

    Compositor(Context context, int width, int height) throws IOException { this(context, width, height, false); }

    Compositor(Context context, int width, int height, boolean effectPasses) throws IOException {
        this.width = width; this.height = height; this.effectPasses = effectPasses;
        quad = new GlKit.Quad();
        fitExternal = new GlKit.Program(FIT_VERTEX, FIT_EXTERNAL);
        fit2d = new GlKit.Program(FIT_VERTEX, FIT_2D);
        vertex = asset(context, "shaders/video.vert"); fragment = asset(context, "shaders/video.frag");
        overlay = new GlKit.Program(OVERLAY_VERTEX, FIT_2D);
        GlKit.check("compositor programs");
    }

    private String source(int effect) { return com.blackice.bc1.core.LayerShader.forEffect(fragment, effect); }
    private GlKit.Program layer(int effect) {
        GlKit.Program p = layer.get(effect);
        if (p == null) {
            long start = System.nanoTime();
            layer.put(effect, p = new GlKit.Program(vertex, source(effect)));
            android.util.Log.d("BC1Live", "layer program " + effect + " compiled in " + (System.nanoTime() - start) / 1_000_000 + " ms");
        }
        return p;
    }
    private GlKit.Program layerBlend(int effect) {
        GlKit.Program p = layerBlend.get(effect);
        if (p == null) layerBlend.put(effect, p = new GlKit.Program(vertex, blendVariant(source(effect))));
        return p;
    }
    private GlKit.Program effectPass(int id) {
        GlKit.Program p = effect.get(id);
        if (p == null) {
            long start = System.nanoTime();
            effect.put(id, p = new GlKit.Program(vertex, com.blackice.bc1.core.LayerShader.effectPass(fragment, id)));
            android.util.Log.d("BC1Live", "effect " + id + " pass compiled in " + (System.nanoTime() - start) / 1_000_000 + " ms");
        }
        return p;
    }
    private GlKit.Program layerExternal(int effect) {
        if (Boolean.FALSE.equals(externalSupported)) return null;
        GlKit.Program p = layerExternal.get(effect);
        if (p == null) {
            try { layerExternal.put(effect, p = new GlKit.Program(vertex, externalVariant(source(effect)))); externalSupported = true; }
            catch (RuntimeException e) {
                if (externalSupported != null) throw e; // external sampling works; this effect's shader itself failed
                GLES30.glGetError(); externalSupported = false; // fall back to a fitted copy per layer
            }
        }
        return p;
    }
    private GlKit.Program layerExternalBlend(int effect) {
        GlKit.Program p = layerExternalBlend.get(effect);
        if (p == null && layerExternal(effect) != null) {
            try { layerExternalBlend.put(effect, p = new GlKit.Program(vertex, externalVariant(blendVariant(source(effect))))); }
            catch (RuntimeException unsupported) { GLES30.glGetError(); }
        }
        return p;
    }

    /**
     * The shipping layer shader, rewritten to sample a decoder's external texture: frame UVs are mapped
     * through the letterbox rectangle, the rotation and the SurfaceTexture matrix, and are transparent
     * outside the picture. Every effect, transform and transition line stays the shipping code.
     */
    static String externalVariant(String fragment) {
        String header = "#version 300 es";
        if (!fragment.startsWith(header) || !fragment.contains("uniform sampler2D uInput;"))
            throw new IllegalStateException("Unexpected layer shader layout");
        String body = fragment.substring(header.length())
                .replace("uniform sampler2D uInput;",
                        "uniform samplerExternalOES uInput;\n" +
                        "uniform mat4 uSt;\nuniform int uRotation;\nuniform vec4 uFit;\n" +
                        "vec4 sampleSource(vec2 uv) {\n" +
                        "  vec2 d = (uv - uFit.xy) / uFit.zw;\n" +
                        "  if (d.x < 0.0 || d.y < 0.0 || d.x > 1.0 || d.y > 1.0) return vec4(0.0);\n" +
                        "  vec2 c = d;\n" +
                        "  if (uRotation == 90) c = vec2(1.0 - d.y, d.x);\n" +
                        "  else if (uRotation == 180) c = vec2(1.0 - d.x, 1.0 - d.y);\n" +
                        "  else if (uRotation == 270) c = vec2(d.y, 1.0 - d.x);\n" +
                        "  return texture(uInput, (uSt * vec4(c, 0.0, 1.0)).xy);\n" +
                        "}")
                .replace("texture(uInput,", "sampleSource(");
        // The helper's own texture() call must keep the sampler.
        body = body.replace("return sampleSource( (uSt", "return texture(uInput, (uSt");
        return header + "\n#extension GL_OES_EGL_image_external_essl3 : require" + body;
    }

    /**
     * The shipping layer shader, ending in a blend over a backdrop texture instead of alpha output: the
     * layer's blended colour (core.BlendMode formulas) is mixed into the backdrop by the layer's alpha.
     */
    static String blendVariant(String fragment) {
        String precision = "precision highp float;";
        String output = "fragColor=vec4(clamp(a.rgb,0.0,1.0),a.a*uOpacity);";
        if (!fragment.contains(precision) || !fragment.contains(output)) throw new IllegalStateException("Unexpected layer shader layout");
        return fragment
                .replace(precision, precision + "\nuniform sampler2D uBackdrop;\nuniform int uBlend;\n" + com.blackice.bc1.core.BlendMode.GLSL)
                .replace(output, "vec3 bcBack=texture(uBackdrop,vUV).rgb; vec3 bcSrc=clamp(a.rgb,0.0,1.0);\n" +
                        "    fragColor=vec4(mix(bcBack,clamp(bcBlend(uBlend,bcBack,bcSrc),0.0,1.0),clamp(a.a*uOpacity,0.0,1.0)),1.0);");
    }

    /** True when layers can be drawn straight from decoder textures (no per-layer copy). */
    boolean drawsExternal() { return layerExternal(0) != null; }

    private static String asset(Context context, String name) throws IOException {
        try (InputStream in = context.getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    /** Draws a decoded frame into {@code target}, rotated and letterboxed with transparent bars. */
    void fitExternal(int oesTexture, float[] stMatrix, int rotation, double displayAspect, GlKit.Target target) {
        target.bind(); clear(0, 0, 0, 0);
        int[] v = GlKit.fit(target.width, target.height, displayAspect);
        GLES30.glViewport(v[0], v[1], v[2], v[3]);
        GLES30.glDisable(GLES30.GL_BLEND);
        fitExternal.use();
        fitExternal.matrix("uSt", stMatrix); fitExternal.set("uRotation", rotation); fitExternal.set("uFlipY", 0f);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        fitExternal.set("uTex", 0);
        quad.draw();
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        GlKit.check("fit external");
    }

    /** Draws a bitmap texture (top row first) into {@code target}, letterboxed. */
    void fitBitmap(int texture, double aspect, GlKit.Target target) {
        target.bind(); clear(0, 0, 0, 0);
        int[] v = GlKit.fit(target.width, target.height, aspect);
        GLES30.glViewport(v[0], v[1], v[2], v[3]);
        GLES30.glDisable(GLES30.GL_BLEND);
        fit2d.use();
        fit2d.matrix("uSt", IDENTITY); fit2d.set("uRotation", 0); fit2d.set("uFlipY", 1f);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        fit2d.set("uTex", 0);
        quad.draw();
        GlKit.check("fit bitmap");
    }

    /** Opaque full-frame copy of a frame-sized texture (a previous render pass) as the bottom layer. */
    void drawPlain(int texture) {
        GLES30.glDisable(GLES30.GL_BLEND);
        fit2d.use();
        fit2d.matrix("uSt", IDENTITY); fit2d.set("uRotation", 0); fit2d.set("uFlipY", 0f);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        fit2d.set("uTex", 0);
        quad.draw();
        GlKit.check("plain");
    }

    /** Keep preview raster resolution independent of a resizable TextureView's native buffer. */
    void beginPreviewFrame(boolean accumulate) {
        if(previewOutput==null)previewOutput=new GlKit.Target(width,height);
        beginFrame(accumulate);
    }
    private void bindOutput(){
        if(previewOutput!=null)previewOutput.bind();
        else { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0); GLES30.glViewport(0,0,width,height); }
    }
    void presentPreview(int displayWidth,int displayHeight){
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
        GLES30.glViewport(0,0,displayWidth,displayHeight);
        drawPlain(previewOutput.texture);
    }

    /** Starts an output frame on the encoder surface: opaque black, like an empty Media3 composition. */
    void beginFrame() { beginFrame(false); }

    /**
     * Starts an output frame. With {@code accumulate} (a blend-mode layer is visible) layers are composed in
     * an off-screen target so blended layers can read what lies beneath; {@link #endLayers} copies it out.
     */
    void beginFrame(boolean accumulate) {
        accumulating = accumulate;
        if (accumulate) {
            if (accum == null) accum = new GlKit.Target[]{new GlKit.Target(width, height), new GlKit.Target(width, height)};
            current = 0;
            accum[0].bind();
        } else {
            bindOutput();
        }
        clear(0, 0, 0, 1);
    }

    /** Finishes the layer stack: overlays and the encoder then see the composed frame. */
    void endLayers() {
        if (!accumulating) return;
        bindOutput();
        drawPlain(accum[current].texture);
        accumulating = false;
    }

    /** Selects the plain or blend program; for a blend layer, targets the other composite buffer. */
    private GlKit.Program begin(RenderPlan.LayerDraw d, boolean external) { return begin(d, external, d.clip.effect); }
    private GlKit.Program begin(RenderPlan.LayerDraw d, boolean external, int e) {
        GlKit.Program blend = accumulating && d.blend != 0 ? (external ? layerExternalBlend(e) : layerBlend(e)) : null;
        if (blend == null) { GlKit.Program plain = external ? layerExternal(e) : layer(e); plain.use(); return plain; }
        int backdrop = accum[current].texture;
        accum[1 - current].bind();
        blend.use();
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, backdrop);
        blend.set("uBackdrop", 1);
        blend.set("uBlend", d.blend);
        return blend;
    }

    private void end(GlKit.Program used, boolean blended) {
        if (blended) {
            current = 1 - current;
            GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        }
    }

    /** One layer through the shipping shader, alpha-composited over what is already drawn. */
    void drawLayer(RenderPlan.LayerDraw d, int texture) {
        boolean pass = effectPasses && com.blackice.bc1.core.LayerShader.separable(d.clip.effect);
        int key = pass ? 0 : d.clip.effect;
        if (pass && com.blackice.bc1.core.LayerShader.separable(d.effect)) texture = applyEffect(d, texture);
        GlKit.Program p = begin(d, false, key);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        boolean blended = p == layerBlend.get(key);
        drawWith(p, d, blended, pass ? 0 : d.effect);
        end(p, blended);
        GlKit.check("layer");
    }

    /** Runs the clip's effect over a frame-sized texture with the layer shader's own uniforms; returns the result. */
    private int applyEffect(RenderPlan.LayerDraw d, int texture) {
        if (effected == null) effected = new GlKit.Target(width, height);
        GlKit.Program p = effectPass(d.effect);
        effected.bind(); clear(0, 0, 0, 0);
        GLES30.glDisable(GLES30.GL_BLEND);
        p.use();
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        p.set("uInput", 0);
        p.set("uTexel", 1f / width, 1f / height);
        p.set("uAspect", (float) width / height);
        p.set("uEffect", d.effect);
        p.set("uIntensity", d.intensity);
        p.set("uTime", d.sourceTimeMs / 1000f);
        quad.draw();
        GlKit.check("effect pass");
        // Back to where layers are being composed (begin() binds the other buffer itself for a blend layer).
        if (accumulating) accum[current].bind();
        else bindOutput();
        return effected.texture;
    }

    /** Same, sampling a decoder's external texture directly through the letterbox and rotation. */
    void drawLayerExternal(RenderPlan.LayerDraw d, int oesTexture, float[] stMatrix, int rotation, double displayAspect) {
        GlKit.Program p = begin(d, true);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        int[] v = GlKit.fit(width, height, displayAspect);
        p.matrix("uSt", stMatrix);
        p.set("uRotation", rotation);
        p.set("uFit", v[0] / (float) width, v[1] / (float) height, v[2] / (float) width, v[3] / (float) height);
        boolean blended = p == layerExternalBlend.get(d.clip.effect);
        drawWith(p, d, blended);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        end(p, blended);
        GlKit.check("external layer");
    }

    private void drawWith(GlKit.Program program, RenderPlan.LayerDraw d, boolean blendMode) { drawWith(program, d, blendMode, d.effect); }
    private void drawWith(GlKit.Program program, RenderPlan.LayerDraw d, boolean blendMode, int effectId) {
        EditProject.Clip c = d.clip;
        if (blendMode) GLES30.glDisable(GLES30.GL_BLEND); // the shader writes the finished pixel
        else {
            GLES30.glEnable(GLES30.GL_BLEND);
            GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        }
        program.set("uInput", 0);
        program.set("uTexel", 1f / width, 1f / height);
        program.set("uAspect", (float) width / height);
        program.set("uTransform", d.transform.x, d.transform.y, d.transform.scale, (float) Math.toRadians(d.transform.rotation%360));
        program.set("uShape", d.transform.flipH ? -1 : 1, d.transform.aspect * (d.transform.flipV ? -1 : 1),
                (float) Math.tan(Math.toRadians(d.transform.skewX)), (float) Math.tan(Math.toRadians(d.transform.skewY)));
        program.set("uAnchor", d.transform.anchorX, d.transform.anchorY);
        program.set("uKeyColor", ((c.keyColor >> 16) & 255) / 255f, ((c.keyColor >> 8) & 255) / 255f, (c.keyColor & 255) / 255f);
        program.set("uChroma", c.chroma ? 1 : 0, c.keyTolerance, c.keySoftness, c.keySpill);
        program.set("uEffect", effectId);
        program.set("uIntensity", d.intensity);
        program.set("uOpacity", d.opacity);
        program.set("uTransition", d.transition);
        program.set("uRole", d.role);
        program.set("uTop", d.top ? 1 : 0);
        program.set("uProgress", d.progress);
        program.set("uTime", d.sourceTimeMs / 1000f);
        quad.draw();
    }

    /** Premultiplied overlay texture over the whole frame. */
    void drawOverlay(int texture) {
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        overlay.use();
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        overlay.set("uTex", 0);
        quad.draw();
        GlKit.check("overlay");
    }

    private static void clear(float r, float g, float b, float a) {
        GLES30.glClearColor(r, g, b, a);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
    }

    void release() {
        fitExternal.release(); fit2d.release();
        for (java.util.Map<Integer, GlKit.Program> m : java.util.List.of(layer, layerBlend, layerExternal, layerExternalBlend, effect)) {
            for (GlKit.Program p : m.values()) p.release();
            m.clear();
        }
        if (accum != null) for (GlKit.Target t : accum) t.release();
        if (effected != null) effected.release();
        if (previewOutput != null) previewOutput.release();
        overlay.release(); quad.release();
    }
}

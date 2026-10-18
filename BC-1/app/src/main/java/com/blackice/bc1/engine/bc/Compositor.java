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
 * BC Core GLES compositor. Each source is first fitted (rotation, letterbox) into a frame-sized texture,
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
    private final GlKit.Program fitExternal, fit2d, layer, overlay;

    Compositor(Context context, int width, int height) throws IOException {
        this.width = width; this.height = height;
        quad = new GlKit.Quad();
        fitExternal = new GlKit.Program(FIT_VERTEX, FIT_EXTERNAL);
        fit2d = new GlKit.Program(FIT_VERTEX, FIT_2D);
        layer = new GlKit.Program(asset(context, "shaders/video.vert"), asset(context, "shaders/video.frag"));
        overlay = new GlKit.Program(OVERLAY_VERTEX, FIT_2D);
        GlKit.check("compositor programs");
    }

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

    /** Starts an output frame on the encoder surface: opaque black, like an empty Media3 composition. */
    void beginFrame() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
        GLES30.glViewport(0, 0, width, height);
        clear(0, 0, 0, 1);
    }

    /** One layer through the shipping shader, alpha-composited over what is already drawn. */
    void drawLayer(RenderPlan.LayerDraw d, int texture) {
        EditProject.Clip c = d.clip;
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        layer.use();
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
        layer.set("uInput", 0);
        layer.set("uTexel", 1f / width, 1f / height);
        layer.set("uAspect", (float) width / height);
        layer.set("uTransform", d.transform.x, d.transform.y, d.transform.scale, (float) Math.toRadians(d.transform.rotation));
        layer.set("uShape", d.transform.flipH ? -1 : 1, d.transform.aspect * (d.transform.flipV ? -1 : 1),
                (float) Math.tan(Math.toRadians(d.transform.skewX)), (float) Math.tan(Math.toRadians(d.transform.skewY)));
        layer.set("uAnchor", d.transform.anchorX, d.transform.anchorY);
        layer.set("uKeyColor", ((c.keyColor >> 16) & 255) / 255f, ((c.keyColor >> 8) & 255) / 255f, (c.keyColor & 255) / 255f);
        layer.set("uChroma", c.chroma ? 1 : 0, c.keyTolerance, c.keySoftness, c.keySpill);
        layer.set("uEffect", d.effect);
        layer.set("uIntensity", d.intensity);
        layer.set("uOpacity", d.opacity);
        layer.set("uTransition", d.transition);
        layer.set("uRole", d.role);
        layer.set("uTop", d.top ? 1 : 0);
        layer.set("uProgress", d.progress);
        quad.draw();
        GlKit.check("layer");
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

    void release() { fitExternal.release(); fit2d.release(); layer.release(); overlay.release(); quad.release(); }
}

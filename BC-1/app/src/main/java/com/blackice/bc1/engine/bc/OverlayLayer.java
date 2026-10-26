package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.OverlayPainter;
import java.util.List;

/** Overlay bitmap texture, repainted only when the visible set changes or something is animated. GL thread only. */
final class OverlayLayer {
    private final OverlayPainter painter;
    private final Bitmap bitmap;
    private final int texture;
    private List<EditProject.Overlay> shown;
    OverlayLayer(Context context, int width, int height) {
        painter = new OverlayPainter(context);
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        texture = GlKit.texture2d();
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
    }
    int texture(List<EditProject.Overlay> active, long timeMs) throws Exception {
        boolean animated = false;
        for (EditProject.Overlay o : active) animated |= (o.motion.animated()||o.fill!=null&&o.fill.animated());
        if (animated || !active.equals(shown)) {
            bitmap.eraseColor(Color.TRANSPARENT);
            Canvas canvas = new Canvas(bitmap);
            for (EditProject.Overlay o : active) painter.draw(canvas, bitmap.getWidth(), bitmap.getHeight(), o, Math.max(0, timeMs - o.startMs));
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture);
            GLUtils.texSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, bitmap);
            shown = active;
        }
        return texture;
    }
    void release() { painter.release(); bitmap.recycle(); GLES30.glDeleteTextures(1, new int[]{texture}, 0); }
}

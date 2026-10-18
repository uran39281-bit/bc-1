package com.blackice.bc1.engine.bc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import java.io.IOException;
import java.io.InputStream;

/** A still picture: decoded once (bounded to 4096 px), fitted once, reused for every frame. */
final class ImageSource implements FrameSource {
    private static final int MAX_EDGE = 4096;
    private final GlKit.Target target;

    ImageSource(Context context, String uri, Compositor compositor) throws IOException {
        Uri parsed = Uri.parse(uri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = context.getContentResolver().openInputStream(parsed)) { BitmapFactory.decodeStream(in, null, bounds); }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("Cannot read picture size");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize == 0 ? 1 : options.inSampleSize) > MAX_EDGE)
            options.inSampleSize = Math.max(2, options.inSampleSize * 2);
        Bitmap bitmap;
        try (InputStream in = context.getContentResolver().openInputStream(parsed)) { bitmap = BitmapFactory.decodeStream(in, null, options); }
        if (bitmap == null) throw new IOException("Cannot decode picture");
        int texture = GlKit.texture2d();
        try {
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0);
            target = new GlKit.Target(compositor.width, compositor.height);
            compositor.fitBitmap(texture, bitmap.getWidth() / (double) bitmap.getHeight(), target);
        } finally {
            GLES30.glDeleteTextures(1, new int[]{texture}, 0);
            bitmap.recycle();
        }
    }

    @Override public int textureAt(long sourceTimeMs) { return target.texture; }
    @Override public void release() { target.release(); }
}

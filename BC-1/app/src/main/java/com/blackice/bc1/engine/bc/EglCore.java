package com.blackice.bc1.engine.bc;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.view.Surface;

/** One GLES 3 context bound to an encoder input surface. Owned by the export thread. */
final class EglCore implements AutoCloseable {
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface = EGL14.EGL_NO_SURFACE;
    private EGLConfig config;

    EglCore(Surface window) {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) throw new IllegalStateException("No EGL display");
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw new IllegalStateException("EGL initialisation failed");
        int[] attributes = {
                EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE};
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] < 1)
            throw new IllegalStateException("No recordable GLES 3 EGL config");
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
        check("eglCreateContext");
        config = configs[0];
        surface = EGL14.eglCreateWindowSurface(display, configs[0], window, new int[]{EGL14.EGL_NONE}, 0);
        check("eglCreateWindowSurface");
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) throw new IllegalStateException("eglMakeCurrent failed");
    }

    /**
     * Drops the window surface but keeps the context (and every texture and compiled program in it)
     * current without a surface. False if the driver cannot do that; then the caller closes this core.
     */
    boolean dropWindow() {
        if (!EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context)) return false;
        if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
        surface = EGL14.EGL_NO_SURFACE;
        return true;
    }

    /** Draws into a new window from now on, keeping the context. */
    void setWindow(Surface window) {
        if (surface != EGL14.EGL_NO_SURFACE && !dropWindow()) throw new IllegalStateException("Cannot release the old window surface");
        surface = EGL14.eglCreateWindowSurface(display, config, window, new int[]{EGL14.EGL_NONE}, 0);
        check("eglCreateWindowSurface");
        makeCurrent();
    }

    boolean hasWindow() { return surface != EGL14.EGL_NO_SURFACE; }

    void makeCurrent() {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) throw new IllegalStateException("eglMakeCurrent failed");
    }

    void setPresentationTime(long nanos) { EGLExt.eglPresentationTimeANDROID(display, surface, nanos); check("eglPresentationTimeANDROID"); }

    void swap() { if (!EGL14.eglSwapBuffers(display, surface)) throw new IllegalStateException("eglSwapBuffers failed: 0x" + Integer.toHexString(EGL14.eglGetError())); }

    private void check(String what) {
        int error = EGL14.eglGetError();
        if (error != EGL14.EGL_SUCCESS) throw new IllegalStateException(what + " failed: 0x" + Integer.toHexString(error));
    }

    @Override public void close() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
            // No eglTerminate: the default display is shared with the preview player in this process.
            EGL14.eglReleaseThread();
        }
        display = EGL14.EGL_NO_DISPLAY; context = EGL14.EGL_NO_CONTEXT; surface = EGL14.EGL_NO_SURFACE;
    }
}

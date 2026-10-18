package com.blackice.bc1.engine.bc;

import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/** Minimal GLES 3 helpers for BC Core: programs, a full-screen quad, render targets. */
final class GlKit {
    private GlKit() {}

    static void check(String what) {
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) throw new IllegalStateException(what + ": GL error 0x" + Integer.toHexString(error));
    }

    /** Linked program with uniform-location cache. Attribute "aPosition" is always location 0. */
    static final class Program {
        final int id;
        private final Map<String, Integer> locations = new HashMap<>();

        Program(String vertex, String fragment) {
            int vs = shader(GLES30.GL_VERTEX_SHADER, vertex), fs = shader(GLES30.GL_FRAGMENT_SHADER, fragment);
            id = GLES30.glCreateProgram();
            GLES30.glAttachShader(id, vs); GLES30.glAttachShader(id, fs);
            GLES30.glBindAttribLocation(id, 0, "aPosition");
            GLES30.glLinkProgram(id);
            int[] ok = new int[1];
            GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0);
            GLES30.glDeleteShader(vs); GLES30.glDeleteShader(fs);
            if (ok[0] == 0) { String log = GLES30.glGetProgramInfoLog(id); GLES30.glDeleteProgram(id); throw new IllegalStateException("Program link failed: " + log); }
        }

        int u(String name) {
            Integer cached = locations.get(name);
            if (cached == null) { cached = GLES30.glGetUniformLocation(id, name); locations.put(name, cached); }
            return cached;
        }
        void use() { GLES30.glUseProgram(id); }
        void set(String name, int value) { GLES30.glUniform1i(u(name), value); }
        void set(String name, float value) { GLES30.glUniform1f(u(name), value); }
        void set(String name, float a, float b) { GLES30.glUniform2f(u(name), a, b); }
        void set(String name, float a, float b, float c) { GLES30.glUniform3f(u(name), a, b, c); }
        void set(String name, float a, float b, float c, float d) { GLES30.glUniform4f(u(name), a, b, c, d); }
        void matrix(String name, float[] m) { GLES30.glUniformMatrix4fv(u(name), 1, false, m, 0); }
        void release() { GLES30.glDeleteProgram(id); }

        private static int shader(int type, String source) {
            int s = GLES30.glCreateShader(type);
            GLES30.glShaderSource(s, source); GLES30.glCompileShader(s);
            int[] ok = new int[1];
            GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) { String log = GLES30.glGetShaderInfoLog(s); GLES30.glDeleteShader(s); throw new IllegalStateException("Shader compile failed: " + log); }
            return s;
        }
    }

    /** Triangle strip covering clip space, attribute 0. */
    static final class Quad {
        private final int vao, vbo;
        Quad() {
            float[] corners = {-1, -1, 1, -1, -1, 1, 1, 1};
            FloatBuffer data = ByteBuffer.allocateDirect(corners.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            data.put(corners).position(0);
            int[] ids = new int[1];
            GLES30.glGenVertexArrays(1, ids, 0); vao = ids[0];
            GLES30.glGenBuffers(1, ids, 0); vbo = ids[0];
            GLES30.glBindVertexArray(vao);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo);
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, corners.length * 4, data, GLES30.GL_STATIC_DRAW);
            GLES30.glEnableVertexAttribArray(0);
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0);
            GLES30.glBindVertexArray(0);
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0);
            check("quad");
        }
        void draw() { GLES30.glBindVertexArray(vao); GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4); GLES30.glBindVertexArray(0); }
        void release() { GLES30.glDeleteBuffers(1, new int[]{vbo}, 0); GLES30.glDeleteVertexArrays(1, new int[]{vao}, 0); }
    }

    /** RGBA8 texture with its framebuffer. */
    static final class Target {
        final int texture, framebuffer, width, height;
        Target(int width, int height) {
            this.width = width; this.height = height;
            texture = texture2d();
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
            int[] ids = new int[1];
            GLES30.glGenFramebuffers(1, ids, 0); framebuffer = ids[0];
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texture, 0);
            int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Framebuffer incomplete: 0x" + Integer.toHexString(status));
        }
        void bind() { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, framebuffer); GLES30.glViewport(0, 0, width, height); }
        void release() { GLES30.glDeleteFramebuffers(1, new int[]{framebuffer}, 0); GLES30.glDeleteTextures(1, new int[]{texture}, 0); }
    }

    static int texture2d() {
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0]);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        return ids[0];
    }

    static int textureExternal() {
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0]);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        return ids[0];
    }

    /** Viewport {x, y, w, h} that fits content of the given aspect inside a frame, centred. */
    static int[] fit(int frameW, int frameH, double contentAspect) {
        if (!(contentAspect > 0)) return new int[]{0, 0, frameW, frameH};
        double frameAspect = frameW / (double) frameH;
        int w = frameW, h = frameH;
        if (contentAspect > frameAspect) h = (int) Math.round(frameW / contentAspect); else w = (int) Math.round(frameH * contentAspect);
        return new int[]{(frameW - w) / 2, (frameH - h) / 2, w, h};
    }
}

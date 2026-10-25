package com.blackice.bc1.core;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.ProjectStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Detects edits made while a background recognition job was reading its private snapshot. */
public final class CaptionState {
    private CaptionState() { }
    public static String fingerprint(EditProject p) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ProjectStore.encodeForPreview(p).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException("Could not snapshot caption source", e); }
    }
}

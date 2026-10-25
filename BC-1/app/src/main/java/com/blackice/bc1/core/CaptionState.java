package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.ProjectStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/* loaded from: classes6.dex */
public final class CaptionState {
    private CaptionState() {
    }

    public static String fingerprint(EditProject p) {
        try {
            EditProject snapshot=ProjectStore.snapshot(p);snapshot.playheadMs=0;snapshot.appliedCaptionJobId="";
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ProjectStore.encode(snapshot).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : digest) {
                out.append(String.format(Locale.ROOT, "%02x", Integer.valueOf(b & 255)));
            }
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not snapshot caption source", e);
        }
    }
}

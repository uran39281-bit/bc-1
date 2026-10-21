package com.blackice.bc1.editor;
/** Runtime-only policy: preserve serialized legacy effect values for future restoration. */
public final class ActiveFeatures {
    private ActiveFeatures() {}
    public static void prepareRender(EditProject snapshot){for(EditProject.Clip c:snapshot.clips)c.effect=0;}
}

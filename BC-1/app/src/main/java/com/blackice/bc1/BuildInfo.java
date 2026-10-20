package com.blackice.bc1;

import android.content.Context;

/** App version for reports ("1.0.5-icelite (17)"). */
final class BuildInfo {
    static String version = "unknown";
    private BuildInfo() {}
    static void init(Context c) {
        try {
            android.content.pm.PackageInfo p = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = c.getPackageName() + " " + p.versionName + " (" + (android.os.Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode) + ")";
        } catch (Exception ignored) { }
    }
}

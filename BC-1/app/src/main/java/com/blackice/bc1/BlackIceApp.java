package com.blackice.bc1;

import android.app.Application;

/** Installs crash reporting before anything else runs. */
public final class BlackIceApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        BuildInfo.init(this);
        CrashLog.install(this);
    }
}

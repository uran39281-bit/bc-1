package com.blackice.bc1;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Why the app last closed unexpectedly. Java crashes are written by an uncaught-exception handler; native
 * crashes, freezes (ANR) and the system killing the app for memory come from Android's exit records
 * (Android 11+). Recent editor actions are kept as breadcrumbs so a report shows what led up to it.
 * Reports stay on the device; the person copies one to share it.
 */
public final class CrashLog {
    private static final String TAG = "BC1Crash";
    private static final String PREFS = "bc1-crash";
    private static final int CRUMBS = 40;
    private static final ArrayDeque<String> crumbs = new ArrayDeque<>();
    private static File dir;

    private CrashLog() {}

    /** Called once from {@link BlackIceApp#onCreate}. */
    static void install(Context context) {
        dir = new File(context.getFilesDir(), "crash-reports");
        dir.mkdirs();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try { write("Crash", "Thread: " + thread.getName() + "\n\n" + stack(error)); } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    /** Records a recent action (kept in memory, newest last) for the next report. */
    public static void note(String action) {
        String line = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()) + "  " + action;
        synchronized (crumbs) { crumbs.addLast(line); while (crumbs.size() > CRUMBS) crumbs.removeFirst(); }
    }

    /** A problem the app recovered from: logged with the breadcrumbs, not shown to the person. */
    public static void handled(String where, Throwable error) {
        Log.w(TAG, where, error);
        note("Recovered from " + where + ": " + error);
    }

    /** Runs {@code task}; an unexpected exception is recorded instead of closing the app. */
    public static Runnable guard(String where, Runnable task) {
        return () -> { try { task.run(); } catch (RuntimeException e) { handled(where, e); } };
    }

    /** A single background thread whose tasks can fail without closing the app (failures are recorded). */
    public static java.util.concurrent.ExecutorService worker(String name) {
        return new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
                new java.util.concurrent.LinkedBlockingQueue<>(), r -> new Thread(r, name)) {
            @Override public void execute(Runnable command) { super.execute(guard(name, command)); }
        };
    }

    static String stack(Throwable t) { StringWriter s = new StringWriter(); t.printStackTrace(new PrintWriter(s)); return s.toString(); }

    private static void write(String kind, String body) throws Exception {
        if (dir == null) return;
        StringBuilder b = new StringBuilder();
        b.append("BC-1 ").append(kind).append(" • ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        b.append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(" • Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(") • ").append(BuildInfo.version).append("\n\n");
        b.append(body).append("\n\nRecent actions (newest last):\n");
        synchronized (crumbs) { for (String c : crumbs) b.append(c).append('\n'); }
        File out = new File(dir, "report-" + System.currentTimeMillis() + ".txt");
        Files.write(out.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        prune();
    }

    private static void prune() {
        File[] files = dir.listFiles();
        if (files == null || files.length <= 10) return;
        java.util.Arrays.sort(files, (x, y) -> Long.compare(x.lastModified(), y.lastModified()));
        for (int i = 0; i < files.length - 10; i++) files[i].delete();
    }

    /**
     * Collects exits Android recorded since the last check (native crash, freeze, out of memory) as
     * reports. Java crashes are already written by the handler.
     */
    static void collectSystemExits(Context context) {
        if (Build.VERSION.SDK_INT < 30 || dir == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long seen = prefs.getLong("exitsSeen", 0), newest = seen;
            List<ApplicationExitInfo> exits = context.getSystemService(ActivityManager.class).getHistoricalProcessExitReasons(context.getPackageName(), 0, 8);
            for (ApplicationExitInfo e : exits) {
                if (e.getTimestamp() <= seen) continue;
                newest = Math.max(newest, e.getTimestamp());
                String kind = kind(e.getReason());
                if (kind == null) continue;
                StringBuilder body = new StringBuilder();
                body.append("Android recorded: ").append(kind).append("\nDescription: ").append(e.getDescription())
                        .append("\nWhen: ").append(new Date(e.getTimestamp()))
                        .append(String.format(Locale.US, "\nMemory at exit: %.0f MB (PSS), %.0f MB (RSS)", e.getPss() / 1024.0, e.getRss() / 1024.0))
                        .append("\nApp was ").append(e.getImportance() <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ? "in the foreground" : "in the background");
                if (e.getReason() == ApplicationExitInfo.REASON_ANR) body.append("\n\n").append(trace(e));
                write(kind, body.toString());
            }
            prefs.edit().putLong("exitsSeen", newest).apply();
        } catch (Exception e) { Log.w(TAG, "Exit records unavailable", e); }
    }

    private static String kind(int reason) {
        if (Build.VERSION.SDK_INT < 30) return null;
        switch (reason) {
            case ApplicationExitInfo.REASON_CRASH_NATIVE: return "Native crash (engine / codec / speech library)";
            case ApplicationExitInfo.REASON_ANR: return "App froze (not responding)";
            case ApplicationExitInfo.REASON_LOW_MEMORY: return "Closed by Android: low memory";
            case ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE: return "Closed by Android: excessive resource use";
            case ApplicationExitInfo.REASON_INITIALIZATION_FAILURE: return "Failed to start";
            default: return null; // normal exits, user swipes, Java crashes (already reported)
        }
    }

    private static String trace(ApplicationExitInfo e) {
        if (Build.VERSION.SDK_INT < 30) return "";
        try (java.io.InputStream in = e.getTraceInputStream()) {
            if (in == null) return "(no trace)";
            byte[] buf = new byte[24_000]; int n, total = 0;
            while (total < buf.length && (n = in.read(buf, total, buf.length - total)) > 0) total += n;
            String text = new String(buf, 0, total, StandardCharsets.UTF_8);
            int main = text.indexOf("\"main\"");
            return "Main thread when it froze:\n" + (main >= 0 ? text.substring(main, Math.min(text.length(), main + 4000)) : text.substring(0, Math.min(text.length(), 4000)));
        } catch (Exception ex) { return "(trace unreadable: " + ex.getMessage() + ")"; }
    }

    /** Reports not yet shown, newest first. */
    static File[] unseen(Context context) {
        File[] all = dir == null ? null : dir.listFiles((d, n) -> n.startsWith("report-"));
        if (all == null) return new File[0];
        long shown = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("shown", 0);
        java.util.List<File> out = new java.util.ArrayList<>();
        for (File f : all) if (f.lastModified() > shown) out.add(f);
        out.sort((x, y) -> Long.compare(y.lastModified(), x.lastModified()));
        return out.toArray(new File[0]);
    }

    static File[] all() {
        File[] all = dir == null ? null : dir.listFiles((d, n) -> n.startsWith("report-"));
        if (all == null) return new File[0];
        java.util.Arrays.sort(all, (x, y) -> Long.compare(y.lastModified(), x.lastModified()));
        return all;
    }

    static void markShown(Context context) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("shown", System.currentTimeMillis()).apply(); }

    static String read(File f) { try { return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8); } catch (Exception e) { return e.toString(); } }
}

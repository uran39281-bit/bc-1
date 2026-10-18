package com.blackice.bc1;

import android.util.Log;
import java.util.Map;

/** Logs every thread's stack (tag BC1Threads) so CI logs show what was running when an export stalled. */
public final class ThreadDump {
    private ThreadDump() {}
    public static void log(String why) {
        Log.w("BC1Threads", "Thread dump: " + why);
        for (Map.Entry<Thread, StackTraceElement[]> e : Thread.getAllStackTraces().entrySet()) {
            Thread t = e.getKey();
            StringBuilder b = new StringBuilder(t.getName()).append(" [").append(t.getState()).append("]");
            StackTraceElement[] s = e.getValue();
            for (int i = 0; i < Math.min(s.length, 12); i++) b.append("\n    at ").append(s[i]);
            Log.w("BC1Threads", b.toString());
        }
        // CPU time per native thread (ticks), busiest first: a spinning thread shows up here.
        java.util.List<String> rows = new java.util.ArrayList<>();
        java.io.File[] tasks = new java.io.File("/proc/self/task").listFiles();
        if (tasks != null) for (java.io.File task : tasks) {
            try {
                String stat = new String(java.nio.file.Files.readAllBytes(new java.io.File(task, "stat").toPath())).trim();
                String name = stat.substring(stat.indexOf('(') + 1, stat.lastIndexOf(')'));
                String[] f = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
                long ticks = Long.parseLong(f[11]) + Long.parseLong(f[12]);
                rows.add(String.format(java.util.Locale.US, "%08d %s (tid %s)", ticks, name, task.getName()));
            } catch (Exception ignored) { }
        }
        rows.sort(java.util.Collections.reverseOrder());
        Log.w("BC1Threads", rows.size() + " native threads; busiest:\n" + String.join("\n", rows.subList(0, Math.min(20, rows.size()))));
    }
}

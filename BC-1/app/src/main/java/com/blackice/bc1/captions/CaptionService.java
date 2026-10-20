package com.blackice.bc1.captions;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import com.blackice.bc1.EditorActivity;
import com.blackice.bc1.R;
import com.blackice.bc1.core.CaptionBuilder;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.ProjectStore;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Foreground, cancellable auto-captions job (keeps running with the screen off). Transcribes a project
 * snapshot and writes the caption cues to a result file; the editor adds them to the project as one
 * undoable edit when it next looks ({@link #takeResult}), so the service never writes the project itself.
 */
public final class CaptionService extends Service {
    public static final String ACTION_CANCEL = "com.blackice.bc1.CANCEL_CAPTIONS";
    public static final String PREFS = "bc1-captions";
    private static final int NOTIFICATION = 42;
    private static volatile boolean alive;

    private volatile Transcriber transcriber;
    private Thread worker;
    private PowerManager.WakeLock wakeLock;

    public static boolean isRunning() { return alive; }

    /** Starts captioning {@code snapshot} (a private copy of the project). */
    public static void start(Context context, EditProject snapshot, Transcriber.Options o) throws Exception {
        File file = new File(context.getCacheDir(), "bc1-captions-" + System.currentTimeMillis() + ".json");
        Files.write(file.toPath(), ProjectStore.encode(snapshot).getBytes(StandardCharsets.UTF_8));
        Intent i = new Intent(context, CaptionService.class).putExtra("project", file.getAbsolutePath())
                .putExtra("model", o.model.name()).putExtra("language", o.language).putExtra("translate", o.translate).putExtra("clip", o.clipId);
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("running", true).putString("status", "Preparing captions…").apply();
        context.startForegroundService(i);
    }

    public static void cancel(Context context) { context.startService(new Intent(context, CaptionService.class).setAction(ACTION_CANCEL)); }

    /** Finished captions waiting for the project, or null. Deletes the result once read. */
    public static Taken takeResult(Context context, String projectId) {
        File f = resultFile(context, projectId);
        if (!f.isFile()) return null;
        try {
            JSONObject j = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            JSONArray a = j.getJSONArray("cues");
            List<CaptionBuilder.Cue> cues = new java.util.ArrayList<>();
            for (int i = 0; i < a.length(); i++) { JSONObject c = a.getJSONObject(i); cues.add(new CaptionBuilder.Cue(c.getLong("start"), c.getLong("end"), c.getString("text"))); }
            return new Taken(cues, j.optString("summary"));
        } catch (Exception e) {
            return new Taken(java.util.Collections.emptyList(), "Captions result was unreadable: " + e.getMessage());
        } finally { f.delete(); }
    }

    public static final class Taken {
        public final List<CaptionBuilder.Cue> cues; public final String summary;
        Taken(List<CaptionBuilder.Cue> cues, String summary) { this.cues = cues; this.summary = summary; }
    }

    static File resultFile(Context context, String projectId) {
        File dir = new File(context.getFilesDir(), "caption-results"); dir.mkdirs();
        return new File(dir, projectId.replaceAll("[^A-Za-z0-9_-]", "_") + ".json");
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate(); alive = true;
        getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("bc1-captions", "Auto captions", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            Transcriber t = transcriber;
            if (t != null) t.cancel(); else finish("Captions cancelled");
            return START_NOT_STICKY;
        }
        if (worker != null) return START_NOT_STICKY;
        Notification first = notification("Preparing captions…", -1);
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION, first);
        wakeLock = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BC1:captions");
        wakeLock.setReferenceCounted(false); wakeLock.acquire(3L * 60 * 60 * 1000);

        Transcriber.Options o = new Transcriber.Options();
        try { o.model = SpeechModels.Model.valueOf(intent.getStringExtra("model")); } catch (RuntimeException ignored) { }
        o.language = intent.getStringExtra("language") == null ? "auto" : intent.getStringExtra("language");
        o.translate = intent.getBooleanExtra("translate", false);
        o.clipId = intent.getStringExtra("clip");
        String path = intent.getStringExtra("project");
        transcriber = new Transcriber(this);
        worker = new Thread(() -> run(path, o), "bc1-captions");
        worker.start();
        return START_NOT_STICKY;
    }

    private void run(String path, Transcriber.Options o) {
        String message;
        try {
            EditProject project = ProjectStore.decode(new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8));
            new File(path).delete();
            long[] last = {0};
            Transcriber.Result r = transcriber.run(project, o, (stage, percent) -> {
                long now = System.currentTimeMillis();
                if (now - last[0] < 400 && percent >= 0 && percent < 100) return;
                last[0] = now;
                update(percent >= 0 ? stage + " " + percent + "%" : stage + "…", percent);
            });
            message = r.cues.isEmpty() ? "No speech found to caption" : String.format(java.util.Locale.US,
                    "%d captions • %s • %s model • %.1f× real time", r.cues.size(), languageName(r.language), o.model.label, r.realtimeFactor());
            JSONArray cues = new JSONArray();
            for (CaptionBuilder.Cue c : r.cues) cues.put(new JSONObject().put("start", c.startMs).put("end", c.endMs).put("text", c.text));
            File out = resultFile(this, project.id), tmp = new File(out.getPath() + ".tmp");
            Files.write(tmp.toPath(), new JSONObject().put("cues", cues).put("summary", message).toString().getBytes(StandardCharsets.UTF_8));
            if (!tmp.renameTo(out)) throw new java.io.IOException("Could not save the captions");
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("project", project.id).apply();
        } catch (java.util.concurrent.CancellationException | WhisperLib.Cancelled e) {
            message = "Captions cancelled";
        } catch (Throwable t) {
            android.util.Log.w("BC1Captions", "Captions failed", t);
            message = "Captions failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }
        new File(path).delete();
        String text = message;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> finish(text));
    }

    static String languageName(String code) {
        for (String[] l : Transcriber.LANGUAGES) if (l[0].equals(code)) return l[1];
        return code == null || code.isEmpty() ? "Unknown language" : code;
    }

    private void finish(String message) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("running", false).putString("status", message)
                .putLong("finishedAt", System.currentTimeMillis()).apply();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void update(String message, int percent) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        prefs.edit().putString("status", message).putInt("percent", percent).apply();
        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(NOTIFICATION, notification(message, percent));
    }

    private Notification notification(String message, int percent) {
        Intent open = new Intent(this, EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 2, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel = PendingIntent.getService(this, 3, new Intent(this, CaptionService.class).setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, "bc1-captions").setSmallIcon(R.drawable.ic_black_ice).setContentTitle("Black Ice • Auto captions")
                .setContentText(message).setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true)
                .setProgress(100, Math.max(0, percent), percent < 0).addAction(new Notification.Action.Builder(null, "Cancel", cancel).build()).build();
    }

    @Override public void onTimeout(int startId, int type) { Transcriber t = transcriber; if (t != null) t.cancel(); }

    @Override public void onDestroy() {
        alive = false;
        Transcriber t = transcriber; if (t != null) t.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }
}

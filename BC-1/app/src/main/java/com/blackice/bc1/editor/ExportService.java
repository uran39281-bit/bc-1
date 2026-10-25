package com.blackice.bc1.editor;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.*;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.*;
import com.blackice.bc1.EditorActivity;
import com.blackice.bc1.R;
import com.blackice.bc1.core.ExportEngine;
import com.blackice.bc1.engine.bc.BcExporter;
import com.blackice.bc1.engine.bc.EncoderCaps;
import com.blackice.bc1.core.ExportPlanner;
import com.blackice.bc1.core.OutputSpec;
import com.blackice.bc1.core.VideoCodec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground, cancellable export. Private temporary output is published only after encoding. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class ExportService extends Service {
    public static final String ACTION_CANCEL="com.blackice.bc1.CANCEL_EXPORT";
    public static final String PREFS="bc1-export";
    public static final String LAST_FAILURE="lastFailure";
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private static volatile boolean alive;
    public static boolean isRunning(){return alive;}
    private Transformer transformer;
    /** Resolution / frame rate chosen for this export. */
    private ExportPlanner.Plan plan;
    /** Why this export uses a different engine than the setting (null when it does not). */
    private String routeNote;
    /** ICE Lite export thread (engine BC_CORE); progress is written from that thread. */
    private Thread coreThread;
    private volatile int coreProgress=-1;
    private File output;
    private Uri destination;
    private boolean copying,finished;
    private final ProgressHolder progress=new ProgressHolder();
    /** Long exports (e.g. 15 minutes of 8-layer 1080p60) must keep running with the screen off. */
    private PowerManager.WakeLock wakeLock;
    private final Runnable poll=new Runnable(){@Override public void run(){
        if(copying || finished)return;
        if(coreThread!=null){update(coreProgress<0?"Preparing export…":"Exporting "+coreProgress+"% • ICE Lite • "+plan.label(),coreProgress);main.postDelayed(this,500);return;}
        if(transformer==null)return;
        int state=transformer.getProgress(progress);
        update(state==Transformer.PROGRESS_STATE_AVAILABLE?"Exporting "+progress.progress+"% • "+plan.label():"Preparing export…",state==Transformer.PROGRESS_STATE_AVAILABLE?progress.progress:-1);
        main.postDelayed(this,500);
    }};
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onCreate(){super.onCreate();alive=true;File[] leftovers=getCacheDir().listFiles((d,n)->n.startsWith("bc1-export-")&&n.endsWith(".mp4"));if(leftovers!=null)for(File f:leftovers)f.delete();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("bc1-export","Video export",NotificationManager.IMPORTANCE_LOW));}
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if(ACTION_CANCEL.equals(intent.getAction())) {cancelExport();return START_NOT_STICKY;}
        if(transformer!=null || coreThread!=null || copying)return START_NOT_STICKY;
        com.blackice.bc1.CrashLogs.operation("Export");
        Notification initial=notification("Preparing export…",-1);
        if(wakeLock==null){wakeLock=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"BC1:export");wakeLock.setReferenceCounted(false);wakeLock.acquire(6L*60*60*1000);}
        if(Build.VERSION.SDK_INT>=35)startForeground(41,initial,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        else if(Build.VERSION.SDK_INT>=29)startForeground(41,initial,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(41,initial);
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("running",true).putBoolean("success",false).putInt("percent",-1).putLong("outputBytes",0).putString("status","Preparing export…").putLong("startedAt",System.currentTimeMillis()).apply();
        try {
            destination=Uri.parse(intent.getStringExtra("destination"));
            String path=intent.getStringExtra("project");
            EditProject project=ProjectStore.decode(new String(java.nio.file.Files.readAllBytes(new File(path).toPath()),StandardCharsets.UTF_8));
            com.blackice.bc1.CrashLogs.project(project);
            new File(path).delete();
            ActiveFeatures.prepareRender(project);
            output=new File(getCacheDir(),"bc1-export-"+System.currentTimeMillis()+".mp4");
            // Resolution and frame rate the device can actually encode (may step down from the request).
            int shortEdge=intent.getIntExtra(ExportPlanner.PREF_RESOLUTION,getSharedPreferences(PREFS,MODE_PRIVATE).getInt(ExportPlanner.PREF_RESOLUTION,OutputSpec.EXPORT_SHORT_EDGE));
            VideoCodec.Choice codecChoice=VideoCodec.Choice.parse(getSharedPreferences(PREFS,MODE_PRIVATE).getString(VideoCodec.Choice.PREF_KEY,null));
            plan=ExportOptions.plan(project,getSharedPreferences(PREFS,MODE_PRIVATE));
            if(intent.hasExtra(ExportPlanner.PREF_RESOLUTION))plan=EncoderCaps.plan(shortEdge,project.frameRate,project.portrait,codecChoice);
            // Pre-flight: the MP4 is written privately, then copied, so both copies must fit.
            long needed=plan.estimatedBytes(project.durationMs())*2+64L*1024*1024,free=new android.os.StatFs(getCacheDir().getPath()).getAvailableBytes();
            if(free<needed)throw new IOException(String.format(java.util.Locale.US,"Not enough storage: this export needs about %.1f GB free, %.1f GB available.",needed/1e9,free/1e9));
            project.frameRate=plan.frameRate;
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("plan",plan.describe()).apply();
            String engineName=intent.getStringExtra(ExportEngine.PREF_KEY);
            if(engineName==null)engineName=getSharedPreferences(PREFS,MODE_PRIVATE).getString(ExportEngine.PREF_KEY,null);
            // Heavy projects go to ICE Lite (it can render in passes); HDR clips go to Media3 (it tone-maps).
            int decoders=0;try{for(int d:new com.blackice.bc1.core.RenderPlan(project,false).decoderDemand())decoders+=d;}catch(IllegalArgumentException noVisual){}
            ExportEngine.Route route=ExportEngine.route(ExportEngine.resolve(engineName,getPackageName()),decoders,com.blackice.bc1.engine.bc.HdrCheck.anyHdr(this,project),com.blackice.bc1.core.RenderPlan.usesBlend(project));
            routeNote=route.reason;
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("route",routeNote==null?"":routeNote).apply();
            if(route.engine==ExportEngine.BC_CORE||(PreviewProxy.withoutVisualClips(project)&&!project.overlays.isEmpty())){startCore(project);return START_NOT_STICKY;}
            transformer=new Transformer.Builder(this).setAssetLoaderFactory(CompositionFactory.assetLoader(this)).setVideoMimeType(plan.codec.mime).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setPortraitEncodingEnabled(true)
                    .setVideoFrameProcessorFactory(CompositionFactory.frameProcessor())
                    .setEncoderFactory(new DefaultEncoderFactory.Builder(this).setEnableFallback(false)
                            .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(plan.bitrate).build()).build())
                    .addListener(new Transformer.Listener(){
                        @Override public void onCompleted(Composition c,ExportResult result) {publish();}
                        @Override public void onError(Composition c,ExportResult r,ExportException e) {exportFailed("encoding",e);}
                    }).build();
            transformer.start(CompositionFactory.buildAtHeight(this,project,plan.shortEdge),output.getAbsolutePath());main.post(poll);
        } catch(Exception e) {exportFailed("preparation",e);}
        return START_NOT_STICKY;
    }
    /** ICE Lite renders on its own thread (it owns a GL context); results return to the main thread. */
    private void startCore(EditProject project) {
        final File target=output;
        coreThread=new Thread(()->{
            try {
                new BcExporter(this,project,plan,cancelled,p->coreProgress=p).export(target);
                main.post(()->{if(!cancelled.get()&&!finished)publish();});
            } catch(CancellationException ignored) {
            } catch(Throwable t) {
                Exception e=t instanceof Exception?(Exception)t:new RuntimeException(t);
                main.post(()->{if(!cancelled.get())exportFailed("encoding",e);});
            }
        },"bc1-core-export");
        coreThread.start();main.post(poll);
    }
    private void recordFailure(String stage,Exception error) {
        com.blackice.bc1.CrashLogs.record("Export failed",error);
        // Preserve nested codec/muxer causes for device QA, only in debuggable builds.
        // Do not change encoder policy or mask a failure with an automatic retry.
        if((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0)
            android.util.Log.e("BC1Export","Export failed",error);
        int code=error instanceof ExportException?((ExportException)error).errorCode:0;
        getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString(LAST_FAILURE,ExportDiagnostics.describe(stage,error,code))
                .putLong("lastFailureAt",System.currentTimeMillis()).apply();
    }
    private void exportFailed(String stage,Exception error) {
        recordFailure(stage,error);
        finish("Export failed: "+error.getMessage(),false);
    }
    private void publish() {
        copying=true;main.removeCallbacks(poll);update("Saving MP4…",100);
        io.execute(()->{
            Exception error=null;
            try(InputStream in=new FileInputStream(output);OutputStream out=getContentResolver().openOutputStream(destination,"wt")) {
                if(out==null)throw new IOException("Destination cannot be opened.");
                byte[] data=new byte[256*1024];int n;
                while((n=in.read(data))!=-1) {if(cancelled.get())throw new IOException("Cancelled");out.write(data,0,n);}
                out.flush();
            } catch(Exception e) {error=e;}
            final Exception failure=error;
            main.post(()->{if(failure!=null&&!cancelled.get())recordFailure("saving",failure);
                finish(cancelled.get()?"Export cancelled":failure==null?"MP4 saved • "+(plan==null?"":plan.describe())+(routeNote==null?"":" • "+routeNote):"Save failed: "+failure.getMessage(),failure==null&&!cancelled.get());});
        });
    }
    private void cancelExport() {
        cancelled.set(true);if(transformer!=null && !copying)transformer.cancel();
        if(!copying)finish("Export cancelled",false);
    }
    private void finish(String message,boolean success) {
        if(finished)return;finished=true;main.removeCallbacks(poll);releaseWakeLock();
        if(!success && destination!=null)try {android.provider.DocumentsContract.deleteDocument(getContentResolver(),destination);}catch(Exception ignored){}
        if(destination!=null)try{getContentResolver().releasePersistableUriPermission(destination,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(SecurityException|IllegalArgumentException ignored){}
        long finalBytes=output!=null?output.length():0;
        if(output!=null)output.delete();
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("running",false).putBoolean("success",success).putInt("percent",success?100:0).putLong("outputBytes",finalBytes)
                .putString("uri",success?destination.toString():"").putString("status",message).putLong("finishedAt",System.currentTimeMillis()).apply();
        stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();
    }
    private void update(String message,int percent) {
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("status",message).putInt("percent",percent).putLong("outputBytes",output!=null?output.length():0).putLong("heartbeat",System.currentTimeMillis()).apply();
        if(Build.VERSION.SDK_INT<33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)
            getSystemService(NotificationManager.class).notify(41,notification(message,percent));
    }
    private Notification notification(String message,int percent) {
        Intent open=new Intent(this,EditorActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel=PendingIntent.getService(this,1,new Intent(this,ExportService.class).setAction(ACTION_CANCEL),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"bc1-export").setSmallIcon(R.drawable.ic_black_ice).setContentTitle("Black Ice • Export")
                .setContentText(message).setContentIntent(content).setOngoing(true).setOnlyAlertOnce(true)
                .setProgress(100,Math.max(0,percent),percent<0).addAction(new Notification.Action.Builder(null,"Cancel",cancel).build()).build();
    }
    @Override public void onTimeout(int startId,int foregroundServiceType) {cancelExport();}
    private void releaseWakeLock(){if(wakeLock!=null&&wakeLock.isHeld())wakeLock.release();wakeLock=null;}
    @Override public void onDestroy(){releaseWakeLock();alive=false;main.removeCallbacks(poll);if(transformer!=null&&!finished)transformer.cancel();cancelled.set(true);io.shutdown();super.onDestroy();}
}

package com.blackice.bc1;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Private bounded diagnostics. No network transport, source names or exception messages are stored. */
public final class CrashLogs extends Application {
    public static final int EXPORT=9042;
    private static volatile CrashLogs app;
    private static volatile String operation="Idle";
    private static final int LIMIT=12;
    private final java.util.concurrent.ExecutorService writer=new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(16),new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();
    private volatile String projectContext="No project context";
    private volatile boolean foreground;
    private long lastCheckpoint;
    private final android.os.HandlerThread frameThread=new android.os.HandlerThread("bc1-frame-metrics");
    private volatile long frameCount,slowFrames,worstFrameNs;
    private final android.view.Window.OnFrameMetricsAvailableListener frames=(window,metrics,dropped)->{long ns=metrics.getMetric(android.view.FrameMetrics.TOTAL_DURATION);frameCount++;if(ns>16666667)slowFrames++;worstFrameNs=Math.max(worstFrameNs,ns);};
    private File sessionFile(){return new File(getFilesDir(),"diagnostic-session.txt");}
    public static void project(com.blackice.bc1.editor.EditProject p){CrashLogs a=app;if(a==null||p==null)return;int image=0,audio=0;for(com.blackice.bc1.editor.EditProject.Clip c:p.clips){if(c.image)image++;if(c.audioOnly)audio++;}
        a.projectContext="Clips: "+p.clips.size()+" images="+image+" audio="+audio+" overlays="+p.overlays.size()+" tracks="+p.tracks.length+" canvas="+p.canvasLabel()+" fps="+p.frameRate+" previewHeight="+p.previewHeight+" portrait="+p.portrait;
    }
    private void checkpoint(boolean force){long now=SystemClock.elapsedRealtime();if(!force&&now-lastCheckpoint<5000)return;if(!queued.compareAndSet(false,true))return;lastCheckpoint=now;
        writer.execute(()->{try{String recent; synchronized(history){recent=String.join("\n",history);}String data="Checkpoint: "+new Date()+"\nSession state: "+(foreground?"foreground":"background")+"\n"+projectContext+"\nRecent operation categories:\n"+recent;
            android.util.AtomicFile file=new android.util.AtomicFile(sessionFile());FileOutputStream out=null;try{out=file.startWrite();out.write(data.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);}catch(Exception error){if(out!=null)file.failWrite(out);}
        }finally{queued.set(false);}});
    }
    private void restoreSession(){try{if(sessionFile().isFile()){
        String previous=new String(Files.readAllBytes(sessionFile().toPath()),StandardCharsets.UTF_8);
        persist("Previous session checkpoint (not proof of a crash; system reclamation or normal background exit is possible):\n"+previous);
    }}catch(Exception ignored){/* Diagnostics must not prevent startup. */}}

    @Override public void onCreate(){super.onCreate();app=this;
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{try{record("Fatal Java exception",error);}finally{
            if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}}});
        writer.execute(()->{restoreSession();collectExits();});
        frameThread.start();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            int visible;
            public void onActivityStarted(Activity activity){visible++;foreground=true;checkpoint(true);}
            public void onActivityStopped(Activity activity){visible=Math.max(0,visible-1);foreground=visible>0;checkpoint(true);}
            public void onActivityCreated(Activity a,Bundle b){a.getWindow().addOnFrameMetricsAvailableListener(frames,new Handler(frameThread.getLooper()));}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){a.getWindow().removeOnFrameMetricsAvailableListener(frames);}
        });
    }
    public static void operation(String value){String first=value==null?"":value.split(" ",2)[0];operation=Arrays.asList("Rotation","Position","Scale","Width","Height","Anchor","Transforms","Opacity","Blend","Audio","Clip","Layer","Trimmed","Duration","Speed","Project","Frame","Imported","Export","Playback","Scrubbing","Dialog").contains(first)?first:"Editing";
        CrashLogs a=app;if(a!=null){synchronized(a.history){a.history.addLast(SystemClock.elapsedRealtime()+" "+operation);while(a.history.size()>40)a.history.removeFirst();}a.checkpoint(false);}
    }
    public static void record(String stage,Throwable error){CrashLogs a=app;if(a==null)return;
        StringBuilder b=new StringBuilder(stage).append("\nThread ID: ").append(Thread.currentThread().getId()).append('\n');Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int n=0;error!=null&&n<6&&seen.add(error);n++,error=error.getCause()){
            b.append(error.getClass().getName()).append('\n');if(error instanceof android.media.MediaCodec.CodecException){android.media.MediaCodec.CodecException codec=(android.media.MediaCodec.CodecException)error;b.append("Decoder code: ").append(codec.getErrorCode()).append(" recoverable=").append(codec.isRecoverable()).append(" transient=").append(codec.isTransient()).append('\n');}StackTraceElement[] stack=error.getStackTrace();
            for(int i=0;i<Math.min(80,stack.length);i++)b.append("  at ").append(stack[i]).append('\n');}
        String recent;synchronized(a.history){recent=String.join("\n",a.history);}String detail=b+"\n"+a.projectContext+"\nRecent operations:\n"+recent;
        if("Fatal Java exception".equals(stage))a.persist(detail);else try{a.writer.execute(()->a.persist(detail));}catch(RuntimeException ignored){}
    }
    private static File directory(Context c){return new File(c.getFilesDir(),"crash-logs");}
    private synchronized void persist(String detail){try{File dir=directory(this);if(!dir.isDirectory()&&!dir.mkdirs())return;
        String text=header(this)+"\nOperation: "+operation+"\n"+detail;
        File f=File.createTempFile("report-",".txt",dir);try(FileOutputStream out=new FileOutputStream(f)){out.write(text.substring(0,Math.min(text.length(),32000)).getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        File[] files=dir.listFiles();if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(int i=LIMIT;i<files.length;i++)if(!files[i].delete())android.util.Log.w("BC1Diagnostics","Could not prune report");}
    }catch(IOException e){android.util.Log.e("BC1Diagnostics","Could not persist diagnostics",e);}}
    private static String header(Context c){String version="unknown";try{android.content.pm.PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);version=p.versionName+" ("+(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode)+")";}catch(Exception ignored){}
        Runtime r=Runtime.getRuntime();return (app==null?"":"Frames observed: "+app.frameCount+" over 16.7ms="+app.slowFrames+" worst ms="+app.worstFrameNs/1000000+"\n")+"BC-1 diagnostic • "+new Date()+"\nVersion: "+version+"\nAndroid: "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\nJava heap used/max bytes: "+(r.totalMemory()-r.freeMemory())+"/"+r.maxMemory()+"\nFree app storage bytes: "+c.getFilesDir().getUsableSpace()+"\nNative heap bytes: "+Debug.getNativeHeapAllocatedSize()+"\nException messages and media identifiers omitted for privacy.";}
    private void collectExits(){if(Build.VERSION.SDK_INT<30)return;try{ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);long last=getSharedPreferences("crash-diagnostics",0).getLong("last-exit",0),latest=last;
        for(ApplicationExitInfo info:manager.getHistoricalProcessExitReasons(getPackageName(),0,LIMIT)){latest=Math.max(latest,info.getTimestamp());if(info.getTimestamp()<=last)continue;
            int reason=info.getReason();persist("Previous process exit: "+new Date(info.getTimestamp())+"\nClassification: "+(reason==ApplicationExitInfo.REASON_LOW_MEMORY?"System low-memory termination (not a confirmed crash)":reason==ApplicationExitInfo.REASON_ANR?"System-reported unresponsive app":reason==ApplicationExitInfo.REASON_CRASH_NATIVE?"System-reported native crash":reason==ApplicationExitInfo.REASON_CRASH?"System-reported Java crash":"System-reported process termination; no crash inferred")+"\nReason code: "+reason+"\nStatus: "+info.getStatus()+"\nPSS/RSS KB: "+info.getPss()+"/"+info.getRss()+"\nNative/ANR trace bodies omitted: may contain private source data.");}
        getSharedPreferences("crash-diagnostics",0).edit().putLong("last-exit",latest).apply();
    }catch(RuntimeException e){record("Previous exit query failed",e);}}
    public static String report(Context c){StringBuilder b=new StringBuilder(header(c));File[] files=directory(c).listFiles();if(files==null||files.length==0)return b+"\n\nNo saved crash reports.";
        Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(File f:files){try{b.append("\n\n---\n").append(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}catch(IOException e){b.append("\nReport unavailable");}}return b.toString();}
    private static void readAsync(Activity a,java.util.function.Consumer<String> use){CrashLogs instance=app;if(instance==null)return;instance.writer.execute(()->{String result;try{result=report(a);}catch(Exception error){result="Report unavailable: "+error.getClass().getSimpleName();}final String text=result.substring(0,Math.min(192000,result.length()));a.runOnUiThread(()->{if(!a.isDestroyed()&&!a.isFinishing())use.accept(text);});});}
    public static void show(Activity a){operation("Dialog");String[] actions={"View recent reports","Copy report","Share report","Clear reports","Export report"};new AlertDialog.Builder(a).setTitle("Diagnostics / Crash Logs").setItems(actions,(d,i)->{
        if(i==0)readAsync(a,report->{TextView text=new TextView(a);text.setText(report);text.setTextIsSelectable(true);text.setPadding(16,16,16,16);ScrollView scroll=new ScrollView(a);scroll.addView(text);new AlertDialog.Builder(a).setTitle("Recent diagnostic reports").setView(scroll).setPositiveButton("Close",null).show();});
        else if(i==1)readAsync(a,report->{((ClipboardManager)a.getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("BC-1 diagnostic",report));Toast.makeText(a,"Diagnostic copied",Toast.LENGTH_SHORT).show();});
        else if(i==2)readAsync(a,report->{try{a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report),"Share diagnostic report"));}catch(ActivityNotFoundException unavailable){Toast.makeText(a,"No app available for sharing",Toast.LENGTH_SHORT).show();}});
        else if(i==3)new AlertDialog.Builder(a).setTitle("Clear saved reports?").setNegativeButton("Cancel",null).setPositiveButton("Clear",(x,y)->{if(app!=null)app.writer.execute(()->{File[] files=directory(a).listFiles();if(files!=null)for(File f:files)f.delete();app.sessionFile().delete();synchronized(app.history){app.history.clear();}a.runOnUiThread(()->Toast.makeText(a,"Saved reports cleared",Toast.LENGTH_SHORT).show());});}).show();
        else a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"BC-1-diagnostic.txt"),EXPORT);
    }).setNegativeButton("Close",null).show();}
}

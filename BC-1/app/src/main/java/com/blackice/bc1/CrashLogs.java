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
    @Override public void onCreate(){super.onCreate();app=this;
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{try{record("Fatal Java exception",error);}finally{
            if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}}});
        new Thread(()->collectExits(),"bc1-exit-diagnostics").start();
    }
    public static void operation(String value){String first=value==null?"":value.split(" ",2)[0];operation=Arrays.asList("Rotation","Position","Scale","Width","Height","Anchor","Transforms","Opacity","Blend","Audio","Clip","Layer","Trimmed","Duration","Speed","Project","Frame","Imported","Export").contains(first)?first:"Editing";}
    public static void record(String stage,Throwable error){CrashLogs a=app;if(a==null)return;
        StringBuilder b=new StringBuilder(stage).append('\n');Set<Throwable> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int n=0;error!=null&&n<6&&seen.add(error);n++,error=error.getCause()){
            b.append(error.getClass().getName()).append('\n');StackTraceElement[] stack=error.getStackTrace();
            for(int i=0;i<Math.min(80,stack.length);i++)b.append("  at ").append(stack[i]).append('\n');}
        a.persist(b.toString());
    }
    private static File directory(Context c){return new File(c.getFilesDir(),"crash-logs");}
    private synchronized void persist(String detail){try{File dir=directory(this);if(!dir.isDirectory()&&!dir.mkdirs())return;
        String text=header(this)+"\nOperation: "+operation+"\n"+detail;
        File f=File.createTempFile("report-",".txt",dir);try(FileOutputStream out=new FileOutputStream(f)){out.write(text.substring(0,Math.min(text.length(),32000)).getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        File[] files=dir.listFiles();if(files!=null){Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(int i=LIMIT;i<files.length;i++)if(!files[i].delete())android.util.Log.w("BC1Diagnostics","Could not prune report");}
    }catch(IOException e){android.util.Log.e("BC1Diagnostics","Could not persist diagnostics",e);}}
    private static String header(Context c){String version="unknown";try{android.content.pm.PackageInfo p=c.getPackageManager().getPackageInfo(c.getPackageName(),0);version=p.versionName+" ("+(Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode)+")";}catch(Exception ignored){}
        Runtime r=Runtime.getRuntime();return "BC-1 diagnostic • "+new Date()+"\nVersion: "+version+"\nAndroid: "+Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT+"\nDevice: "+Build.MANUFACTURER+" "+Build.MODEL+"\nJava heap used/max bytes: "+(r.totalMemory()-r.freeMemory())+"/"+r.maxMemory()+"\nNative heap bytes: "+Debug.getNativeHeapAllocatedSize()+"\nException messages and media identifiers omitted for privacy.";}
    private void collectExits(){if(Build.VERSION.SDK_INT<30)return;try{ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);long last=getSharedPreferences("crash-diagnostics",0).getLong("last-exit",0),latest=last;
        for(ApplicationExitInfo info:manager.getHistoricalProcessExitReasons(getPackageName(),0,LIMIT)){latest=Math.max(latest,info.getTimestamp());if(info.getTimestamp()<=last)continue;
            int reason=info.getReason();if(reason==ApplicationExitInfo.REASON_CRASH||reason==ApplicationExitInfo.REASON_CRASH_NATIVE||reason==ApplicationExitInfo.REASON_ANR||reason==ApplicationExitInfo.REASON_LOW_MEMORY)
                persist("Previous process exit: "+new Date(info.getTimestamp())+"\nReason code: "+reason+"\nStatus: "+info.getStatus()+"\nPSS/RSS KB: "+info.getPss()+"/"+info.getRss()+"\nNative/ANR trace bodies omitted: may contain private source data.");}
        getSharedPreferences("crash-diagnostics",0).edit().putLong("last-exit",latest).apply();
    }catch(RuntimeException e){record("Previous exit query failed",e);}}
    public static String report(Context c){StringBuilder b=new StringBuilder(header(c));File[] files=directory(c).listFiles();if(files==null||files.length==0)return b+"\n\nNo saved crash reports.";
        Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());for(File f:files){try{b.append("\n\n---\n").append(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));}catch(IOException e){b.append("\nReport unavailable");}}return b.toString();}
    public static void show(Activity a){String[] actions={"View Crash Logs","Copy Log","Share Log","Clear Logs","Export Diagnostic Report"};new AlertDialog.Builder(a).setTitle("Diagnostics → Crash Logs").setItems(actions,(d,i)->{
        if(i==0){TextView text=new TextView(a);text.setText(report(a));text.setTextIsSelectable(true);text.setPadding(16,16,16,16);ScrollView scroll=new ScrollView(a);scroll.addView(text);new AlertDialog.Builder(a).setTitle("Crash Logs").setView(scroll).setPositiveButton("Close",null).show();}
        else if(i==1){((ClipboardManager)a.getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("BC-1 diagnostic",report(a)));Toast.makeText(a,"Diagnostic copied",Toast.LENGTH_SHORT).show();}
        else if(i==2)a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,report(a)),"Share diagnostic report"));
        else if(i==3){File[] files=directory(a).listFiles();boolean ok=true;if(files!=null)for(File f:files)ok&=f.delete();Toast.makeText(a,ok?"Logs cleared":"Some logs could not be cleared",Toast.LENGTH_LONG).show();}
        else a.startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"BC-1-diagnostic.txt"),EXPORT);
    }).setNegativeButton("Close",null).show();}
}

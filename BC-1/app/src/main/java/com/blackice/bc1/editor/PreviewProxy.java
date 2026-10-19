package com.blackice.bc1.editor;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MimeTypes;
import androidx.media3.transformer.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;

/** Seekable preview of complex compositions, rendered by the same GLES graph as export.
 * Media3 1.11's live multi-input player cannot reliably render paused/seeking frames.
 * All calls and callbacks run on the application thread; codecs/GLES run asynchronously.
 */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PreviewProxy implements AutoCloseable {
    public interface Listener {
        void onProgress(int percent);
        void onReady(File file);
        void onError(String message);
    }
    private static final long BUDGET=512L*1024*1024;
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final File directory;
    private Transformer transformer;
    private File partial;
    private int generation;
    private Runnable poll;
    /** Cancels an ICE Lite preview render (used for compositions Media3 cannot decode at once). */
    private java.util.concurrent.atomic.AtomicBoolean coreStop;

    public PreviewProxy(Context context) {
        this.context=context.getApplicationContext();
        directory=new File(context.getCacheDir(),"bc1-preview-v11");
    }
    public static boolean required(EditProject project) {
        return !project.overlays.isEmpty() || project.clips.size()>1 || (!project.clips.isEmpty()&&project.clips.get(0).image) || com.blackice.bc1.core.RenderPlan.usesBlend(project);
    }
    /** The render in flight, by project key; a second request for the same project joins it instead of restarting. */
    private String runningKey;
    private Listener current;
    private final Listener relay=new Listener(){
        @Override public void onProgress(int percent){if(current!=null)current.onProgress(percent);}
        @Override public void onReady(File file){Listener l=current;runningKey=null;if(l!=null)l.onReady(file);}
        @Override public void onError(String message){Listener l=current;runningKey=null;if(l!=null)l.onError(message);}
    };
    /** Whether a render for this project is in flight or already cached (the editor prepares one in the background). */
    public boolean preparing(){return runningKey!=null;}
    public void prepare(EditProject project,Listener listener) {
        try {
            EditProject snapshot=ProjectStore.snapshot(project);snapshot.playheadMs=0;
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(ProjectStore.encode(snapshot).getBytes(StandardCharsets.UTF_8));
            StringBuilder key=new StringBuilder();for(byte b:digest)key.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            if(key.toString().equals(runningKey)&&(transformer!=null||coreStop!=null)){current=listener;return;}
            cancel();final int request=generation;
            directory.mkdirs();
            File completed=new File(directory,key+".mp4");
            if(completed.isFile()&&completed.length()>0){completed.setLastModified(System.currentTimeMillis());listener.onReady(completed);return;}
            trimCache(null);
            if(directory.getUsableSpace()<64L*1024*1024)throw new IOException("Free some storage to prepare the preview.");
            runningKey=key.toString();current=listener;final Listener out=relay;
            partial=new File(directory,key+".part.mp4");partial.delete();
            if(needsIceLite(snapshot)){prepareWithIceLite(snapshot,completed,request,out);return;}
            transformer=new Transformer.Builder(context)
                    .setAssetLoaderFactory(CompositionFactory.assetLoader(context))
                    .setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setPortraitEncodingEnabled(true)
                    .setVideoFrameProcessorFactory(CompositionFactory.frameProcessor())
                    .setEncoderFactory(new DefaultEncoderFactory.Builder(context).setEnableFallback(true)
                            .setRequestedVideoEncoderSettings(new VideoEncoderSettings.Builder().setBitrate(2_000_000).build()).build())
                    .addListener(new Transformer.Listener(){
                        @Override public void onCompleted(Composition composition,ExportResult result){
                            if(request!=generation)return;
                            main.removeCallbacks(poll);transformer=null;
                            if(partial==null||!partial.renameTo(completed)){fail(request,out,"Could not save the prepared preview.");return;}
                            partial=null;trimCache(completed);out.onReady(completed);
                        }
                        @Override public void onError(Composition composition,ExportResult result,ExportException error){
                            fail(request,out,error.getMessage());
                        }
                    }).build();
            ProgressHolder progress=new ProgressHolder();
            poll=new Runnable(){@Override public void run(){
                if(request!=generation||transformer==null)return;
                if(partial.length()>BUDGET||directory.getUsableSpace()<16L*1024*1024){fail(request,out,"Preview cache is full. Shorten the project or free storage; the project is preserved.");return;}
                int state=transformer.getProgress(progress);
                out.onProgress(state==Transformer.PROGRESS_STATE_AVAILABLE?progress.progress:-1);
                main.postDelayed(this,500);
            }};
            transformer.start(CompositionFactory.build(context,snapshot,false),partial.getAbsolutePath());
            main.post(poll);
        }catch(Exception error){runningKey=null;cancel();listener.onError(error.getMessage()==null?"Preview preparation failed.":error.getMessage());}
    }
    /** More simultaneous video layers than Media3 can open: ICE Lite renders the preview (in passes if needed). */
    static boolean needsIceLite(EditProject project){
        if(project.clips.isEmpty()&&!project.overlays.isEmpty())return true;
        if(com.blackice.bc1.core.RenderPlan.usesBlend(project))return true;
        int decoders=0;
        try{for(int d:new com.blackice.bc1.core.RenderPlan(project,true).decoderDemand())decoders+=d;}catch(IllegalArgumentException noVisual){return false;}
        return decoders>com.blackice.bc1.core.ExportEngine.MEDIA3_MAX_DECODERS;
    }
    private void prepareWithIceLite(EditProject snapshot,File completed,int request,Listener listener){
        final File target=partial;final java.util.concurrent.atomic.AtomicBoolean stop=new java.util.concurrent.atomic.AtomicBoolean();coreStop=stop;
        new Thread(()->{
            try{
                new com.blackice.bc1.engine.bc.BcExporter(context,snapshot,
                        com.blackice.bc1.core.ExportPlanner.fixed(snapshot.previewHeight,snapshot.frameRate,com.blackice.bc1.core.VideoCodec.H264,snapshot.portrait),
                        stop,p->main.post(()->{if(request==generation)listener.onProgress(p);})).previewSemantics(true).export(target);
                main.post(()->{
                    if(request!=generation){target.delete();return;}
                    coreStop=null;
                    if(!target.renameTo(completed)){fail(request,listener,"Could not save the prepared preview.");return;}
                    partial=null;trimCache(completed);listener.onReady(completed);
                });
            }catch(java.util.concurrent.CancellationException cancelled){target.delete();
            }catch(Throwable error){main.post(()->fail(request,listener,String.valueOf(error.getMessage())));}
        },"bc1-ice-preview").start();
    }
    private void fail(int request,Listener listener,String message){
        if(request!=generation)return;Listener target=listener==relay?current:listener;cancel();if(target!=null)target.onError(message==null?"Preview preparation failed.":message);
    }
    private void trimCache(File keep){
        File[] files=directory.listFiles((d,n)->n.endsWith(".mp4"));if(files==null)return;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified));long bytes=0;
        for(File file:files)bytes+=file.length();
        for(File file:files)if(!file.equals(keep)&&(file.getName().endsWith(".part.mp4")||bytes>BUDGET/2)){
            long length=file.length();if(file.delete())bytes-=length;
        }
    }
    public void cancel(){
        generation++;runningKey=null;current=null;if(poll!=null)main.removeCallbacks(poll);poll=null;
        if(transformer!=null){transformer.cancel();transformer=null;}
        if(coreStop!=null){coreStop.set(true);coreStop=null;}
        if(partial!=null){partial.delete();partial=null;}
    }
    @Override public void close(){cancel();}
}

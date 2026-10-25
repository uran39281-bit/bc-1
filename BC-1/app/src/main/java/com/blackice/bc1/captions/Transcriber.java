package com.blackice.bc1.captions;

import android.content.Context;
import com.blackice.bc1.core.AudioPlan;
import com.blackice.bc1.core.CaptionBuilder;
import com.blackice.bc1.core.OutputSpec;
import com.blackice.bc1.core.SpeechChunks;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.engine.bc.SpeechAudio;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Speech in a project to timed caption cues: mixes the project's speech (music bed excluded) at 16 kHz,
 * transcribes it with whisper in pause-aligned chunks, and builds readable cues on the timeline.
 */
public final class Transcriber {
    public interface Listener { void onProgress(String stage, int percent); }

    /** What to caption and how. */
    public static final class Options {
        public SpeechModels.Model model = SpeechModels.Model.BASE_EN;
        /** ISO language code, or "auto". */
        public String language = "auto";
        /** Output English captions whatever language is spoken. */
        public boolean translate;
        /** Caption only this clip's sound (null: the whole timeline). */
        public String clipId;
    }

    public static final class Result {
        public final List<CaptionBuilder.Cue> cues; public final String language; public final long audioMs, elapsedMs;
        Result(List<CaptionBuilder.Cue> cues, String language, long audioMs, long elapsedMs) { this.cues = cues; this.language = language; this.audioMs = audioMs; this.elapsedMs = elapsedMs; }
        public double realtimeFactor() { return elapsedMs <= 0 ? 0 : audioMs / (double) elapsedMs; }
    }

    /** Languages offered in the captions dialog (ISO code, name). Whisper supports more; these cover most use. */
    public static final String[][] LANGUAGES = {
            {"auto", "Detect automatically"}, {"en", "English"}, {"es", "Spanish"}, {"fr", "French"}, {"de", "German"},
            {"it", "Italian"}, {"pt", "Portuguese"}, {"ru", "Russian"}, {"uk", "Ukrainian"}, {"pl", "Polish"},
            {"ja", "Japanese"}, {"ko", "Korean"}, {"zh", "Chinese"}, {"hi", "Hindi"}, {"ar", "Arabic"},
            {"tr", "Turkish"}, {"nl", "Dutch"}, {"sv", "Swedish"}, {"vi", "Vietnamese"}, {"id", "Indonesian"}};

    private final Context context;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private volatile WhisperLib whisper;

    public Transcriber(Context context) { this.context = context.getApplicationContext(); }

    public void cancel() { cancelled.set(true); WhisperLib w = whisper; if (w != null) w.cancel(); }

    /** Threads for whisper: the big cores (leave the rest for the UI); 6 on 8-core phones. */
    public static int threads() { int cores = Runtime.getRuntime().availableProcessors(); return Math.max(1, Math.min(6, cores - 2)); }

    public Result run(EditProject project, Options options, Listener listener) throws Exception {
        return run(project, options, listener, null, "temporary");
    }

    public Result run(EditProject project, Options options, Listener listener, File checkpoint, String jobId) throws Exception {
        if (options.model.englishOnly()) { options.language = "en"; options.translate = false; }
        long started = android.os.SystemClock.elapsedRealtime();
        if (!WhisperLib.available()) throw new IllegalStateException("Auto captions are not available on this device's CPU type.");
        com.blackice.bc1.core.SpeechScope scope = com.blackice.bc1.core.SpeechScope.of(project, options.clipId);
        AudioPlan plan = scope.plan;
        long from=scope.fromMs,to=scope.toMs,total=(to-from)*SpeechAudio.RATE/1000;
        if(plan.segments.isEmpty())throw new IllegalArgumentException("This selection has no audible speech source.");
        com.blackice.bc1.core.SpeechCheckpoint saved=checkpoint==null ? new com.blackice.bc1.core.SpeechCheckpoint(jobId)
                : com.blackice.bc1.core.SpeechCheckpoint.load(checkpoint,jobId,total);
        long previousElapsed=saved.elapsedMs;
        if(saved.nextFrame<total){
            File model=SpeechModels.ensure(context,options.model,cancelled::get,
                    p->listener.onProgress(options.model.preparation()+" "+options.model.label+" speech model",p));
            check();listener.onProgress(saved.nextFrame>0?"Resuming saved speech job":"Loading speech model",-1);
            try(WhisperLib w=new WhisperLib(model.getPath()); SpeechAudio audio=new SpeechAudio(context,plan,from,to,saved.nextFrame,cancelled::get)){
                whisper=w;
                int rate=SpeechAudio.RATE,chunk=SpeechChunks.CHUNK_SECONDS*rate,search=SpeechChunks.SEARCH_SECONDS*rate;
                float[] buffer=new float[(int)Math.min(total-saved.nextFrame,chunk)];
                int filled=0;boolean end=false;
                while(true){
                    check();
                    while(!end&&filled<buffer.length){
                        int n=audio.read(buffer,filled,Math.min(16384,buffer.length-filled));
                        if(n<=0)end=true;else filled+=n;check();
                    }
                    if(filled==0)break;
                    int cut=(end||saved.nextFrame+filled>=total)?filled:SpeechChunks.quietestCut(buffer,filled,rate,search);
                    float[] piece=java.util.Arrays.copyOf(buffer,cut);
                    long base=saved.nextFrame,baseMs=base*1000/rate,pieceMs=cut*1000L/rate;
                    Thread poll=progressPoller(w,listener,base,cut,total);
                    List<WhisperLib.Segment> segments;
                    try {check();segments=silent(piece)?java.util.Collections.emptyList():w.transcribe(piece,options.language,options.translate,threads(),null);}
                    finally {poll.interrupt();poll.join();}
                    check();
                    if(!segments.isEmpty()&&"auto".equals(options.language))saved.language=w.language();
                    else if(!"auto".equals(options.language))saved.language=options.language;
                    for(WhisperLib.Segment segment:segments){
                        long[] t=SpeechChunks.tighten(piece,rate,segment.startMs,segment.endMs);
                        long start=Math.max(0,Math.min(pieceMs,t[0])),finish=Math.max(start,Math.min(pieceMs,t[1]));
                        if(finish>start){saved.times.add(new long[]{start+baseMs,finish+baseMs});saved.texts.add(segment.text);}
                    }
                    saved.nextFrame+=cut;saved.elapsedMs=previousElapsed+android.os.SystemClock.elapsedRealtime()-started;
                    if(checkpoint!=null)saved.save(checkpoint);
                    listener.onProgress("Saved transcription progress",(int)Math.min(99,saved.nextFrame*100/Math.max(1,total)));
                    System.arraycopy(buffer,cut,buffer,0,filled-cut);filled-=cut;
                    if(end&&filled==0)break;
                }
                if(saved.nextFrame<total)throw new java.io.IOException("Audio ended before the selection. Progress was saved; check the source media.");
            }finally{whisper=null;}
        }
        check();
        long[] starts=new long[saved.times.size()],ends=new long[saved.times.size()];
        for(int i=0;i<starts.length;i++){starts[i]=saved.times.get(i)[0];ends[i]=saved.times.get(i)[1];}
        OutputSpec spec=OutputSpec.of(project,1080);
        List<CaptionBuilder.Cue> cues=CaptionBuilder.cues(starts,ends,saved.texts.toArray(new String[0]),from,to,CaptionBuilder.lineChars(spec.width,spec.height));
        return new Result(cues,saved.language,to-from,Math.max(saved.elapsedMs,previousElapsed+android.os.SystemClock.elapsedRealtime()-started));
    }

    private Thread progressPoller(WhisperLib w, Listener listener, long done, int length, long total) {
        Thread t = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long position = done + (long) length * w.progress() / 100;
                    listener.onProgress("Transcribing speech", (int) Math.min(99, position * 100 / total));
                    Thread.sleep(500);
                }
            } catch (InterruptedException ignored) { }
        }, "bc1-captions-progress");
        t.setDaemon(true); t.start();
        return t;
    }

    private static boolean silent(float[] pcm) { double energy = 0; for (float v : pcm) energy += v * v; return pcm.length == 0 || energy / pcm.length < 1e-9; }

    private void check() { if (cancelled.get()) throw new java.util.concurrent.CancellationException("Captions cancelled"); }
}

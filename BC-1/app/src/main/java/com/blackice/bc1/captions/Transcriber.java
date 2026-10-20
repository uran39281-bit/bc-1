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
        public SpeechModels.Model model = SpeechModels.Model.BASE;
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
        long started = System.currentTimeMillis();
        if (!WhisperLib.available()) throw new IllegalStateException("Auto captions are not available on this device's CPU type.");
        File model = SpeechModels.ensure(context, options.model, cancelled::get,
                p -> listener.onProgress("Downloading " + options.model.label + " speech model", p));
        check();

        // Scope: one clip's sound, or every non-music sound on the timeline.
        AudioPlan plan = AudioPlan.of(project, false);
        long from = 0, to = project.durationMs();
        if (options.clipId != null) {
            EditProject.Placement placement = null;
            for (EditProject.Placement p : project.placements()) if (p.clip.id.equals(options.clipId)) placement = p;
            if (placement == null) throw new IllegalArgumentException("The selected clip is no longer in the project.");
            if (placement.clip.image) throw new IllegalArgumentException("The selected clip is a picture and has no sound.");
            EditProject.Clip clip = placement.clip;
            long start = placement.startMs;
            from = start; to = start + clip.durationMs();
            plan = plan.filter(s -> !s.music && s.uri.equals(clip.uri) && s.timelineStartMs == start);
        } else plan = plan.filter(s -> !s.music);
        if (plan.segments.isEmpty()) throw new IllegalArgumentException(options.clipId != null
                ? "The selected clip has no sound (or its volume is 0)." : "This project has no speech audio to caption. Music is not captioned.");

        List<long[]> times = new ArrayList<>(); List<String> texts = new ArrayList<>();
        String language = options.language;
        listener.onProgress("Loading speech model", -1);
        try (WhisperLib w = new WhisperLib(model.getPath()); SpeechAudio audio = new SpeechAudio(context, plan, from, to)) {
            whisper = w;
            int rate = SpeechAudio.RATE, chunk = SpeechChunks.CHUNK_SECONDS * rate, search = SpeechChunks.SEARCH_SECONDS * rate;
            long total = Math.max(1, audio.frames());
            float[] buffer = new float[(int) Math.min(total, chunk + search)];
            int filled = 0; long consumed = 0; boolean end = false;
            while (true) {
                check();
                // Fill the buffer (decoding is a few percent of the work; report it as part of the chunk).
                while (!end && filled < buffer.length) {
                    int n = audio.read(buffer, filled, Math.min(16_384, buffer.length - filled));
                    if (n <= 0) end = true; else filled += n;
                    check();
                }
                if (filled == 0) break;
                int cut = end && filled <= chunk ? filled : SpeechChunks.quietestCut(buffer, Math.min(filled, chunk), rate, search);
                float[] piece = java.util.Arrays.copyOf(buffer, cut);
                long base = consumed;
                final long done = consumed;
                Thread poll = progressPoller(w, listener, done, cut, total);
                List<WhisperLib.Segment> segments;
                try { segments = w.transcribe(piece, options.language, options.translate, threads(), null); }
                finally { poll.interrupt(); }
                if ("auto".equals(language)) language = w.language();
                long baseMs = base * 1000 / rate;
                for (WhisperLib.Segment s : segments) {
                    long[] t = SpeechChunks.tighten(piece, rate, s.startMs, s.endMs); // align to the actual sound
                    times.add(new long[]{t[0] + baseMs, t[1] + baseMs}); texts.add(s.text);
                }
                System.arraycopy(buffer, cut, buffer, 0, filled - cut);
                filled -= cut; consumed += cut;
                if (end && filled == 0) break;
            }
        } finally { whisper = null; }
        check();

        long[] s = new long[times.size()], e = new long[times.size()];
        for (int i = 0; i < s.length; i++) { s[i] = times.get(i)[0]; e[i] = times.get(i)[1]; }
        OutputSpec spec = OutputSpec.of(project, 1080);
        List<CaptionBuilder.Cue> cues = CaptionBuilder.cues(s, e, texts.toArray(new String[0]), from, to, CaptionBuilder.lineChars(spec.width, spec.height));
        return new Result(cues, language, to - from, System.currentTimeMillis() - started);
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

    private void check() { if (cancelled.get()) throw new java.util.concurrent.CancellationException("Captions cancelled"); }
}

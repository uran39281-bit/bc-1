package com.blackice.bc1.core;

import com.blackice.bc1.audio.AudioSettings;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.LayerOps;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/* loaded from: classes6.dex */
public final class AudioPlan {
    public final long durationMs;
    public final List<Segment> segments;

    public static final class Segment {
        public AudioSettings audio = new AudioSettings();
        public final long durationMs;
        public final long fadeInMs;
        public final long fadeOutMs;
        public final boolean music;
        private EditProject.Clip clip;
        public final long sourceInMs;
        public final float speed;
        public final long timelineStartMs;
        public final String uri;
        public final float volume;

        Segment(String uri, long start, long duration, long in, float speed, float volume, long fadeIn, long fadeOut, boolean music) {
            this.uri = uri;
            this.timelineStartMs = start;
            this.durationMs = duration;
            this.sourceInMs = in;
            this.speed = speed;
            this.volume = volume;
            this.fadeInMs = fadeIn;
            this.fadeOutMs = fadeOut;
            this.music = music;
        }

        public long timelineEndMs() {
            return this.timelineStartMs + this.durationMs;
        }

        public float gainAt(double localMs) {
            if(clip!=null)return com.blackice.bc1.editor.ClipAudio.gain(clip,localMs,false);
            double envelope = 1;
            if (fadeInMs > 0) envelope = Math.min(envelope, localMs / fadeInMs);
            if (fadeOutMs > 0) envelope = Math.min(envelope, (durationMs - localMs) / fadeOutMs);
            return (float) (volume * Math.pow(10,audio.normalizationDb/20.0) * Math.max(0, Math.min(1, envelope)));
        }
    }

    private AudioPlan(List<Segment> segments, long durationMs) {
        this.segments = segments;
        this.durationMs = durationMs;
    }

    public static AudioPlan of(EditProject project, boolean preview) {
        project.validate();
        long duration = project.durationMs();
        List<Segment> list = new ArrayList<>();
        boolean solo = preview && LayerOps.anySolo(project);
        for (EditProject.Placement p : project.placements()) {
            EditProject.Clip c = p.clip;
            if (c.image) continue;
            boolean renders = LayerOps.renders(project, c, preview);
            float volume = c.volume;
            if (EditProject.audioTrack(c.track) && !renders) continue;
            if (!EditProject.audioTrack(c.track) && solo && !renders) continue;
            if (c.unmutedVolume>=0 || volume<=0&&c.motion.curves.get(com.blackice.bc1.editor.Motion.VOLUME).isEmpty()) continue;
            Segment segment=new Segment(c.uri,p.startMs,c.durationMs(),c.inMs,c.speed,volume,c.fadeInMs,c.fadeOutMs,false);segment.clip=c.copy();segment.audio=segment.clip.audio;list.add(segment);
        }
        EditProject.Music m = project.music;
        if (m != null && m.startMs < duration && m.volume > 0.0f) {
            long cursor = m.startMs;
            int count = 0;
            long cursor2 = cursor;
            do {
                long cursor3 = m.outMs;
                long length = Math.min(cursor3 - m.inMs, duration - cursor2);
                list.add(new Segment(m.uri, cursor2, length, m.inMs, 1.0f, m.volume, 0L, 0L, true));
                cursor2 += length;
                count++;
                if (count > 2000) {
                    throw new IllegalArgumentException("Music segment is too short to loop across this project. Use a longer trim.");
                }
                if (!m.loop) {
                    break;
                }
            } while (cursor2 < duration);
        }
        return new AudioPlan(list, duration);
    }

    public AudioPlan filter(Predicate<Segment> keep) {
        List<Segment> kept = new ArrayList<>();
        for (Segment s : this.segments) {
            if (keep.test(s)) {
                kept.add(s);
            }
        }
        return new AudioPlan(kept, this.durationMs);
    }

    public List<Segment> between(long fromMs, long toMs) {
        List<Segment> result = new ArrayList<>();
        for (Segment s : this.segments) {
            if (s.timelineStartMs < toMs && s.timelineEndMs() > fromMs) {
                result.add(s);
            }
        }
        return result;
    }
}

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
            double envelope = this.fadeInMs > 0 ? Math.min(1.0d, localMs / this.fadeInMs) : 1.0d;
            if (this.fadeOutMs > 0) {
                envelope = Math.min(envelope, (this.durationMs - localMs) / this.fadeOutMs);
            }
            return (float) (this.volume * Math.pow(10.0d, this.audio.normalizationDb / 20.0d) * Math.max(0.0d, Math.min(1.0d, envelope)));
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
            if (!c.image) {
                boolean renders = LayerOps.renders(project, c, preview);
                float volume = c.volume;
                if (c.track >= 8 && !renders) {
                    volume = 0.0f;
                }
                float volume2 = (c.track >= 8 || !solo || renders) ? volume : 0.0f;
                if (volume2 > 0.0f) {
                    Segment segment = new Segment(c.uri, p.startMs, c.durationMs(), c.inMs, c.speed, volume2, c.fadeInMs, c.fadeOutMs, false);
                    segment.audio = c.audio.copy();
                    list.add(segment);
                    solo = solo;
                }
            }
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

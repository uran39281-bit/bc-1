package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.LayerOps;
import java.util.ArrayList;
import java.util.List;

/**
 * ICE Lite audio graph: every sound source as a timed segment with its gain envelope. Pure Java.
 * Clip sound follows the shipping rules: visual clips keep their sound even when the picture is hidden,
 * muted audio lanes are silent, pictures carry no sound, and the music bed loops to the end of the timeline.
 */
public final class AudioPlan {
    public static final class Segment {
        public final String uri;
        public final long timelineStartMs, durationMs, sourceInMs;
        public final float speed, volume;
        public final long fadeInMs, fadeOutMs;
        public final boolean music;
        private EditProject.Clip clip;

        Segment(String uri, long start, long duration, long in, float speed, float volume, long fadeIn, long fadeOut, boolean music) {
            this.uri = uri; timelineStartMs = start; durationMs = duration; sourceInMs = in;
            this.speed = speed; this.volume = volume; fadeInMs = fadeIn; fadeOutMs = fadeOut; this.music = music;
        }

        public long timelineEndMs() { return timelineStartMs + durationMs; }

        /** Gain at a time measured from the segment start (fades are linear, like the Media3 GainProcessor). */
        public float gainAt(double localMs) {
            if(clip!=null)return com.blackice.bc1.editor.ClipAudio.gain(clip,localMs,false);
            double envelope = 1;
            if (fadeInMs > 0) envelope = Math.min(envelope, localMs / fadeInMs);
            if (fadeOutMs > 0) envelope = Math.min(envelope, (durationMs - localMs) / fadeOutMs);
            return (float) (volume * Math.max(0, Math.min(1, envelope)));
        }
    }

    public final List<Segment> segments;
    public final long durationMs;

    private AudioPlan(List<Segment> segments, long durationMs) { this.segments = segments; this.durationMs = durationMs; }

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
            Segment segment=new Segment(c.uri,p.startMs,c.durationMs(),c.inMs,c.speed,volume,c.fadeInMs,c.fadeOutMs,false);segment.clip=c.copy();list.add(segment);
        }
        EditProject.Music m = project.music;
        if (m != null && m.startMs < duration && m.volume > 0) {
            long cursor = m.startMs; int count = 0;
            do {
                long length = Math.min(m.outMs - m.inMs, duration - cursor);
                list.add(new Segment(m.uri, cursor, length, m.inMs, 1f, m.volume, 0, 0, true));
                cursor += length;
                if (++count > 2000) throw new IllegalArgumentException("Music segment is too short to loop across this project. Use a longer trim.");
            } while (m.loop && cursor < duration);
        }
        return new AudioPlan(list, duration);
    }

    /** Segments that produce sound inside [fromMs, toMs). */
    public List<Segment> between(long fromMs, long toMs) {
        List<Segment> result = new ArrayList<>();
        for (Segment s : segments) if (s.timelineStartMs < toMs && s.timelineEndMs() > fromMs) result.add(s);
        return result;
    }
}

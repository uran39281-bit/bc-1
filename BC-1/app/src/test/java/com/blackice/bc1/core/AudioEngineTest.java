package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AudioEngineTest {
    /** Constant-value stereo source of a fixed length. */
    static Resampler.Source constant(float value, long frames) {
        return new Resampler.Source() {
            long left = frames;
            @Override public int read(float[] stereo, int max) {
                if (left <= 0) return -1;
                int n = (int) Math.min(max, left);
                for (int i = 0; i < n * 2; i++) stereo[i] = value;
                left -= n; return n;
            }
        };
    }

    /** Ramp source: frame i has value i, so resampled positions are directly visible. */
    static Resampler.Source ramp(long frames) {
        return new Resampler.Source() {
            long next;
            @Override public int read(float[] stereo, int max) {
                if (next >= frames) return -1;
                int n = (int) Math.min(max, frames - next);
                for (int i = 0; i < n; i++) { stereo[i * 2] = next + i; stereo[i * 2 + 1] = -(next + i); }
                next += n; return n;
            }
        };
    }

    @Test public void resamplerInterpolatesAndAppliesSpeed() throws Exception {
        Resampler r = new Resampler(ramp(100_000), 24_000, 48_000, 1.0);
        float[] out = new float[2 * 8];
        assertEquals(8, r.read(out, 8));
        assertEquals(0f, out[0], 1e-6); assertEquals(.5f, out[2], 1e-6); assertEquals(1f, out[4], 1e-6); assertEquals(-1.5f, out[7], 1e-6);

        Resampler fast = new Resampler(ramp(100_000), 48_000, 48_000, 2.0);
        assertEquals(2.0, fast.step(), 1e-9);
        float[] big = new float[2 * 5000];
        int got = fast.read(big, 5000);
        assertEquals(5000, got);
        assertEquals("Reads across refills", 2 * 4999f, big[2 * 4999], 1e-3);
    }

    @Test public void resamplerStopsAtEndOfSource() throws Exception {
        Resampler r = new Resampler(ramp(10), 48_000, 48_000, 1.0);
        float[] out = new float[2 * 100];
        int got = r.read(out, 100);
        assertTrue("Got " + got, got >= 9 && got <= 11);
        assertEquals(0, r.read(out, 100));
    }

    @Test public void stereoConversion() {
        float[] out = new float[4];
        Resampler.toStereo(new float[]{.1f, .2f}, 2, 1, out);
        assertArrayEquals(new float[]{.1f, .1f, .2f, .2f}, out, 1e-6f);
        Resampler.toStereo(new float[]{1, 2, 3, 4, 5, 6}, 2, 3, out);
        assertArrayEquals(new float[]{1, 2, 4, 5}, out, 1e-6f);
    }

    static EditProject project() {
        EditProject p = new EditProject();
        EditProject.Clip a = RenderPlanTest.clip("a", 2000, 0, 0); a.volume = .5f; a.fadeInMs = 1000;
        EditProject.Clip pic = RenderPlanTest.clip("pic", 1000, 0, 0); pic.image = true;
        p.clips.add(a); p.clips.add(pic);
        EditProject.Music m = new EditProject.Music(); m.uri = "file:///m"; m.name = "m"; m.sourceDurationMs = 1000; m.inMs = 200; m.outMs = 1000; m.startMs = 500; m.volume = .25f;
        p.music = m;
        return p;
    }

    @Test public void audioPlanSkipsPicturesAndLoopsMusic() {
        AudioPlan plan = AudioPlan.of(project(), false);
        assertEquals(3000, plan.durationMs);
        List<AudioPlan.Segment> music = new ArrayList<>(), clips = new ArrayList<>();
        for (AudioPlan.Segment s : plan.segments) (s.music ? music : clips).add(s);
        assertEquals("Pictures carry no sound", 1, clips.size());
        // Music: 800 ms loops from 500 → 1300 → 2100 → 2900, last one cut to 100 ms.
        assertEquals(4, music.size());
        assertEquals(500, music.get(0).timelineStartMs); assertEquals(800, music.get(0).durationMs);
        assertEquals(2900, music.get(3).timelineStartMs); assertEquals(100, music.get(3).durationMs);
        for (AudioPlan.Segment s : music) assertEquals(200, s.sourceInMs);
        assertEquals(1, plan.between(0, 400).size());
    }

    @Test public void mutedAudioLaneIsSilentButHiddenPictureKeepsSound() {
        EditProject p = project(); p.music = null;
        EditProject.Clip voice = RenderPlanTest.clip("voice", 1000, 4, 0); voice.audioOnly = true; p.clips.add(voice);
        p.tracks[0].hidden = true;
        assertEquals(2, AudioPlan.of(p, false).segments.size());
        p.tracks[4].muted = true;
        assertEquals(1, AudioPlan.of(p, false).segments.size());
    }

    @Test public void gainEnvelope() {
        AudioPlan.Segment s = AudioPlan.of(project(), false).segments.get(0);
        assertEquals(0f, s.gainAt(0), 1e-6);
        assertEquals(.25f, s.gainAt(500), 1e-6);
        assertEquals(.5f, s.gainAt(1500), 1e-6);
    }

    @Test public void mixerSumsTimedSegmentsAndClamps() throws Exception {
        EditProject p = project();
        p.clips.get(0).fadeInMs = 0; p.clips.get(0).volume = 1f; p.music.volume = 1f;
        AudioPlan plan = AudioPlan.of(p, false);
        int rate = 1000; // 1 frame per millisecond keeps the arithmetic readable
        int[] opened = {0};
        try (AudioMixer mixer = new AudioMixer(plan, s -> { opened[0]++; return new AudioMixer.Opened(constant(s.music ? .75f : .5f, 10_000), rate, null); }, rate)) {
            assertEquals(3000, mixer.totalFrames());
            float[] out = new float[2 * 3000];
            int total = 0, n;
            float[] block = new float[2 * 256];
            while ((n = mixer.render(block, 256)) > 0) { System.arraycopy(block, 0, out, total * 2, n * 2); total += n; }
            assertEquals(3000, total);
            assertEquals("Clip alone", .5f, out[2 * 100], 1e-5);
            assertEquals("Clip + music clamps at 1", 1f, out[2 * 600], 1e-5);
            assertEquals("Music alone after the clip", .75f, out[2 * 2500], 1e-5);
            assertEquals("Silent gap before the music? none: picture time has music", .75f, out[2 * 2050], 1e-5);
            assertTrue(mixer.done());
            assertEquals("Each segment opened once", plan.segments.size(), opened[0]);
        }
    }

    @Test public void silentSourcesAndShortMediaDoNotStall() throws Exception {
        AudioPlan plan = AudioPlan.of(project(), false);
        try (AudioMixer mixer = new AudioMixer(plan, s -> s.music ? null : new AudioMixer.Opened(constant(.5f, 10), 1000, null), 1000)) {
            float[] block = new float[2 * 4096];
            assertEquals(3000, mixer.render(block, 4096));
            assertEquals(0f, block[2 * 2500], 0);
            assertEquals(0, mixer.render(block, 4096));
        }
    }
}

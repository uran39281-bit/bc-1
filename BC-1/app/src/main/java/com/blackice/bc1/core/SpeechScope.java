package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;

/** Builds an unambiguous recognition input for one clip, including its timeline offset. */
public final class SpeechScope {
    public final AudioPlan plan;
    public final long fromMs, toMs;
    private SpeechScope(AudioPlan plan, long from, long to) { this.plan = plan; fromMs = from; toMs = to; }
    public static SpeechScope of(EditProject project, String clipId) {
        if (clipId == null) return new SpeechScope(AudioPlan.of(project, false).filter(s -> !s.music), 0, project.durationMs());
        for (EditProject.Placement placement : project.placements()) if (placement.clip.id.equals(clipId)) {
            if (placement.clip.image) throw new IllegalArgumentException("The selected picture has no sound.");
            EditProject single = new EditProject();
            EditProject.Clip copy = placement.clip.copy();
            // An independent lane retains its timeline position; track zero would rebase to zero.
            copy.track = EditProject.AUDIO_FIRST; copy.startMs = placement.startMs;
            if (EditProject.audioTrack(placement.clip.track))
                single.tracks[copy.track] = project.tracks[placement.clip.track].copy();
            single.clips.add(copy);
            return new SpeechScope(AudioPlan.of(single, false), placement.startMs, placement.endMs);
        }
        throw new IllegalArgumentException("The selected clip is no longer in the project.");
    }
}

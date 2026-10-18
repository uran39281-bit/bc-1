package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.LayerOps;
import com.blackice.bc1.editor.Motion;
import com.blackice.bc1.editor.TransformMath;
import java.util.ArrayList;
import java.util.List;

/**
 * BC Core frame graph: what to draw at a timeline instant, bottom to top, with every shader parameter
 * resolved. Pure Java, so any backend (Android GLES, desktop OpenGL) renders identical frames.
 *
 * Stacking matches the shipping Media3 graph: the main lane alternates between two streams (odd ordinals
 * below even ones, so transition masks apply to the upper stream), then visual layers 1, 2, 3 above,
 * then text / shape / image overlays above everything in list order.
 */
public final class RenderPlan {
    /** One visual layer instance at one instant. */
    public static final class LayerDraw {
        public final EditProject.Clip clip;
        public final int placementIndex, track;
        public final long startMs, endMs, sourceTimeMs;
        public final TransformMath.Transform transform;
        public final int effect, transition, role;
        public final float intensity, opacity, progress;
        /** Upper of the two alternating main-lane streams (transition masks cut its alpha). */
        public final boolean top;

        LayerDraw(EditProject.Placement p, long sourceTimeMs, Transition t, boolean top) {
            EditProject.Clip c = p.clip;
            clip = c; placementIndex = p.index; track = c.track; startMs = p.startMs; endMs = p.endMs;
            this.sourceTimeMs = sourceTimeMs; this.top = top;
            transform = TransformMath.of(c, sourceTimeMs);
            effect = c.effect;
            intensity = c.motion.value(Motion.INTENSITY, sourceTimeMs, c.intensity);
            opacity = c.motion.value(Motion.OPACITY, sourceTimeMs, c.opacity);
            transition = t.type; role = t.role; progress = t.progress;
        }
    }

    /** Transition shader state: type −1 none; role 0 none, 1 incoming, 2 outgoing; progress eased. */
    public static final class Transition {
        public static final Transition NONE = new Transition(-1, 0, 0);
        public final int type, role;
        public final float progress;
        public Transition(int type, int role, float progress) { this.type = type; this.role = role; this.progress = progress; }
    }

    private final EditProject project;
    private final boolean preview;
    private final List<EditProject.Placement> placements;
    /** Placement index → previous main-lane clip (null when none or not on the main lane). */
    private final EditProject.Clip[] previous;
    /** Placement index → main-lane ordinal (0 for layer clips). */
    private final int[] ordinal;
    /** Visual placements in drawing order, bottom first. */
    private final List<EditProject.Placement> drawOrder = new ArrayList<>();

    public RenderPlan(EditProject project, boolean preview) {
        project.validate();
        this.project = project; this.preview = preview;
        placements = project.placements();
        previous = new EditProject.Clip[placements.size()];
        ordinal = new int[placements.size()];
        EditProject.Clip last = null; int count = 0;
        List<EditProject.Placement> even = new ArrayList<>(), odd = new ArrayList<>();
        for (EditProject.Placement p : placements) {
            if (p.clip.track != 0) continue;
            previous[p.index] = last; ordinal[p.index] = count;
            (count % 2 == 0 ? even : odd).add(p);
            last = p.clip; count++;
        }
        drawOrder.addAll(odd);
        drawOrder.addAll(even);
        for (int layer = 1; layer <= 3; layer++)
            for (EditProject.Placement p : placements) if (p.clip.track == layer) drawOrder.add(p);
        if (drawOrder.isEmpty()) throw new IllegalArgumentException("Keep at least one video or image layer in the project.");
    }

    public EditProject project() { return project; }
    public List<EditProject.Placement> placements() { return placements; }

    /** Visible layers at a timeline instant, bottom to top. */
    public List<LayerDraw> layersAt(long timeMs) {
        List<LayerDraw> result = new ArrayList<>();
        for (EditProject.Placement p : drawOrder) {
            if (timeMs < p.startMs || timeMs >= p.endMs) continue;
            if (!LayerOps.renders(project, p.clip, preview)) continue;
            boolean top = p.clip.track != 0 || ordinal[p.index] % 2 == 0;
            result.add(new LayerDraw(p, sourceTime(p, timeMs), transition(p, previous[p.index], timeMs), top));
        }
        return result;
    }

    /** Overlays (text, shapes, images) visible at an instant, bottom to top. */
    public List<EditProject.Overlay> overlaysAt(long timeMs) {
        List<EditProject.Overlay> result = new ArrayList<>();
        for (EditProject.Overlay o : project.overlays)
            if (timeMs >= o.startMs && timeMs < o.endMs && LayerOps.renders(project, o, preview)) result.add(o);
        return result;
    }

    /** Visual placements whose decoders are needed in [fromMs, toMs). */
    public List<EditProject.Placement> visualPlacementsBetween(long fromMs, long toMs) {
        List<EditProject.Placement> result = new ArrayList<>();
        for (EditProject.Placement p : drawOrder)
            if (p.startMs < toMs && p.endMs > fromMs && LayerOps.renders(project, p.clip, preview)) result.add(p);
        return result;
    }

    /** Source media time shown by a placement at a timeline instant (speed applied, never before the in-point). */
    public static long sourceTime(EditProject.Placement p, long timeMs) {
        EditProject.Clip c = p.clip;
        return Math.round(Math.max(0, timeMs - p.startMs) * c.speed) + c.inMs;
    }

    /** Transition state for a main-lane placement; identical to the rules the Media3 ClipEffect applies. */
    public static Transition transition(EditProject.Placement p, EditProject.Clip previous, long timeMs) {
        EditProject.Clip c = p.clip;
        int type = -1, role = 0, easing = c.easing;
        float progress = 0;
        if (p.incomingMs > 0 && timeMs < p.startMs + p.incomingMs && previous != null) {
            type = previous.transition; role = 1; easing = previous.easing;
            progress = (float) (timeMs - p.startMs) / p.incomingMs;
        } else if (p.outgoingMs > 0 && timeMs >= p.endMs - p.outgoingMs) {
            type = c.transition; role = 2;
            progress = (float) (timeMs - (p.endMs - p.outgoingMs)) / p.outgoingMs;
        }
        progress = Math.max(0, Math.min(1, progress));
        if (easing == 1) progress = progress * progress * (3 - 2 * progress);
        return role == 0 ? Transition.NONE : new Transition(type, role, progress);
    }
}

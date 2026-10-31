package com.blackice.bc1;

import android.app.AlertDialog;
import com.blackice.bc1.core.BlendMode;
import com.blackice.bc1.core.EffectCatalog;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.menu.BlendChoices;
import com.blackice.bc1.menu.CubicBezier;
import com.blackice.bc1.menu.MenuScreenFactory;
import com.blackice.bc1.menu.MenuScreenFactory.Blend;
import com.blackice.bc1.menu.MenuScreenFactory.KeyVisual;
import com.blackice.bc1.menu.MenuScreenFactory.Property;
import java.util.List;

/**
 * Connects the five menus to the real editor: the project model, evaluated animation, live-adjust undo,
 * key selection and the shared renderer. Reads never mutate; every write goes through the same
 * begin / preview / commit-or-cancel path the old controls used, so one gesture is one undo step.
 */
final class MenuHost implements MenuScreenFactory.Host {
    private final EditorActivity a;
    private final MenuPanels owner;
    private String label = "Edit";
    /** Identity of the interval a graph gesture started on; a gesture never follows a changed selection. */
    private String tokenProject, tokenLayer, tokenStart, tokenEnd;
    private int tokenTrack = -1;

    MenuHost(EditorActivity activity, MenuPanels panels) { a = activity; owner = panels; }

    // ---- Targets and ids -------------------------------------------------------------------------
    private EditProject.Clip clip() { return a.clip(); }
    private EditProject.Overlay overlay() { return a.overlay(); }
    private boolean present() { return a.multiCount() <= 1 && (clip() != null || overlay() != null); }
    private Motion motion() { return a.selectedMotion(); }
    private long time() { return a.panels.motionTime(); }
    private long tolerance() { return a.panels.tolerance(); }
    private float neutral() { return clip() != null ? 1f : new EditProject.Overlay().scale; }
    private boolean visualClip() { return clip() != null && clip().track < EditProject.AUDIO_FIRST && !clip().audioOnly; }

    static int id(Property p) {
        switch (p) {
            case X: return Motion.X;
            case Y: return Motion.Y;
            case ROTATION: return Motion.ROTATION;
            case WIDTH: return Motion.SCALE;
            case HEIGHT: return Motion.HEIGHT;
            case OPACITY: return Motion.OPACITY;
            default: return Motion.INTENSITY;
        }
    }

    /** The key track that drives a property. Legacy uniform scale animates height through the Width track. */
    private int track(Property p) {
        int id = id(p);
        if (id == Motion.HEIGHT) {
            boolean legacy = clip() != null ? clip().heightScale < 0 : overlay() != null && overlay().heightScale < 0;
            if (legacy) return Motion.SCALE;
        }
        return id;
    }

    static String title(Property p) {
        switch (p) {
            case X: return "X";
            case Y: return "Y";
            case ROTATION: return "Rotation";
            case WIDTH: return "Width";
            case HEIGHT: return "Height";
            case OPACITY: return "Opacity";
            default: return "Effect strength";
        }
    }

    /** Properties whose keys are navigated together (X and Y move as one position). */
    private int[] group(Property p) {
        if (p == Property.X || p == Property.Y) return new int[]{Motion.X, Motion.Y};
        if (p == Property.WIDTH || p == Property.HEIGHT) return new int[]{Motion.SCALE, Motion.HEIGHT};
        return new int[]{id(p)};
    }

    private static Property property(int id, Property fallback) {
        switch (id) {
            case Motion.X: return Property.X;
            case Motion.Y: return Property.Y;
            case Motion.SCALE: return Property.WIDTH;
            case Motion.HEIGHT: return Property.HEIGHT;
            default: return fallback;
        }
    }

    // ---- Reads (side-effect free) ----------------------------------------------------------------
    @Override public double value(Property p) {
        if (!present()) return 0;
        TransformMath.Transform t = a.transformPanel.current();
        int[] size = a.exportSize();
        switch (p) {
            case X: return t.x * size[0] / 2d;
            case Y: return t.y * size[1] / 2d;
            case ROTATION: return 0.0 - t.rotation;
            case WIDTH: return t.scale * 100d / neutral();
            case HEIGHT: return t.scale * t.aspect * 100d / neutral();
            case OPACITY: return motion().value(Motion.OPACITY, time(), clip() != null ? clip().opacity : overlay().opacity) * 100d;
            default: return clip() == null ? 0 : motion().value(Motion.INTENSITY, time(), clip().intensity) * 100d;
        }
    }

    @Override public double minimum(Property p) {
        int[] size = a.exportSize();
        switch (p) {
            case X: return -100000d * size[0] / 2d;
            case Y: return -100000d * size[1] / 2d;
            case ROTATION: return -360000d;
            case WIDTH: return (clip() != null ? .05d : .1d) * 100d / neutral();
            case HEIGHT: return .0025d * 100d / neutral();
            default: return 0;
        }
    }

    @Override public double maximum(Property p) {
        int[] size = a.exportSize();
        switch (p) {
            case X: return 100000d * size[0] / 2d;
            case Y: return 100000d * size[1] / 2d;
            case ROTATION: return 360000d;
            case WIDTH: return Motion.MAX_SCALE * 100d / neutral();
            case HEIGHT: return Motion.MAX_HEIGHT * 100d / neutral();
            default: return 100;
        }
    }

    @Override public double step(Property p) { return 1; }

    /** Quiet: no toasts, so it can run on every refresh. */
    private boolean unlocked() {
        return !a.loading && !a.importing && !a.captionApplying && !a.uiLocked && present() && !a.selectionLocked();
    }

    @Override public boolean editable(Property p) {
        if (!unlocked()) return false;
        if (p == Property.EFFECT_STRENGTH) return hasSelectedEffect();
        if (clip() != null && clip().track >= EditProject.AUDIO_FIRST) return false;
        return !(clip() != null && clip().audioOnly);
    }

    @Override public KeyVisual keyVisual(Property p) {
        if (!present()) return KeyVisual.STATIC;
        int state = Keyframes.state(motion(), track(p), time(), tolerance());
        return state == Keyframes.ON_KEY ? KeyVisual.CURRENT_KEY : state == Keyframes.ANIMATED ? KeyVisual.ANIMATED : KeyVisual.STATIC;
    }

    // ---- Key navigation --------------------------------------------------------------------------
    private long timelineTime(Motion.Key key) {
        EditProject.Clip c = clip();
        if (c != null) return a.project.placements().get(a.selected).startMs + Math.round((key.timeMs - c.inMs) / (double) c.speed);
        return overlay().startMs + key.timeMs;
    }

    /** Timeline time of the nearest key before/after the playhead on this property's tracks; -1 when none. */
    private long adjacent(Property p, boolean next) {
        if (!present()) return -1;
        EditProject.Clip c = clip();
        EditProject.Overlay o = overlay();
        long now = a.project.playheadMs, best = -1, here = time(), tol = tolerance();
        for (int id : group(p)) {
            for (Motion.Key k : motion().curves.get(id)) {
                if (Math.abs(k.timeMs - here) <= tol) continue;
                if (c != null && (k.timeMs < c.inMs || k.timeMs > c.outMs)) continue;
                if (o != null && (k.timeMs < 0 || k.timeMs > o.endMs - o.startMs)) continue;
                long at = timelineTime(k);
                if (at < 0 || at > a.project.durationMs()) continue;
                if (next ? at > now && (best < 0 || at < best) : at < now && (best < 0 || at > best)) best = at;
            }
        }
        return best;
    }

    @Override public boolean canPrevious(Property p) { return adjacent(p, false) >= 0; }
    @Override public boolean canNext(Property p) { return adjacent(p, true) >= 0; }
    @Override public void previousKey(Property p) { go(p, false); }
    @Override public void nextKey(Property p) { go(p, true); }

    private void go(Property p, boolean next) {
        a.pausePlayback();
        long target = adjacent(p, next);
        if (target < 0) return;
        a.seek(target);
        long at = time();
        Property show = p;
        boolean selected = false;
        for (int id : group(p)) {
            if (Keyframes.keyAt(motion(), id, at, tolerance()) != null) {
                a.easingControls.select(id, at);
                if (!selected) { show = property(id, p); selected = true; }
            }
        }
        owner.showGraph(show);
        a.renderInspector();
    }

    @Override public void toggleKey(Property p) {
        if (!present()) return;
        final int track = track(p);
        a.transformPanel.commit(title(p) + " keyframe", () -> {
            if (track == Motion.SCALE || track == Motion.HEIGHT) a.transformPanel.separate();
            Motion m = motion();
            long t = time(), tol = tolerance();
            float v = (float) internalValue(p);
            Motion.Key key = Keyframes.keyAt(m, track, t, tol);
            if (key != null) {
                m.curves.get(track).remove(key);
                if (m.curves.get(track).isEmpty()) a.setStatic(track, v);
            } else m.put(track, new Motion.Key(t, v));
            a.easingControls.select(track, t);
        });
    }

    /** The property's value in the model's own units at the playhead. */
    private double internalValue(Property p) {
        TransformMath.Transform t = a.transformPanel.current();
        switch (p) {
            case X: return t.x;
            case Y: return t.y;
            case ROTATION: return t.rotation;
            case WIDTH: return t.scale;
            case HEIGHT: return t.scale * t.aspect;
            default: return value(p) / 100d;
        }
    }

    // ---- Edit transactions -----------------------------------------------------------------------
    @Override public void beginEdit(String text) {
        label = text;
        a.pausePlayback();
        a.beginLiveAdjust();
        if (a.liveBefore == null) throw new IllegalStateException("Edit unavailable");
        a.transformPanel.beginGesture();
    }

    @Override public void previewValue(Property p, double v) { a.liveAdjust(() -> applyDisplay(p, v)); }

    @Override public void commitEdit() {
        clearToken();
        a.transformPanel.clearGesture();
        a.endLiveAdjust(label, () -> {});
    }

    @Override public void cancelEdit() {
        clearToken();
        a.cancelLiveAdjust();
    }

    private void clearToken() { tokenProject = tokenLayer = tokenStart = tokenEnd = null; tokenTrack = -1; }

    /** Display units (px, degrees, percent) to the model, once. */
    private void applyDisplay(Property p, double v) {
        int[] size = a.exportSize();
        switch (p) {
            case X: a.transformPanel.write(Motion.X, (float) (v * 2d / size[0])); break;
            case Y: a.transformPanel.write(Motion.Y, (float) (v * 2d / size[1])); break;
            case ROTATION: { float r = (float) -v; a.transformPanel.write(Motion.ROTATION, r == 0 ? 0f : r); break; }
            case WIDTH: a.transformPanel.applyInternal(Motion.SCALE, (float) (v * neutral() / 100d)); break;
            case HEIGHT: a.transformPanel.applyInternal(Motion.HEIGHT, (float) (v * neutral() / 100d)); break;
            case OPACITY: setMotionValue(Motion.OPACITY, (float) (v / 100d)); break;
            default: setMotionValue(Motion.INTENSITY, (float) (v / 100d)); break;
        }
    }

    private void setMotionValue(int id, float value) {
        if (!Motion.allowed(id, value)) throw new IllegalArgumentException(Motion.PROPERTIES[id] + " is outside the supported range.");
        if (Keyframes.set(motion(), id, time(), value, tolerance())) a.setStatic(id, value);
    }

    // ---- Discrete commands -----------------------------------------------------------------------
    @Override public void reset(Property p) {
        if (!present()) return;
        a.transformPanel.commit(title(p) + " reset", () -> {
            switch (p) {
                case X: a.transformPanel.write(Motion.X, 0); break;
                case Y: a.transformPanel.write(Motion.Y, overlay() == null ? 0 : new EditProject.Overlay().y); break;
                case ROTATION: a.transformPanel.write(Motion.ROTATION, 0); break;
                case WIDTH: a.transformPanel.applyInternal(Motion.SCALE, neutral()); break;
                case HEIGHT: a.transformPanel.applyInternal(Motion.HEIGHT, neutral()); break;
                case OPACITY: setMotionValue(Motion.OPACITY, 1f); break;
                default: setMotionValue(Motion.INTENSITY, .6f); break;
            }
        });
    }

    @Override public void resetPosition() { a.transformPanel.resetAll(); }
    @Override public boolean linked() { return a.transformPanel.linked; }

    @Override public void setLinked(boolean on) {
        if (on) {
            TransformMath.Transform t = a.transformPanel.current();
            if (!(t.scale > 0 && t.aspect > 0)) { a.tell("Both dimensions must be above zero before linking."); return; }
        }
        a.transformPanel.linked = on;
    }

    @Override public void fitCanvas(boolean fill) { a.transformPanel.frame(fill); }
    @Override public boolean canFrame(boolean fill) { return a.transformPanel.canFrame(); }

    // ---- Curves ----------------------------------------------------------------------------------
    /** The selected key and its successor on one track, or null when no outgoing interval is selected. */
    private final class Interval {
        final Motion.Key start, end;
        final int track, index;
        Interval(Motion.Key start, Motion.Key end, int track, int index) { this.start = start; this.end = end; this.track = track; this.index = index; }
    }

    private int selectedIndex(int track) {
        if (!present()) return -1;
        return CurveEdits.indexOf(motion().curves.get(track), a.easingControls.selected.get(a.easingControls.scope(track)));
    }

    private Interval interval(Property p) {
        if (!present()) return null;
        int track = track(p), index = selectedIndex(track);
        List<Motion.Key> keys = motion().curves.get(track);
        if (!CurveEdits.hasNext(keys, index)) return null;
        return new Interval(keys.get(index), keys.get(index + 1), track, index);
    }

    private String layerId() { return clip() != null ? clip().id : overlay() != null ? overlay().id : ""; }

    @Override public CubicBezier curve(Property p) {
        Interval iv = interval(p);
        return iv == null ? CubicBezier.LINEAR : CurveEdits.seed(iv.start);
    }

    @Override public double curveProgress(Property p, double u) {
        Interval iv = interval(p);
        return iv == null ? Math.max(0, Math.min(1, u)) : CurveEdits.progress(iv.start, u);
    }

    @Override public boolean hasInterval(Property p) { return interval(p) != null; }

    @Override public String intervalLabel(Property p) {
        if (!present()) return "Select a keyframe";
        int track = track(p), index = selectedIndex(track);
        List<Motion.Key> keys = motion().curves.get(track);
        if (keys.isEmpty()) return "No next keyframe";
        if (index < 0) return "Select a keyframe";
        if (!CurveEdits.hasNext(keys, index)) return "No next keyframe";
        String text = title(p) + " · Key " + (index + 1) + " → Key " + (index + 2);
        if (CurveEdits.handlesApproximate(keys.get(index))) text += " · Ease In/Out (handles approximate until edited)";
        return text;
    }

    @Override public void selectInterval(Property p) {
        if (!present()) return;
        final int track = track(p);
        final List<Motion.Key> keys = motion().curves.get(track);
        if (keys.isEmpty()) { a.tell("Add a keyframe to " + title(p) + " first."); return; }
        String[] names = new String[keys.size()];
        for (int i = 0; i < names.length; i++) names[i] = "Key " + (i + 1) + " · " + EditorActivity.seconds(keys.get(i).timeMs) + " s";
        new AlertDialog.Builder(a).setTitle(title(p) + " keyframe")
                .setItems(names, (d, i) -> {
                    a.easingControls.selected.put(a.easingControls.scope(track), keys.get(i).id);
                    owner.showGraph(p);
                    a.renderInspector();
                }).setNegativeButton("Cancel", null).show();
    }

    @Override public void beginCurveEdit(Property p, String text) {
        Interval iv = interval(p);
        if (iv == null) throw new IllegalStateException("No keyframe interval is selected");
        beginEdit(text);
        tokenProject = a.project.id;
        tokenLayer = layerId();
        tokenTrack = iv.track;
        tokenStart = iv.start.id;
        tokenEnd = iv.end.id;
    }

    @Override public void previewCurve(Property p, CubicBezier curve) {
        Interval iv = interval(p);
        if (iv == null || tokenStart == null || !a.project.id.equals(tokenProject) || !layerId().equals(tokenLayer)
                || iv.track != tokenTrack || !iv.start.id.equals(tokenStart) || !iv.end.id.equals(tokenEnd))
            throw new IllegalStateException("The selected keyframe interval changed");
        a.liveAdjust(() -> CurveEdits.write(iv.start, curve));
    }

    // ---- Blending --------------------------------------------------------------------------------
    @Override public Blend blend() {
        if (clip() != null) return BlendChoices.fromClipMode(BlendMode.of(clip().blend));
        if (overlay() != null) return BlendChoices.fromOverlayIndex(overlay().blend);
        return Blend.NORMAL;
    }

    @Override public boolean canSelectBlend(Blend mode) { return unlocked() && !(clip() != null && !visualClip()); }

    @Override public void selectBlend(Blend mode) {
        if (!present()) return;
        a.transformPanel.commit("Blend mode updated", () -> {
            if (clip() != null) clip().blend = BlendChoices.clipMode(mode).ordinal();
            else overlay().blend = BlendChoices.overlayIndex(mode);
        });
    }

    @Override public boolean hasMoreBlends() { return clip() != null; }
    @Override public String[] moreBlendLabels() { return BlendMode.labels(); }
    @Override public int moreBlendSelected() { return clip() != null && blend() == null ? clip().blend : -1; }

    @Override public void selectMoreBlend(int index) {
        if (clip() == null || !BlendMode.valid(index)) return;
        a.transformPanel.commit("Blend mode updated", () -> clip().blend = index);
    }

    // ---- Effects ---------------------------------------------------------------------------------
    @Override public boolean hasSelectedEffect() { return present() && visualClip() && clip().effect != 0; }
    @Override public String effectTitle() { return hasSelectedEffect() ? EffectCatalog.name(clip().effect) : ""; }
    /** Stays available for any unlocked selection; the catalog itself explains when a layer cannot take effects. */
    @Override public boolean canAddEffects() { return unlocked(); }
    @Override public void addEffects() { a.effectCatalog.open(); }

    /** Removes only the effect and its strength animation; one undo restores both exactly. */
    @Override public void removeSelectedEffect() {
        if (!hasSelectedEffect()) return;
        a.transformPanel.commit("Effect removed", () -> {
            EditProject.Clip c = clip();
            c.effect = 0;
            c.intensity = .6f;
            c.motion.curves.get(Motion.INTENSITY).clear();
            a.easingControls.selected.remove(a.easingControls.scope(Motion.INTENSITY));
        });
    }

    // ---- Navigation ------------------------------------------------------------------------------
    @Override public void back() { a.panels.back(); }
    @Override public void settings() { a.projectSettingsDialog(); }
    @Override public void export() { a.exportMenu(); }
    @Override public void reportInputError(String message) { a.tell(message); }

    @Override public void menuChanged(MenuScreenFactory.Menu menu) {
        switch (menu) {
            case POSITION: a.transformPanel.tab = 0; break;
            case ROTATION: a.transformPanel.tab = 1; break;
            case SCALE: a.transformPanel.tab = 2; break;
            default: break;
        }
    }
}

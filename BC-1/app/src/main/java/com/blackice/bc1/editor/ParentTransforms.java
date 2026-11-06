package com.blackice.bc1.editor;

import java.util.*;

/**
 * Joint Movement: spatial parent/child relationships between visual layers.
 *
 * <p>Every affine here maps frame units (y up, the frame spans [-A/2, A/2] × [-1/2, 1/2]) with the same
 * convention as {@link TransformMath}: p' = {a b; c d}·p + (tx, ty). A child's world pose is
 * {@code World(child,t) = World(parent,t) · Attach · Local(child,t)} where Local is the child's own authored
 * transform (keyframes included), Attach is a constant offset chosen when the link is made so the child does
 * not jump, and World(parent) is evaluated at the same project time (held at its first/last instant outside
 * its own time range). Only position, rotation, scale, skew and flips are inherited: never opacity, effects,
 * crop, volume, speed, reverse state, visibility or timing.
 *
 * <p>Attach is stored as {a,b,c,d,txNorm,tyNorm} with the translation in the project's ±1 position units,
 * the same units as a layer's x/y, so it keeps its meaning if the canvas aspect changes.
 *
 * <p>Pure Java. Layers without a link are untouched: {@link #effective} returns exactly
 * {@code TransformMath.of(...)} for them.
 */
public final class ParentTransforms {
    public static final float[] IDENTITY = {1, 0, 0, 1, 0, 0};
    private static final int MAX_DEPTH = 32;

    private final EditProject project;
    private final double aspect;
    private final List<EditProject.Placement> placements;
    private final Map<String, Object> layers = new HashMap<>();
    private final Map<Object, EditProject.Placement> placementOf = new IdentityHashMap<>();

    public ParentTransforms(EditProject project) {
        this.project = project;
        this.aspect = project.canvasAspect();
        this.placements = project.placements();
        for (EditProject.Placement p : placements) placementOf.put(p.clip, p);
        for (EditProject.Clip c : project.clips) if (!EditProject.audioTrack(c.track)) layers.put(c.id, c);
        for (EditProject.Overlay o : project.overlays) layers.put(o.id, o);
    }

    // ---- Lookup ---------------------------------------------------------------------------------------

    public Object find(String id) { return id == null ? null : layers.get(id); }
    public static String idOf(Object layer) { return layer instanceof EditProject.Clip ? ((EditProject.Clip) layer).id : ((EditProject.Overlay) layer).id; }
    public static String parentOf(Object layer) { return layer instanceof EditProject.Clip ? ((EditProject.Clip) layer).parentId : ((EditProject.Overlay) layer).parentId; }
    public static float[] attachOf(Object layer) { return layer instanceof EditProject.Clip ? ((EditProject.Clip) layer).attach : ((EditProject.Overlay) layer).attach; }
    private static void setLink(Object layer, String parent, float[] attach) {
        if (layer instanceof EditProject.Clip) { ((EditProject.Clip) layer).parentId = parent; ((EditProject.Clip) layer).attach = attach; }
        else { ((EditProject.Overlay) layer).parentId = parent; ((EditProject.Overlay) layer).attach = attach; }
    }

    /** True when the layer has a parent or a retained attachment offset, so its pose is not just its own transform. */
    public static boolean linked(Object layer) { return parentOf(layer) != null || attachOf(layer) != null; }

    /** Layers whose parent is {@code id}. */
    public List<Object> childrenOf(String id) {
        List<Object> out = new ArrayList<>();
        for (Object layer : layers.values()) if (id.equals(parentOf(layer))) out.add(layer);
        return out;
    }

    /** The layer and everything below it in the tree (never loops, even on corrupted data). */
    public Set<String> descendantsOf(String id) {
        Set<String> out = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(); pending.add(id);
        while (!pending.isEmpty()) {
            String next = pending.poll();
            if (!out.add(next)) continue;
            for (Object child : childrenOf(next)) pending.add(idOf(child));
        }
        return out;
    }

    /** Layers that may become the parent of {@code childId}: visual layers, never itself or one of its descendants. */
    public List<Object> parentCandidates(String childId) {
        Set<String> blocked = descendantsOf(childId);
        List<Object> out = new ArrayList<>();
        for (EditProject.Clip c : project.clips) if (!EditProject.audioTrack(c.track) && !blocked.contains(c.id)) out.add(c);
        for (EditProject.Overlay o : project.overlays) if (o.visualizerClipId == null && !blocked.contains(o.id)) out.add(o);
        return out;
    }

    // ---- Evaluation -----------------------------------------------------------------------------------

    /** Own authored transform at a project time (clip motion runs in source time, overlay motion in local time). */
    public TransformMath.Transform local(Object layer, long projectTime) {
        if (layer instanceof EditProject.Clip) {
            EditProject.Clip c = (EditProject.Clip) layer;
            EditProject.Placement p = placementOf.get(c);
            if (p == null) return TransformMath.of(c, c.inMs);
            long t = Math.max(p.startMs, Math.min(Math.max(p.startMs, p.endMs - 1), projectTime));
            return TransformMath.of(c, Math.round(Math.max(0, t - p.startMs) * (double) c.speed) + c.inMs);
        }
        EditProject.Overlay o = (EditProject.Overlay) layer;
        long t = Math.max(o.startMs, Math.min(Math.max(o.startMs, o.endMs - 1), projectTime));
        return TransformMath.of(o, Math.max(0, t - o.startMs));
    }

    /** The pose to draw: the layer's own transform, or its world pose when it has a parent or retained offset. */
    public TransformMath.Transform effective(Object layer, long projectTime) {
        TransformMath.Transform own = local(layer, projectTime);
        if (!linked(layer)) return own;
        double[] world = world(layer, projectTime, new HashSet<>(), 0);
        if (world == null) return own;
        TransformMath.Transform out = decompose(world, own, aspect);
        return out == null ? own : out;
    }

    /** World affine of a layer, or null when the chain is broken (missing parent, cycle, too deep). */
    double[] world(Object layer, long t, Set<String> seen, int depth) {
        if (depth > MAX_DEPTH || !seen.add(idOf(layer))) return null;
        double[] own = affine(local(layer, t), aspect);
        float[] attach = attachOf(layer);
        double[] offset = attach == null ? identity() : fromAttach(attach, aspect);
        String parent = parentOf(layer);
        double[] base = identity();
        if (parent != null) {
            Object p = find(parent);
            if (p == null) return null;
            base = world(p, t, seen, depth + 1);
            if (base == null) return null;
        }
        return multiply(base, multiply(offset, own));
    }

    // ---- Commands (callers wrap these in one undoable edit) -----------------------------------------------

    /**
     * Makes {@code parentId} the parent of {@code layer}, keeping the child's current world pose at {@code t0}.
     * Throws IllegalArgumentException (with nothing changed) for self-parenting, cycles, missing or non-visual
     * parents, and transforms that cannot be inverted or expressed.
     */
    public void attach(Object layer, String parentId, long t0) {
        String id = idOf(layer);
        if (parentId == null) throw new IllegalArgumentException("Choose a parent layer.");
        if (parentId.equals(id)) throw new IllegalArgumentException("A layer cannot be its own parent.");
        Object parent = find(parentId);
        if (parent == null) throw new IllegalArgumentException("That parent layer no longer exists.");
        if (descendantsOf(id).contains(parentId)) throw new IllegalArgumentException("That layer is already a child of this one.");
        double[] oldWorld = world(layer, t0, new HashSet<>(), 0);
        double[] parentWorld = world(parent, t0, new HashSet<>(), 0);
        if (oldWorld == null || parentWorld == null) throw new IllegalArgumentException("The layer relationship is damaged.");
        double[] parentInverse = invert(parentWorld), ownInverse = invert(affine(local(layer, t0), aspect));
        if (parentInverse == null || ownInverse == null) throw new IllegalArgumentException("A transform here is flattened to zero size and cannot be a parent.");
        double[] offset = multiply(parentInverse, multiply(oldWorld, ownInverse));
        float[] stored = toAttach(offset, aspect);
        // The new pose must be expressible by the transform fields renderers read.
        if (decompose(multiply(parentWorld, multiply(fromAttach(stored, aspect), affine(local(layer, t0), aspect))), local(layer, t0), aspect) == null)
            throw new IllegalArgumentException("That relationship would skew the layer too far to draw.");
        setLink(layer, parentId, stored);
    }

    /** Removes the parent but keeps the child's pose at {@code t0}; its own authored animation continues unchanged. */
    public void detach(Object layer, long t0) {
        String parent = parentOf(layer);
        if (parent == null) return;
        Object p = find(parent);
        double[] parentWorld = p == null ? null : world(p, t0, new HashSet<>(), 0);
        if (parentWorld == null) { setLink(layer, null, attachOf(layer)); return; }
        float[] old = attachOf(layer);
        double[] offset = multiply(parentWorld, old == null ? identity() : fromAttach(old, aspect));
        setLink(layer, null, isIdentity(offset) ? null : toAttach(offset, aspect));
    }

    /** Detaches every direct child of the given layers (call before deleting them, inside the same edit). */
    public void detachChildrenOf(Collection<String> ids, long t0) {
        for (String id : ids) for (Object child : childrenOf(id)) if (!ids.contains(idOf(child))) detach(child, t0);
    }

    /**
     * After any edit that removed layers (delete, replace, cut…): children whose parent is gone are released with
     * their pose at the playhead preserved, using the pre-edit project to know where they were. Nothing dangles,
     * and no keyframes are baked: the offset keeps the child where it was while its own animation continues.
     */
    public static void releaseOrphans(EditProject now, String beforeJson) {
        List<Object> orphans = new ArrayList<>();
        ParentTransforms pt = new ParentTransforms(now);
        for (Object layer : pt.layers.values()) { String parent = parentOf(layer); if (parent != null && pt.find(parent) == null) orphans.add(layer); }
        if (orphans.isEmpty()) return;
        EditProject before;
        try { before = ProjectStore.decode(beforeJson); } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
        ParentTransforms old = new ParentTransforms(before);
        long t0 = now.playheadMs;
        for (Object layer : orphans) {
            Object was = old.find(idOf(layer));
            double[] worldBefore = was == null ? null : old.world(was, t0, new HashSet<>(), 0);
            double[] ownInverse = invert(affine(pt.local(layer, t0), pt.aspect));
            if (worldBefore == null || ownInverse == null) { setLink(layer, null, null); continue; }
            double[] offset = multiply(worldBefore, ownInverse);
            setLink(layer, null, isIdentity(offset) ? null : toAttach(offset, pt.aspect));
        }
    }

    /** Clears relationships that cannot be honoured (missing parents, cycles, bad offsets); safe on corrupted files. */
    public static void sanitize(EditProject project) {
        ParentTransforms pt = new ParentTransforms(project);
        for (Object layer : pt.layers.values()) {
            float[] attach = attachOf(layer);
            if (attach != null && !validAttach(attach)) { setLink(layer, parentOf(layer), null); }
            String parent = parentOf(layer);
            if (parent == null) continue;
            if (pt.find(parent) == null || parent.equals(idOf(layer)) || pt.reachesItself(layer)) setLink(layer, null, attachOf(layer));
        }
    }

    private boolean reachesItself(Object layer) {
        Set<String> seen = new HashSet<>();
        String cursor = parentOf(layer);
        while (cursor != null) {
            if (cursor.equals(idOf(layer)) || !seen.add(cursor)) return true;
            Object next = find(cursor);
            cursor = next == null ? null : parentOf(next);
        }
        return false;
    }

    /** True for a finite 6-value offset. */
    public static boolean validAttach(float[] a) {
        if (a == null || a.length != 6) return false;
        for (float v : a) if (!Float.isFinite(v) || Math.abs(v) > 1e6f) return false;
        return Math.abs((double) a[0] * a[3] - (double) a[1] * a[2]) > 1e-9;
    }

    /** Throws when a link in the project cannot be honoured (used by EditProject.validate). */
    public static void check(EditProject project) {
        boolean any = false;
        for (EditProject.Clip c : project.clips) any |= c.parentId != null || c.attach != null;
        for (EditProject.Overlay o : project.overlays) any |= o.parentId != null || o.attach != null;
        if (!any) return;
        ParentTransforms pt = new ParentTransforms(project);
        for (Object layer : pt.layers.values()) {
            float[] attach = attachOf(layer);
            if (attach != null && !validAttach(attach)) throw new IllegalArgumentException("A layer relationship has an invalid offset.");
            String parent = parentOf(layer);
            if (parent == null) continue;
            if (parent.equals(idOf(layer))) throw new IllegalArgumentException("A layer cannot be its own parent.");
            if (pt.find(parent) == null) throw new IllegalArgumentException("A layer's parent no longer exists.");
            if (pt.reachesItself(layer)) throw new IllegalArgumentException("Layer parents cannot form a loop.");
        }
    }

    // ---- Affine algebra (frame units) -------------------------------------------------------------------

    private static double[] identity() { return new double[]{1, 0, 0, 1, 0, 0}; }
    private static boolean isIdentity(double[] m) {
        return Math.abs(m[0] - 1) < 1e-6 && Math.abs(m[1]) < 1e-6 && Math.abs(m[2]) < 1e-6 && Math.abs(m[3] - 1) < 1e-6 && Math.abs(m[4]) < 1e-6 && Math.abs(m[5]) < 1e-6;
    }
    /** a·b: apply b first, then a. */
    static double[] multiply(double[] a, double[] b) {
        return new double[]{a[0] * b[0] + a[1] * b[2], a[0] * b[1] + a[1] * b[3], a[2] * b[0] + a[3] * b[2], a[2] * b[1] + a[3] * b[3],
                a[0] * b[4] + a[1] * b[5] + a[4], a[2] * b[4] + a[3] * b[5] + a[5]};
    }
    static double[] invert(double[] m) {
        double det = m[0] * m[3] - m[1] * m[2];
        if (!(Math.abs(det) > 1e-9) || !Double.isFinite(det)) return null;
        double a = m[3] / det, b = -m[1] / det, c = -m[2] / det, d = m[0] / det;
        return new double[]{a, b, c, d, -(a * m[4] + b * m[5]), -(c * m[4] + d * m[5])};
    }
    /** The affine {@link TransformMath#forward} applies for this transform. */
    static double[] affine(TransformMath.Transform t, double aspect) {
        double[] m = TransformMath.matrix(t);
        double ax = t.anchorX * .5 * aspect, ay = t.anchorY * .5, tx = t.x * .5 * aspect, ty = t.y * .5;
        return new double[]{m[0], m[1], m[2], m[3], tx + ax - (m[0] * ax + m[1] * ay), ty + ay - (m[2] * ax + m[3] * ay)};
    }
    private static double[] fromAttach(float[] a, double aspect) { return new double[]{a[0], a[1], a[2], a[3], a[4] * .5 * aspect, a[5] * .5}; }
    private static float[] toAttach(double[] m, double aspect) { return new float[]{(float) m[0], (float) m[1], (float) m[2], (float) m[3], (float) (m[4] / (.5 * aspect)), (float) (m[5] / .5)}; }

    /**
     * Writes an affine back into the transform fields every renderer reads, keeping the layer's own anchor.
     * M = R·S·K with K a pure x-shear, which is a QR factorisation, so any invertible affine fits exactly.
     * Null when the matrix is singular or needs a shear beyond what the fields allow.
     */
    static TransformMath.Transform decompose(double[] w, TransformMath.Transform like, double aspect) {
        double rotation = Math.atan2(w[2], w[0]), cs = Math.cos(rotation), sn = Math.sin(rotation);
        double u00 = cs * w[0] + sn * w[2], u01 = cs * w[1] + sn * w[3], u11 = -sn * w[1] + cs * w[3];
        if (!(u00 > 1e-6) || !(Math.abs(u11) > 1e-9)) return null;
        double kx = u01 / u00, skew = Math.toDegrees(Math.atan(kx));
        if (Math.abs(skew) > 80) return null;
        TransformMath.Transform t = new TransformMath.Transform();
        t.anchorX = like.anchorX; t.anchorY = like.anchorY;
        t.scale = (float) u00; t.aspect = (float) (Math.abs(u11) / u00); t.flipH = false; t.flipV = u11 < 0;
        t.rotation = (float) Math.toDegrees(rotation); t.skewX = (float) skew; t.skewY = 0;
        // Translation T with s = T + a + M(l − a)  ⇒  T = b − a + M·a (frame units).
        double ax = like.anchorX * .5 * aspect, ay = like.anchorY * .5;
        double tx = w[4] - ax + (w[0] * ax + w[1] * ay), ty = w[5] - ay + (w[2] * ax + w[3] * ay);
        t.x = (float) (tx / (.5 * aspect)); t.y = (float) (ty / .5);
        return t;
    }

    /** Inverse of {@link #effective} for one layer: the own (local) transform that yields the wanted world pose. */
    public TransformMath.Transform toLocal(Object layer, TransformMath.Transform wantedWorld, long projectTime) {
        if (!linked(layer)) return wantedWorld;
        double[] base = identity();
        String parent = parentOf(layer);
        if (parent != null) {
            Object p = find(parent);
            double[] w = p == null ? null : world(p, projectTime, new HashSet<>(), 0);
            if (w == null) return wantedWorld;
            base = w;
        }
        float[] attach = attachOf(layer);
        double[] prefix = multiply(base, attach == null ? identity() : fromAttach(attach, aspect));
        double[] inverse = invert(prefix);
        if (inverse == null) return wantedWorld;
        TransformMath.Transform like = local(layer, projectTime);
        TransformMath.Transform out = decompose(multiply(inverse, affine(wantedWorld, aspect)), like, aspect);
        return out == null ? like : out;
    }
}

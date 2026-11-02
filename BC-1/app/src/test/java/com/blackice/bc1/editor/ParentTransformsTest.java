package com.blackice.bc1.editor;

import static org.junit.Assert.*;
import org.junit.Test;

/** Joint Movement: keep-world attach/detach, rotation and unequal scale, cycles and corrupted data. */
public class ParentTransformsTest {
    private static EditProject.Overlay shape() { EditProject.Overlay o = new EditProject.Overlay(); o.endMs = 4000; return o; }
    private static EditProject project(EditProject.Overlay... layers) { EditProject p = new EditProject(); for (EditProject.Overlay o : layers) p.overlays.add(o); return p; }
    private static double[] corner(EditProject p, Object layer, long t, double lx, double ly) {
        return TransformMath.forward(new ParentTransforms(p).effective(layer, t), p.canvasAspect(), lx, ly);
    }

    @Test public void unlinkedLayersAreDrawnExactlyAsBefore() {
        EditProject.Overlay o = shape(); o.x = .3f; o.rotation = 25; o.scale = 1.4f;
        EditProject p = project(o);
        TransformMath.Transform t = new ParentTransforms(p).effective(o, 500);
        assertEquals(.3f, t.x, 0); assertEquals(25f, t.rotation, 0); assertEquals(1.4f, t.scale, 0);
    }

    @Test public void attachingKeepsTheWorldPoseEvenUnderARotatedUnequallyScaledParent() {
        EditProject.Overlay parent = shape(), child = shape();
        parent.x = -.4f; parent.y = .2f; parent.rotation = 40; parent.scale = 1.5f; parent.heightScale = .8f;
        child.x = .5f; child.y = -.3f; child.rotation = 10; child.scale = .6f;
        EditProject p = project(parent, child);
        double[][] before = new double[4][];
        double[][] local = {{-.2, .1}, {.2, .1}, {.2, -.1}, {-.2, -.1}};
        for (int i = 0; i < 4; i++) before[i] = corner(p, child, 1000, local[i][0], local[i][1]);
        new ParentTransforms(p).attach(child, parent.id, 1000);
        assertEquals(parent.id, child.parentId); assertNotNull(child.attach);
        for (int i = 0; i < 4; i++) { double[] after = corner(p, child, 1000, local[i][0], local[i][1]); assertEquals(before[i][0], after[0], 1e-4); assertEquals(before[i][1], after[1], 1e-4); }
    }

    @Test public void theChildFollowsTheParentsLaterMovement() {
        EditProject.Overlay parent = shape(), child = shape();
        child.x = .5f; EditProject p = project(parent, child);
        parent.motion.put(Motion.X, new Motion.Key(0, 0)); parent.motion.put(Motion.X, new Motion.Key(2000, .4f));
        new ParentTransforms(p).attach(child, parent.id, 0);
        double[] start = corner(p, child, 0, 0, 0), later = corner(p, child, 2000, 0, 0);
        assertEquals("Moves by the parent's travel in frame units", .4 * .5 * p.canvasAspect(), later[0] - start[0], 1e-4);
        assertEquals(start[1], later[1], 1e-4);
        assertEquals("The parent itself is untouched", 0f, parent.x, 0);
        parent.rotation = 90;
        TransformMath.Transform turned = new ParentTransforms(p).effective(child, 2000);
        assertEquals("Parent rotation turns the child", 90f, turned.rotation % 360, .01f);
    }

    @Test public void parentTimeIsHeldOutsideItsOwnRange() {
        EditProject.Overlay parent = shape(), child = shape(); parent.startMs = 1000; parent.endMs = 2000; child.endMs = 5000;
        parent.motion.put(Motion.X, new Motion.Key(0, 0)); parent.motion.put(Motion.X, new Motion.Key(1000, .5f));
        EditProject p = project(parent, child); new ParentTransforms(p).attach(child, parent.id, 1000);
        double[] heldAfter = corner(p, child, 4000, 0, 0), atEnd = corner(p, child, 1999, 0, 0);
        assertEquals("Child does not jump or vanish after the parent's last instant", atEnd[0], heldAfter[0], 1e-3);
    }

    @Test public void detachingKeepsThePoseAndLaterParentMotionStops() {
        EditProject.Overlay parent = shape(), child = shape(); child.x = .2f; EditProject p = project(parent, child);
        parent.motion.put(Motion.X, new Motion.Key(0, 0)); parent.motion.put(Motion.X, new Motion.Key(2000, .5f));
        ParentTransforms pt = new ParentTransforms(p); pt.attach(child, parent.id, 0);
        double[] worldAt1000 = corner(p, child, 1000, 0, 0);
        pt.detach(child, 1000);
        assertNull(child.parentId);
        double[] after = corner(p, child, 1000, 0, 0);
        assertEquals(worldAt1000[0], after[0], 1e-4); assertEquals(worldAt1000[1], after[1], 1e-4);
        assertEquals("No more inherited motion", after[0], corner(p, child, 2000, 0, 0)[0], 1e-6);
    }

    @Test public void selfDirectAndIndirectCyclesAreRejectedBeforeAnyChange() {
        EditProject.Overlay a = shape(), b = shape(), c = shape(); EditProject p = project(a, b, c);
        ParentTransforms pt = new ParentTransforms(p);
        try { pt.attach(a, a.id, 0); fail(); } catch (IllegalArgumentException expected) { }
        pt.attach(b, a.id, 0); pt.attach(c, b.id, 0);
        try { pt.attach(a, c.id, 0); fail("indirect cycle"); } catch (IllegalArgumentException expected) { }
        try { pt.attach(a, b.id, 0); fail("direct cycle"); } catch (IllegalArgumentException expected) { }
        assertNull("Nothing was written", a.parentId); assertNull(a.attach);
        assertEquals("Candidates exclude the layer and its descendants", 0, new ParentTransforms(p).parentCandidates(a.id).size() - 0);
        assertEquals(1, new ParentTransforms(p).parentCandidates(c.id).size() - 1);
    }

    @Test public void deletingAParentDetachesChildrenWithoutMovingThem() {
        EditProject.Overlay parent = shape(), child = shape(); parent.rotation = 30; parent.x = .1f; child.x = -.3f;
        EditProject p = project(parent, child); ParentTransforms pt = new ParentTransforms(p); pt.attach(child, parent.id, 500);
        double[] before = corner(p, child, 500, .1, .1);
        pt.detachChildrenOf(java.util.Collections.singleton(parent.id), 500); p.overlays.remove(parent);
        assertNull(child.parentId); p.validate();
        double[] after = corner(p, child, 500, .1, .1);
        assertEquals(before[0], after[0], 1e-4); assertEquals(before[1], after[1], 1e-4);
    }

    @Test public void worldDraggingConvertsBackToTheAuthoredLocalTransform() {
        EditProject.Overlay parent = shape(), child = shape(); parent.rotation = 35; parent.scale = 1.7f; child.x = .3f; child.y = .2f;
        EditProject p = project(parent, child); ParentTransforms pt = new ParentTransforms(p); pt.attach(child, parent.id, 0);
        TransformMath.Transform world = pt.effective(child, 0);
        TransformMath.Transform local = pt.toLocal(child, world, 0);
        assertEquals("Writing the unchanged world pose leaves the local one alone", .3f, local.x, 1e-3f); assertEquals(.2f, local.y, 1e-3f);
        world.x += .2f;
        TransformMath.Transform moved = pt.toLocal(child, world, 0);
        child.x = moved.x; child.y = moved.y; child.rotation = moved.rotation; child.scale = moved.scale;
        assertEquals("The world pose is what the drag asked for", world.x, pt.effective(child, 0).x, 1e-3f);
    }

    @Test public void relationshipsRoundTripThroughSaveAndCorruptedOnesAreCleared() throws Exception {
        EditProject.Overlay parent = shape(), child = shape(); child.x = .25f; EditProject p = project(parent, child);
        new ParentTransforms(p).attach(child, parent.id, 0);
        EditProject back = ProjectStore.decode(ProjectStore.encode(p));
        assertEquals(parent.id, back.overlays.get(1).parentId); assertArrayEquals(child.attach, back.overlays.get(1).attach, 0);
        assertFalse("Unlinked layers write no extra fields", ProjectStore.encode(project(shape())).contains("parentId"));
        String broken = ProjectStore.encode(p).replace(parent.id, "missing-layer-id").replace("\"id\":\"missing-layer-id\"", "\"id\":\"" + parent.id + "\"");
        EditProject repaired = ProjectStore.decode(broken);
        assertNull("A reference to a missing layer is dropped, not crashed on", repaired.overlays.get(1).parentId);
        // A loop in a file is repaired rather than recursed into.
        EditProject.Overlay a = shape(), b = shape(); a.parentId = b.id; b.parentId = a.id;
        EditProject loop = project(a, b); ParentTransforms.sanitize(loop);
        assertTrue(a.parentId == null || b.parentId == null); loop.validate();
    }
}

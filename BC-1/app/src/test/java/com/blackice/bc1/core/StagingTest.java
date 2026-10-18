package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class StagingTest {
    private static String passes(List<int[]> list) {
        StringBuilder b = new StringBuilder();
        for (int[] p : list) b.append('[').append(p[0]).append(',').append(p[1]).append(')');
        return b.toString();
    }

    @Test public void fitsInOnePassWithinBudget() {
        assertEquals("[0,9)", passes(Staging.split(new int[]{1, 1, 1, 1, 0, 0, 0, 0, 0}, 4)));
        assertEquals("Budget below two cannot stage", "[0,9)", passes(Staging.split(new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1}, 1)));
    }

    @Test public void laterPassesReserveOneDecoderForTheIntermediate() {
        int[] nine = {1, 1, 1, 1, 1, 1, 1, 1, 1};
        // Budget 4: first pass 4 groups, then 3 new groups + intermediate per pass.
        assertEquals("[0,4)[4,7)[7,9)", passes(Staging.split(nine, 4)));
        assertEquals("[0,2)[2,3)[3,4)[4,5)[5,6)[6,7)[7,8)[8,9)", passes(Staging.split(nine, 2)));
        assertEquals("Pictures need no decoder", "[0,6)[6,9)", passes(Staging.split(new int[]{1, 0, 1, 0, 1, 1, 1, 1, 1}, 4)));
    }

    @Test public void demandCountsVideoGroupsAndSkipsPictures() {
        EditProject p = RenderPlanTest.mainLane(); // three main clips: both main streams in use
        EditProject.Clip still = RenderPlanTest.clip("still", 1000, 2, 0); still.image = true; p.clips.add(still);
        EditProject.Clip top = RenderPlanTest.clip("top", 1000, EditProject.LAST_LAYER, 0); p.clips.add(top);
        int[] demand = new RenderPlan(p, false).decoderDemand();
        assertEquals(RenderPlan.groupCount(), demand.length);
        assertEquals(1, demand[0]); assertEquals(1, demand[1]);
        assertEquals("Picture layer", 0, demand[RenderPlan.groupOf(2, true)]);
        assertEquals(1, demand[RenderPlan.groupOf(EditProject.LAST_LAYER, true)]);
        p.tracks[EditProject.LAST_LAYER].hidden = true;
        assertEquals("Hidden layers need no decoder", 0, new RenderPlan(p, false).decoderDemand()[RenderPlan.groupOf(EditProject.LAST_LAYER, true)]);
    }

    @Test public void passesPartitionTheLayersExactly() {
        EditProject p = RenderPlanTest.mainLane();
        for (int t = 1; t <= EditProject.LAST_LAYER; t++) p.clips.add(RenderPlanTest.clip("L" + t, 1000, t, 0));
        RenderPlan plan = new RenderPlan(p, false);
        List<RenderPlan.LayerDraw> all = plan.layersAt(100);
        assertEquals("Main + seven layers", 8, all.size());
        int seen = 0;
        for (int[] pass : Staging.split(plan.decoderDemand(), 3)) {
            List<RenderPlan.LayerDraw> part = plan.layersAt(100, pass[0], pass[1]);
            for (RenderPlan.LayerDraw d : part) assertSame("Order preserved across passes", all.get(seen++).clip, d.clip);
        }
        assertEquals(all.size(), seen);
    }
}

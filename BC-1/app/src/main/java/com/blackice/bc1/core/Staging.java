package com.blackice.bc1.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a composition into render passes when it needs more simultaneous video decoders than the
 * device can provide. Pass k renders stacking groups [from, to) over the previous pass's output (an
 * intermediate video), so alpha-over compositing gives exactly the single-pass result. The first pass
 * may use the whole budget; later passes reserve one decoder for reading the intermediate.
 */
public final class Staging {
    private Staging() {}

    /** Group ranges {from, to} per pass, covering every group in order. */
    public static List<int[]> split(int[] demand, int budget) {
        List<int[]> passes = new ArrayList<>();
        int total = 0; for (int d : demand) total += d;
        if (total <= budget || budget < 2) { passes.add(new int[]{0, demand.length}); return passes; }
        int from = 0, used = 0, limit = budget;
        for (int g = 0; g < demand.length; g++) {
            if (used + demand[g] > limit) {
                passes.add(new int[]{from, g});
                from = g; used = 0; limit = budget - 1;
            }
            used += demand[g];
        }
        passes.add(new int[]{from, demand.length});
        return passes;
    }
}

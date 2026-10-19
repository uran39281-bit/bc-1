package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class EffectCatalogTest {
    @Test public void idsAreStableAndEveryEffectHasACategory() {
        assertEquals(48, EffectCatalog.count());
        assertEquals("None", EffectCatalog.name(0)); assertEquals("Ice Grade", EffectCatalog.name(11));
        assertEquals("Box Blur", EffectCatalog.name(12)); assertEquals("Halftone", EffectCatalog.name(47));
        Set<Integer> seen = new HashSet<>();
        for (int cat = 0; cat < EffectCatalog.CATEGORIES.length; cat++) {
            assertFalse(EffectCatalog.CATEGORIES[cat] + " is empty", EffectCatalog.inCategory(cat).isEmpty());
            for (int id : EffectCatalog.inCategory(cat)) assertTrue("listed once", seen.add(id));
        }
        assertEquals("Every effect appears in exactly one category", EffectCatalog.count(), seen.size());
        assertEquals(4, EffectCatalog.category(3)); assertEquals(2, EffectCatalog.category(4)); assertEquals(5, EffectCatalog.category(8));
        assertTrue(EffectCatalog.distortion(29)); assertTrue(EffectCatalog.distortion(37)); assertFalse(EffectCatalog.distortion(38));
        assertTrue(EffectCatalog.centered(17)); assertFalse(EffectCatalog.centered(12));
        assertEquals("Unknown ids fall back", "None", EffectCatalog.name(999));
        // The original twelve keep their names and order (EngineSettings is the v0.3 lab's list).
        for (int i = 0; i < com.blackice.bc1.engine.EngineSettings.EFFECTS.length; i++) assertEquals(com.blackice.bc1.engine.EngineSettings.EFFECTS[i], EffectCatalog.name(i));
    }

    @Test public void projectsAcceptEveryCatalogEffectOnly() {
        EditProject p = RenderPlanTest.mainLane();
        for (int id = 0; id < EffectCatalog.count(); id++) { p.clips.get(0).effect = id; p.validate(); }
        p.clips.get(0).effect = EffectCatalog.count();
        try { p.validate(); fail(); } catch (IllegalArgumentException expected) { }
    }
}

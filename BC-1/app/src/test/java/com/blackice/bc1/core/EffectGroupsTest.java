package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.EffectInstance;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EffectGroupsTest {
    @Test public void thirtyFiveEntriesInFiveCategoriesCoverEveryEffectOnce() {
        assertEquals(35, EffectGroups.all().size());
        int[] perCategory = new int[EffectGroups.CATEGORIES.length];
        Set<Integer> ids = new HashSet<>(); Set<String> names = new HashSet<>();
        for (EffectGroups.Entry e : EffectGroups.all()) {
            perCategory[e.category]++;
            assertTrue("Unique entry name " + e.name, names.add(e.name));
            assertFalse(e.name + " offers a mode for new clips", e.allModes().get(0).legacy);
            for (EffectGroups.Mode m : e.allModes()) assertTrue("Effect " + m.id + " is listed once", ids.add(m.id));
        }
        assertArrayEquals(new int[]{13, 6, 8, 5, 3}, perCategory);
        for (int id = 1; id < EffectCatalog.count(); id++) assertNotNull(EffectCatalog.name(id) + " has an entry", EffectGroups.entry(id));
        assertNull(EffectGroups.entry(0));
    }

    @Test public void combinedEntriesStartNewClipsOnTheCombinedEffect() {
        assertEquals(66, EffectGroups.entry(9).defaultId());   // Exposure -> Light
        assertEquals(67, EffectGroups.entry(22).defaultId());  // Warm -> White Balance
        assertEquals(68, EffectGroups.entry(53).defaultId());  // Vibrance -> Color Intensity
        assertEquals(69, EffectGroups.entry(3).defaultId());   // Green key -> Chroma Key
        assertEquals(7, EffectGroups.entry(16).defaultId());   // Unsharp Mask -> Detail (Sharpen)
        assertEquals(1, EffectGroups.entry(13).defaultId());   // Blur starts on Gaussian
        assertEquals(70, EffectGroups.entry(75).defaultId());  // HSL starts on Reds
        assertEquals(57, EffectGroups.entry(78).defaultId());  // Color Balance keeps Split Tone first
        for (int id : new int[]{66, 67, 68, 69, 70, 77, 78, 80}) {
            assertEquals(1f, EffectCatalog.defaultStrength(id), 0);
            assertEquals(1f, new EffectInstance(id).intensity, 0);
        }
        assertEquals(.6f, new EffectInstance(13).intensity, 0);
    }

    @Test public void legacyModesShowOnlyOnClipsThatUseThem() {
        EffectGroups.Entry light = EffectGroups.entry(66);
        assertEquals(1, light.choices(66).size());
        assertEquals(Arrays.asList("Adjust", "Exposure"), labels(light.choices(9)));
        assertEquals("Light", EffectGroups.title(66));
        assertEquals("Light · Exposure", EffectGroups.title(9));
        assertEquals("Blur · Motion", EffectGroups.title(13));
        assertEquals("Swirl", EffectGroups.title(29));
        assertEquals("Detail · Unsharp Mask", EffectGroups.title(16));
        assertEquals(Arrays.asList("Sharpen", "Clarity"), labels(EffectGroups.entry(7).choices(7)));
        assertEquals("HSL · Greens", EffectGroups.title(73));
        assertEquals("Color Balance · Shadows", EffectGroups.title(78));
        assertEquals(Arrays.asList("Split Tone", "Shadows", "Midtones", "Highlights"), labels(EffectGroups.entry(57).choices(57)));
    }

    @Test public void presetsFitTheirModesControls() {
        assertEquals(Arrays.asList("Warm", "Cool", "Neutral"), presetLabels(EffectGroups.entry(67).presets(67)));
        assertTrue("Presets belong to the combined mode only", EffectGroups.entry(51).presets(51).isEmpty());
        for (EffectGroups.Entry e : EffectGroups.all()) for (EffectGroups.Mode m : e.allModes()) for (EffectGroups.Preset p : e.presets(m.id)) {
            float[] v = p.params();
            assertEquals(e.name + " " + p.label, EffectCatalog.paramCount(p.id), v.length);
            for (float x : v) assertTrue(x >= 0 && x <= 1);
        }
        float[] green = EffectGroups.entry(69).presets(69).get(0).params();
        assertEquals("Green preset is the key's default hue", EffectCatalog.params(69)[0].def, green[0], 1e-6);
    }

    @Test public void searchFindsEntriesByOldNamesModesAndWords() {
        assertEquals(Arrays.asList("Light"), names(EffectGroups.entries(-1, "exposure")));
        assertEquals(Arrays.asList("Chroma Key"), names(EffectGroups.entries(-1, "green screen")));
        assertEquals(Arrays.asList("Chroma Key"), names(EffectGroups.entries(-1, "Chroma Key (Blue)")));
        assertEquals(Arrays.asList("Detail"), names(EffectGroups.entries(-1, "unsharp")));
        assertTrue(names(EffectGroups.entries(-1, "warm")).contains("White Balance"));
        assertTrue(EffectGroups.entries(4, "blur").isEmpty());
        assertEquals(Arrays.asList("Color Balance"), names(EffectGroups.entries(-1, "color wheels")));
        assertTrue(names(EffectGroups.entries(-1, "hsl")).contains("HSL"));
        assertEquals(35, EffectGroups.entries(-1, "  ").size());
    }

    /** Switching mode is a type change on the same instance; the project keeps a valid, saved effect. */
    @Test public void switchingModeKeepsTheInstanceAndDropsOldControls() {
        EditProject.Clip c = new EditProject.Clip(); c.uri = "file:///a.mp4"; c.name = "a"; c.sourceDurationMs = 4000; c.outMs = 4000;
        String id = c.addEffect(13); EffectInstance e = c.effect(id); e.params = new float[]{.25f, .9f}; e.intensity = .8f; c.updateEffect(e);
        e = c.effect(id); e.type = 15; e.params = null; c.updateEffect(e);
        EffectInstance after = c.effect(id);
        assertEquals(15, after.type); assertEquals(.8f, after.intensity, 0);
        assertArrayEquals("Zoom blur starts from its own defaults", new float[]{.5f, .5f, 0f, 0f}, after.resolved(), 0);
        c.validateEffects();
    }

    private static List<String> labels(List<EffectGroups.Mode> modes) { List<String> out = new ArrayList<>(); for (EffectGroups.Mode m : modes) out.add(m.label); return out; }
    private static List<String> presetLabels(List<EffectGroups.Preset> presets) { List<String> out = new ArrayList<>(); for (EffectGroups.Preset p : presets) out.add(p.label); return out; }
    private static List<String> names(List<EffectGroups.Entry> entries) { List<String> out = new ArrayList<>(); for (EffectGroups.Entry e : entries) out.add(e.name); return out; }
}

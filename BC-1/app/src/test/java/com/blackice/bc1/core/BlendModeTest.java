package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlendModeTest {
    static final float[] B = {.75f, .5f, .25f}, S = {.25f, .5f, .75f};

    private static void close(String label, float[] expected, float[] actual) {
        for (int i = 0; i < 3; i++) assertEquals(label + " channel " + i, expected[i], actual[i], 1e-4f);
    }

    @Test public void separableModesMatchTheirDefinitions() {
        close("normal", S, BlendMode.NORMAL.blend(B, S));
        close("darken", new float[]{.25f, .5f, .25f}, BlendMode.DARKEN.blend(B, S));
        close("lighten", new float[]{.75f, .5f, .75f}, BlendMode.LIGHTEN.blend(B, S));
        close("multiply", new float[]{.1875f, .25f, .1875f}, BlendMode.MULTIPLY.blend(B, S));
        close("screen", new float[]{.8125f, .75f, .8125f}, BlendMode.SCREEN.blend(B, S));
        close("difference", new float[]{.5f, 0f, .5f}, BlendMode.DIFFERENCE.blend(B, S));
        close("exclusion", new float[]{.625f, .5f, .625f}, BlendMode.EXCLUSION.blend(B, S));
        close("linear burn", new float[]{0f, 0f, 0f}, BlendMode.LINEAR_BURN.blend(B, S));
        close("linear dodge", new float[]{1f, 1f, 1f}, BlendMode.LINEAR_DODGE.blend(B, S));
        close("subtract", new float[]{.5f, 0f, 0f}, BlendMode.SUBTRACT.blend(B, S));
        close("divide", new float[]{1f, 1f, 1f / 3f}, BlendMode.DIVIDE.blend(B, S));
        close("color dodge", new float[]{1f, 1f, 1f}, BlendMode.COLOR_DODGE.blend(B, S));
        close("color burn", new float[]{0f, 0f, 0f}, BlendMode.COLOR_BURN.blend(B, S));
        close("linear light", new float[]{.25f, .5f, .75f}, BlendMode.LINEAR_LIGHT.blend(B, S));
        close("pin light", new float[]{.5f, .5f, .5f}, BlendMode.PIN_LIGHT.blend(B, S));
        close("hard mix", new float[]{1f, 1f, 1f}, BlendMode.HARD_MIX.blend(B, S));
        // Overlay = hard light with layers swapped; mid-grey source leaves hard light unchanged.
        close("hard light", new float[]{.375f, .5f, .625f}, BlendMode.HARD_LIGHT.blend(B, S));
        close("overlay", new float[]{.625f, .5f, .375f}, BlendMode.OVERLAY.blend(B, S));
        close("soft light grey is identity", B, BlendMode.SOFT_LIGHT.blend(B, new float[]{.5f, .5f, .5f}));
    }

    @Test public void edgeCasesNeverDivideByZero() {
        float[] black = {0, 0, 0}, white = {1, 1, 1};
        for (BlendMode m : BlendMode.values())
            for (float[] b : new float[][]{black, white, B}) for (float[] s : new float[][]{black, white, S}) {
                float[] r = m.composite(b, s, 1);
                for (float v : r) assertTrue(m + " stays in range: " + v, v >= 0 && v <= 1 && !Float.isNaN(v));
            }
        close("color burn over white", white, BlendMode.COLOR_BURN.blend(white, black));
        close("color dodge over black", black, BlendMode.COLOR_DODGE.blend(black, white));
    }

    @Test public void nonSeparableModesKeepTheRightComponents() {
        float[] red = {1, 0, 0}, grey = {.5f, .5f, .5f};
        // Luminosity of grey onto red keeps red's hue, takes grey's brightness.
        float[] l = BlendMode.LUMINOSITY.blend(red, grey);
        assertEquals(.5f, BlendMode.lum(l), 1e-4f);
        assertTrue("still reddest", l[0] > l[1] && l[0] > l[2]);
        // Saturation from a grey source removes colour; hue from grey too (no hue to take).
        float[] desat = BlendMode.SATURATION.blend(red, grey);
        assertEquals(desat[0], desat[1], 1e-4f); assertEquals(desat[1], desat[2], 1e-4f);
        close("color keeps backdrop luminance", new float[]{BlendMode.lum(B), BlendMode.lum(B), BlendMode.lum(B)},
                new float[]{BlendMode.lum(BlendMode.COLOR.blend(B, S)), BlendMode.lum(B), BlendMode.lum(B)});
        close("darker color picks the darker pixel", new float[]{.25f, .5f, .75f}, BlendMode.DARKER_COLOR.blend(new float[]{.9f, .9f, .9f}, S));
        close("lighter color picks the lighter pixel", new float[]{.9f, .9f, .9f}, BlendMode.LIGHTER_COLOR.blend(new float[]{.9f, .9f, .9f}, S));
    }

    @Test public void alphaMixesTheBlendIntoTheBackdrop() {
        close("half opacity multiply", new float[]{(.75f + .1875f) / 2, (.5f + .25f) / 2, (.25f + .1875f) / 2}, BlendMode.MULTIPLY.composite(B, S, .5f));
        close("transparent changes nothing", B, BlendMode.DIFFERENCE.composite(B, S, 0));
    }

    @Test public void idsAreStableAndValidated() {
        assertEquals(26, BlendMode.values().length);
        assertEquals(BlendMode.NORMAL, BlendMode.of(0)); assertEquals(BlendMode.LUMINOSITY, BlendMode.of(25));
        assertEquals("Unknown ids render normally", BlendMode.NORMAL, BlendMode.of(99));
        assertTrue(BlendMode.GLSL.contains("vec3 bcBlend(int m,vec3 b,vec3 s)"));
        EditProject p = RenderPlanTest.mainLane();
        assertFalse(RenderPlan.usesBlend(p));
        p.clips.add(RenderPlanTest.clip("top", 1000, 1, 0)); p.clips.get(3).blend = BlendMode.SCREEN.ordinal();
        p.validate();
        assertTrue(RenderPlan.usesBlend(p));
        assertEquals(BlendMode.SCREEN.ordinal(), new RenderPlan(p, false).layersAt(100).get(1).blend);
        assertEquals("Copies keep the blend", BlendMode.SCREEN.ordinal(), p.clips.get(3).copy().blend);
        p.clips.get(3).blend = 26;
        try { p.validate(); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void routingSendsBlendsAndHeavyProjectsToIceLiteAndHdrToMedia3() {
        ExportEngine.Route light = ExportEngine.route(ExportEngine.MEDIA3, 4, false);
        assertEquals(ExportEngine.MEDIA3, light.engine); assertNull(light.reason);
        assertEquals(ExportEngine.BC_CORE, ExportEngine.route(ExportEngine.MEDIA3, 8, false).engine);
        ExportEngine.Route blends = ExportEngine.route(ExportEngine.MEDIA3, 2, false, true);
        assertEquals(ExportEngine.BC_CORE, blends.engine); assertEquals("ICE Lite used: blend modes", blends.reason);
        assertEquals("HDR goes to Media3 for tone mapping", ExportEngine.MEDIA3, ExportEngine.route(ExportEngine.BC_CORE, 2, true).engine);
        assertEquals("Blend + HDR stays on ICE Lite", ExportEngine.BC_CORE, ExportEngine.route(ExportEngine.BC_CORE, 2, true, true).engine);
        assertEquals(ExportEngine.BC_CORE, ExportEngine.route(ExportEngine.BC_CORE, 6, true).engine);
        assertNull(ExportEngine.route(ExportEngine.BC_CORE, 8, false).reason);
    }
}

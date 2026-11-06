package com.blackice.bc1.core;

import com.blackice.bc1.editor.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

/** Per-effect controls: a clean table, defaults that keep the original look, persistence, copy/paste and shader wiring. */
public class EffectParamsTest {
    private static String shader() throws Exception {
        for (String path : new String[]{"src/main/assets/shaders/video.frag", "main/assets/shaders/video.frag", "app/src/main/assets/shaders/video.frag"}) {
            File f = new File(path);
            if (f.isFile()) return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        }
        throw new AssertionError("video.frag not found");
    }

    @Test public void theTableIsWellFormedAndMostEffectsHaveOwnControls() {
        int withControls = 0;
        for (int id = 0; id < EffectCatalog.count(); id++) {
            EffectCatalog.Param[] params = EffectCatalog.params(id);
            assertTrue(EffectCatalog.name(id), params.length <= EffectCatalog.MAX_PARAMS);
            Set<String> names = new HashSet<>();
            for (EffectCatalog.Param p : params) { assertFalse(p.name.isEmpty()); assertTrue(names.add(p.name)); assertTrue(p.def >= 0 && p.def <= 1); }
            if (params.length > 0) withControls++;
        }
        assertEquals("None has no controls", 0, EffectCatalog.paramCount(0));
        assertTrue("Most effects with a real choice to make now have controls", withControls >= 30);
        // Effects fully described by one strength stay that way on purpose.
        for (int id : new int[]{5, 9, 10, 17, 18, 19, 21, 22, 23, 24, 25, 26, 27, 38}) assertEquals(EffectCatalog.name(id), 0, EffectCatalog.paramCount(id));
    }

    @Test public void resolvedValuesAreDefaultsUnlessThisClipsEffectOwnsStoredOnes() {
        EditProject.Clip c = new EditProject.Clip(); c.effect = 13; // Motion blur: Angle, Length
        assertArrayEquals(new float[]{0f, .5f, 0f, 0f}, c.effectParams(), 0);
        c.fx = new float[]{.25f, 1f, 9f, -3f}; c.fxEffect = 13;
        float[] now = c.effectParams();
        assertEquals(.25f, now[0], 0); assertEquals(1f, now[1], 0);
        assertEquals("Values beyond the effect's controls are ignored", 0f, now[2], 0); assertEquals(0f, now[3], 0);
        c.effect = 12; // a different effect: the stored values do not carry over
        assertEquals(.5f, c.effectParams()[0], 0);
        c.effect = 13; c.fx = new float[]{5f, Float.NaN};
        assertEquals("Out of range clamps, junk falls back", 1f, c.effectParams()[0], 0); assertEquals(.5f, c.effectParams()[1], 0);
    }

    @Test public void controlsSurviveSaveCopyAndPasteAndDropWhenTheEffectChanges() throws Exception {
        EditProject p = new EditProject(); EditProject.Clip c = new EditProject.Clip();
        c.uri = "file:///a.mp4"; c.name = "a"; c.sourceDurationMs = 4000; c.outMs = 4000; c.effect = 29; c.fx = new float[]{.8f, .25f, .75f}; c.fxEffect = 29; p.clips.add(c);
        EditProject back = ProjectStore.decode(ProjectStore.encode(p));
        assertArrayEquals(new float[]{.8f, .25f, .75f, 0f}, back.clips.get(0).effectParams(), 1e-6f);
        assertArrayEquals(new float[]{.8f, .25f, .75f, 0f}, p.copy().clips.get(0).effectParams(), 0);
        EditProject.Clip target = new EditProject.Clip(); target.uri = "file:///b.mp4"; target.name = "b"; target.sourceDurationMs = 4000; target.outMs = 4000;
        Attributes.of(c, Attributes.EFFECTS).applyTo(target, Attributes.EFFECTS);
        assertEquals(29, target.effect); assertArrayEquals(new float[]{.8f, .25f, .75f, 0f}, target.effectParams(), 1e-6f);
        c.effect = 30; // changed effect: stored values no longer apply and are not written
        assertFalse(ProjectStore.encode(p).contains("\"fx\""));
        assertEquals(.5f, c.effectParams()[0], 0);
        assertFalse("Projects without controls write nothing extra", ProjectStore.encode(new EditProject()).contains("fx"));
    }

    @Test public void everyEffectWithControlsActuallyReadsThemInTheShader() throws Exception {
        String source = shader();
        for (int id = 1; id < EffectCatalog.count(); id++) {
            if (EffectCatalog.paramCount(id) == 0) continue;
            String block;
            if (id >= 12 && !EffectCatalog.distortion(id)) {
                int at = source.indexOf("BC_EFFECT == " + id + "\n");
                assertTrue("effect block " + id, at >= 0);
                block = source.substring(at, source.indexOf("#endif", at));
            } else if (EffectCatalog.distortion(id)) {
                int at = source.indexOf("BC_EFFECT == " + id + "\n");
                assertTrue("distortion block " + id, at >= 0);
                block = source.substring(at, source.indexOf("#endif", at)) + (EffectCatalog.params(id).length > 1 && EffectCatalog.params(id)[1].name.startsWith("Center") ? "uParams" : "");
            } else {
                StringBuilder lines = new StringBuilder();
                for (String line : source.split("\n")) if (line.contains("uEffect==" + id) || (id <= 2 && line.contains("uEffect==1 || uEffect==2"))) lines.append(line).append('\n');
                block = lines.toString() + (id == 1 || id == 2 ? source.substring(source.indexOf("uEffect==1 || uEffect==2"), source.indexOf("if(uEffect==3)")) : "") + (id == 7 ? source.substring(source.indexOf("if(uEffect==7)"), source.indexOf("if(uEffect==9)")) : "");
            }
            assertTrue(EffectCatalog.name(id) + " reads its controls", block.contains("uParams"));
        }
        assertTrue(source.contains("uniform vec4 uParams;"));
    }
}

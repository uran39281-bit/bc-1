package com.blackice.bc1.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

/** Guards the layer shader against constructs that mobile GLSL compilers reject. */
public class ShaderSourceTest {
    private static String shader() throws Exception {
        for (String path : new String[]{"src/main/assets/shaders/video.frag", "main/assets/shaders/video.frag", "app/src/main/assets/shaders/video.frag"}) {
            File f = new File(path);
            if (f.isFile()) return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        }
        throw new AssertionError("video.frag not found from " + new File(".").getAbsolutePath());
    }

    @Test public void noDeepIfElseChains() throws Exception {
        // A 34-deep else-if chain exhausted the emulator's GLSL parser ("memory exhausted"). Keep chains short.
        int longest = 0, run = 0;
        for (String line : shader().split("\n")) {
            if (line.trim().startsWith("else if")) longest = Math.max(longest, ++run);
            else if (line.trim().startsWith("if(") || line.trim().startsWith("if (")) run = 0;
        }
        assertTrue("Longest else-if chain: " + longest, longest <= 6);
    }

    @Test public void everyCatalogEffectIsHandled() throws Exception {
        String s = shader();
        for (int id = 12; id < EffectCatalog.count(); id++)
            assertTrue("Effect " + id + " (" + EffectCatalog.name(id) + ") has no shader branch", s.contains("e==" + id + ")"));
        assertTrue(s.contains("uniform float uTime;"));
    }

    /** Each effect compiles alone (BC_EFFECT) so a clip's program only pays for its own effect. */
    @Test public void everyEffectBlockIsGuardedForSpecialisation() throws Exception {
        String s = shader();
        for (int id = 12; id < EffectCatalog.count(); id++) {
            String guard = "#if !defined(BC_EFFECT) || BC_EFFECT == " + id + "\n";
            int at = s.indexOf(guard);
            assertTrue("Effect " + id + " has no BC_EFFECT guard", at >= 0);
            int end = s.indexOf("#endif", at);
            assertTrue("Effect " + id + "'s code sits inside its guard", s.substring(at, end).contains("e==" + id + ")"));
        }
        assertEquals("Guards balance", s.split("\n#if ", -1).length, s.split("\n#endif", -1).length);
        String specialised = LayerShader.forEffect(s, 21);
        assertTrue(specialised.startsWith("#version 300 es\n#define BC_EFFECT 21\n"));
        assertTrue(LayerShader.forEffect(s, 999).contains("#define BC_EFFECT 0\n"));
    }
}

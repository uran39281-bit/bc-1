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
}

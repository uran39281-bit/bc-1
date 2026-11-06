package com.blackice.bc1.menu;

import static org.junit.Assert.*;
import com.blackice.bc1.core.BlendMode;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.menu.MenuScreenFactory.Blend;
import org.junit.Test;

public class BlendChoicesTest {
    @Test public void everyVisibleModeRoundTripsForClips() {
        for (Blend b : Blend.values()) assertEquals(b, BlendChoices.fromClipMode(BlendChoices.clipMode(b)));
    }

    @Test public void clipModesCarryTheLabelsTheyAreShownWith() {
        assertEquals("Normal", BlendChoices.clipMode(Blend.NORMAL).label);
        assertEquals("Multiply", BlendChoices.clipMode(Blend.MULTIPLY).label);
        assertEquals("Screen", BlendChoices.clipMode(Blend.SCREEN).label);
        assertEquals("Overlay", BlendChoices.clipMode(Blend.OVERLAY).label);
        assertEquals("Linear Dodge (Add)", BlendChoices.clipMode(Blend.ADD).label);
        assertEquals("Darken", BlendChoices.clipMode(Blend.DARKEN).label);
        assertEquals("Lighten", BlendChoices.clipMode(Blend.LIGHTEN).label);
    }

    @Test public void otherExistingClipModesAreNotMislabelled() {
        for (BlendMode m : BlendMode.values()) {
            Blend shown = BlendChoices.fromClipMode(m);
            if (shown != null) assertEquals(m, BlendChoices.clipMode(shown));
        }
        assertNull(BlendChoices.fromClipMode(BlendMode.SOFT_LIGHT));
        assertNull(BlendChoices.fromClipMode(BlendMode.DIFFERENCE));
    }

    @Test public void overlayIndicesMatchTheStoredNamesNotTheEnumOrder() {
        assertEquals(BlendChoices.OVERLAY_NAMES.length, Blend.values().length);
        for (Blend b : Blend.values()) {
            int index = BlendChoices.overlayIndex(b);
            assertEquals(b, BlendChoices.fromOverlayIndex(index));
            assertEquals(EditProject.BLENDS[index].toLowerCase(), BlendChoices.OVERLAY_NAMES[index].toLowerCase());
        }
        assertArrayEquals(BlendChoices.OVERLAY_NAMES, EditProject.BLENDS);
        assertEquals(Blend.NORMAL, BlendChoices.fromOverlayIndex(99));
    }
}

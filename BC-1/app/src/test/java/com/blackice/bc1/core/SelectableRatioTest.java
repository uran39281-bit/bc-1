package com.blackice.bc1.core;

import static org.junit.Assert.*;
import com.blackice.bc1.editor.EditProject;
import org.junit.Test;

public class SelectableRatioTest {
    @Test public void portraitNineBySixteenIsOffered() {
        assertTrue(CanvasFormat.selectable("9:16"));
        for (String ratio : new String[]{"16:9", "4:5", "1:1", "4:3"}) assertTrue(ratio, CanvasFormat.selectable(ratio));
        assertArrayEquals(CanvasFormat.RATIOS, CanvasFormat.SELECTABLE);
    }

    @Test public void everyOfferedRatioIsStillAValidFormat() {
        for (String ratio : CanvasFormat.SELECTABLE) assertTrue(CanvasFormat.valid(ratio));
    }

    @Test public void existingPortraitProjectsStillWork() {
        EditProject p = new EditProject();
        p.setCanvasRatio("9:16");
        p.validate();
        assertEquals("9:16", p.ratioName());
        assertArrayEquals(new int[]{1080, 1920}, p.canvasSize(1080));
    }
}

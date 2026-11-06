package com.blackice.bc1.menu;

import com.blackice.bc1.core.BlendMode;
import com.blackice.bc1.menu.MenuScreenFactory.Blend;

/**
 * The seven blend modes shown in the menu, mapped explicitly to each pipeline's own identifiers. The
 * display order never doubles as an enum ordinal or a stored index.
 */
public final class BlendChoices {
    private BlendChoices() {}

    /** Overlay layers store an index into EditProject.BLENDS: Normal, Multiply, Screen, Overlay, Add, Darken, Lighten. */
    public static final String[] OVERLAY_NAMES = {"Normal", "Multiply", "Screen", "Overlay", "Add", "Darken", "Lighten"};

    public static BlendMode clipMode(Blend b) {
        switch (b) {
            case MULTIPLY: return BlendMode.MULTIPLY;
            case SCREEN: return BlendMode.SCREEN;
            case OVERLAY: return BlendMode.OVERLAY;
            case ADD: return BlendMode.LINEAR_DODGE;
            case DARKEN: return BlendMode.DARKEN;
            case LIGHTEN: return BlendMode.LIGHTEN;
            default: return BlendMode.NORMAL;
        }
    }

    /** Null when a clip uses one of its other existing modes. */
    public static Blend fromClipMode(BlendMode m) {
        switch (m) {
            case NORMAL: return Blend.NORMAL;
            case MULTIPLY: return Blend.MULTIPLY;
            case SCREEN: return Blend.SCREEN;
            case OVERLAY: return Blend.OVERLAY;
            case LINEAR_DODGE: return Blend.ADD;
            case DARKEN: return Blend.DARKEN;
            case LIGHTEN: return Blend.LIGHTEN;
            default: return null;
        }
    }

    public static int overlayIndex(Blend b) {
        switch (b) {
            case MULTIPLY: return 1;
            case SCREEN: return 2;
            case OVERLAY: return 3;
            case ADD: return 4;
            case DARKEN: return 5;
            case LIGHTEN: return 6;
            default: return 0;
        }
    }

    public static Blend fromOverlayIndex(int i) {
        switch (i) {
            case 1: return Blend.MULTIPLY;
            case 2: return Blend.SCREEN;
            case 3: return Blend.OVERLAY;
            case 4: return Blend.ADD;
            case 5: return Blend.DARKEN;
            case 6: return Blend.LIGHTEN;
            default: return Blend.NORMAL;
        }
    }
}

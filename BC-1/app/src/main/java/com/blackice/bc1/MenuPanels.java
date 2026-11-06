package com.blackice.bc1;

import android.widget.LinearLayout;
import com.blackice.bc1.menu.MenuScreenFactory;

/**
 * Owns the one menu factory for the editor. The inspector is rebuilt after every committed edit, so the
 * factory (and its remembered graph selection) outlives each rebuilt view.
 */
final class MenuPanels {
    private final MenuScreenFactory factory;
    private LinearLayout body;
    String selectedEffectId;

    MenuPanels(EditorActivity activity) { factory = new MenuScreenFactory(activity, new MenuHost(activity, this)); }

    /** Adds the menu's content to the inspector in place of the old stacked controls. */
    void mount(LinearLayout into, MenuScreenFactory.Menu menu) {
        body = factory.createEmbedded(menu);
        into.addView(body, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Mounts the menu as if the panel were this wide; used to render every layout for review. */
    void mountAt(LinearLayout into, MenuScreenFactory.Menu menu, int widthDp) {
        factory.overrideWidthDp(widthDp);
        try { mount(into, menu); } finally { factory.overrideWidthDp(0); }
    }

    /** Called on playhead, selection and live-gesture changes; updates readouts without rebuilding views. */
    void refresh() {
        if (body != null && body.isAttachedToWindow()) factory.refresh();
    }

    void save(android.os.Bundle b){b.putBundle("menu-curve-expansion",factory.expansionState());}
    void restore(android.os.Bundle b){if(b!=null)factory.restoreExpansion(b.getBundle("menu-curve-expansion"));}
    void showGraph(MenuScreenFactory.Property property) { factory.restoreGraphSelection(property.ordinal()); }
    int graphSelection() { return factory.graphSelection(); }
    void restoreGraphSelection(int ordinal) { factory.restoreGraphSelection(ordinal); }
}

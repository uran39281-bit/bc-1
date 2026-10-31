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

    MenuPanels(EditorActivity activity) { factory = new MenuScreenFactory(activity, new MenuHost(activity, this)); }

    /** Adds the menu's content to the inspector in place of the old stacked controls. */
    void mount(LinearLayout into, MenuScreenFactory.Menu menu) {
        body = factory.createEmbedded(menu);
        into.addView(body, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Called on playhead, selection and live-gesture changes; updates readouts without rebuilding views. */
    void refresh() {
        if (body != null && body.isAttachedToWindow()) factory.refresh();
    }

    void showGraph(MenuScreenFactory.Property property) { factory.restoreGraphSelection(property.ordinal()); }
    int graphSelection() { return factory.graphSelection(); }
    void restoreGraphSelection(int ordinal) { factory.restoreGraphSelection(ordinal); }
}

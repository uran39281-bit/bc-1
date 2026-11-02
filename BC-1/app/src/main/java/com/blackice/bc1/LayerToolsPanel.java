package com.blackice.bc1;

import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.Locale;

/**
 * Media Settings → Layer Tools: Speed, Reverse, Screenshot, Joint Movement and Copy All as five separate tabs.
 * Every control here is wired to a real project edit; a tab whose engine support does not exist yet says so
 * and keeps its action disabled rather than pretending to work.
 */
final class LayerToolsPanel {
    static final String TITLE = "Layer Tools";
    static final String[] TABS = {"Speed", "Reverse", "Screenshot", "Joint Movement", "Copy All"};
    private final EditorActivity a;
    private int tab;
    private final int[] scroll = new int[TABS.length];

    LayerToolsPanel(EditorActivity activity) { a = activity; }

    /** Opens the panel on its first tab. Back returns to Media Settings with the same layer and playhead. */
    void open() { tab = 0; java.util.Arrays.fill(scroll, 0); a.panels.open(TITLE, this::build); }

    int tab() { return tab; }

    private ScrollView scroller() { return a.inspector.getParent() instanceof ScrollView ? (ScrollView) a.inspector.getParent() : null; }

    void select(int index) {
        if (index == tab || index < 0 || index >= TABS.length) return;
        ScrollView scrollView = scroller();
        if (scrollView != null) scroll[tab] = scrollView.getScrollY();
        tab = index;
        a.renderInspector();
    }

    /** Draws the whole panel into the inspector; called again after every committed edit. */
    void build() {
        LinearLayout inspector = a.inspector;
        LinearLayout bar = a.row();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = a.button("‹  Back", v -> a.panels.back());
        back.setContentDescription("Back to Media Settings");
        bar.addView(back, new LinearLayout.LayoutParams(-2, a.dp(44)));
        TextView title = a.label(TITLE, 18, EditorActivity.TEXT, true);
        title.setPadding(a.dp(12), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        inspector.addView(bar);
        inspector.addView(tabStrip());
        switch (tab) {
            case 0: speed(inspector); break;
            case 1: reverse(inspector); break;
            case 2: screenshot(inspector); break;
            case 3: jointMovement(inspector); break;
            default: copyAll(inspector); break;
        }
        ScrollView scrollView = scroller();
        if (scrollView != null) { int y = scroll[tab]; scrollView.post(() -> scrollView.scrollTo(0, y)); }
    }

    /** The strip scrolls sideways if five labels do not fit; the panel itself never does. */
    private View tabStrip() {
        HorizontalScrollView strip = new HorizontalScrollView(a);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setContentDescription("Layer Tools tabs");
        LinearLayout row = a.row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            boolean selected = i == tab;
            TextView b = a.button(TABS[i], v -> select(index));
            b.setTextSize(12);
            b.setMaxLines(1);
            b.setSelected(selected);
            b.setBackground(a.buttonBackground(selected));
            b.setTextColor(selected ? EditorActivity.CYAN : EditorActivity.TEXT);
            b.setContentDescription(TABS[i] + (selected ? ", selected tab" : " tab"));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, a.dp(40));
            lp.rightMargin = a.dp(8);
            row.addView(b, lp);
        }
        strip.addView(row);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = a.dp(8);
        lp.bottomMargin = a.dp(8);
        strip.setLayoutParams(lp);
        return strip;
    }

    private TextView heading(String text) { TextView h = a.label(text, 16, EditorActivity.TEXT, true); h.setPadding(0, a.dp(6), 0, a.dp(4)); return h; }

    private void unavailable(LinearLayout into, String message) { into.addView(a.note(message)); }

    private String selectedName() {
        EditProject.Clip c = a.clip();
        EditProject.Overlay o = a.overlay();
        return c != null ? c.name : o != null ? o.label() : null;
    }

    private static String seconds(long ms) { return String.format(Locale.US, "%.2f s", ms / 1000.0); }

    // ---- Speed ------------------------------------------------------------------------------------------

    private void speed(LinearLayout into) {
        into.addView(heading("Speed"));
        EditProject.Clip c = a.clip();
        if (c == null) { unavailable(into, a.overlay() != null ? "Speed is not available for text, shape or image layers." : "Select a layer."); return; }
        if (c.image) { unavailable(into, "Pictures have no playback speed. Use Duration in Media Settings."); return; }
        TextView readout = a.label(String.format(Locale.US, "%.2f×", c.speed), 26, EditorActivity.CYAN, true);
        readout.setContentDescription("Playback rate " + String.format(Locale.US, "%.2f", c.speed) + " times");
        into.addView(readout);
        a.panels.speedControls();
        TextView reset = a.button("Reset to 1.00×", v -> {
            if (!a.layerEditable()) return;
            EditProject.Clip now = a.clip();
            if (now == null || now.image || now.speed == 1f) return;
            a.edit("Speed reset", () -> TimelineEdits.speed(a.project, a.selected, 1f));
        });
        EditorActivity.dim(reset, !c.image && c.speed != 1f && !a.selectionLocked() && !a.uiLocked);
        into.addView(reset, new LinearLayout.LayoutParams(-1, a.dp(44)));
        long source = c.outMs - c.inMs;
        into.addView(a.note("Trimmed source: " + seconds(source) + "\nOn the timeline: " + seconds(c.durationMs())
                + "\nThe clip keeps its start; its end moves with the rate. Audio follows the same rate. Reverse direction is its own tab."));
    }

    // ---- Reverse ----------------------------------------------------------------------------------------

    private void reverse(LinearLayout into) {
        into.addView(heading("Reverse"));
        EditProject.Clip c = a.clip();
        if (c == null) { unavailable(into, a.overlay() != null ? "Reverse is available for video footage only." : "Select a layer."); return; }
        if (c.image || c.track >= EditProject.AUDIO_FIRST) { unavailable(into, "Reverse is available for video footage only."); return; }
        into.addView(a.label("Direction: Forward", 14, EditorActivity.TEXT, false));
        TextView action = a.button("Reverse Footage", v -> { });
        EditorActivity.dim(action, false);
        into.addView(action, new LinearLayout.LayoutParams(-1, a.dp(44)));
        unavailable(into, "Reversing footage needs a reversed-frame and reversed-audio pipeline that this build's engines do not have yet, so the action stays disabled instead of faking the result.");
    }

    // ---- Screenshot -------------------------------------------------------------------------------------

    private void screenshot(LinearLayout into) {
        into.addView(heading("Screenshot"));
        into.addView(a.label("Capture time " + EditorActivity.clock(a.project.playheadMs), 14, EditorActivity.TEXT, false));
        String reason = a.screenshotTool.unavailableReason();
        TextView action = a.button("Take Screenshot", v -> a.screenshotTool.start());
        action.setContentDescription("Take Screenshot");
        EditorActivity.dim(action, reason == null);
        into.addView(action, new LinearLayout.LayoutParams(-1, a.dp(44)));
        into.addView(a.note("Capture the selected layer at the playhead as a new image layer."));
        if (reason != null) unavailable(into, reason);
    }

    // ---- Joint Movement ---------------------------------------------------------------------------------

    private void jointMovement(LinearLayout into) {
        into.addView(heading("Joint Movement"));
        into.addView(a.label("Parent & Child", 12, EditorActivity.MUTED, false));
        String name = selectedName();
        if (name == null) { unavailable(into, "Select a layer."); return; }
        into.addView(a.label("Child: " + name, 14, EditorActivity.TEXT, false));
        into.addView(a.label("Parent: None", 14, EditorActivity.TEXT, false));
        TextView set = a.button("Set Parent", v -> { });
        EditorActivity.dim(set, false);
        into.addView(set, new LinearLayout.LayoutParams(-1, a.dp(44)));
        unavailable(into, "Parenting needs a composed parent transform in preview, selection and both export engines, which this build does not have yet, so the action stays disabled.");
    }

    // ---- Copy All ---------------------------------------------------------------------------------------

    /** Transformations, effects and their keyframes; never source media, trim, placement, speed, reverse or parenting. */
    static final int COPY_ALL = Attributes.TRANSFORM | Attributes.EFFECTS | Attributes.ANIMATION;

    private void copyAll(LinearLayout into) {
        into.addView(heading("Copy All"));
        String name = selectedName();
        if (name == null) { unavailable(into, "Select a layer."); return; }
        into.addView(a.label("Source: " + name, 14, EditorActivity.TEXT, false));
        into.addView(a.label("Transformations and effects", 12, EditorActivity.MUTED, false));
        TextView copy = a.button("Copy All", v -> copy());
        into.addView(copy, new LinearLayout.LayoutParams(-1, a.dp(44)));
        TextView status = a.note(a.copiedAttributes == null ? "Nothing copied yet. Use Paste attributes… from the layer menu to apply it to another layer."
                : "Settings snapshot copied (" + Attributes.names(a.copiedAttributes.categories) + "). Use Paste attributes… from the layer menu to apply it.");
        into.addView(status);
    }

    /** Re-checks the selection when pressed; snapshots the settings without touching the project or its history. */
    void copy() {
        EditProject.Clip c = a.clip();
        EditProject.Overlay o = a.overlay();
        if (c == null && o == null) { a.tell("Select a layer to copy from."); return; }
        Attributes snapshot = c != null ? Attributes.of(c, COPY_ALL) : Attributes.of(o, COPY_ALL);
        a.copiedAttributes = snapshot;
        a.tell("Copied transformations and effects");
        a.renderInspector();
    }
}

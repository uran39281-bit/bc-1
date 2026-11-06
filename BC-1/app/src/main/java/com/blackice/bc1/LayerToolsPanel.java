package com.blackice.bc1;

import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.List;
import java.util.Locale;

/**
 * Media Settings → Layer Tools: Speed, Reverse, Screenshot, Joint Movement and Copy All as five separate tabs.
 * Every control here is wired to a real project edit; a tab whose engine support does not exist yet says so
 * and keeps its action disabled rather than pretending to work.
 */
final class LayerToolsPanel {
    static final String TITLE = "Layer Tools";
    static final String[] TABS = {"Speed", "Reverse", "Screenshot", "Joint Movement", "Copy All", "Character Swap"};
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
            case 4: copyAll(inspector); break;
            default:
                inspector.addView(heading("Replace person • experimental"));
                inspector.addView(a.note("Use one full-body performer, a fixed camera and an empty background image (or empty first frame). Choose a transparent full-body character PNG facing forward with arms extended sideways. Up to 12 seconds, 360 frames and 720p. Audio must be AAC or silent."));
                String why=a.characterSwap.reason();
                TextView swap=a.button("Choose character PNG",v->a.characterSwap.choose());
                EditorActivity.dim(swap,why==null);inspector.addView(swap);
                if(why!=null)inspector.addView(a.note(why));
                break;
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

    private ProgressBar progressBar;
    private TextView progressText;

    /** Updates the running Reverse progress without rebuilding the panel. */
    void showProgress() {
        if (progressBar != null && progressBar.isAttachedToWindow()) progressBar.setProgress(a.reverseTool.progress());
        if (progressText != null && progressText.isAttachedToWindow()) progressText.setText("Reversing… " + a.reverseTool.progress() + "%");
    }

    private void reverse(LinearLayout into) {
        progressBar = null; progressText = null;
        into.addView(heading("Reverse"));
        EditProject.Clip c = a.clip();
        if (c == null) { unavailable(into, a.overlay() != null ? "Reverse is available for video footage only." : "Select a layer."); return; }
        String unsupported = ReverseOps.unsupported(c);
        if (unsupported != null) { unavailable(into, unsupported); return; }
        into.addView(a.label("Direction: " + (c.reversed ? "Reversed" : "Forward"), 14, EditorActivity.TEXT, false));
        if (a.reverseTool.running()) {
            progressText = a.label("Reversing… " + a.reverseTool.progress() + "%", 13, EditorActivity.TEXT, false);
            into.addView(progressText);
            progressBar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100); progressBar.setProgress(a.reverseTool.progress());
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(EditorActivity.CYAN));
            into.addView(progressBar, new LinearLayout.LayoutParams(-1, a.dp(12)));
            TextView cancel = a.button("Cancel", v -> a.reverseTool.cancel());
            cancel.setContentDescription("Cancel reversing");
            into.addView(cancel, new LinearLayout.LayoutParams(-1, a.dp(44)));
            return;
        }
        String reason = c.reversed ? null : a.reverseTool.unavailableReason();
        TextView action = a.button(c.reversed ? "Restore Forward" : "Reverse Footage", v -> { if (c.reversed) a.reverseTool.restore(); else a.reverseTool.start(); });
        EditorActivity.dim(action, reason == null && !a.selectionLocked() && !a.uiLocked);
        into.addView(action, new LinearLayout.LayoutParams(-1, a.dp(44)));
        into.addView(a.note(c.reversed ? "Restore Forward returns to the original footage and the same part of it. Nothing is re-encoded."
                : "Plays the trimmed footage backwards, picture and sound together. Position, rotation and other animation keep their own timing; the original file is never changed."));
        if (reason != null) unavailable(into, reason);
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

    /** The parent the person has picked in the list but not applied yet (stable layer id, never an index). */
    private String chosenParent;
    private String chosenFor;

    private String describe(Object layer, Object... all) {
        EditProject.Clip c = layer instanceof EditProject.Clip ? (EditProject.Clip) layer : null;
        EditProject.Overlay o = c == null ? (EditProject.Overlay) layer : null;
        String name = c != null ? c.name : o.label();
        String kind = ToolPanels.kind(c, o);
        // Duplicate names stay distinguishable: add the layer's time range and, if still ambiguous, its position.
        long start = c != null ? startOf(c) : o.startMs;
        return kind + " • " + name + " • " + EditorActivity.clockShort(start);
    }

    private long startOf(EditProject.Clip c) {
        for (EditProject.Placement p : a.project.placements()) if (p.clip == c) return p.startMs;
        return c.startMs;
    }

    private void jointMovement(LinearLayout into) {
        into.addView(heading("Joint Movement"));
        into.addView(a.label("Parent & Child", 12, EditorActivity.MUTED, false));
        EditProject.Clip c = a.clip();
        EditProject.Overlay o = a.overlay();
        Object child = c != null ? c : o;
        if (child == null) { unavailable(into, "Select a layer."); return; }
        if (c != null && c.track >= EditProject.AUDIO_FIRST || o != null && o.visualizerClipId != null) { unavailable(into, "Joint Movement applies to visual layers only."); return; }
        String childId = ParentTransforms.idOf(child);
        if (!childId.equals(chosenFor)) { chosenFor = childId; chosenParent = null; }
        ParentTransforms links = new ParentTransforms(a.project);
        Object parent = links.find(ParentTransforms.parentOf(child));
        into.addView(a.label("Child: " + (c != null ? c.name : o.label()), 14, EditorActivity.TEXT, false));
        into.addView(a.label("Current Parent: " + (parent == null ? "None" : describe(parent)), 14, EditorActivity.TEXT, false));
        Object picked = links.find(chosenParent);
        List<Object> candidates = links.parentCandidates(childId);
        TextView choose = a.button(picked == null ? "Choose parent layer" : describe(picked), v -> chooseParent(candidates));
        choose.setContentDescription("Parent layer: " + (picked == null ? "none chosen" : describe(picked)));
        EditorActivity.dim(choose, !candidates.isEmpty() && !a.selectionLocked() && !a.uiLocked);
        into.addView(choose, new LinearLayout.LayoutParams(-1, a.dp(44)));
        LinearLayout actions = a.row();
        TextView set = a.button("Set Parent", v -> applyParent(childId));
        TextView remove = a.button("Remove Parent", v -> removeParent(childId));
        boolean editable = !a.selectionLocked() && !a.uiLocked;
        EditorActivity.dim(set, editable && picked != null && !ParentTransforms.idOf(picked).equals(ParentTransforms.parentOf(child)));
        EditorActivity.dim(remove, editable && parent != null);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, a.dp(44), 1);
        half.rightMargin = a.dp(8);
        actions.addView(set, half);
        actions.addView(remove, new LinearLayout.LayoutParams(0, a.dp(44), 1));
        into.addView(actions);
        into.addView(a.note("The child follows its parent's position, rotation and scale while staying independently editable. Opacity, effects, speed, duration and visibility are not inherited. Attaching or removing keeps the child exactly where it is now."));
        if (candidates.isEmpty()) unavailable(into, "There is no other visual layer to use as a parent.");
    }

    /** Picks the parent in the list (what tapping a list row does). */
    void pick(String parentId) { chosenParent = parentId; a.renderInspector(); }

    private void chooseParent(List<Object> candidates) {
        if (candidates.isEmpty()) return;
        String[] names = new String[candidates.size()];
        for (int i = 0; i < names.length; i++) names[i] = describe(candidates.get(i)) + (isDuplicateLabel(candidates, i) ? "  (#" + (i + 1) + ")" : "");
        new android.app.AlertDialog.Builder(a).setTitle("Parent layer").setItems(names, (d, which) -> {
            chosenParent = ParentTransforms.idOf(candidates.get(which));
            a.renderInspector();
        }).setNegativeButton("Cancel", null).show();
    }

    private boolean isDuplicateLabel(List<Object> all, int index) {
        String label = describe(all.get(index));
        for (int i = 0; i < all.size(); i++) if (i != index && describe(all.get(i)).equals(label)) return true;
        return false;
    }

    /** Re-checks identity, lock state and validity at the moment of the click; the whole change is one undo step. */
    void applyParent(String childId) {
        String parentId = chosenParent;
        if (parentId == null || !a.layerEditable()) return;
        a.edit("Parent set", () -> {
            ParentTransforms links = new ParentTransforms(a.project);
            Object child = links.find(childId);
            if (child == null || !childId.equals(selectedId())) throw new IllegalArgumentException("The selected layer changed.");
            links.attach(child, parentId, a.project.playheadMs);
        });
        chosenParent = null;
        a.renderInspector();
    }

    void removeParent(String childId) {
        if (!a.layerEditable()) return;
        a.edit("Parent removed", () -> {
            ParentTransforms links = new ParentTransforms(a.project);
            Object child = links.find(childId);
            if (child == null || !childId.equals(selectedId())) throw new IllegalArgumentException("The selected layer changed.");
            links.detach(child, a.project.playheadMs);
        });
        a.renderInspector();
    }

    private String selectedId() {
        EditProject.Clip c = a.clip();
        EditProject.Overlay o = a.overlay();
        return c != null ? c.id : o != null ? o.id : null;
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

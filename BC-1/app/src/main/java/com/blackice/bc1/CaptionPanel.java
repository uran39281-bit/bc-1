package com.blackice.bc1;

import android.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.blackice.bc1.core.SubtitleFiles;
import com.blackice.bc1.editor.EditProject;
import java.util.List;
import java.util.Locale;

/** Caption text and timing are edited through the editor's existing undo/project machinery. */
final class CaptionPanel {
    static void show(EditorActivity a) {
        new AlertDialog.Builder(a).setTitle("Speech & captions")
            .setItems(new String[]{"Generate auto captions", "Review / edit captions", "Export subtitles (.srt)", "Export subtitles (.vtt)", "Speech models", "Resume saved transcription", "Review pending result", "Discard saved transcription", "Shift caption timing"}, (d, which) -> {
                if (which == 0) a.autoCaptions();
                else if (which == 1) review(a);
                else if (which == 2 || which == 3) a.exportSubtitles(which == 3);
                else if(which==4)a.speechModels();
                else if(which==5)a.resumeCaptions();
                else if(which==6)a.reviewPendingCaptions();
                else if(which==7)a.discardCaptionJob();
                else shift(a);
            }).setNegativeButton("Close", null).show();
    }
    static void shift(EditorActivity a) {
        if(!a.editable())return;String id=a.project.id;
        EditText input=new EditText(a);input.setHint("Milliseconds: positive = later, negative = earlier");input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
        new AlertDialog.Builder(a).setTitle("Shift all captions").setView(input).setPositiveButton("Apply",(d,w)->{
            if(!id.equals(a.project.id))return;
            try{long delta=Long.parseLong(input.getText().toString().trim());a.edit("Caption timing shifted",()->com.blackice.bc1.core.CaptionTiming.shift(a.project,delta));}
            catch(RuntimeException e){a.tell("Enter a signed whole number of milliseconds.");}
        }).setNegativeButton("Cancel",null).show();
    }
    static void review(EditorActivity a) {
        List<EditProject.Overlay> captions = SubtitleFiles.captions(a.project);
        if (captions.isEmpty()) { a.tell("Generate captions first."); return; }
        String[] rows = new String[captions.size()];
        for (int i = 0; i < rows.length; i++) {
            EditProject.Overlay o = captions.get(i);
            rows[i] = (i + 1) + "  ·  " + SubtitleFiles.time(o.startMs, true) + " – " + SubtitleFiles.time(o.endMs, true) + "\n" + o.text;
        }
        new AlertDialog.Builder(a).setTitle("Review captions • " + rows.length)
            .setItems(rows, (d, which) -> edit(a, captions.get(which))).setNegativeButton("Close", null).show();
    }
    private static void edit(EditorActivity a, EditProject.Overlay o) {
        if (!a.editable()) return;
        String projectId = a.project.id;
        LinearLayout box = new LinearLayout(a); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(8));
        EditText words = input(a, box, "Caption text", o.text, false);
        words.setMinLines(2); words.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(200)});
        EditText start = input(a, box, "Start (seconds)", String.format(Locale.ROOT, "%.3f", o.startMs / 1000.0), true);
        EditText end = input(a, box, "End (seconds)", String.format(Locale.ROOT, "%.3f", o.endMs / 1000.0), true);
        AlertDialog dialog = new AlertDialog.Builder(a).setTitle("Edit caption").setView(box)
            .setPositiveButton("Save", null).setNeutralButton("Preview", null).setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> { a.seek(o.startMs); });
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                try {
                    if (!a.editable()) return;
                    if (!projectId.equals(a.project.id) || !a.project.overlays.contains(o)) throw new IllegalArgumentException("The project changed. Reopen caption review.");
                    long s = SubtitleFiles.seconds(start.getText().toString()), e = SubtitleFiles.seconds(end.getText().toString());
                    String text = words.getText().toString().trim();
                    if (text.isEmpty() || e - s < 100 || e > a.project.durationMs()) throw new IllegalArgumentException("Enter text and a range of at least 0.1 seconds inside the project.");
                    for (EditProject.Overlay other : SubtitleFiles.captions(a.project))
                        if (other != o && other.startMs < e && other.endMs > s) throw new IllegalArgumentException("This range overlaps another caption.");
                    a.edit("Caption updated", () -> { o.text = text; o.startMs = s; o.endMs = e; });
                    dialog.dismiss(); review(a);
                } catch (RuntimeException error) { end.setError(error.getMessage()); }
            });
        });
        dialog.show();
    }
    private static EditText input(EditorActivity a, LinearLayout box, String label, String value, boolean number) {
        TextView title = new TextView(a); title.setText(label); box.addView(title);
        EditText input = new EditText(a); input.setText(value);
        input.setInputType(number ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(input); return input;
    }
}

package com.blackice.bc1;
import android.app.AlertDialog;
import android.widget.*;
import com.blackice.bc1.editor.EditProject;
final class LandmarkControls {
    static void add(EditorActivity a,long time){if(!a.editable())return;if(a.project.landmarks.size()>=5){a.tell("Five markers maximum. Delete a marker to free a slot.");return;}a.edit("Marker added",()->a.project.addLandmark(time));}
    static void show(EditorActivity a){
        LinearLayout body=a.form();TextView add=a.button("Add marker at playhead • "+a.project.landmarks.size()+"/5",null);add.setEnabled(a.project.landmarks.size()<5);body.addView(add);
        if(!add.isEnabled())body.addView(a.note("Delete a marker to free a slot."));
        AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Timeline markers").setView(body).setNegativeButton("Close",null).create();
        add.setOnClickListener(v->{dialog.dismiss();add(a,a.project.playheadMs);});
        for(EditProject.Landmark m:a.project.landmarks){TextView row=a.button((m.slot+1)+" • "+m.label+" • "+EditorActivity.seconds(m.timeMs)+" s",v->{dialog.dismiss();a.pausePlayback();a.seek(m.timeMs);edit(a,m);});row.setTextColor(EditProject.MARKER_COLORS[m.slot]);body.addView(row);}
        dialog.show();
    }
    static void edit(EditorActivity a,EditProject.Landmark marker){
        LinearLayout form=a.form();EditText label=a.field(form,"Label",marker.label),time=a.field(form,"Project time (seconds)",EditorActivity.seconds(marker.timeMs));
        a.dialog("Marker "+(marker.slot+1),form,()->{String name=label.getText().toString().trim();long ms=EditorActivity.parseMs(time.getText().toString());if(name.length()>80||ms<0)throw new IllegalArgumentException("Use a label under 81 characters and a non-negative time.");a.edit("Marker updated",()->{marker.label=name;marker.timeMs=ms;});},()->a.edit("Marker deleted",()->a.project.landmarks.remove(marker)));
    }
}

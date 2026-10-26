package com.blackice.bc1;
import android.app.AlertDialog;
import android.widget.*;
import android.view.*;
import android.content.ClipData;
import com.blackice.bc1.editor.QuickInsertion;
import com.blackice.bc1.editor.EditProject;
final class LandmarkControls {
    static void add(EditorActivity a,long time){if(!a.editable())return;for(EditProject.Landmark m:a.project.landmarks)if(QuickInsertion.frame(m.timeMs,a.project.frameRate)==QuickInsertion.frame(time,a.project.frameRate)){a.selectedMarkerId=m.id;a.tell("Selected "+m.label);if(a.timeline!=null)a.timeline.invalidate();return;}if(a.project.landmarks.size()>=5){a.tell("Five markers maximum. Delete a marker to free a slot.");return;}a.edit("Marker added",()->a.selectedMarkerId=a.project.addLandmark(QuickInsertion.snapped(time,a.project.frameRate)).id);}
    static final class Drag{final String project;Drag(String id){project=id;}}
    static void dragSource(EditorActivity a,View view){view.setOnTouchListener(new View.OnTouchListener(){float x,y;boolean dragging;
        public boolean onTouch(View v,MotionEvent e){switch(e.getActionMasked()){
          case MotionEvent.ACTION_DOWN:x=e.getX();y=e.getY();dragging=false;v.getParent().requestDisallowInterceptTouchEvent(true);return false;
          case MotionEvent.ACTION_MOVE:if(!dragging&&Math.hypot(e.getX()-x,e.getY()-y)>ViewConfiguration.get(a).getScaledTouchSlop()){
            if(!a.editable())return true;dragging=true;v.setPressed(false);v.getParent().requestDisallowInterceptTouchEvent(true);v.startDragAndDrop(ClipData.newPlainText("marker","Marker"),new View.DragShadowBuilder(v),new Drag(a.project.id),0);return true;}return dragging;
          case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:v.getParent().requestDisallowInterceptTouchEvent(false);if(dragging){dragging=false;v.setPressed(false);return true;}return false;
        }return dragging;}});}
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

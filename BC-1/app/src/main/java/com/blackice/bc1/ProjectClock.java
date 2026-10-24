package com.blackice.bc1;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.*;
import android.widget.TextView;
import java.util.Locale;
/** Work time is kept outside the edit graph, so ticks never invalidate a preview or undo snapshot. */
final class ProjectClock {
    final EditorActivity a;final Handler handler=new Handler(Looper.getMainLooper());
    String id;long total,last;boolean visible,paused;int ticks;TextView label;
    ProjectClock(EditorActivity a){this.a=a;}
    SharedPreferences prefs(){return a.getSharedPreferences("project-work-clocks",0);}
    void checkpoint(){if(id==null)return;long now=SystemClock.elapsedRealtime();if(visible&&!paused)total+=Math.max(0,now-last);last=now;prefs().edit().putLong(id,total).putBoolean(id+"-paused",paused).apply();}
    void resume(){visible=true;last=SystemClock.elapsedRealtime();handler.removeCallbacks(tick);handler.post(tick);}
    void pause(){checkpoint();visible=false;handler.removeCallbacks(tick);}
    final Runnable tick=new Runnable(){public void run(){
        if(!visible)return;
        if(!a.loading&&!a.project.id.equals(id)){checkpoint();id=a.project.id;total=prefs().getLong(id,0);paused=prefs().getBoolean(id+"-paused",false);last=SystemClock.elapsedRealtime();}
        if(label!=null){long ms=total+(visible&&!paused&&id!=null?Math.max(0,SystemClock.elapsedRealtime()-last):0),seconds=ms/1000;label.setText(String.format(Locale.US,"◷ %02d:%02d:%02d%s",seconds/3600,seconds/60%60,seconds%60,paused?" Ⅱ":""));label.setContentDescription("Project work clock "+label.getText()+". Tap for pause, resume or reset.");}
        if(++ticks%10==0)checkpoint();handler.postDelayed(this,1000);
    }};
    void menu(){if(id==null)return;new AlertDialog.Builder(a).setTitle("Project work clock").setItems(new String[]{paused?"Resume tracking":"Pause tracking","Reset accumulated time"},(d,i)->{
        if(i==0){checkpoint();paused=!paused;last=SystemClock.elapsedRealtime();checkpoint();}
        else new AlertDialog.Builder(a).setTitle("Reset this project's work clock?").setMessage("This clears accumulated work time. Your edits are preserved.").setNegativeButton("Cancel",null).setPositiveButton("Reset",(x,y)->{total=0;last=SystemClock.elapsedRealtime();checkpoint();}).show();
    }).setNegativeButton("Close",null).show();}
}

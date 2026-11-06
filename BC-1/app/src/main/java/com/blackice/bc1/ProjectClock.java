package com.blackice.bc1;
import android.content.SharedPreferences;
import android.os.*;
import android.widget.TextView;
import java.util.Locale;
/** Read-only foreground project time. The ledger is independent of edits and diagnostic retention. */
final class ProjectClock {
    final EditorActivity a;final Handler handler=new Handler(Looper.getMainLooper());
    private String id;private long total,last;private boolean visible;private int ticks;TextView label;
    ProjectClock(EditorActivity a){this.a=a;}
    private SharedPreferences prefs(){return a.getSharedPreferences("project-work-clocks",0);}
    void checkpoint(){if(id==null)return;long now=ActionTrace.now();if(visible)total+=Math.max(0,now-last);last=now;
        // apply updates the in-process ledger immediately and serializes it off the UI thread.
        prefs().edit().putLong(id,total).remove(id+"-paused").apply();ActionTrace.event("work_clock","checkpoint","","project_id",id,"confirmed_ms",total,"units","ms");}
    void resume(){visible=true;last=ActionTrace.now();handler.removeCallbacks(tick);handler.post(tick);}
    void pause(){checkpoint();visible=false;handler.removeCallbacks(tick);ActionTrace.event("work_clock","suspend","","scope","foreground project");}
    private void attach(){if(a.loading||a.project.id.equals(id))return;checkpoint();id=a.project.id;total=Math.max(0,prefs().getLong(id,0));last=ActionTrace.now();prefs().edit().remove(id+"-paused").apply();ActionTrace.event("work_clock","restore","","project_id",id,"confirmed_ms",total,"recovery","Confirmed checkpoint only; no background or crash-to-relaunch time is charged.");}
    final Runnable tick=new Runnable(){public void run(){if(!visible)return;attach();if(label!=null){long ms=total+(id!=null?Math.max(0,ActionTrace.now()-last):0),seconds=ms/1000;label.setText(String.format(Locale.US,"◷ %02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60));label.setContentDescription("Project work clock "+label.getText()+". Read only.");}if(++ticks%10==0)checkpoint();handler.postDelayed(this,1000);}};
}

package com.blackice.bc1;
import android.content.Context;
import android.content.SharedPreferences;
import java.util.concurrent.*;
/** Process-local continuity plus ordered durable checkpoints; never part of project undo data. */
final class WorkClockLedger {
 private static final ConcurrentMap<String,Long> live=new ConcurrentHashMap<>();
 private static final ExecutorService writer=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"bc1-work-clock");t.setDaemon(true);return t;});
 static long load(Context context,String id){return live.computeIfAbsent(id,k->Math.max(0,context.getSharedPreferences("project-work-clocks",0).getLong(k,0)));}
 static void checkpoint(Context context,String id,long total){Context app=context.getApplicationContext();long value=live.merge(id,total,Math::max);writer.execute(()->{
  try{SharedPreferences prefs=app.getSharedPreferences("project-work-clocks",0);boolean stored=prefs.edit().putLong(id,Math.max(value,prefs.getLong(id,0))).remove(id+"-paused").commit();
  ActionTrace.event("work_clock",stored?"checkpoint":"checkpoint_failure","","project_id",id,"confirmed_ms",stored?Long.valueOf(value):null,"units","ms","outcome",stored?"success":"failure");}catch(RuntimeException failure){ActionTrace.event("work_clock","checkpoint_failure","","project_id",id,"outcome","failure","exception_class",failure.getClass().getSimpleName());}
 });}
}

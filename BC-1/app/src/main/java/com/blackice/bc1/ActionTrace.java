package com.blackice.bc1;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.*;
import com.blackice.bc1.editor.EditProject;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Ordered local action trace. Input collection never performs file IO or formats JSON. */
public final class ActionTrace {
    private static volatile Recorder recorder;
    private static final ThreadLocal<String> INPUT=new ThreadLocal<>(),PARENT=new ThreadLocal<>();
    static String enter(Span span){String old=PARENT.get();if(span!=null)PARENT.set(span.id);return old;}
    static void leave(String parent){if(parent==null)PARENT.remove();else PARENT.set(parent);}
    private static volatile String workspace="startup",project="",layer="",property="";
    private static final int CAPACITY=8192,RESERVE=256,SEGMENT=1024*1024,SEGMENTS=8;
    public static long now(){return SystemClock.elapsedRealtime();}
    public static boolean phone(Context context){while(context instanceof android.content.ContextWrapper){if(context instanceof EditorActivity)return ((EditorActivity)context).portraitPhone();Context next=((android.content.ContextWrapper)context).getBaseContext();if(next==context)break;context=next;}return false;}
    static void start(Context context){if(recorder!=null)return;recorder=new Recorder(context.getApplicationContext());event("session","start","","recovery","Only confirmed project clock checkpoints are recovered. Uncheckpointed active time is unavailable after process death.");}
    static void context(EditorActivity a){String previousWorkspace=workspace,previousProject=project,previousLayer=layer,previousProperty=property;workspace=a.portraitPhone()?"phone_portrait":a.wideLayout?"wide":"phone_landscape";project=a.project.id;layer=a.clip()!=null?a.clip().id:a.overlay()!=null?a.overlay().id:"";property=a.panels.title()==null?"":a.panels.title();if(!Objects.equals(previousWorkspace,workspace))event("workspace","change","","before",previousWorkspace,"after",workspace);if(!Objects.equals(previousProject,project)||!Objects.equals(previousLayer,layer)||!Objects.equals(previousProperty,property))event("tool_context","change","","previous_project_id",previousProject,"previous_layer_id",previousLayer,"previous_property_id",previousProperty);}
    public static void event(String action,String phase,String operation,Object... fields){Recorder r=recorder;if(r!=null)r.offer(new Entry(action,phase,operation,fields),false);}
    public static Span begin(String action){return new Span(action);}
    public static final class Span {
        public final String id=UUID.randomUUID().toString();final String action;final String parentId=PARENT.get();final String projectId=project,layerId=layer,workspaceId=workspace,propertyId=property,inputId=INPUT.get();final long requested=now();boolean ended;
        Span(String action){this.action=action;phase("request");}
        public long requestedAt(){return requested;}
        public void started(){phase("start","queue_ms",Math.max(0,now()-requested));}
        public synchronized void end(String outcome){if(ended)return;ended=true;phase("complete","outcome",outcome,"duration_ms",Math.max(0,now()-requested));}
        public void phase(String phase,Object... fields){Object[] all=Arrays.copyOf(fields,fields.length+12);Object[] context={"parent_operation_id",parentId==null?"":parentId,"project_id",projectId,"layer_id",layerId,"workspace",workspaceId,"property_id",propertyId,"input_id",inputId==null?"command_or_background":inputId};System.arraycopy(context,0,all,fields.length,12);event(action,phase,id,all);}
    }
    /** Value snapshots contain IDs and numbers only, never media paths, names or text. */
    static Map<String,Object> values(EditProject p){Map<String,Object> out=new TreeMap<>();for(EditProject.Clip c:p.clips){numbers(out,"clip/"+c.id+"/",c);for(com.blackice.bc1.editor.EffectInstance e:c.effects())numbers(out,"clip/"+c.id+"/effect/"+e.id+"/",e);}for(EditProject.Overlay o:p.overlays)numbers(out,"overlay/"+o.id+"/",o);numbers(out,"project/",p);if(p.music!=null)numbers(out,"music/",p.music);for(int i=0;i<p.tracks.length;i++)numbers(out,"track/"+i+"/",p.tracks[i]);out.put("playhead_ms",p.playheadMs);return out;}
    private static void numbers(Map<String,Object> out,String prefix,Object target){for(Field f:target.getClass().getFields())try{if(Modifier.isStatic(f.getModifiers()))continue;Object v=f.get(target);if(v instanceof Number||v instanceof Boolean)out.put(prefix+f.getName(),v);else if(v instanceof float[])out.put(prefix+f.getName(),Arrays.toString((float[])v));else if(v instanceof com.blackice.bc1.editor.Motion){com.blackice.bc1.editor.Motion m=(com.blackice.bc1.editor.Motion)v;for(int i=0;i<m.curves.size();i++)for(com.blackice.bc1.editor.Motion.Key k:m.curves.get(i))numbers(out,prefix+f.getName()+"/"+i+"/"+k.id+"/",k);}else if(v!=null&&(v.getClass().getName().equals("com.blackice.bc1.audio.AudioSettings")||v.getClass().getName().startsWith("com.blackice.bc1.editor.Fill")))numbers(out,prefix+f.getName()+"/",v);}catch(IllegalAccessException ignored){}}
    static void changes(String action,Map<String,Object> before,EditProject p,String op){Map<String,Object> after=values(p);TreeSet<String> keys=new TreeSet<>(before.keySet());keys.addAll(after.keySet());for(String key:keys)if(!Objects.equals(before.get(key),after.get(key)))event(action,"value",op,"property",key,"before",before.get(key),"after",after.get(key),"units","property native units; time fields ms; rotation degrees; transforms project units");}
    public static void install(Window window){if(window==null)return;Window.Callback original=window.getCallback();if(original==null||Proxy.isProxyClass(original.getClass()))return;
        String[] touch={""};
        window.setCallback((Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},(proxy,method,args)->{
            String name=method.getName();boolean input=false;if(name.equals("onAttachedToWindow")||name.equals("onDetachedFromWindow"))event("window",name,"","window_id",System.identityHashCode(window));
            if(args!=null&&args.length>0&&args[0] instanceof MotionEvent&&(name.equals("dispatchTouchEvent")||name.equals("dispatchGenericMotionEvent")||name.equals("dispatchTrackballEvent"))){
                MotionEvent e=(MotionEvent)args[0];if(e.getActionMasked()==MotionEvent.ACTION_DOWN||touch[0].isEmpty()||e.getActionMasked()==MotionEvent.ACTION_HOVER_ENTER||e.getActionMasked()==MotionEvent.ACTION_SCROLL)touch[0]=UUID.randomUUID().toString();INPUT.set(touch[0]);input=true;motion(window.getDecorView(),e,touch[0]);
            }else if(args!=null&&args.length>0&&args[0] instanceof KeyEvent&&(name.equals("dispatchKeyEvent")||name.equals("dispatchKeyShortcutEvent"))){KeyEvent e=(KeyEvent)args[0];INPUT.set("key-"+e.getDownTime());input=true;key(e);}
            try{return method.invoke(original,args);}catch(InvocationTargetException ex){throw ex.getCause();}finally{if(input)INPUT.remove();}
        }));
    }
    private static void key(KeyEvent e){boolean command=!e.isPrintingKey()||e.isCtrlPressed()||e.isAltPressed();event("keyboard","input",INPUT.get(),"input_action",e.getAction(),"modifiers",e.getMetaState(),"source",e.getSource(),"key_code",command?Integer.valueOf(e.getKeyCode()):null,"text_entry",command?"command":"redacted");}
    static boolean inputMotion(View root,MotionEvent e,String id,java.util.function.BooleanSupplier forward){String previous=INPUT.get();INPUT.set(id);try{motion(root,e,id);return forward.getAsBoolean();}finally{if(previous==null)INPUT.remove();else INPUT.set(previous);}}
    static boolean inputKey(KeyEvent e,java.util.function.BooleanSupplier forward){String previous=INPUT.get();INPUT.set("key-"+e.getDownTime());try{key(e);return forward.getAsBoolean();}finally{if(previous==null)INPUT.remove();else INPUT.set(previous);}}
    private static void motion(View decor,MotionEvent e,String id){Recorder r=recorder;if(r==null)return;int[] origin=new int[2];decor.getLocationOnScreen(origin);for(int h=-1;h<e.getHistorySize();h++)for(int p=0;p<e.getPointerCount();p++){
        // History precedes the current sample; each received pointer and timestamp is retained.
        int history=h<0?0:h+1;boolean current=history==e.getHistorySize();long t=current?e.getEventTime():e.getHistoricalEventTime(history);float x=current?e.getX(p):e.getHistoricalX(p,history),y=current?e.getY(p):e.getHistoricalY(p,history);
        r.offer(new Entry("pointer","input",id,new Object[]{"sample_uptime_ms",t,"pointer_id",e.getPointerId(p),"tool",e.getToolType(p),"source",e.getSource(),"input_action",e.getActionMasked(),"coordinate_space","window pixels","x",x,"y",y,"window_screen_x",origin[0],"window_screen_y",origin[1],"window_width",decor.getWidth(),"window_height",decor.getHeight(),"density",decor.getResources().getDisplayMetrics().density,"wheel_x",current?e.getAxisValue(MotionEvent.AXIS_HSCROLL):e.getHistoricalAxisValue(MotionEvent.AXIS_HSCROLL,p,history),"wheel_y",current?e.getAxisValue(MotionEvent.AXIS_VSCROLL):e.getHistoricalAxisValue(MotionEvent.AXIS_VSCROLL,p,history)}),true);
    }}
    static String snapshot() throws Exception {Recorder r=recorder;if(r==null)return "Action trace is unavailable.\n";return r.worker.submit(()->{r.drain();StringBuilder b=new StringBuilder("Black Ice action log. Scope: all retained sessions and events, chronological file/sequence order. Rotation and capture gaps are explicit. Durations use elapsedRealtime; pointer samples use input uptime.\n");if(r.failure!=null)b.append("CAPTURE GAP: ").append(r.failure).append('\n');for(File f:r.files())b.append(new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8));return b.toString();}).get();}
    static final class Entry {
        long sequence;final long elapsed=now(),wall=System.currentTimeMillis();final String action,phase,operation,input,projectId,layerId,workspaceId,propertyId;final Object[] fields;
        Entry(String action,String phase,String operation,Object[] fields){this.action=action;this.phase=phase;this.operation=operation;this.fields=fields;input=INPUT.get();projectId=project;layerId=layer;workspaceId=workspace;propertyId=property;}
        JSONObject json(Recorder r)throws JSONException {JSONObject j=new JSONObject();j.put("schema_version",1).put("event_id",r.session+":"+sequence).put("sequence",sequence).put("session_id",r.session).put("process_id",android.os.Process.myPid()).put("app_version",r.version).put("wall_time_utc",Instant.ofEpochMilli(wall).toString()).put("session_elapsed_ms",elapsed-r.origin).put("elapsed_realtime_ms",elapsed).put("workspace",workspaceId).put("project_id",projectId).put("layer_id",layerId).put("property_id",propertyId).put("action",action).put("phase",phase).put("operation_id",operation.isEmpty()?(input==null?r.session+":"+sequence:input):operation).put("input_id",input==null?"command_or_background":input).put("outcome","input".equals(phase)?"received":"recorded").put("message",action.replace('_',' ')+" / "+phase+".");for(int i=0;i+1<fields.length;i+=2)j.put(String.valueOf(fields[i]),fields[i+1]==null?JSONObject.NULL:fields[i+1]);String verb="request".equals(phase)?"Request ":"start".equals(phase)?"Start ":"complete".equals(phase)?("failure".equals(j.optString("outcome"))?"Fail ":"cancelled".equals(j.optString("outcome"))?"Cancel ":"Complete "):"Record ";j.put("message",verb+action.replace('_',' ')+".");return j;}
    }
    static final class Recorder {
        final File directory;final String session=UUID.randomUUID().toString(),version;final long origin=now();final ArrayDeque<Entry> queue=new ArrayDeque<>();
        final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"bc1-action-trace");t.setDaemon(true);return t;});
        long sequence,droppedFirst,droppedLast,droppedCount,fileIndex;String retention;volatile String failure;File active;
        Recorder(Context context){directory=new File(context.getFilesDir(),"action-trace");String v="unknown";try{android.content.pm.PackageInfo p=context.getPackageManager().getPackageInfo(context.getPackageName(),0);v=p.versionName+" ("+p.versionCode+")";}catch(Exception ignored){}version=v;worker.scheduleWithFixedDelay(this::drain,0,50,TimeUnit.MILLISECONDS);}
        synchronized void offer(Entry e,boolean sample){e.sequence=++sequence;if(queue.size()>=(sample?CAPACITY-RESERVE:CAPACITY)){if(droppedCount++==0)droppedFirst=e.sequence;droppedLast=e.sequence;return;}if(droppedCount>0&&queue.size()<CAPACITY-1){Entry gap=new Entry("capture_gap","notice","",new Object[]{"first_sequence",droppedFirst,"last_sequence",droppedLast,"count",droppedCount,"reason","Input buffer pressure; events unavailable"});gap.sequence=e.sequence;e.sequence=++sequence;queue.addLast(gap);droppedCount=0;}queue.addLast(e);}
        File[] files(){File[] f=directory.listFiles((d,n)->n.endsWith(".jsonl"));if(f==null)return new File[0];Arrays.sort(f,Comparator.comparing(File::getName));return f;}
        void drain(){long lossFirst=0,lossLast=0;int lossCount=0;try{ArrayList<Entry> batch=new ArrayList<>();synchronized(this){while(!queue.isEmpty())batch.add(queue.removeFirst());if(droppedCount>0){Entry gap=new Entry("capture_gap","notice","",new Object[]{"first_sequence",droppedFirst,"last_sequence",droppedLast,"count",droppedCount,"reason","Input buffer pressure"});gap.sequence=++sequence;batch.add(gap);droppedCount=0;}}if(batch.isEmpty())return;lossFirst=batch.get(0).sequence;lossLast=batch.get(batch.size()-1).sequence;lossCount=batch.size();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Trace directory unavailable");
            if(active==null){File[] old=files();fileIndex=old.length==0?0:Long.parseLong(old[old.length-1].getName().replace(".jsonl",""));rotate();}
            if(active.length()>SEGMENT)rotate();
            try(BufferedWriter out=Files.newBufferedWriter(active.toPath(),StandardCharsets.UTF_8,java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND)){if(failure!=null){out.write(batch.remove(0).json(this).put("capture_gap",failure).toString());out.newLine();failure=null;}for(Entry e:batch){JSONObject record=e.json(this);if(retention!=null){record.put("retention_rotation",retention);retention=null;}out.write(record.toString());out.newLine();}}
        }catch(Exception ex){failure="Storage failure: "+ex.getClass().getSimpleName()+". Potentially unavailable sequence range "+lossFirst+".."+lossLast+", count "+lossCount+" (partial persistence is possible).";}}
        void rotate()throws Exception {File[] old=files();String removed=null;if(old.length>=SEGMENTS){removed=old[old.length-SEGMENTS].getName();for(int i=0;i<=old.length-SEGMENTS;i++)if(!old[i].delete())throw new IOException("Trace retention could not remove segment");}active=new File(directory,String.format(Locale.ROOT,"%020d.jsonl",++fileIndex));if(removed!=null)retention="Old segments removed through "+removed;}
    }
}

package com.blackice.bc1;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.util.*;
/** One binding registry drives dispatch, the guide and customization. */
final class Shortcuts {
    static final int CTRL=KeyEvent.META_CTRL_ON,SHIFT=KeyEvent.META_SHIFT_ON,ALT=KeyEvent.META_ALT_ON;
    enum Command {
        PLAY("Play / Pause",KeyEvent.KEYCODE_SPACE,0),SAVE("Save project",KeyEvent.KEYCODE_S,CTRL),UNDO("Undo",KeyEvent.KEYCODE_Z,CTRL),REDO("Redo",KeyEvent.KEYCODE_Z,CTRL|SHIFT),IMPORT("Import media",KeyEvent.KEYCODE_I,CTRL),DUPLICATE("Duplicate selection",KeyEvent.KEYCODE_D,CTRL),SPLIT("Split selection",KeyEvent.KEYCODE_B,CTRL),DELETE("Delete timeline selection",KeyEvent.KEYCODE_FORWARD_DEL,0),PREVIOUS("Previous frame",KeyEvent.KEYCODE_DPAD_LEFT,0),NEXT("Next frame",KeyEvent.KEYCODE_DPAD_RIGHT,0),BACK_TEN("Back ten frames",KeyEvent.KEYCODE_DPAD_LEFT,SHIFT),FORWARD_TEN("Forward ten frames",KeyEvent.KEYCODE_DPAD_RIGHT,SHIFT),START("Timeline start",KeyEvent.KEYCODE_MOVE_HOME,0),END("Timeline end",KeyEvent.KEYCODE_MOVE_END,0),MARKER("Add timeline marker",KeyEvent.KEYCODE_M,0),ESCAPE("Cancel / Dismiss",KeyEvent.KEYCODE_ESCAPE,0),COPY("Copy attributes",KeyEvent.KEYCODE_C,CTRL),PASTE("Paste attributes",KeyEvent.KEYCODE_V,CTRL),ZOOM_IN("Zoom timeline in",KeyEvent.KEYCODE_EQUALS,CTRL),ZOOM_OUT("Zoom timeline out",KeyEvent.KEYCODE_MINUS,CTRL);
        final String title;final int key,mod;Command(String title,int key,int mod){this.title=title;this.key=key;this.mod=mod;}
    }
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("keyboard-shortcuts",0);}
    static String defaultBinding(Command c){String s=c.key+":"+c.mod;if(c==Command.REDO)s+=","+KeyEvent.KEYCODE_Y+":"+CTRL;if(c==Command.DELETE)s+=","+KeyEvent.KEYCODE_DEL+":0";return s;}
    static String binding(Context context,Command c){return prefs(context).getString(c.name(),defaultBinding(c));}
    static String label(String bindings){if(bindings.isEmpty())return "Unassigned";ArrayList<String> parts=new ArrayList<>();for(String b:bindings.split(",")){String[] p=b.split(":");try{int key=Integer.parseInt(p[0]),mod=Integer.parseInt(p[1]);parts.add(((mod&CTRL)!=0?"Ctrl+":"")+((mod&SHIFT)!=0?"Shift+":"")+((mod&ALT)!=0?"Alt+":"")+KeyEvent.keyCodeToString(key).replace("KEYCODE_","").replace('_',' '));}catch(RuntimeException ignored){parts.add("Invalid binding");}}return String.join(" / ",parts);}
    static int modifiers(KeyEvent e){int m=KeyEvent.normalizeMetaState(e.getMetaState());return m&(CTRL|SHIFT|ALT|KeyEvent.META_META_ON);}
    static Command resolve(Context context,KeyEvent e){String wanted=e.getKeyCode()+":"+modifiers(e);for(Command c:Command.values())if(Arrays.asList(binding(context,c).split(",")).contains(wanted))return c;return null;}
    static boolean dispatch(EditorActivity a,KeyEvent e){if(a.loading||a.getCurrentFocus() instanceof EditText)return false;Command command=resolve(a,e);if(command==null)return false;
        if(e.getRepeatCount()>0&&command!=Command.PREVIOUS&&command!=Command.NEXT&&command!=Command.BACK_TEN&&command!=Command.FORWARD_TEN)return true;
        switch(command){
            case PLAY:a.togglePlay();break;case SAVE:a.saveNow();break;case UNDO:a.undo(false);break;case REDO:a.undo(true);break;
            case IMPORT:a.pick(EditorActivity.IMPORT,"*/*");break;case DUPLICATE:a.duplicateSelected();break;case SPLIT:a.splitSelected();break;
            case DELETE:View focus=a.getCurrentFocus();if(focus==null||a.timeline!=null&&a.timeline.hasFocus()||a.previewControls!=null&&a.previewControls.hasFocus())a.deleteSelection();break;
            case PREVIOUS:case NEXT:case BACK_TEN:case FORWARD_TEN:
                int count=command==Command.BACK_TEN||command==Command.FORWARD_TEN?10:1;int direction=command==Command.PREVIOUS||command==Command.BACK_TEN?-1:1;a.pausePlayback();long frame=Math.round(a.project.playheadMs*a.project.frameRate/1000.0);a.seek(Math.round((frame+direction*count)*1000.0/a.project.frameRate));break;
            case START:a.pausePlayback();a.seek(0);break;case END:a.pausePlayback();a.seek(a.project.durationMs());break;case MARKER:a.toggleMarker();break;
            case COPY:a.copyAttributes();break;case PASTE:a.pasteAttributes();break;case ZOOM_IN:a.zoomTimeline(1.5f,Float.NaN);break;case ZOOM_OUT:a.zoomTimeline(1/1.5f,Float.NaN);break;
            case ESCAPE:a.cancelLiveAdjust();if(a.panels.showing())a.panels.back();else if(a.mobile==null||!a.mobile.dismiss())a.deselect();break;
        }return true;
    }
    static void show(Activity a){
        LinearLayout body=new LinearLayout(a);body.setOrientation(LinearLayout.VERTICAL);int pad=(int)(16*a.getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);
        EditText search=new EditText(a);search.setSingleLine(true);search.setHint("Search keyboard shortcuts");body.addView(search);
        ScrollView scroll=new ScrollView(a);LinearLayout rows=new LinearLayout(a);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,(int)(Math.min(320,Math.max(96,a.getResources().getConfiguration().screenHeightDp-210))*a.getResources().getDisplayMetrics().density)));
        final AlertDialog[] guideRef=new AlertDialog[1];
        Runnable render=()->{rows.removeAllViews();String q=search.getText().toString().trim().toLowerCase(Locale.ROOT);for(Command c:Command.values()){String text=c.title+" • "+label(binding(a,c));if(!text.toLowerCase(Locale.ROOT).contains(q))continue;Button b=new Button(a);b.setText(text);rows.addView(b);b.setOnClickListener(v->{if(guideRef[0]!=null)guideRef[0].dismiss();edit(a,c,()->show(a));});}};
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int n,int af){}public void onTextChanged(CharSequence s,int st,int before,int count){render.run();}public void afterTextChanged(Editable e){}});render.run();
        AlertDialog guide=new AlertDialog.Builder(a).setTitle("Keyboard shortcuts").setView(body).setNegativeButton("Close",null).setNeutralButton("Restore defaults",(d,w)->new AlertDialog.Builder(a).setTitle("Restore all default bindings?").setNegativeButton("Cancel",null).setPositiveButton("Restore",(x,y)->{prefs(a).edit().clear().apply();show(a);}).show()).create();
        // Refresh this same guide after a binding edit instead of accumulating dialogs.
        for(int i=0;i<rows.getChildCount();i++){final Button button=(Button)rows.getChildAt(i);button.setOnClickListener(v->{for(Command c:Command.values())if(button.getText().toString().startsWith(c.title+" •")){guide.dismiss();edit(a,c,()->show(a));break;}});}
        guideRef[0]=guide;guide.show();
    }
    static void edit(Activity a,Command command,Runnable refresh){new AlertDialog.Builder(a).setTitle(command.title).setItems(new String[]{"Record shortcut","Remove binding","Restore this default"},(d,i)->{
        if(i==1){prefs(a).edit().putString(command.name(),"").apply();refresh.run();return;}if(i==2){assign(a,command,defaultBinding(command),refresh);return;}
        AlertDialog record=new AlertDialog.Builder(a).setTitle("Press a shortcut").setMessage("Use a key with optional Ctrl or Shift. System and Alt/Meta combinations are reserved.").setNegativeButton("Cancel",(x,y)->refresh.run()).create();
        record.setOnKeyListener((dialog,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN||KeyEvent.isModifierKey(key))return false;if(key==KeyEvent.KEYCODE_BACK)return false;int mod=modifiers(event);
            if((mod&(ALT|KeyEvent.META_META_ON))!=0||key==KeyEvent.KEYCODE_HOME||key==KeyEvent.KEYCODE_POWER||key==KeyEvent.KEYCODE_APP_SWITCH||((mod&CTRL)!=0&&(key==KeyEvent.KEYCODE_SPACE||key==KeyEvent.KEYCODE_ESCAPE))){Toast.makeText(a,"This combination is reserved by the system",Toast.LENGTH_SHORT).show();return true;}
            record.dismiss();assign(a,command,key+":"+mod,refresh);return true;});record.show();
    }).setNegativeButton("Cancel",(d,w)->refresh.run()).show();}
    static void assign(Activity a,Command target,String value,Runnable refresh){List<Command> conflicts=new ArrayList<>();for(Command c:Command.values())if(c!=target)for(String b:value.split(","))if(Arrays.asList(binding(a,c).split(",")).contains(b)){conflicts.add(c);break;}
        Runnable save=()->{SharedPreferences.Editor editor=prefs(a).edit();for(Command conflict:conflicts){ArrayList<String> remaining=new ArrayList<>(Arrays.asList(binding(a,conflict).split(",")));remaining.removeAll(Arrays.asList(value.split(",")));editor.putString(conflict.name(),String.join(",",remaining));}editor.putString(target.name(),value).apply();refresh.run();};
        if(conflicts.isEmpty())save.run();else new AlertDialog.Builder(a).setTitle("Shortcut conflict").setMessage("Reassign "+label(value)+" from "+conflicts.stream().map(c->c.title).collect(java.util.stream.Collectors.joining(", "))+"?").setNegativeButton("Cancel",(d,w)->refresh.run()).setPositiveButton("Reassign",(d,w)->save.run()).show();
    }
}

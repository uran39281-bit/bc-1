package com.blackice.bc1;

import android.app.AlertDialog;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.*;

/** Explicit per-property key selection. A playhead value alone never selects an interval. */
final class EasingControls {
    final EditorActivity a;final Map<String,String> selected=new HashMap<>();
    EasingControls(EditorActivity a){this.a=a;}
    void save(android.os.Bundle state){android.os.Bundle keys=new android.os.Bundle();for(Map.Entry<String,String> entry:selected.entrySet())keys.putString(entry.getKey(),entry.getValue());state.putBundle("easing-selection",keys);}
    void restore(android.os.Bundle state){if(state==null)return;android.os.Bundle keys=state.getBundle("easing-selection");if(keys!=null)for(String key:keys.keySet()){String id=keys.getString(key);if(id!=null)selected.put(key,id);}}
    String scope(int property){return a.project.id+":"+(a.clip()!=null?a.clip().id:a.overlay()!=null?a.overlay().id:"")+":"+property;}
    void select(int property,long time){Motion m=a.selectedMotion();if(m==null)return;Motion.Key k=Keyframes.keyAt(m,property,time,a.panels.tolerance());if(k!=null)selected.put(scope(property),k.id);else selected.remove(scope(property));}
    void build(LinearLayout parent,int property){
        Motion m=a.selectedMotion();if(m==null)return;List<Motion.Key> keys=m.curves.get(property);String id=selected.get(scope(property));int index=-1;
        for(int i=0;i<keys.size();i++)if(keys.get(i).id.equals(id)){index=i;break;}
        String title=Motion.PROPERTIES[property];boolean interval=index>=0&&index+1<keys.size();
        String status=index<0?"Select a keyframe.":!interval?"No next keyframe.":title+" · Key "+(index+1)+" → Key "+(index+2);
        TextView label=a.button(status,v->choose(property));label.setContentDescription(title+" easing: "+status);parent.addView(label,new LinearLayout.LayoutParams(-1,a.dp(48)));
        int preset=interval?Keyframes.easingOf(keys.get(index)):-1;
        if(interval&&preset<0)parent.addView(a.note("Existing interpolation"));
        LinearLayout row=null;
        for(int i=0;i<4;i++){if(i%2==0){row=a.row();parent.addView(row);}final int choice=i;TextView b=a.button(Keyframes.EASINGS[i],v->apply(property,choice));b.setSelected(interval&&preset==i);b.setTextColor(interval&&preset==i?EditorActivity.CYAN:EditorActivity.TEXT);b.setBackground(a.buttonBackground(interval&&preset==i));EditorActivity.dim(b,interval&&!a.selectionLocked()&&!a.uiLocked);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(48),1);lp.topMargin=a.dp(8);if(i%2==1)lp.leftMargin=a.dp(8);row.addView(b,lp);}
    }
    void choose(int property){Motion m=a.selectedMotion();if(m==null)return;List<Motion.Key> keys=m.curves.get(property);if(keys.isEmpty()){a.tell("Add a keyframe to "+Motion.PROPERTIES[property]+" first.");return;}
        String[] names=new String[keys.size()];for(int i=0;i<keys.size();i++)names[i]="Key "+(i+1)+" · "+EditorActivity.seconds(keys.get(i).timeMs)+" s";
        new AlertDialog.Builder(a).setTitle(Motion.PROPERTIES[property]+" keyframe").setItems(names,(d,i)->{selected.put(scope(property),keys.get(i).id);a.renderInspector();}).setNegativeButton("Cancel",null).show();
    }
    void apply(int property,int preset){Motion m=a.selectedMotion();if(m==null||!a.layerEditable())return;List<Motion.Key> keys=m.curves.get(property);String id=selected.get(scope(property));
        for(int i=0;i+1<keys.size();i++){Motion.Key key=keys.get(i);if(key.id.equals(id)){if(Keyframes.easingOf(key)==preset)return;a.edit(Motion.PROPERTIES[property]+" easing updated",()->Keyframes.ease(key,preset));return;}}
    }
}

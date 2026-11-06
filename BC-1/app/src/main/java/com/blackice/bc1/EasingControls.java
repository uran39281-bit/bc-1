package com.blackice.bc1;

import android.widget.*;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.menu.*;
import java.util.*;

/** Stable selected-key identities and presentation-only expansion state. No selection invents keys. */
final class EasingControls {
    final EditorActivity a;final Map<String,String> selected=new HashMap<>();
    final Map<String,Boolean> expanded=new HashMap<>();
    EasingControls(EditorActivity a){this.a=a;}
    void save(android.os.Bundle state){android.os.Bundle keys=new android.os.Bundle();for(Map.Entry<String,String> e:selected.entrySet())keys.putString(e.getKey(),e.getValue());state.putBundle("easing-selection",keys);android.os.Bundle sections=new android.os.Bundle();for(Map.Entry<String,Boolean> e:expanded.entrySet())sections.putBoolean(e.getKey(),e.getValue());state.putBundle("easing-expanded",sections);}
    void restore(android.os.Bundle state){if(state==null)return;android.os.Bundle keys=state.getBundle("easing-selection");if(keys!=null)for(String key:keys.keySet()){String id=keys.getString(key);if(id!=null)selected.put(key,id);}android.os.Bundle sections=state.getBundle("easing-expanded");if(sections!=null)for(String key:sections.keySet())expanded.put(key,sections.getBoolean(key));}
    String scope(int property){return a.project.id+":"+(a.clip()!=null?a.clip().id:a.overlay()!=null?a.overlay().id:"")+":"+property;}
    void select(int property,long time){Motion m=a.selectedMotion();if(m==null)return;Motion.Key k=Keyframes.keyAt(m,property,time,a.panels.tolerance());if(k!=null)selected.put(scope(property),k.id);else selected.remove(scope(property));}
    void build(LinearLayout parent,int property){
        Motion motion=a.selectedMotion();if(motion==null)return;String scope=scope(property);String identity=selected.get(scope);List<Motion.Key> keys=motion.curves.get(property);
        int index=CurveEdits.indexOf(keys,identity);Motion.Key first=index>=0&&index+1<keys.size()?keys.get(index):null;
        String status=index<0?"Select a keyframe.":first==null?"No next keyframe.":Motion.PROPERTIES[property]+" · Key "+(index+1)+" → Key "+(index+2);
        LinearLayout content=a.column();TextView select=a.button(status,v->choose(property));content.addView(select);
        CurveEditorView graph=new CurveEditorView(a,new CurveEditorView.Model(){
            public CubicBezier curve(){return first==null?CubicBezier.LINEAR:CurveEdits.seed(first);}
            public boolean editable(){return first!=null&&scope.equals(scope(property))&&a.layerEditable();}
            public String intervalLabel(){return status;}
            public double progressAt(double u){return first==null?u:CurveEdits.progress(first,u);}
        },new CurveEditorView.Edits(){
            public void begin(){a.pausePlayback();a.beginLiveAdjust();}
            public void preview(CubicBezier c){if(first!=null&&scope.equals(scope(property)))a.liveAdjust(()->CurveEdits.write(first,c));}
            public void commit(){a.endLiveAdjust("Easing curve updated",()->{});}
            public void cancel(){a.cancelLiveAdjust();}
        });content.addView(graph);
        TextView reset=a.button("Reset curve",v->{if(first!=null&&scope.equals(scope(property))&&a.layerEditable())a.edit("Easing curve reset",()->CurveEdits.write(first,CubicBezier.LINEAR));});reset.setEnabled(first!=null);content.addView(reset);
        for(int i=0;i<4;i++){final int preset=i;TextView button=a.button(Keyframes.EASINGS[i],v->apply(property,preset));button.setEnabled(first!=null);content.addView(button);}
        CollapsibleSection section=new CollapsibleSection(a,scope,expanded,"Easing Curve Editor",content,a::cancelLiveAdjust);section.context(status);parent.addView(section);
    }
    void choose(int property){Motion m=a.selectedMotion();if(m==null)return;List<Motion.Key> keys=m.curves.get(property);if(keys.isEmpty()){a.tell("Add a keyframe to "+Motion.PROPERTIES[property]+" first.");return;}String[] names=new String[keys.size()];for(int i=0;i<names.length;i++)names[i]="Key "+(i+1)+" · "+EditorActivity.seconds(keys.get(i).timeMs)+" s";BlackIceDialog.choices(a,Motion.PROPERTIES[property]+" keyframe",names,-1,i->{selected.put(scope(property),keys.get(i).id);a.renderInspector();});}
    void apply(int property,int preset){Motion m=a.selectedMotion();if(m==null||!a.layerEditable())return;List<Motion.Key> keys=m.curves.get(property);String id=selected.get(scope(property));for(int i=0;i+1<keys.size();i++)if(keys.get(i).id.equals(id)){Motion.Key key=keys.get(i);if(Keyframes.easingOf(key)!=preset)a.edit(Motion.PROPERTIES[property]+" easing updated",()->Keyframes.ease(key,preset));return;}}
    String fillScope(String property){return a.project.id+":"+(a.overlay()==null?"":a.overlay().id)+":fill:"+property;}
    void selectFill(String property,FillStyle.Track track,long time){FillStyle.Track.Interval key=track.interval(time);if(key==null)selected.remove(fillScope(property));else selected.put(fillScope(property),key.id);}
    void buildFill(LinearLayout parent,String title,String property,java.util.function.Supplier<FillStyle.Track> source,Runnable activate){
        String scope=fillScope(property),identity=selected.get(scope);FillStyle.Track track=source.get();List<Long> times=new ArrayList<>(track.keys.keySet());int index=-1;for(int i=0;i<times.size();i++)if(track.interval(times.get(i)).id.equals(identity))index=i;
        final Long start=index>=0&&index+1<times.size()?times.get(index):null;
        String status=index<0?"Select a keyframe.":start==null?"No next keyframe.":title+" · Key "+(index+1)+" → Key "+(index+2);
        java.util.function.BooleanSupplier valid=()->start!=null&&scope.equals(fillScope(property))&&a.overlay()!=null&&source.get().keys.higherKey(start)!=null&&source.get().interval(start)!=null&&identity.equals(source.get().interval(start).id);
        LinearLayout content=a.column();content.addView(a.button(status,v->{List<Long> options=new ArrayList<>(source.get().keys.keySet());if(options.isEmpty()){a.tell("Add a keyframe to "+title+" first.");return;}String[] names=new String[options.size()];for(int i=0;i<names.length;i++)names[i]="Key "+(i+1)+" · "+EditorActivity.seconds(options.get(i))+" s";BlackIceDialog.choices(a,title+" keyframe",names,-1,i->{activate.run();selectFill(property,source.get(),options.get(i));a.renderInspector();});}));
        CurveEditorView graph=new CurveEditorView(a,new CurveEditorView.Model(){
            public CubicBezier curve(){if(!valid.getAsBoolean())return CubicBezier.LINEAR;FillStyle.Track.Interval k=source.get().interval(start);return k.easing<0?new CubicBezier(k.x1,k.y1,k.x2,k.y2):k.easing==1?new CubicBezier(.333,0,.667,.333):k.easing==2?new CubicBezier(.333,.667,.667,1):k.easing==3?new CubicBezier(.42,0,.58,1):CubicBezier.LINEAR;}
            public boolean editable(){return valid.getAsBoolean()&&!a.selectionLocked()&&!a.uiLocked;}
            public String intervalLabel(){return status;}
            public double progressAt(double u){return valid.getAsBoolean()?source.get().interval(start).progress(u):u;}
        },new CurveEditorView.Edits(){
            public void begin(){activate.run();a.fillPanel.begin();}
            public void preview(CubicBezier c){if(valid.getAsBoolean())a.fillPanel.live(()->source.get().curve(start,(float)c.x1,(float)c.y1,(float)c.x2,(float)c.y2));}
            public void commit(){a.fillPanel.end(false);}public void cancel(){a.fillPanel.end(true);}
        });content.addView(graph);TextView reset=a.button("Reset curve",v->{if(valid.getAsBoolean())a.fillPanel.commit(title+" easing reset",()->source.get().ease(start,0));});reset.setEnabled(start!=null);content.addView(reset);
        for(int i=0;i<4;i++){final int preset=i;TextView choice=a.button(Keyframes.EASINGS[i],v->{if(valid.getAsBoolean()){activate.run();a.fillPanel.commit(title+" easing updated",()->source.get().ease(start,preset));}});choice.setContentDescription(title+" "+Keyframes.EASINGS[i]);choice.setEnabled(start!=null);choice.setSelected(start!=null&&track.interval(start).easing==i);if(choice.isSelected())choice.setBackground(MenuPalette.surfaceGreen(a));content.addView(choice);}
        CollapsibleSection section=new CollapsibleSection(a,scope,expanded,"Easing Curve Editor",content,()->a.fillPanel.end(true));section.context(status);parent.addView(section);
    }
}

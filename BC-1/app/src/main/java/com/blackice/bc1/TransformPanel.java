package com.blackice.bc1;

import android.app.AlertDialog;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.*;

/** Shared phone/DeX transform controls, using the same model and live renderer as export. */
final class TransformPanel {
    private final EditorActivity a;
    int tab;
    boolean linked=true;
    float angleSnap=0;
    static final String[] TABS={"Position","Rotation","Scale"};
    /** Pair of dimensions captured when a drag starts, so a linked drag scales from the original proportions. */
    private TransformMath.Transform editBase;
    /** The five menus (position, rotation, scale, blending and effects) mounted into the inspector. */
    final MenuPanels menus;
    TransformPanel(EditorActivity activity){a=activity;menus=new MenuPanels(activity);}
    TransformMath.Transform current(){EditProject.Clip c=a.clip();return c!=null?TransformMath.of(c,a.panels.motionTime()):TransformMath.of(a.overlay(),a.panels.motionTime());}
    private Motion motion(){return a.selectedMotion();}
    void write(int property,float value){
        if(!Motion.allowed(property,value))throw new IllegalArgumentException(Motion.PROPERTIES[property]+" is outside the supported range.");
        if(a.overlay()!=null&&property==Motion.SCALE&&value<.1f)throw new IllegalArgumentException("Overlay Width must be at least 10% of its base size.");
        if(Keyframes.set(motion(),property,a.panels.motionTime(),value,a.panels.tolerance()))a.setStatic(property,value);
    }
    void separate(){if(a.clip()!=null)TransformEdits.separateHeight(a.clip());else TransformEdits.separateHeight(a.overlay());}
    void writeDimensions(float w,float h,boolean paired){
        TransformEdits.scalePair(w,h,Motion.SCALE,w,false);
        if(a.overlay()!=null&&w<.1f)throw new IllegalArgumentException("Overlay Width must be positive and at least 10% of its base size.");
        separate();Motion m=motion();long time=a.panels.motionTime();
        if(paired&&(!m.curves.get(Motion.SCALE).isEmpty()||!m.curves.get(Motion.HEIGHT).isEmpty())){
            TransformMath.Transform t=current();Keyframes.enable(m,Motion.SCALE,time,t.scale);Keyframes.enable(m,Motion.HEIGHT,time,t.scale*t.aspect);
        }
        write(Motion.SCALE,w);write(Motion.HEIGHT,h);
    }
    private float value(int p){TransformMath.Transform t=current();return p==Motion.X?t.x:p==Motion.Y?t.y:p==Motion.ROTATION?t.rotation:p==Motion.SCALE?t.scale:t.scale*t.aspect;}
    /** Writes a model-unit value; a linked width or height edit scales both from the gesture's starting pair. */
    void applyInternal(int p,float v){
        if(p==Motion.SCALE||p==Motion.HEIGHT){TransformMath.Transform t=editBase==null?current():editBase;float[] pair=TransformEdits.scalePair(t.scale,t.scale*t.aspect,p,v,linked);
            if(linked)writeDimensions(pair[0],pair[1],true);
            else{separate();write(p,v);}
        }else write(p,v);
    }
    void commit(String label,Runnable change){if(!a.layerEditable())return;a.pausePlayback();a.edit(label,change);a.renderInspector();}
    /** Mounts the Position, Rotation or Scale menu for the current tab. */
    void build(LinearLayout into){
        if(a.clip()==null&&a.overlay()==null)return;
        if(a.clip()!=null&&a.clip().audioOnly){into.addView(a.button("‹  Back",v->a.panels.back()),new LinearLayout.LayoutParams(-2,a.dp(48)));into.addView(a.note("Audio has no picture to transform."));return;}
        tab=Math.max(0,Math.min(2,tab));
        menus.mount(into,tab==0?com.blackice.bc1.menu.MenuScreenFactory.Menu.POSITION:tab==1?com.blackice.bc1.menu.MenuScreenFactory.Menu.ROTATION:com.blackice.bc1.menu.MenuScreenFactory.Menu.SCALE);
    }
    boolean canFrame(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(a.loading||a.uiLocked||a.multiCount()>1||a.selectionLocked()||(c==null&&o==null)||c!=null&&c.audioOnly)return false;
        long t=a.project.playheadMs,start=c!=null?a.project.placements().get(a.selected).startMs:o.startMs,end=c!=null?a.project.placements().get(a.selected).endMs:o.endMs;
        return t>=start&&t<=end&&!a.selectionGeometry.bounds(c,o).isEmpty();
    }
    /** Fit or Fill Canvas: one undoable position and scale change using the shared framing geometry. */
    void frame(boolean fill){
        if(!canFrame()||!a.layerEditable())return;
        try{RectF b=a.selectionGeometry.bounds(a.clip(),a.overlay());TransformMath.Transform before=current(),next=CanvasFraming.solve(before,a.previewAspect(),b.left,-b.bottom,b.right,-b.top,fill,a.overlay()==null?.05f:.1f);
            EditProject.Overlay overlay=a.overlay();
            if(overlay!=null&&overlay.shape==0&&overlay.imageUri==null){int[] size=a.project.canvasSize(1080);boolean converged=false;for(int i=0;i<20;i++){RectF actual=LayerGeometry.textBounds(overlay,size[0],size[1],next.scale);TransformMath.Transform refined=CanvasFraming.solve(next,a.previewAspect(),actual.left,-actual.bottom,actual.right,-actual.top,fill,.1f);if(CanvasFraming.same(next,refined)){next=refined;converged=true;break;}next=refined;}if(!converged)throw new IllegalArgumentException("The existing text width limit prevents this framing. Adjust the text or use Fit Canvas.");}
            if(CanvasFraming.same(before,next))return;TransformMath.Transform target=next;
            commit(fill?"Fill Canvas":"Fit Canvas",()->{writeDimensions(target.scale,target.scale*target.aspect,false);write(Motion.X,target.x);write(Motion.Y,target.y);});
        }catch(IllegalArgumentException error){a.tell(error.getMessage());}
    }
    void resetAll(){commit("Position reset",()->{write(Motion.X,0);write(Motion.Y,a.overlay()==null?0:new EditProject.Overlay().y);});}
    /** Adds degrees to the displayed angle; the model stores the opposite sign, and nothing wraps at 360. */
    void increment(float degrees){commit("Rotation increment",()->write(Motion.ROTATION,value(Motion.ROTATION)-degrees));}
    /** Starts a gesture: later linked width/height edits scale from this pair. */
    void beginGesture(){editBase=current();}
    void clearGesture(){editBase=null;}
    /** Keeps the menus' readouts in step with the playhead, selection and live edits. */
    void refresh(){menus.refresh();}
}

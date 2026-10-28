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
    private final Map<Integer,TextView> values=new HashMap<>(),diamonds=new HashMap<>();
    private TextView previousKey,nextKey;
    private final Map<Integer,ValueRuler> rulers=new HashMap<>();
    private Dial dial;
    private TransformMath.Transform editBase;
    TransformPanel(EditorActivity activity){a=activity;}
    TransformMath.Transform current(){EditProject.Clip c=a.clip();return c!=null?TransformMath.of(c,a.panels.motionTime()):TransformMath.of(a.overlay(),a.panels.motionTime());}
    private Motion motion(){return a.selectedMotion();}
    void write(int property,float value){
        if(!Motion.allowed(property,value))throw new IllegalArgumentException(Motion.PROPERTIES[property]+" is outside the supported range.");
        if(a.overlay()!=null&&property==Motion.SCALE&&value<.1f)throw new IllegalArgumentException("Overlay Width must be at least 10% of its base size.");
        if(Keyframes.set(motion(),property,a.panels.motionTime(),value,a.panels.tolerance()))a.setStatic(property,value);
    }
    private void separate(){if(a.clip()!=null)TransformEdits.separateHeight(a.clip());else TransformEdits.separateHeight(a.overlay());}
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
    private float display(int p){return p==Motion.X?a.exportSize()[0]/2f:p==Motion.Y?a.exportSize()[1]/2f:p==Motion.ROTATION?-1:100/neutralScale();}
    private float neutralScale(){return a.overlay()==null?1:new EditProject.Overlay().scale;}
    private String unit(int p){return p<2?" px":p==Motion.ROTATION?"°":"%";}
    private String title(int p){return p==Motion.SCALE?"Width":Motion.PROPERTIES[p];}
    private void apply(int p,float v){
        if(p==Motion.SCALE||p==Motion.HEIGHT){TransformMath.Transform t=editBase==null?current():editBase;float[] pair=TransformEdits.scalePair(t.scale,t.scale*t.aspect,p,v,linked);
            if(linked)writeDimensions(pair[0],pair[1],true);
            else{separate();write(p,v);}
        }else write(p,v);
    }
    void commit(String label,Runnable change){if(!a.layerEditable())return;a.pausePlayback();a.edit(label,change);a.renderInspector();}
    void build(LinearLayout into){
        values.clear();diamonds.clear();rulers.clear();dial=null;
        if(a.clip()==null&&a.overlay()==null)return;
        if(a.clip()!=null&&a.clip().audioOnly){into.addView(a.note("Audio has no picture to transform."));return;}
        tab=Math.max(0,Math.min(2,tab));LinearLayout tabs=a.row();
        for(int i=0;i<3;i++){final int n=i;LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);TextView b=a.button(TABS[i],v->{tab=n;a.renderInspector();});b.setSelected(i==tab);b.setTextColor(i==tab?EditorActivity.CYAN:EditorActivity.TEXT);b.setContentDescription(TABS[i]+(i==tab?", selected":""));col.addView(b,new LinearLayout.LayoutParams(-1,a.dp(48)));View line=new View(a);line.setBackgroundColor(i==tab?EditorActivity.CYAN:Color.TRANSPARENT);col.addView(line,new LinearLayout.LayoutParams(-1,a.dp(3)));tabs.addView(col,new LinearLayout.LayoutParams(0,-2,1));}into.addView(tabs);
        if(tab==0)into.addView(a.button("Reset all",v->resetAll()),new LinearLayout.LayoutParams(-1,a.dp(48)));
        navigation(into);
        if(tab==0){control(into,Motion.X,true);control(into,Motion.Y,true);}
        else if(tab==1){dial=new Dial();into.addView(dial,new LinearLayout.LayoutParams(-1,a.dp(230)));control(into,Motion.ROTATION,false);into.addView(a.label("Presets",15,EditorActivity.TEXT,true));LinearLayout presets=a.row();for(int amount:new int[]{45,90,180,360}){LinearLayout column=new LinearLayout(a);column.setOrientation(LinearLayout.VERTICAL);column.addView(a.compactButton("+","Add "+amount+" degrees",v->increment(amount)),new LinearLayout.LayoutParams(-1,a.dp(48)));TextView label=a.label(amount+"°",14,EditorActivity.TEXT,true);label.setGravity(Gravity.CENTER);column.addView(label,new LinearLayout.LayoutParams(-1,a.dp(36)));column.addView(a.compactButton("−","Subtract "+amount+" degrees",v->increment(-amount)),new LinearLayout.LayoutParams(-1,a.dp(48)));presets.addView(column,new LinearLayout.LayoutParams(0,-2,1));}into.addView(presets);}
        else{framing(into);control(into,Motion.SCALE,true);TextView lock=a.button("↔  Lock W and H",v->{TransformMath.Transform t=current();if(!linked&&!(t.scale>0&&t.aspect>0)){a.tell("Both dimensions must be above zero before linking.");return;}linked=!linked;a.renderInspector();});lock.setCompoundDrawables(a.icon("Link",EditorActivity.CYAN),null,null,null);lock.setSelected(linked);lock.setBackground(a.buttonBackground(linked));lock.setTextColor(linked?EditorActivity.CYAN:EditorActivity.TEXT);lock.setContentDescription("Lock W and H: "+(linked?"on":"off"));into.addView(lock,new LinearLayout.LayoutParams(-1,a.dp(52)));control(into,Motion.HEIGHT,true);}
        refresh();
    }
    private boolean canFrame(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(a.loading||a.uiLocked||a.multiCount()>1||a.selectionLocked()||(c==null&&o==null)||c!=null&&c.audioOnly)return false;
        long t=a.project.playheadMs,start=c!=null?a.project.placements().get(a.selected).startMs:o.startMs,end=c!=null?a.project.placements().get(a.selected).endMs:o.endMs;
        return t>=start&&t<=end&&!a.selectionGeometry.bounds(c,o).isEmpty();
    }
    private void framing(LinearLayout into){
        LinearLayout row=a.row();boolean stack=a.getResources().getConfiguration().fontScale>1.3f||a.getResources().getConfiguration().screenWidthDp<360;
        row.setOrientation(stack?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);
        for(boolean fill:new boolean[]{false,true}){TextView b=a.button(fill?"Fill Canvas":"Fit Canvas",v->frame(fill));b.setContentDescription(fill?"Fill Canvas":"Fit Canvas");EditorActivity.dim(b,canFrame());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(stack?-1:0,a.dp(48),stack?0:1);if(fill){if(stack)lp.topMargin=a.dp(8);else lp.leftMargin=a.dp(8);}row.addView(b,lp);}into.addView(row);
    }
    private void frame(boolean fill){
        if(!canFrame()||!a.layerEditable())return;
        try{RectF b=a.selectionGeometry.bounds(a.clip(),a.overlay());TransformMath.Transform before=current(),next=CanvasFraming.solve(before,a.previewAspect(),b.left,-b.bottom,b.right,-b.top,fill,a.overlay()==null?.05f:.1f);
            EditProject.Overlay overlay=a.overlay();
            if(overlay!=null&&overlay.shape==0&&overlay.imageUri==null){int[] size=a.project.canvasSize(1080);boolean converged=false;for(int i=0;i<20;i++){RectF actual=LayerGeometry.textBounds(overlay,size[0],size[1],next.scale);TransformMath.Transform refined=CanvasFraming.solve(next,a.previewAspect(),actual.left,-actual.bottom,actual.right,-actual.top,fill,.1f);if(CanvasFraming.same(next,refined)){next=refined;converged=true;break;}next=refined;}if(!converged)throw new IllegalArgumentException("The existing text width limit prevents this framing. Adjust the text or use Fit Canvas.");}
            if(CanvasFraming.same(before,next))return;TransformMath.Transform target=next;
            commit(fill?"Fill Canvas":"Fit Canvas",()->{writeDimensions(target.scale,target.scale*target.aspect,false);write(Motion.X,target.x);write(Motion.Y,target.y);});
        }catch(IllegalArgumentException error){a.tell(error.getMessage());}
    }
    void resetAll(){commit("Position reset",()->{write(Motion.X,0);write(Motion.Y,a.overlay()==null?0:new EditProject.Overlay().y);});}
    void increment(float degrees){commit("Rotation increment",()->write(Motion.ROTATION,value(Motion.ROTATION)-degrees));}
    private int[] properties(){return tab==0?new int[]{Motion.X,Motion.Y}:tab==1?new int[]{Motion.ROTATION}:new int[]{Motion.SCALE,Motion.HEIGHT};}
    private long adjacent(boolean next){long now=a.panels.motionTime(),best=-1,tol=a.panels.tolerance();for(int p:properties())for(Motion.Key k:motion().curves.get(p)){if(Math.abs(k.timeMs-now)<=tol||k.timeMs<0)continue;if(next?k.timeMs>now:k.timeMs<now){if(best<0||(next?k.timeMs<best:k.timeMs>best))best=k.timeMs;}}return best;}
    private void navigation(LinearLayout into){LinearLayout row=a.row();for(boolean next:new boolean[]{false,true}){TextView b=a.button(next?"Next Key  ›":"‹  Previous Key",v->{a.pausePlayback();long t=adjacent(next);if(t<0)return;EditProject.Clip c=a.clip();long time=c!=null?a.project.placements().get(a.selected).startMs+Math.round((t-c.inMs)/(double)c.speed):a.overlay().startMs+t;a.seek(time);for(int p:properties())if(Keyframes.keyAt(motion(),p,t,a.panels.tolerance())!=null){a.easingControls.select(p,t);break;}a.renderInspector();});if(next)nextKey=b;else previousKey=b;EditorActivity.dim(b,adjacent(next)>=0);row.addView(b,new LinearLayout.LayoutParams(0,a.dp(48),1));}into.addView(row);}
    private void control(LinearLayout into,int p,boolean ruler){
        LinearLayout card=new LinearLayout(a);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(a.dp(4),a.dp(6),a.dp(4),a.dp(6));card.setBackground(a.buttonBackground(false));LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);
        TextView diamond=a.compactButton("◇",title(p)+" keyframe",v->toggleKey(p));diamonds.put(p,diamond);row.addView(diamond,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        TextView label=a.label(title(p),12,EditorActivity.TEXT,true);row.addView(label,new LinearLayout.LayoutParams(0,a.dp(48),.75f));
        TextView number=a.compactButton("",title(p)+" exact value",v->numeric(p));number.setTextSize(14);values.put(p,number);row.addView(number,new LinearLayout.LayoutParams(0,a.dp(48),1.4f));
        row.addView(a.compactButton("Reset","Reset "+title(p),v->commit(title(p)+" reset",()->apply(p,p==Motion.SCALE||p==Motion.HEIGHT?neutralScale():p==Motion.Y&&a.overlay()!=null?new EditProject.Overlay().y:0))),new LinearLayout.LayoutParams(a.dp(52),a.dp(48)));card.addView(row);
        if(ruler){ValueRuler r=new ValueRuler(p);rulers.put(p,r);card.addView(r,new LinearLayout.LayoutParams(-1,a.dp(56)));}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=a.dp(10);into.addView(card,lp);a.easingControls.build(into,p);
    }
    void refresh(){if(a.clip()==null&&a.overlay()==null)return;for(int p:values.keySet()){String formatted=String.format(Locale.US,"%.2f%s",value(p)*display(p),unit(p));if(!formatted.contentEquals(values.get(p).getText()))values.get(p).setText(formatted);int effective=p==Motion.HEIGHT&&(a.clip()!=null?a.clip().heightScale:a.overlay().heightScale)<0?Motion.SCALE:p;int state=Keyframes.state(motion(),effective,a.panels.motionTime(),a.panels.tolerance());TextView d=diamonds.get(p);String glyph=state==Keyframes.ON_KEY?"◆":state==Keyframes.ANIMATED?"◇·":"◇";if(!glyph.contentEquals(d.getText()))d.setText(glyph);d.setTextColor(state==Keyframes.STATIC?EditorActivity.MUTED:EditorActivity.CYAN);d.setContentDescription(title(p)+" keyframe: "+(state==Keyframes.ON_KEY?"at this frame; remove":state==Keyframes.ANIMATED?"animated elsewhere; add here":"static; add here"));}for(ValueRuler r:rulers.values())r.invalidate();if(dial!=null)dial.invalidate();if(previousKey!=null)EditorActivity.dim(previousKey,adjacent(false)>=0);if(nextKey!=null)EditorActivity.dim(nextKey,adjacent(true)>=0);}
    private void toggleKey(int p){commit(title(p)+" keyframe",()->{if(p==Motion.SCALE||p==Motion.HEIGHT)separate();Motion m=motion();long t=a.panels.motionTime(),tol=a.panels.tolerance();float v=value(p);Motion.Key key=Keyframes.keyAt(m,p,t,tol);if(key!=null){m.curves.get(p).remove(key);if(m.curves.get(p).isEmpty())a.setStatic(p,v);}else m.put(p,new Motion.Key(t,v));a.easingControls.select(p,t);});}
    private void numeric(int p){a.pausePlayback();EditText input=new EditText(a);input.setSingleLine();input.setSelectAllOnFocus(true);input.setText(String.format(Locale.US,"%.2f",value(p)*display(p)));input.setInputType(2|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);input.setContentDescription(title(p)+" exact value");AlertDialog dialog=new AlertDialog.Builder(a).setTitle(title(p)+" ("+unit(p).trim()+")").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button->{try{float n=Float.parseFloat(input.getText().toString().trim())/display(p);if(!Float.isFinite(n)||!Motion.allowed(p,n))throw new IllegalArgumentException("Enter a finite value within the supported range.");if(p==Motion.SCALE||p==Motion.HEIGHT){TransformMath.Transform t=current();TransformEdits.scalePair(t.scale,t.scale*t.aspect,p,n,linked);}commit(title(p)+" updated",()->apply(p,n));dialog.dismiss();}catch(IllegalArgumentException e){input.setError(e.getMessage());}}));dialog.show();input.requestFocus();}
    void clearGesture(){editBase=null;}
    private void begin(){a.pausePlayback();editBase=current();a.beginLiveAdjust();}
    private void live(int p,float n){a.liveAdjust(()->apply(p,n));refresh();}
    private void end(boolean cancel){editBase=null;if(cancel)a.cancelLiveAdjust();else a.endLiveAdjust("Transform updated",()->{});refresh();}
    final class ValueRuler extends View {
        final int property;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float start,down;boolean dragging;
        ValueRuler(int p){super(a);property=p;setFocusable(true);setContentDescription(title(p)+" ruler: drag the tick strip beneath the fixed indicator");}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),spacing=a.dp(12),shown=value(property)*display(property);int center=(int)Math.floor(shown),radius=(int)(w/(2*spacing))+2;paint.setStrokeWidth(a.dp(1));paint.setTextSize(a.dp(10));paint.setTextAlign(Paint.Align.CENTER);
            for(int tick=center-radius;tick<=center+radius;tick++){float x=w/2+(tick-shown)*spacing;paint.setColor(EditorActivity.MUTED);boolean major=tick%5==0;c.drawLine(x,h-(major?h*.45f:h*.25f),x,h,paint);if(major)c.drawText(Integer.toString(tick),x,a.dp(12),paint);}
            paint.setColor(EditorActivity.CYAN);paint.setStrokeWidth(a.dp(2));c.drawLine(w/2,0,w/2,h,paint);
        }
        @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:if(!a.layerEditable())return false;begin();start=value(property);down=e.getX();dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;case MotionEvent.ACTION_MOVE:if(dragging)live(property,start-(e.getX()-down)/a.dp(12)/display(property));return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(dragging){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);end(e.getActionMasked()==MotionEvent.ACTION_CANCEL);performClick();}return true;}return false;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
    final class Dial extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);float last,accumulated;boolean dragging,valid;
        Dial(){super(a);setFocusable(true);setContentDescription("Rotation dial: clockwise increases; continuous full turns");}
        private float pointer(MotionEvent e){return (float)Math.toDegrees(Math.atan2(e.getY()-getHeight()/2f,e.getX()-getWidth()/2f));}
        private boolean outside(MotionEvent e){return Math.hypot(e.getX()-getWidth()/2f,e.getY()-getHeight()/2f)>a.dp(28);}
        @Override protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-a.dp(14);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(2));paint.setColor(0xff356382);c.drawCircle(cx,cy,r,paint);for(int i=0;i<60;i++){double rad=i*Math.PI/30;float len=a.dp(i%5==0?10:4);c.drawLine(cx+(float)Math.sin(rad)*r,cy-(float)Math.cos(rad)*r,cx+(float)Math.sin(rad)*(r-len),cy-(float)Math.cos(rad)*(r-len),paint);}double rad=Math.toRadians(-value(Motion.ROTATION)%360);float x=cx+(float)Math.sin(rad)*r,y=cy-(float)Math.cos(rad)*r;paint.setColor(EditorActivity.CYAN);c.drawLine(cx+(x-cx)*.45f,cy+(y-cy)*.45f,x,y,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,a.dp(8),paint);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(a.dp(17));paint.setColor(EditorActivity.TEXT);c.drawText(String.format(Locale.US,"%.2f°",-value(Motion.ROTATION)),cx,cy+a.dp(6),paint);}
        @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:if(!a.layerEditable())return false;begin();accumulated=-value(Motion.ROTATION);last=pointer(e);valid=outside(e);dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;case MotionEvent.ACTION_MOVE:if(!dragging)return false;boolean out=outside(e);float p=pointer(e);if(out&&valid){accumulated+=TransformEdits.angleDelta(last,p);live(Motion.ROTATION,-accumulated);}last=p;valid=out;return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(dragging){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);end(e.getActionMasked()==MotionEvent.ACTION_CANCEL);performClick();}return true;}return false;}
        @Override public boolean performClick(){super.performClick();return true;}
    }
}

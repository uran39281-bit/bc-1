package com.blackice.bc1;

import android.graphics.*;
import android.view.*;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.Locale;

/** Selected-layer transform controls; all presentation uses the existing project and live renderer. */
final class TransformPanel {
    private final EditorActivity a;
    int tab;
    private final java.util.Map<Integer,TextView> readouts=new java.util.HashMap<>();
    private final java.util.Map<Integer,TextView> diamonds=new java.util.HashMap<>();
    boolean fine,linked=true,lockX,lockY;
    float angleSnap=0;
    static final String[] TABS={"Position","Rotation","Scale"};
    TransformPanel(EditorActivity activity){a=activity;}
    TransformMath.Transform current(){return a.clip()!=null?TransformMath.of(a.clip(),time()):TransformMath.of(a.overlay(),time());}
    private Motion motion(){return a.selectedMotion();}
    private long time(){
        long timeline=TransformEdits.frameTime(a.project.playheadMs,a.project.frameRate);
        EditProject.Clip c=a.clip();
        return c!=null?Math.max(c.inMs,Math.min(c.outMs,c.inMs+Math.round((timeline-a.project.placements().get(a.selected).startMs)*c.speed))):Math.max(0,Math.min(a.overlay().endMs-a.overlay().startMs,timeline-a.overlay().startMs));
    }
    private void materialize(int p){if(p!=Motion.WIDTH&&p!=Motion.HEIGHT)return;EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();float v=c!=null?(p==Motion.WIDTH?c.widthScale:c.heightScale):(p==Motion.WIDTH?o.widthScale:o.heightScale);TransformEdits.materialize(motion(),p,v,c!=null?c.aspect:o.aspect);}
    void write(int property,float value){
        if(!Motion.allowed(property,value))throw new IllegalArgumentException(Motion.PROPERTIES[property]+" is out of range.");
        materialize(property);
        if(Keyframes.set(motion(),property,time(),value,a.panels.tolerance()))a.setStatic(property,value);
    }
    void writeDimensions(float width,float height,boolean pair){
        if(!Motion.allowed(Motion.WIDTH,width)||!Motion.allowed(Motion.HEIGHT,height))throw new IllegalArgumentException("Scale is out of range");
        materialize(Motion.WIDTH);materialize(Motion.HEIGHT);
        boolean animated=!motion().curves.get(Motion.WIDTH).isEmpty()||!motion().curves.get(Motion.HEIGHT).isEmpty();
        if(pair&&animated){Keyframes.enable(motion(),Motion.WIDTH,time(),current().scale);Keyframes.enable(motion(),Motion.HEIGHT,time(),current().scale*current().aspect);}
        write(Motion.WIDTH,width);write(Motion.HEIGHT,height);
    }
    private float value(int p){TransformMath.Transform t=current();return p==Motion.X?t.x:p==Motion.Y?t.y:p==Motion.ROTATION?t.rotation:p==Motion.WIDTH?t.scale:t.scale*t.aspect;}
    private void apply(int p,float v,float width,float height){
        if(p==Motion.WIDTH||p==Motion.HEIGHT){float[] size=TransformEdits.dimensions(width,height,p==Motion.WIDTH,v,linked);if(linked)writeDimensions(size[0],size[1],true);else write(p,p==Motion.WIDTH?size[0]:size[1]);}
        else write(p,v);
    }
    void commit(String label,Runnable change){if(!a.layerEditable())return;a.pausePlayback();a.edit(label+" updated",change);a.renderInspector();}
    void resetAll(){commit("Position reset",()->{write(Motion.X,0);write(Motion.Y,0);});}
    void build(LinearLayout into){
        if(a.clip()==null&&a.overlay()==null){into.addView(a.note("Select a visual layer to transform it."));return;}
        if(a.clip()!=null&&a.clip().track>=EditProject.AUDIO_FIRST){into.addView(a.note("Audio has no picture to transform."));return;}
        readouts.clear();diamonds.clear();tab=Math.max(0,Math.min(2,tab));HorizontalScrollView scroll=new HorizontalScrollView(a);LinearLayout tabs=a.row();
        for(int i=0;i<TABS.length;i++){final int index=i;TextView b=a.button(TABS[i],v->{tab=index;a.renderInspector();});b.setSelected(i==tab);b.setTextColor(i==tab?EditorActivity.CYAN:EditorActivity.TEXT);b.setBackground(a.navBackground(i==tab));tabs.addView(b,new LinearLayout.LayoutParams(-2,a.dp(48)));}scroll.addView(tabs);into.addView(scroll);
        if(tab==0)into.addView(a.button("Reset all",v->resetAll()),new LinearLayout.LayoutParams(-1,a.dp(48)));
        navigation(into);
        if(tab==0){int[] size=a.exportSize();card(into,"X",Motion.X,0,"px",size[0]/2f);card(into,"Y",Motion.Y,0,"px",size[1]/2f);}
        else if(tab==1){Dial dial=new Dial(current().rotation);into.addView(dial,new LinearLayout.LayoutParams(-1,a.dp(220)));card(into,"Rotation",Motion.ROTATION,0,"°",1);into.addView(a.note("Presets • relative increments"));LinearLayout row=a.row();for(int amount:new int[]{45,90,180,360}){LinearLayout col=new LinearLayout(a);col.setOrientation(LinearLayout.VERTICAL);col.addView(a.compactButton("+","Add "+amount+" degrees",v->commit("Rotation",()->write(Motion.ROTATION,current().rotation+amount))),new LinearLayout.LayoutParams(-1,a.dp(48)));TextView n=a.label(amount+"°",14,EditorActivity.TEXT,true);n.setGravity(Gravity.CENTER);col.addView(n);col.addView(a.compactButton("−","Subtract "+amount+" degrees",v->commit("Rotation",()->write(Motion.ROTATION,current().rotation-amount))),new LinearLayout.LayoutParams(-1,a.dp(48)));row.addView(col,new LinearLayout.LayoutParams(0,-2,1));}into.addView(row);}
        else{card(into,"Width",Motion.WIDTH,1,"%",100);TextView lock=a.button("Lock W and H",v->{linked=!linked;a.renderInspector();});lock.setSelected(linked);lock.setContentDescription("Lock W and H: "+(linked?"on":"off"));lock.setBackground(a.buttonBackground(linked));lock.setTextColor(linked?EditorActivity.CYAN:EditorActivity.TEXT);into.addView(lock,new LinearLayout.LayoutParams(-1,a.dp(48)));card(into,"Height",Motion.HEIGHT,1,"%",100);}
    }
    private int[] properties(){return tab==0?new int[]{Motion.X,Motion.Y}:tab==1?new int[]{Motion.ROTATION}:scaleProperties();}
    private int[] scaleProperties(){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();float w=c!=null?c.widthScale:o.widthScale,h=c!=null?c.heightScale:o.heightScale;return (w<0&&motion().curves.get(Motion.WIDTH).isEmpty()||h<0&&motion().curves.get(Motion.HEIGHT).isEmpty())?new int[]{Motion.WIDTH,Motion.HEIGHT,Motion.SCALE}:new int[]{Motion.WIDTH,Motion.HEIGHT};}
    void refreshReadouts(){if(motion()==null)return;for(java.util.Map.Entry<Integer,TextView> e:readouts.entrySet()){int p=e.getKey();float display=p==Motion.X?a.exportSize()[0]/2f:p==Motion.Y?a.exportSize()[1]/2f:p==Motion.ROTATION?1:100;String unit=p<2?"px":p==Motion.ROTATION?"°":"%";e.getValue().setText(Motion.PROPERTIES[p]+"  "+format(value(p)*display)+unit);TextView d=diamonds.get(p);if(d!=null){int state=Keyframes.state(motion(),p,time(),a.panels.tolerance());d.setText(state==Keyframes.ON_KEY?"◆":"◇");d.setTextColor(state==Keyframes.STATIC?EditorActivity.MUTED:EditorActivity.CYAN);}}}
    private void navigation(LinearLayout into){LinearLayout row=a.row();for(boolean next:new boolean[]{false,true}){long target=Keyframes.adjacent(motion(),time(),next,properties());TextView b=a.button(next?"Next key ›":"‹ Previous key",v->{a.pausePlayback();EditProject.Clip c=a.clip();long pos=c!=null?a.project.placements().get(a.selected).startMs+Math.round((target-c.inMs)/(double)c.speed):a.overlay().startMs+target;a.seek(pos);a.renderInspector();});b.setContentDescription(next?"Next transform key":"Previous transform key");EditorActivity.dim(b,target>=0);row.addView(b,new LinearLayout.LayoutParams(0,a.dp(48),1));}into.addView(row);}
    private TextView diamond(String title,int p){
        Motion m=motion();int state=Keyframes.state(m,p,time(),a.panels.tolerance());
        if(state==Keyframes.STATIC&&(p==Motion.WIDTH||p==Motion.HEIGHT)){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();float override=c!=null?(p==Motion.WIDTH?c.widthScale:c.heightScale):(p==Motion.WIDTH?o.widthScale:o.heightScale);if(override<0)state=Keyframes.state(m,Motion.SCALE,time(),a.panels.tolerance());}
        TextView b=a.compactButton(state==Keyframes.ON_KEY?"◆":"◇",title+" keyframe: "+(state==Keyframes.ON_KEY?"at playhead":state==Keyframes.ANIMATED?"animated elsewhere":"static"),v->commit(title+" keyframe",()->{float shown=value(p);materialize(p);Keyframes.toggle(motion(),p,time(),shown,a.panels.tolerance());if(motion().curves.get(p).isEmpty())a.setStatic(p,shown);}));b.setTextColor(state==Keyframes.STATIC?EditorActivity.MUTED:EditorActivity.CYAN);diamonds.put(p,b);return b;
    }
    private void card(LinearLayout into,String title,int p,float reset,String unit,float display){
        LinearLayout box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setBackground(a.panelBackground());box.setPadding(a.dp(4),a.dp(8),a.dp(4),a.dp(8));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=a.dp(8);into.addView(box,bp);
        LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.addView(diamond(title,p),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        TextView number=a.button(title+"  "+format(value(p)*display)+unit,v->{a.pausePlayback();LinearLayout form=a.form();EditText input=a.field(form,title+" ("+unit+")",format(value(p)*display));input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);a.dialog(title,form,()->{float typed=Float.parseFloat(input.getText().toString().trim())/display;if(!Float.isFinite(typed))throw new IllegalArgumentException("Enter a finite number");TransformMath.Transform t=current();commit(title,()->apply(p,typed,t.scale,t.scale*t.aspect));},null);});readouts.put(p,number);number.setTextSize(12);number.setContentDescription(title+" exact value");row.addView(number,new LinearLayout.LayoutParams(0,a.dp(56),1));row.addView(a.compactButton("↺","Reset "+title,v->{TransformMath.Transform t=current();commit(title,()->apply(p,reset,t.scale,t.scale*t.aspect));}),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));box.addView(row);
        ValueRuler ruler=new ValueRuler(title,p,display,number);box.addView(ruler,new LinearLayout.LayoutParams(-1,a.dp(56)));
    }
    private static String format(float v){return String.format(Locale.US,"%.2f",v);}
    private abstract class Gesture extends View {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);boolean dragging;float start,width,height;final int property;
        Gesture(int p){super(a);property=p;setFocusable(true);}
        boolean begin(){if(!a.layerEditable())return false;a.pausePlayback();start=value(property);TransformMath.Transform t=current();width=t.scale;height=t.scale*t.aspect;a.beginLiveAdjust();dragging=true;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;}
        void update(float v){try{if(property==Motion.WIDTH||property==Motion.HEIGHT)TransformEdits.dimensions(width,height,property==Motion.WIDTH,v,linked);else if(!Motion.allowed(property,v))return;a.liveAdjust(()->apply(property,v,width,height));refreshReadouts();invalidate();}catch(IllegalArgumentException ignored){/* Keep last valid value at the documented renderer limit. */}}
        void end(boolean cancel){if(!dragging)return;dragging=false;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(cancel)a.cancelLiveAdjust();else a.endLiveAdjust("Transform updated",()->{});a.renderInspector();}
    }
    final class ValueRuler extends Gesture {
        final String title;final float display;final TextView number;float down;
        ValueRuler(String title,int p,float display,TextView number){super(p);this.title=title;this.display=display;this.number=number;setContentDescription(title+" ruler: drag right to increase, left to decrease");}
        @Override protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),spacing=a.dp(12),offset=(value(property)*display)%10*spacing;paint.setColor(EditorActivity.MUTED);paint.setStrokeWidth(a.dp(1));for(float x=w/2-offset-spacing*40;x<w;x+=spacing){int i=Math.round((x-w/2+offset)/spacing);c.drawLine(x,h-(i%5==0?h*.7f:h*.35f),x,h,paint);}paint.setColor(EditorActivity.CYAN);paint.setStrokeWidth(a.dp(2));c.drawLine(w/2,0,w/2,h,paint);}
        @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:down=e.getX();return begin();case MotionEvent.ACTION_MOVE:if(!dragging)return false;update(start+(e.getX()-down)/a.dp(6)/display);number.setText(title+"  "+format(value(property)*display)+(property<2?"px":property==Motion.ROTATION?"°":"%"));return true;case MotionEvent.ACTION_UP:end(false);return true;case MotionEvent.ACTION_CANCEL:end(true);return true;}return false;}
    }
    final class Dial extends Gesture {
        float angle,last;boolean pointerValid;
        Dial(float angle){super(Motion.ROTATION);this.angle=angle;setContentDescription("Rotation dial: clockwise increases cumulative angle");}
        private float pointer(MotionEvent e){return (float)Math.toDegrees(Math.atan2(e.getY()-getHeight()/2f,e.getX()-getWidth()/2f));}
        private boolean outside(MotionEvent e){return Math.hypot(e.getX()-getWidth()/2f,e.getY()-getHeight()/2f)>a.dp(28);}
        @Override protected void onDraw(Canvas c){float r=Math.min(getWidth(),getHeight())/2f-a.dp(12),cx=getWidth()/2f,cy=getHeight()/2f;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(1));paint.setColor(0xFF315B78);c.drawCircle(cx,cy,r,paint);for(int d=0;d<360;d+=10){double v=Math.toRadians(d);float len=d%90==0?a.dp(12):a.dp(5);c.drawLine(cx+(float)Math.cos(v)*r,cy+(float)Math.sin(v)*r,cx+(float)Math.cos(v)*(r-len),cy+(float)Math.sin(v)*(r-len),paint);}double rad=Math.toRadians(value(Motion.ROTATION)-90);paint.setColor(EditorActivity.CYAN);paint.setStrokeWidth(a.dp(3));c.drawLine(cx,cy-a.dp(25),cx+(float)Math.cos(rad)*r,cy+(float)Math.sin(rad)*r,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(cx+(float)Math.cos(rad)*r,cy+(float)Math.sin(rad)*r,a.dp(7),paint);paint.setColor(EditorActivity.TEXT);paint.setTextSize(a.dp(18));paint.setTextAlign(Paint.Align.CENTER);c.drawText(format(value(Motion.ROTATION))+"°",cx,cy+a.dp(7),paint);}
        @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:if(!begin())return false;angle=start;last=pointer(e);pointerValid=outside(e);return true;case MotionEvent.ACTION_MOVE:if(!dragging)return false;if(outside(e)){float next=pointer(e);if(pointerValid){angle+=TransformEdits.pointerDelta(last,next);update(angle);}last=next;pointerValid=true;}else pointerValid=false;return true;case MotionEvent.ACTION_UP:end(false);return true;case MotionEvent.ACTION_CANCEL:end(true);return true;}return false;}
    }
}

package com.blackice.bc1;

import android.graphics.*;
import android.view.*;
import android.widget.ScrollView;
import com.blackice.bc1.editor.*;
import java.util.*;

/** Layered timeline with a fixed center playhead; the timeline moves underneath it.
 * Gestures: drag a layer to move it in time, or up/down onto another row (the empty row creates a new
 * layer); drag a selected layer's edges to trim; tap or drag the ruler to scrub; drag empty space to
 * scroll; pinch to zoom; drag a row's ≡ handle to reorder layers. Media dragged from Project Media drops
 * onto the row and time under the finger. One continuous drag is one undo entry. */
final class TimelineView extends View {
    static final int RULER_DP=30,ROW_DP=52,BAR_DP=40;
    private final EditorActivity a;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF thumbBounds=new RectF();
    private final ScaleGestureDetector pinch;
    /** Row kinds: 0 track (index = track number), 1 text/shape/image layer (index = overlay), 2 music. */
    static final class Row{final int kind,index;float top;Row(int k,int i){kind=k;index=i;}}
    private List<Row> rows=new ArrayList<>();
    // Gesture state
    private float downX,downY,lastRawX,lastRawY,lastEventX,lastEventY;private int lastEventScroll;private long downTime;
    private boolean pinching,moved,vertical;
    private int operation,hitClip=-1,hitOverlay=-1,headerZone=-1,reorderFrom=-1,reorderTo=-1,pendingClip=-1,pendingOverlay=-1;
    private long pendingDelta,guide=-1,downPlayhead;private float downRawX;
    private EditProject.Placement original;private EditProject.Overlay originalOverlay;
    private final Runnable autoScroll=new Runnable(){@Override public void run(){autoScrollStep();}};
    private int autoDirection;
    // Media dragged in from Project Media: target row and start, or -1 when nothing is hovering.
    private EditProject.Clip dropAsset;private int dropTrack=-1;private long dropStart;
    static final int OP_TRIM_START=1,OP_TRIM_END=2,OP_MOVE=3,OP_OV_START=4,OP_OV_END=5,OP_OV_MOVE=6,OP_MULTI=7,OP_SCRUB=8;

    TimelineView(EditorActivity activity){
        super(activity);a=activity;setFocusable(true);
        setContentDescription("Timeline: hold then drag a layer to move it in time or onto another row, drag a selected layer's edges to trim, drag the timeline beneath the centered playhead to scrub, tap the ruler to seek, pinch to zoom. Drop media from Project Media onto a row.");
        pinch=new ScaleGestureDetector(activity,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            public boolean onScaleBegin(ScaleGestureDetector d){pinching=true;operation=0;pendingDelta=0;guide=-1;stopAutoScroll();return true;}
            public boolean onScale(ScaleGestureDetector d){a.zoomTimeline(d.getScaleFactor(),d.getFocusX()-a.timelineScroll.getScrollX());return true;}
        });
    }
    private int accessibleFocus=-1;
    @Override public android.view.accessibility.AccessibilityNodeProvider getAccessibilityNodeProvider(){return new android.view.accessibility.AccessibilityNodeProvider(){
        @Override public android.view.accessibility.AccessibilityNodeInfo createAccessibilityNodeInfo(int id){
            android.view.accessibility.AccessibilityNodeInfo n=android.view.accessibility.AccessibilityNodeInfo.obtain();
            if(id==View.NO_ID){onInitializeAccessibilityNodeInfo(n);for(int i=0;i<rows().size();i++)if(rows.get(i).kind!=2)n.addChild(TimelineView.this,i);return n;}
            List<Row> list=rows();if(id<0||id>=list.size())return null;Row r=list.get(id);if(r.kind==2)return null;
            n.setSource(TimelineView.this,id);n.setParent(TimelineView.this);n.setPackageName(a.getPackageName());n.setClassName("android.widget.Button");n.setEnabled(true);n.setFocusable(true);n.setClickable(true);n.setLongClickable(true);n.setAccessibilityFocused(id==accessibleFocus);
            boolean ov=r.kind==1;boolean hidden=ov?a.project.overlays.get(r.index).hidden:EditProject.audioTrack(r.index)?a.project.tracks[r.index].muted:a.project.tracks[r.index].hidden;boolean locked=ov?a.project.overlays.get(r.index).locked:a.project.tracks[r.index].locked;
            n.setContentDescription(a.rowName(new int[]{r.kind,r.index})+", "+(ov?"overlay ":EditProject.audioTrack(r.index)?"audio track ":"visual track ")+(r.index+1)+", "+(hidden?"hidden or muted":"enabled")+(locked?", locked":"")+". Tap to toggle visibility or mute. Long press for lock and layer options.");
            Rect bounds=new Rect(a.timelineScroll.getScrollX(),Math.round(r.top),a.timelineScroll.getScrollX()+headerWidth(),Math.round(r.top)+a.dp(ROW_DP));n.setBoundsInParent(bounds);int[] location=new int[2];getLocationOnScreen(location);bounds.offset(location[0],location[1]);n.setBoundsInScreen(bounds);Rect visible=new Rect();n.setVisibleToUser(getGlobalVisibleRect(visible)&&Rect.intersects(bounds,visible));
            n.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);n.addAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK);n.addAction(id==accessibleFocus?android.view.accessibility.AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS:android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);return n;
        }
        @Override public boolean performAction(int id,int action,android.os.Bundle args){List<Row> list=rows();if(id<0||id>=list.size())return false;if(action==16||action==32){headerTap(list.get(id),2,action==32);return true;}if(action==64||action==128){accessibleFocus=action==64?id:-1;android.view.accessibility.AccessibilityEvent e=android.view.accessibility.AccessibilityEvent.obtain(action==64?32768:65536);e.setSource(TimelineView.this,id);getParent().requestSendAccessibilityEvent(TimelineView.this,e);return true;}return false;}
    };}
    @Override public boolean dispatchHoverEvent(MotionEvent e){if(inHeader(e.getX())){Row r=rowAt(e.getY());if(r!=null&&r.kind!=2){android.view.accessibility.AccessibilityEvent event=android.view.accessibility.AccessibilityEvent.obtain(e.getAction()==MotionEvent.ACTION_HOVER_EXIT?256:128);event.setSource(this,rows.indexOf(r));getParent().requestSendAccessibilityEvent(this,event);return true;}}return super.dispatchHoverEvent(e);}
    // ---- Geometry ---------------------------------------------------------------------------------
    int viewport(){int w=a.timelineScroll==null?0:a.timelineScroll.getWidth();return w>0?w:getResources().getDisplayMetrics().widthPixels;}
    /** Time zero starts centered, with enough leading space to scrub back to the beginning. */
    float pad(){return viewport()/2f;}
    float timelineScale(){return Math.min(a.dp(a.pixelsPerSecond),16_000_000f/Math.max(1f,a.project.durationMs()/1000f));}
    float x(long ms){return pad()+ms/1000f*timelineScale();}
    long time(float x){return Math.round((x-pad())/timelineScale()*1000);}
    int headerWidth(){return a.dp(56);}
    List<Row> rows(){
        List<Row> list=new ArrayList<>();float y=a.dp(RULER_DP);
        for(int[] r:a.layerRows()){Row row=new Row(r[0],r[1]);row.top=y;y+=a.dp(ROW_DP);list.add(row);}
        Row music=new Row(2,0);music.top=y;list.add(music);rows=list;return list;
    }
    float contentHeight(){return a.dp(RULER_DP)+(a.layerRows().size()+1)*a.dp(ROW_DP)+a.dp(8);}
    Row rowAt(float y){for(Row r:rows)if(y>=r.top&&y<r.top+a.dp(ROW_DP))return r;return null;}
    float rowTopOfTrack(int track){for(Row r:rows())if(r.kind==0&&r.index==track)return r.top;return -1;}
    float rowTopOfOverlay(int overlay){for(Row r:rows())if(r.kind==1&&r.index==overlay)return r.top;return -1;}
    /** Room after the last clip so layers can be dragged, and media dropped, past the current end. */
    private float tail(){return Math.max(viewport()*.75f,a.dp(320));}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(Math.max(viewport()+1,Math.round(pad()+a.project.durationMs()/1000f*timelineScale()+tail())),Math.round(contentHeight()));}
    /** Every seek and playback tick leaves the playhead at the viewport center. */
    void syncScroll(){
        if(a.timelineScroll==null)return;
        a.timelineScroll.scrollTo(Math.max(0,Math.round(x(a.project.playheadMs)-pad())),0);
        invalidate();
    }
    /** Zoom keeps the time at the fixed playhead unchanged. */
    void zoomAround(float before,float anchor){
        requestLayout();syncScroll();a.timelineScroll.post(this::syncScroll);
    }
    /** Fit changes display scale only; retain the current time at the fixed playhead. */
    void reveal(long ms){requestLayout();a.timelineScroll.post(this::syncScroll);}


    // ---- Drawing ----------------------------------------------------------------------------------
    @Override protected void onDraw(Canvas canvas){
        List<Row> rows=rows();int height=getHeight();float left=a.timelineScroll.getScrollX(),right=left+viewport();
        float top=0,bottom=height;if(a.timelineScroll.getParent() instanceof ScrollView){ScrollView sv=(ScrollView)a.timelineScroll.getParent();top=sv.getScrollY();bottom=top+sv.getHeight();}
        canvas.drawColor(0xFF060D17);
        for(int i=0;i<rows.size();i++){Row r=rows.get(i);paint.setColor(i%2==0?0xFF08121E:0xFF060D17);canvas.drawRect(left,r.top,right,r.top+a.dp(ROW_DP),paint);paint.setColor(0xFF0E1C2B);canvas.drawRect(left,r.top+a.dp(ROW_DP)-a.dp(1),right,r.top+a.dp(ROW_DP),paint);}
        drawRuler(canvas,left,right,height);
        EditProject p=a.project;List<EditProject.Placement> placements=p.placements();float bar=a.dp(BAR_DP),inset=(a.dp(ROW_DP)-bar)/2f;
        for(EditProject.Placement pl:placements){float y=rowTopOfTrackCached(rows,pl.clip.track);if(y<0)continue;float l=x(pl.startMs),r=x(pl.endMs);if(r<left||l>right||y+bar<top||y>bottom)continue;drawClip(canvas,pl,l,r,y+inset,bar,left,right);}
        for(Row row:rows)if(row.kind==1){String g=a.project.overlays.get(row.index).group;
            if(g!=null&&a.collapsedGroups.contains(g)){for(int i=0;i<a.project.overlays.size();i++)if(g.equals(a.project.overlays.get(i).group))drawOverlay(canvas,i,row.top+inset,bar,left,right);}
            else drawOverlay(canvas,row.index,row.top+inset,bar,left,right);}
        Row musicRow=rows.get(rows.size()-1);
        if(p.music!=null){long end=p.music.loop?p.durationMs():Math.min(p.durationMs(),p.music.startMs+p.music.outMs-p.music.inMs);paint.setColor(0xFF15604A);canvas.drawRoundRect(x(p.music.startMs),musicRow.top+inset,x(end),musicRow.top+inset+bar,a.dp(6),a.dp(6),paint);paint.setColor(EditorActivity.TEXT);paint.setTextSize(a.dp(11));canvas.drawText("♫  "+p.music.name,Math.max(x(p.music.startMs),left+headerWidth())+a.dp(8),musicRow.top+inset+bar/2+a.dp(4),paint);}
        if(operation!=0&&operation!=OP_SCRUB&&moved)drawDragPreview(canvas,rows,bar,inset);
        if(dropAsset!=null&&dropTrack>=0)drawDropPreview(canvas,rows,bar,inset);
        if(guide>=0){paint.setColor(0xAA9FEAFF);paint.setStrokeWidth(a.dp(1));for(float y=a.dp(RULER_DP);y<height;y+=a.dp(8))canvas.drawLine(x(guide),y,x(guide),y+a.dp(4),paint);}
        drawHeaders(canvas,rows,left);
        drawPlayhead(canvas,left,height);
    }
    private void drawPlayhead(Canvas canvas,float left,int height){
        float px=left+pad();
        paint.setColor(EditorActivity.CYAN);paint.setStrokeWidth(a.dp(2));canvas.drawLine(px,a.dp(12),px,height,paint);
        Path head=new Path();float w=a.dp(7);head.moveTo(px-w,a.dp(2));head.lineTo(px+w,a.dp(2));head.lineTo(px+w,a.dp(10));head.lineTo(px,a.dp(16));head.lineTo(px-w,a.dp(10));head.close();canvas.drawPath(head,paint);
        if(operation==OP_SCRUB){String label=EditorActivity.clock(a.project.playheadMs)+"  f"+frame(a.project.playheadMs);paint.setTextSize(a.dp(11));paint.setTypeface(Typeface.DEFAULT_BOLD);float tw=paint.measureText(label),lx=Math.max(left+headerWidth()+tw/2+a.dp(8),px);
            canvas.drawRoundRect(lx+a.dp(10),a.dp(2),lx+tw+a.dp(22),a.dp(19),a.dp(9),a.dp(9),paint);paint.setColor(0xFF04121C);canvas.drawText(label,lx+a.dp(16),a.dp(15),paint);paint.setTypeface(Typeface.DEFAULT);}
    }
    private float rowTopOfTrackCached(List<Row> rows,int track){for(Row r:rows)if(r.kind==0&&r.index==track)return r.top;return -1;}
    long frame(long ms){return Math.round(ms*a.project.frameRate/1000.0);}
    private void drawRuler(Canvas canvas,float left,float right,int height){
        EditProject p=a.project;paint.setColor(0xFF07101B);canvas.drawRect(left,0,right,a.dp(RULER_DP),paint);paint.setColor(0xFF16293A);canvas.drawRect(left,a.dp(RULER_DP)-a.dp(1),right,a.dp(RULER_DP),paint);
        if(p.loopInMs>=0){paint.setColor(0x5540BFFF);canvas.drawRect(x(p.loopInMs),a.dp(20),x(p.loopOutMs),a.dp(RULER_DP),paint);paint.setColor(0x1A40BFFF);canvas.drawRect(x(p.loopInMs),a.dp(RULER_DP),x(p.loopOutMs),height,paint);}
        double interval=TimelineEdits.rulerStep(timelineScale(),a.dp(64),p.frameRate)*1000;
        long first=(long)Math.max(0,Math.floor(time(left)/interval)),last=(long)Math.ceil(time(right)/interval);
        paint.setTextSize(a.dp(10));paint.setStrokeWidth(a.dp(1));
        for(long tick=first;tick<=last&&tick-first<400;tick++){long ms=Math.round(tick*interval);float xx=x(ms);
            paint.setColor(0xFF6F8AA3);canvas.drawLine(xx,a.dp(19),xx,a.dp(RULER_DP),paint);canvas.drawText(TimelineEdits.rulerLabel(ms,interval/1000,p.frameRate).replace(".0s","s"),xx+a.dp(3),a.dp(14),paint);
            paint.setColor(0x14FFFFFF);canvas.drawLine(xx,a.dp(RULER_DP),xx,height,paint);
            // Minor ticks at quarters.
            paint.setColor(0xFF2E465A);for(int q=1;q<4;q++){float mx=x(ms+Math.round(interval*q/4));canvas.drawLine(mx,a.dp(24),mx,a.dp(RULER_DP),paint);}}
        float perFrame=timelineScale()/p.frameRate;
        if(perFrame>=a.dp(5)){paint.setColor(0xFF345465);long f0=(long)Math.max(0,Math.floor(time(left)*p.frameRate/1000.0)),f1=(long)Math.ceil(time(right)*p.frameRate/1000.0);for(long f=f0;f<=f1&&f-f0<2000;f++){float xx=x(Math.round(f*1000.0/p.frameRate));canvas.drawLine(xx,a.dp(RULER_DP)-a.dp(4),xx,a.dp(RULER_DP),paint);}}
        paint.setColor(0xFFFFC145);for(long m:p.markers){float xx=x(m);if(xx<left-a.dp(8)||xx>right+a.dp(8))continue;Path tri=new Path();tri.moveTo(xx-a.dp(5),a.dp(18));tri.lineTo(xx+a.dp(5),a.dp(18));tri.lineTo(xx,a.dp(26));tri.close();canvas.drawPath(tri,paint);paint.setStrokeWidth(a.dp(1));canvas.drawLine(xx,a.dp(26),xx,height,paint);}
    }
    private boolean multiSelected(int clip){return a.multiClips.contains(clip);}
    /** Reference palette: video navy with thumbnails, pictures violet, audio green (layers 2+ blue). */
    private static int clipColor(EditProject.Clip c){return c.track>=EditProject.AUDIO_FIRST?(c.track==EditProject.AUDIO_FIRST?0xFF145A44:0xFF233A8C):c.image?0xFF4B3591:0xFF15304A;}
    private static int clipEdge(EditProject.Clip c){return c.track>=EditProject.AUDIO_FIRST?(c.track==EditProject.AUDIO_FIRST?0xFF2FB98A:0xFF5A78F0):c.image?0xFF9A7BFF:0xFF3C88C0;}
    private void drawClip(Canvas canvas,EditProject.Placement pl,float l,float r,float y,float bar,float left,float right){
        EditProject p=a.project;EditProject.Clip c=pl.clip;boolean selected=pl.index==a.selected||multiSelected(pl.index),hidden=c.track<EditProject.AUDIO_FIRST?p.tracks[c.track].hidden:p.tracks[c.track].muted;
        float radius=a.dp(6);paint.setAlpha(255);paint.setColor(clipColor(c));canvas.drawRoundRect(l,y,r,y+bar,radius,radius,paint);
        if(c.track<EditProject.AUDIO_FIRST){Bitmap thumb=a.thumbnails.get(c,a.timelineThumbnailsReady);if(thumb!=null){canvas.save();canvas.clipRect(l+a.dp(1),y+a.dp(1),r-a.dp(1),y+bar-a.dp(1));float tw=bar*thumb.getWidth()/thumb.getHeight();float firstX=l+Math.max(0,(float)Math.floor((left-l)/tw))*tw;for(float tx=firstX;tx<Math.min(r,right);tx+=tw){thumbBounds.set(tx,y,tx+tw,y+bar);canvas.drawBitmap(thumb,null,thumbBounds,paint);}canvas.restore();}}
        float[] peaks=a.waveforms.get(c.uri);if(peaks!=null){paint.setColor(c.track>=EditProject.AUDIO_FIRST?(c.track==EditProject.AUDIO_FIRST?0xFF3EE0A4:0xFF8FA6FF):0xFF74D3C1);for(float xx=Math.max(l,left);xx<Math.min(r,right);xx+=a.dp(2)){long source=c.inMs+(long)((xx-l)/(r-l)*(c.outMs-c.inMs));int bin=(int)Math.min(peaks.length-1,source*peaks.length/Math.max(1,c.sourceDurationMs));float amp=peaks[Math.max(0,bin)]*bar*.38f;canvas.drawLine(xx,y+bar/2-amp,xx,y+bar/2+amp,paint);}}
        if(pl.outgoingMs>0){paint.setColor(0x9940BFFF);canvas.drawRect(x(pl.endMs-pl.outgoingMs),y,r,y+bar,paint);}
        // Name tag in a dark chip so it stays readable over thumbnails and waveforms.
        canvas.save();canvas.clipRect(l+a.dp(3),y,r-a.dp(3),y+bar);paint.setTextSize(a.dp(11));String label=(c.track>=EditProject.AUDIO_FIRST?"♫  ":c.image?"▣  ":"")+c.name;float tx=Math.max(l,left+headerWidth())+a.dp(8),tw=paint.measureText(label);
        if(c.track<EditProject.AUDIO_FIRST){paint.setColor(0xB3070F19);canvas.drawRoundRect(tx-a.dp(4),y+bar/2-a.dp(9),tx+tw+a.dp(4),y+bar/2+a.dp(9),a.dp(4),a.dp(4),paint);}
        paint.setColor(EditorActivity.TEXT);canvas.drawText(label,tx,y+bar/2+a.dp(4),paint);canvas.restore();
        if(selected)drawKeys(canvas,c.motion,l,y,bar,k->x(pl.startMs+Math.round((k-c.inMs)/(double)c.speed)),left,right);
        if(hidden){paint.setColor(0x99060D13);canvas.drawRoundRect(l,y,r,y+bar,radius,radius,paint);}
        if(p.tracks[c.track].locked){paint.setColor(0x33FFFFFF);paint.setStrokeWidth(a.dp(1));canvas.save();canvas.clipRect(l,y,r,y+bar);for(float xx=Math.max(l,left)-bar;xx<Math.min(r,right);xx+=a.dp(10))canvas.drawLine(xx,y+bar,xx+bar,y,paint);canvas.restore();}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(selected?2:1));paint.setColor(selected?EditorActivity.CYAN:clipEdge(c));canvas.drawRoundRect(l,y,r,y+bar,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
        if(pl.index==a.selected&&a.multiCount()<=1){paint.setColor(EditorActivity.CYAN);float handle=Math.min(a.dp(8),Math.max(a.dp(3),(r-l)/4));canvas.drawRoundRect(l,y,l+handle,y+bar,a.dp(3),a.dp(3),paint);canvas.drawRoundRect(r-handle,y,r,y+bar,a.dp(3),a.dp(3),paint);
            paint.setColor(0xFF04121C);canvas.drawRect(l+handle/2-a.dp(.75f),y+bar/2-a.dp(7),l+handle/2+a.dp(.75f),y+bar/2+a.dp(7),paint);canvas.drawRect(r-handle/2-a.dp(.75f),y+bar/2-a.dp(7),r-handle/2+a.dp(.75f),y+bar/2+a.dp(7),paint);}
    }
    interface KeyX{float x(long keyTime);}
    /** Diamonds at keyframe times so the clip's timing stays visible while editing animation. */
    private void drawKeys(Canvas canvas,Motion m,float l,float y,float bar,KeyX at,float left,float right){
        paint.setColor(0xFFFFFFFF);TreeSet<Long> times=new TreeSet<>();for(List<Motion.Key> keys:m.curves)for(Motion.Key k:keys)times.add(k.timeMs);
        for(long t:times){float xx=at.x(t);if(xx<left||xx>right)continue;Path d=new Path();float s=a.dp(5),cy=y+a.dp(8);d.moveTo(xx,cy-s);d.lineTo(xx+s,cy);d.lineTo(xx,cy+s);d.lineTo(xx-s,cy);d.close();canvas.drawPath(d,paint);}
    }
    private void drawOverlay(Canvas canvas,int index,float y,float bar,float left,float right){
        EditProject.Overlay o=a.project.overlays.get(index);boolean chosen=(index==a.selectedOverlay&&a.selected<0)||a.multiOverlays.contains(index);
        long delta=chosen&&operation>=OP_OV_START&&operation<=OP_OV_MOVE&&moved?pendingDelta:0;
        float l=x(o.startMs+(operation==OP_OV_START||operation==OP_OV_MOVE?delta:0)),r=x(o.endMs+(operation==OP_OV_END||operation==OP_OV_MOVE?delta:0));
        if(r<left||l>right)return;
        float radius=a.dp(6);int base=o.imageUri!=null?0xFF4B3591:o.shape>0?0xFF1F5A63:0xFF503079,edge=o.imageUri!=null?0xFF9A7BFF:o.shape>0?0xFF4FC3CF:0xFFB38CFF;
        paint.setColor(base);canvas.drawRoundRect(l,y,r,y+bar,radius,radius,paint);
        canvas.save();canvas.clipRect(l+a.dp(6),y,r-a.dp(6),y+bar);paint.setColor(EditorActivity.TEXT);paint.setTextSize(a.dp(11));canvas.drawText((o.shape>0?"◆  ":o.imageUri!=null?"▣  ":"T  ")+o.label(),Math.max(l,left+headerWidth())+a.dp(9),y+bar/2+a.dp(4),paint);canvas.restore();
        if(chosen)drawKeys(canvas,o.motion,l,y,bar,k->x(o.startMs+k),left,right);
        if(o.hidden){paint.setColor(0x99060D13);canvas.drawRoundRect(l,y,r,y+bar,radius,radius,paint);}
        if(o.locked){paint.setColor(0x33FFFFFF);paint.setStrokeWidth(a.dp(1));canvas.save();canvas.clipRect(l,y,r,y+bar);for(float xx=Math.max(l,left)-bar;xx<Math.min(r,right);xx+=a.dp(10))canvas.drawLine(xx,y+bar,xx+bar,y,paint);canvas.restore();}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(chosen?2:1));paint.setColor(chosen?EditorActivity.CYAN:edge);canvas.drawRoundRect(l,y,r,y+bar,radius,radius,paint);paint.setStyle(Paint.Style.FILL);
        if(chosen&&a.multiCount()<=1){paint.setColor(EditorActivity.CYAN);float handle=Math.min(a.dp(8),Math.max(a.dp(3),(r-l)/4));canvas.drawRoundRect(l,y,l+handle,y+bar,a.dp(3),a.dp(3),paint);canvas.drawRoundRect(r-handle,y,r,y+bar,a.dp(3),a.dp(3),paint);}
    }
    private void ghost(Canvas canvas,float l,float y,float r,float bar,String text){
        paint.setColor(0x5540BFFF);canvas.drawRoundRect(l,y,r,y+bar,a.dp(6),a.dp(6),paint);paint.setColor(EditorActivity.CYAN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(2));canvas.drawRoundRect(l,y,r,y+bar,a.dp(6),a.dp(6),paint);paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(a.dp(11));canvas.drawText(text,Math.max(a.timelineScroll.getScrollX()+headerWidth(),l)+a.dp(6),y+a.dp(15),paint);
    }
    private void drawDragPreview(Canvas canvas,List<Row> rows,float bar,float inset){
        if(original!=null&&operation<=OP_MOVE){int track=operation==OP_MOVE?targetTrack():original.clip.track;float y=rowTopOfTrackCached(rows,track)+inset,l=x(original.startMs+(operation==OP_TRIM_START||operation==OP_MOVE?pendingDelta:0)),r=x(original.endMs+(operation==OP_TRIM_END||operation==OP_MOVE?pendingDelta:0));
            long length=original.clip.durationMs()+(operation==OP_TRIM_START?-pendingDelta:operation==OP_TRIM_END?pendingDelta:0);
            ghost(canvas,l,y,r,bar,operation==OP_MOVE?EditorActivity.seconds(original.startMs+pendingDelta)+" s":EditorActivity.seconds(length)+" s");}
        if(operation==OP_MULTI){paint.setColor(0x5540BFFF);for(int i:a.selectedClips()){EditProject.Placement pl=a.project.placements().get(i);float y=rowTopOfTrackCached(rows,pl.clip.track)+inset;canvas.drawRect(x(pl.startMs+pendingDelta),y,x(pl.endMs+pendingDelta),y+bar,paint);}
            for(int i:a.selectedOverlays()){EditProject.Overlay o=a.project.overlays.get(i);float y=rowTopOfOverlay(i)+inset;canvas.drawRect(x(o.startMs+pendingDelta),y,x(o.endMs+pendingDelta),y+bar,paint);}}
    }
    private void drawDropPreview(Canvas canvas,List<Row> rows,float bar,float inset){
        float y=rowTopOfTrackCached(rows,dropTrack);if(y<0)return;long length=dropAsset.durationMs();ghost(canvas,x(dropStart),y+inset,x(dropStart+length),bar,dropAsset.name+"  ·  "+EditorActivity.seconds(dropStart)+" s");
    }
    /** Pinned left column: ≡ reorder handle, color, name, visibility and lock per row. */
    private void drawHeaders(Canvas canvas,List<Row> rows,float left){
        int w=headerWidth();paint.setColor(BlackIceTheme.PRIMARY);canvas.drawRect(left,0,left+w,getHeight(),paint);
        paint.setColor(BlackIceTheme.BORDER);paint.setStrokeWidth(a.dp(1));canvas.drawLine(left+w,0,left+w,getHeight(),paint);
        paint.setColor(EditorActivity.CYAN);paint.setTextSize(a.dp(10));canvas.drawText(EditorActivity.clock(a.project.playheadMs),left+a.dp(2),a.dp(19),paint);
        for(Row r:rows){float mid=r.top+a.dp(ROW_DP)/2f;
            if(r.kind==2){drawIcon(canvas,"Audio",left+a.dp(16),mid-a.dp(12),EditorActivity.MUTED,24);continue;}
            boolean overlay=r.kind==1;EditProject.Overlay o=overlay?a.project.overlays.get(r.index):null;EditProject.Track t=overlay?null:a.project.tracks[r.index];
            boolean audio=!overlay&&EditProject.audioTrack(r.index),hidden=overlay?o.hidden:audio?t.muted:t.hidden,locked=overlay?o.locked:t.locked;
            drawIcon(canvas,audio?(hidden?"Mute":"Volume"):hidden?"Hidden":"Eye",left+a.dp(16),mid-a.dp(12),hidden?EditorActivity.MUTED:EditorActivity.TEXT,24);
            drawIcon(canvas,locked?"Lock":overlay?(o.shape>0?"Shapes":o.imageUri!=null?"Media":"Text"):audio?"Audio":"Media",left+a.dp(2),r.top+a.dp(2),locked?EditorActivity.CYAN:EditorActivity.MUTED,12);
            if(!overlay&&a.trackEmpty(r.index)){paint.setColor(EditorActivity.MUTED);paint.setTextSize(a.dp(10));canvas.drawText("Drop media here",Math.max(x(0),left+w)+a.dp(8),mid+a.dp(4),paint);}
        }
    }
    private void drawIcon(Canvas canvas,String name,float x,float y,int color,int size){V1Icon icon=new V1Icon(name,color);icon.setBounds(Math.round(x),Math.round(y),Math.round(x+a.dp(size)),Math.round(y+a.dp(size)));icon.draw(canvas);}

    // ---- Hit testing -------------------------------------------------------------------------------
    int hitClip(float xx,float yy){Row r=rowAt(yy);if(r==null||r.kind!=0)return -1;List<EditProject.Placement> ps=a.project.placements();for(int i=ps.size()-1;i>=0;i--){EditProject.Placement p=ps.get(i);if(p.clip.track==r.index&&xx>=x(p.startMs)-a.dp(6)&&xx<=x(p.endMs)+a.dp(6))return p.index;}return -1;}
    int hitOverlay(float xx,float yy){Row r=rowAt(yy);if(r==null||r.kind!=1)return -1;EditProject.Overlay o=a.project.overlays.get(r.index);return xx>=x(o.startMs)-a.dp(6)&&xx<=x(o.endMs)+a.dp(6)?r.index:-1;}
    private boolean inHeader(float xx){return xx<a.timelineScroll.getScrollX()+headerWidth();}
    /** The row a dragged clip would land on: a row of the same kind (visual / audio) under the finger. */
    int targetTrack(){
        if(original==null)return -1;int track=original.clip.track;Row r=rowAt(lastEventY);
        if(r!=null&&r.kind==0&&EditProject.audioTrack(r.index)==EditProject.audioTrack(track))return r.index;return track;
    }
    /** Row for media dropped at a point: the row under it when compatible, otherwise a sensible default. */
    int dropTrackFor(EditProject.Clip asset,float yy,long start){
        Row r=rowAt(yy);boolean audio=asset.audioOnly;long end=start+asset.durationMs();
        if(r!=null&&r.kind==0&&EditProject.audioTrack(r.index)==audio)return r.index;
        if(audio){for(int t=EditProject.AUDIO_FIRST;t<=EditProject.AUDIO_LAST;t++)if(LayerOps.free(a.project,t,start,end,-1))return t;return EditProject.AUDIO_FIRST;}
        // Above the video rows (text layers or the ruler): the first free layer; below them: the main sequence.
        if(r==null||r.kind==1){for(int t=1;t<=EditProject.LAST_LAYER;t++)if(LayerOps.free(a.project,t,start,end,-1))return t;}
        return 0;
    }

    // ---- Touch -------------------------------------------------------------------------------------
    @Override public boolean onTouchEvent(MotionEvent e){
        int action=e.getActionMasked();pinch.onTouchEvent(e);
        if(action==MotionEvent.ACTION_DOWN)return down(e);
        if(e.getPointerCount()>1){pinching=true;operation=0;pendingDelta=0;guide=-1;stopAutoScroll();invalidate();return true;}
        if(action==MotionEvent.ACTION_MOVE&&!pinching)return move(e);
        if(action==MotionEvent.ACTION_UP)return up(e);
        if(action==MotionEvent.ACTION_CANCEL)reset();
        return true;
    }
    private boolean down(MotionEvent e){
        rows();downX=e.getX();downY=e.getY();lastEventX=downX;lastEventY=downY;lastEventScroll=a.timelineScroll.getScrollX();lastRawX=e.getRawX();lastRawY=e.getRawY();downTime=e.getEventTime();
        downRawX=e.getRawX();downPlayhead=a.project.playheadMs;moved=false;pinching=false;vertical=false;operation=0;hitClip=-1;hitOverlay=-1;pendingDelta=0;guide=-1;original=null;originalOverlay=null;headerZone=-1;reorderFrom=-1;reorderTo=-1;pendingClip=-1;pendingOverlay=-1;
        getParent().requestDisallowInterceptTouchEvent(true);a.pausePlayback();
        if(downY>=a.dp(RULER_DP)&&inHeader(downX)){Row r=rowAt(downY);if(r!=null&&r.kind!=2){float local=downX-a.timelineScroll.getScrollX();int w=headerWidth();
            headerZone=2;}return true;}
        // The ruler moves the playhead: tap to jump, drag to scrub.
        if(downY<a.dp(RULER_DP)){if(!inHeader(downX)){operation=OP_SCRUB;}return true;}
        hitClip=hitClip(downX,downY);hitOverlay=hitClip>=0?-1:hitOverlay(downX,downY);
        // Edge handles of the selected clip win even slightly outside its bar.
        if(a.multiCount()<=1&&a.clip()!=null&&!LayerOps.locked(a.project,a.selected)){EditProject.Placement chosen=a.project.placements().get(a.selected);float y=rowTopOfTrack(chosen.clip.track);
            if(y>=0&&downY>=y&&downY<=y+a.dp(ROW_DP)&&Math.min(Math.abs(downX-x(chosen.startMs)),Math.abs(downX-x(chosen.endMs)))<=a.dp(16))hitClip=a.selected;}
        if(a.uiLocked||a.loading||a.importing||a.exporting())return true;
        if(a.multiCount()>1&&(hitClip>=0&&a.multiClips.contains(hitClip)||hitOverlay>=0&&a.multiOverlays.contains(hitOverlay))){boolean anyLocked=false;for(int i:a.selectedClips())anyLocked|=LayerOps.locked(a.project,i);for(int i:a.selectedOverlays())anyLocked|=a.project.overlays.get(i).locked;if(!anyLocked)operation=OP_MULTI;return true;}
        if(hitClip>=0&&!LayerOps.locked(a.project,hitClip)){original=a.project.placements().get(hitClip);
            if(hitClip==a.selected){float dl=Math.abs(downX-x(original.startMs)),dr=Math.abs(downX-x(original.endMs));operation=Math.min(dl,dr)<=a.dp(16)?(dl<dr?OP_TRIM_START:OP_TRIM_END):OP_MOVE;}
            // An unselected layer can be dragged straight away; it becomes selected when the drag starts.
            else{operation=OP_MOVE;pendingClip=hitClip;}}
        else if(hitOverlay>=0&&!a.project.overlays.get(hitOverlay).locked){originalOverlay=a.project.overlays.get(hitOverlay);
            if(hitOverlay==a.selectedOverlay&&a.selected<0){float dl=Math.abs(downX-x(originalOverlay.startMs)),dr=Math.abs(downX-x(originalOverlay.endMs));operation=Math.min(dl,dr)<=a.dp(16)?(dl<dr?OP_OV_START:OP_OV_END):OP_OV_MOVE;}
            else{operation=OP_OV_MOVE;pendingOverlay=hitOverlay;}}
        if(e.isButtonPressed(MotionEvent.BUTTON_SECONDARY)){operation=0;if(hitClip>=0)a.selectClip(hitClip,false);else if(hitOverlay>=0)a.selectOverlay(hitOverlay);if(hitClip>=0||hitOverlay>=0)a.moreMenu();}
        return true;
    }
    private boolean move(MotionEvent e){
        float dx=e.getRawX()-lastRawX,dy=e.getRawY()-lastRawY;
        if(!moved&&Math.hypot(e.getX()-downX,e.getY()-downY)>a.dp(6)){moved=true;vertical=Math.abs(e.getY()-downY)>Math.abs(e.getX()-downX)*1.3f;
            // A normal horizontal swipe navigates time. Hold first to move a layer;
            // mouse drags and selected trim handles retain their editing behavior.
            boolean mouse=(e.getSource()&android.view.InputDevice.SOURCE_MOUSE)==android.view.InputDevice.SOURCE_MOUSE;
            if(!mouse&&(operation==OP_MOVE||operation==OP_OV_MOVE||operation==OP_MULTI)&&e.getEventTime()-downTime<ViewConfiguration.getLongPressTimeout()){
                operation=vertical?0:OP_SCRUB;pendingClip=-1;pendingOverlay=-1;
            }
            if(pendingClip>=0){a.selectClip(pendingClip,false);pendingClip=-1;}if(pendingOverlay>=0){a.selectOverlay(pendingOverlay);pendingOverlay=-1;}}
        if(!moved)return true;
        if(operation==OP_SCRUB){scrub(e);lastRawX=e.getRawX();lastRawY=e.getRawY();return true;}
        if(reorderFrom>=0){Row r=rowAt(e.getY());reorderTo=r==null||r.kind==2?-1:rows.indexOf(r);invalidate();return true;}
        if(headerZone>=0){((ScrollView)a.timelineScroll.getParent()).scrollBy(0,Math.round(-dy));lastRawX=e.getRawX();lastRawY=e.getRawY();return true;}
        if(operation!=0){lastEventX=e.getX();lastEventY=e.getY();lastEventScroll=a.timelineScroll.getScrollX();updateDrag();updateAutoScroll();return true;}
        // Empty space: drag to scroll the timeline (horizontal) or the layer list (vertical).
        if(vertical)((ScrollView)a.timelineScroll.getParent()).scrollBy(0,Math.round(-dy));
        else{operation=OP_SCRUB;scrub(e);}
        lastRawX=e.getRawX();lastRawY=e.getRawY();return true;
    }
    /** The finger's content x: the last event position plus any auto-scroll since that event. */
    private float contentX(){return lastEventX+(a.timelineScroll.getScrollX()-lastEventScroll);}
    private void updateDrag(){
        float xx=contentX();EditProject p=a.project;long tol=Math.round(a.dp(8)*1000/timelineScale());
        pendingDelta=time(xx)-time(downX);guide=-1;
        if(operation==OP_MULTI){long earliest=Long.MAX_VALUE;for(int i:a.selectedClips())earliest=Math.min(earliest,p.placements().get(i).startMs);for(int i:a.selectedOverlays())earliest=Math.min(earliest,p.overlays.get(i).startMs);
            if(a.snapping){long snapped=TimelineEdits.snap(p,-1,earliest+pendingDelta,tol);if(snapped!=earliest+pendingDelta)guide=snapped;pendingDelta=snapped-earliest;}pendingDelta=Math.max(-earliest,pendingDelta);invalidate();return;}
        if(operation>=OP_OV_START){long anchor=operation==OP_OV_END?originalOverlay.endMs:originalOverlay.startMs;if(a.snapping){long snapped=TimelineEdits.snap(p,-1,anchor+pendingDelta,tol);if(snapped!=anchor+pendingDelta)guide=snapped;pendingDelta=snapped-anchor;}
            long before=pendingDelta;pendingDelta=Math.max(operation==OP_OV_END?originalOverlay.startMs+EditProject.MIN_CLIP_MS-originalOverlay.endMs:-originalOverlay.startMs,pendingDelta);if(operation==OP_OV_START)pendingDelta=Math.min(originalOverlay.endMs-originalOverlay.startMs-EditProject.MIN_CLIP_MS,pendingDelta);if(pendingDelta!=before)guide=-1;invalidate();return;}
        int self=original.index;long anchor=operation==OP_TRIM_END?original.endMs:original.startMs,desired=anchor+pendingDelta;
        if(a.snapping){long snapped=TimelineEdits.snap(p,self,desired,tol);if(snapped==desired&&operation==OP_MOVE){long end=original.endMs+pendingDelta,endSnap=TimelineEdits.snap(p,self,end,tol);if(endSnap!=end){snapped=desired+endSnap-end;guide=endSnap;}}else if(snapped!=desired)guide=snapped;pendingDelta=snapped-anchor;}
        long before=pendingDelta;EditProject.Clip c=original.clip;
        // Pictures extend freely; video and audio stop at their source unless speed is changed explicitly.
        if(operation==OP_TRIM_START){long min=c.image?-7_200_000_000L:Math.round(-c.inMs/c.speed);if(c.track>0)min=Math.max(min,-original.startMs);pendingDelta=Math.max(min,Math.min(c.durationMs()-EditProject.MIN_CLIP_MS,pendingDelta));}
        if(operation==OP_TRIM_END)pendingDelta=Math.max(EditProject.MIN_CLIP_MS-c.durationMs(),Math.min(c.image?7_200_000_000L-c.outMs:Math.round((c.sourceDurationMs-c.outMs)/c.speed),pendingDelta));
        if(operation==OP_MOVE)pendingDelta=Math.max(-original.startMs,pendingDelta);
        if(pendingDelta!=before)guide=-1;invalidate();
    }
    /** Dragging near a viewport edge keeps scrolling so long moves and trims are possible. */
    private void updateAutoScroll(){
        float local=lastEventX-lastEventScroll,edge=a.dp(36);
        int direction=local<edge+headerWidth()?-1:local>viewport()-edge?1:0;
        if(direction!=autoDirection){autoDirection=direction;removeCallbacks(autoScroll);if(direction!=0)postOnAnimation(autoScroll);}
    }
    private void autoScrollStep(){
        if(autoDirection==0||operation==0)return;int before=a.timelineScroll.getScrollX();a.seek(a.project.playheadMs+Math.round(autoDirection*a.dp(8)/timelineScale()*1000));
        if(a.timelineScroll.getScrollX()==before){autoDirection=0;return;}
        updateDrag();postOnAnimation(autoScroll);
    }
    private void scrub(MotionEvent e){a.seek(downPlayhead+Math.round((downRawX-e.getRawX())/timelineScale()*1000));}
    private void stopAutoScroll(){autoDirection=0;removeCallbacks(autoScroll);}
    private boolean up(MotionEvent e){
        stopAutoScroll();boolean longPress=e.getEventTime()-downTime>=ViewConfiguration.getLongPressTimeout();
        if(operation==OP_SCRUB){
            if(moved)scrub(e);
            else if(downY<a.dp(RULER_DP))a.seek(Math.max(0,time(downX)));
            else if(downY>=a.dp(RULER_DP)){if(hitClip>=0)a.selectClip(hitClip,false);else if(hitOverlay>=0)a.selectOverlay(hitOverlay);}
            reset();performClick();return true;
        }
        if(reorderFrom>=0){int from=reorderFrom,to=reorderTo;reset();if(moved&&to>=0&&to!=from)a.reorderRows(from,to);else if(!moved)a.tell("Drag the ≡ handle up or down to reorder layers.");return true;}
        if(headerZone>=0&&!moved){Row r=rowAt(downY);int zone=headerZone;reset();if(r!=null)headerTap(r,zone,longPress);return true;}
        if(!pinching&&operation!=0&&moved&&(pendingDelta!=0||operation==OP_MOVE&&targetTrack()!=original.clip.track))commit();
        else if(!pinching&&!moved){
            boolean multi=longPress||(e.getMetaState()&(KeyEvent.META_CTRL_ON|KeyEvent.META_SHIFT_ON))!=0;
            if(hitClip>=0){if(multi)a.toggleMulti(hitClip,-1);else a.selectClip(hitClip,false);}
            else if(hitOverlay>=0){if(multi)a.toggleMulti(-1,hitOverlay);else a.selectOverlay(hitOverlay);}
            else if(longPress)a.toggleMarkerAt(time(downX));
            else a.deselect();
            performClick();
        }
        reset();return true;
    }
    private void headerTap(Row r,int zone,boolean longPress){
        int[] row={r.kind,r.index};
        if(longPress){String name=a.rowName(row);new android.app.AlertDialog.Builder(a).setTitle(name).setItems(new String[]{"Toggle visibility / mute","Lock / unlock","Select layer","Rename layer","Layer management"},(d,i)->{if(i<2)a.rowFlag(row,i==0?"hide":"lock");else if(i==3)a.renameRow(row);else if(i==4){a.tab=3;if(a.mobile!=null)a.mobile.dismissed=false;a.renderInspector();}else if(r.kind==1)a.selectOverlay(r.index);else for(int n=0;n<a.project.clips.size();n++)if(a.project.clips.get(n).track==r.index){a.selectClip(n,false);break;}}).show();return;}
        a.rowFlag(row,"hide");
    }
    private void commit(){
        int op=operation;long delta=pendingDelta;EditProject.Placement origin=original;EditProject.Overlay ov=originalOverlay;int track=op==OP_MOVE?targetTrack():-1;
        if(op==OP_MULTI){java.util.List<Integer> clips=a.selectedClips(),overlays=a.selectedOverlays();java.util.List<Integer> movable=new ArrayList<>();for(int i:clips)if(a.project.clips.get(i).track>0)movable.add(i);
            if(movable.size()<clips.size())a.tell("Main-sequence clips keep their order; other selected layers moved.");a.edit("Layers moved",()->LayerOps.moveMany(a.project,movable,overlays,delta));return;}
        a.edit(op==OP_MOVE?(track!=origin.clip.track?"Moved to "+a.project.tracks[track].name:"Clip moved"):op==OP_OV_MOVE?"Layer moved":"Trim applied",()->{
            if(op>=OP_OV_START){if(op==OP_OV_START||op==OP_OV_MOVE)ov.startMs+=delta;if(op==OP_OV_END||op==OP_OV_MOVE)ov.endMs+=delta;}
            else if(op<OP_MOVE)TimelineEdits.edge(a.project,a.selected,op==OP_TRIM_START,delta);
            else a.selected=TimelineEdits.place(a.project,origin.index,track,origin.startMs+delta);
        });
    }
    private void reset(){operation=0;guide=-1;pendingDelta=0;pinching=false;headerZone=-1;reorderFrom=-1;reorderTo=-1;pendingClip=-1;pendingOverlay=-1;getParent().requestDisallowInterceptTouchEvent(false);invalidate();}
    @Override public boolean performClick(){super.performClick();return true;}

    // ---- Media dropped from Project Media ------------------------------------------------------------
    @Override public boolean onDragEvent(DragEvent e){
        switch(e.getAction()){
            case DragEvent.ACTION_DRAG_STARTED:return e.getLocalState() instanceof EditProject.Clip&&!a.uiLocked;
            case DragEvent.ACTION_DRAG_ENTERED:case DragEvent.ACTION_DRAG_LOCATION:{
                rows();dropAsset=(EditProject.Clip)e.getLocalState();long start=Math.max(0,time(e.getX()));
                if(a.snapping){long snapped=TimelineEdits.snap(a.project,-1,start,Math.round(a.dp(8)*1000/timelineScale()));start=snapped;}
                dropStart=start;dropTrack=dropTrackFor(dropAsset,e.getY(),start);
                // Dragging near either edge scrolls so media can be dropped anywhere.
                float local=e.getX()-a.timelineScroll.getScrollX();if(local>viewport()-a.dp(40))a.timelineScroll.scrollBy(a.dp(10),0);else if(local<headerWidth()+a.dp(20))a.timelineScroll.scrollBy(-a.dp(10),0);
                invalidate();return true;}
            case DragEvent.ACTION_DROP:{EditProject.Clip asset=(EditProject.Clip)e.getLocalState();int track=dropTrack;long start=dropStart;clearDrop();
                if(track>=0)a.dropMedia(asset,track,start);return true;}
            case DragEvent.ACTION_DRAG_EXITED:case DragEvent.ACTION_DRAG_ENDED:clearDrop();return true;
            default:return true;
        }
    }
    private void clearDrop(){dropAsset=null;dropTrack=-1;invalidate();}
    @Override public boolean onGenericMotionEvent(MotionEvent event){
        if(event.getAction()==MotionEvent.ACTION_SCROLL){float wheel=event.getAxisValue(MotionEvent.AXIS_VSCROLL),horizontal=event.getAxisValue(MotionEvent.AXIS_HSCROLL);
            if((event.getMetaState()&KeyEvent.META_CTRL_ON)!=0){a.zoomTimeline(wheel>0?1.2f:1/1.2f,event.getX()-a.timelineScroll.getScrollX());return true;}
            boolean sideways=(event.getMetaState()&KeyEvent.META_SHIFT_ON)!=0||horizontal!=0||contentHeight()<=((View)a.timelineScroll.getParent()).getHeight();
            if(sideways){float amount=horizontal!=0?horizontal:wheel;a.pausePlayback();a.seek(a.project.playheadMs-Math.round(amount*a.dp(70)/timelineScale()*1000));}
            else((ScrollView)a.timelineScroll.getParent()).smoothScrollBy(0,Math.round(-wheel*a.dp(52)));
            return true;}
        return super.onGenericMotionEvent(event);
    }
}

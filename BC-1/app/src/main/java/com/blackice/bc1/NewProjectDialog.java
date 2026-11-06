package com.blackice.bc1;

import android.app.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import com.blackice.bc1.core.CanvasFormat;
import com.blackice.bc1.editor.EditProject;
import java.util.function.Consumer;

/** Native project setup. Canvas choice never depends on display orientation. */
final class NewProjectDialog {
    private final Activity a;private final EditProject project=new EditProject();
    private final java.util.List<RatioTile> tiles=new java.util.ArrayList<>();
    private NewProjectDialog(Activity activity){a=activity;}
    static AlertDialog show(Activity a,Consumer<EditProject> create){return new NewProjectDialog(a).show(create);}
    int dp(float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    TextView text(String s,int size){TextView t=new TextView(a);t.setText(s);t.setTextSize(size);t.setTextColor(BlackIceTheme.TEXT);return t;}
    GradientDrawable background(int color,int border){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(12));g.setStroke(dp(1),border);return g;}
    TextView button(String s,Runnable action){TextView b=text(s,14);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(48));b.setPadding(dp(16),0,dp(16),0);b.setFocusable(true);b.setBackground(background(BlackIceTheme.ELEVATED,BlackIceTheme.BORDER));b.setOnClickListener(v->action.run());return b;}
    private View preview;private TextView caption;
    AlertDialog show(Consumer<EditProject> create){
        BlackIceDialog shell=new BlackIceDialog(a,"Create new project","Add",null);AlertDialog dialog=shell.dialog;
        View close=findClose(shell.root);if(close!=null)close.setContentDescription("Close create new project");
        LinearLayout columns=new LinearLayout(a);columns.setOrientation(shell.wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        LinearLayout setup=BlackIceDialog.column(a),canvas=BlackIceDialog.column(a);
        setup.addView(text("Project name",14));EditText name=BlackIceDialog.field(a,"Project name","");name.setHint("Automatic: ICE_01, ICE_02…");LinearLayout.LayoutParams nameLp=new LinearLayout.LayoutParams(-1,dp(48));nameLp.topMargin=dp(8);nameLp.bottomMargin=dp(20);setup.addView(name,nameLp);
        setup.addView(text("Aspect ratio",14));RatioFlow ratios=new RatioFlow();for(String ratio:CanvasFormat.SELECTABLE){RatioTile tile=new RatioTile(ratio);tiles.add(tile);ratios.addView(tile);}setup.addView(ratios,new LinearLayout.LayoutParams(-1,-2));
        canvas.addView(text("Canvas preview",14));preview=new View(a){final Paint paint=new Paint(3);@Override protected void onDraw(Canvas c){
            float pad=dp(10),aspect=project.canvasAspect(),w=Math.min(getWidth()-2*pad,(getHeight()-2*pad)*aspect),h=w/aspect;
            RectF r=new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
            paint.setStyle(Paint.Style.FILL);paint.setColor(BlackIceTheme.BACKGROUND);c.drawRoundRect(r,dp(6),dp(6),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setColor(BlackIceTheme.BORDER);paint.setStrokeWidth(dp(1));c.drawRoundRect(r,dp(6),dp(6),paint);
            for(int i=1;i<3;i++){c.drawLine(r.left+w*i/3,r.top,r.left+w*i/3,r.bottom,paint);c.drawLine(r.left,r.top+h*i/3,r.right,r.top+h*i/3,paint);}
            paint.setColor(BlackIceTheme.MUTED);for(int x:new int[]{-1,1})for(int y:new int[]{-1,1}){float cx=x<0?r.left+dp(8):r.right-dp(8),cy=y<0?r.top+dp(8):r.bottom-dp(8);c.drawLine(cx,cy,cx-x*dp(14),cy,paint);c.drawLine(cx,cy,cx,cy-y*dp(14),paint);}
        }};preview.setContentDescription("Project canvas preview");canvas.addView(preview,new LinearLayout.LayoutParams(-1,dp(190)));caption=text("",13);caption.setGravity(Gravity.CENTER);canvas.addView(caption);updateCanvas();
        columns.addView(setup,shell.wide?new LinearLayout.LayoutParams(0,-2,1.5f):new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams canvasLp=shell.wide?new LinearLayout.LayoutParams(0,-2,1):new LinearLayout.LayoutParams(-1,-2);if(shell.wide){canvasLp.leftMargin=dp(20);canvas.setPadding(dp(20),0,0,0);}else canvasLp.topMargin=dp(16);columns.addView(canvas,canvasLp);shell.content.addView(columns);
        shell.footer("Cancel",false,dialog::dismiss);final boolean[] submitting={false};shell.footer("Create project",true,()->{if(submitting[0])return;String n=name.getText().toString().trim();if(n.length()>120){name.setError("Use at most 120 characters");return;}submitting[0]=true;project.name=n;dialog.dismiss();create.accept(project);});shell.show();return dialog;
    }
    View findClose(View v){if(v instanceof TextView&&"×".contentEquals(((TextView)v).getText()))return v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){View found=findClose(((android.view.ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    void updateCanvas(){if(preview!=null)preview.invalidate();if(caption!=null){int[] size=project.canvasSize(1080);caption.setText(size[0]+" × "+size[1]+" · "+project.frameRate+" fps");}}
    final class RatioTile extends View {
        final String ratio;final Paint p=new Paint(3);
        RatioTile(String r){super(a);ratio=r;setFocusable(true);setClickable(true);setContentDescription("Aspect ratio "+r);setSelected(project.ratioName().equals(r));setOnClickListener(v->{project.setCanvasRatio(r);updateCanvas();for(RatioTile t:tiles){t.setSelected(t==this);t.invalidate();}sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);});}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo n){super.onInitializeAccessibilityNodeInfo(n);n.setClassName("android.widget.RadioButton");n.setCheckable(true);n.setChecked(isSelected());}
        @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
        @Override protected void onDraw(Canvas c){float maxW=getWidth()-dp(14),maxH=getHeight()-dp(24),aspect=CanvasFormat.aspect(ratio),w=Math.min(maxW,maxH*aspect),h=w/aspect;RectF r=new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);p.setStyle(Paint.Style.FILL);p.setColor(isSelected()?0xFF123E5F:BlackIceTheme.BACKGROUND);c.drawRoundRect(r,dp(5),dp(5),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(isSelected()||isFocused()?2:1));p.setColor(isSelected()?BlackIceTheme.ACCENT:isFocused()||isPressed()?BlackIceTheme.ICE:BlackIceTheme.BORDER);c.drawRoundRect(r,dp(5),dp(5),p);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(12)*getResources().getConfiguration().fontScale);p.setTextAlign(Paint.Align.CENTER);p.setColor(isSelected()?BlackIceTheme.ACCENT:BlackIceTheme.TEXT);c.drawText(ratio,r.centerX(),r.centerY()-(p.ascent()+p.descent())/2,p);if(isSelected()){p.setColor(BlackIceTheme.ICE);p.setStrokeWidth(dp(2));c.drawLine(r.right-dp(14),r.top+dp(7),r.right-dp(11),r.top+dp(10),p);c.drawLine(r.right-dp(11),r.top+dp(10),r.right-dp(5),r.top+dp(4),p);}}
    }
    final class RatioFlow extends ViewGroup {
        int columns,cell;
        RatioFlow(){super(a);}
        @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);int min=dp(88*Math.max(1,getResources().getConfiguration().fontScale));columns=Math.max(1,Math.min(5,width/min));cell=width/columns;for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(112),MeasureSpec.EXACTLY));setMeasuredDimension(width,((getChildCount()+columns-1)/columns)*dp(112));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){int x=i%columns*cell,y=i/columns*dp(112);getChildAt(i).layout(x,y,x+cell,y+dp(112));}}
    }
}

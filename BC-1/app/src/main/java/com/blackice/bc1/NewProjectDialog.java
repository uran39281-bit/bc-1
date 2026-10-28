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
    AlertDialog show(Consumer<EditProject> create){
        LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(20),dp(12),dp(20),dp(16));root.setBackground(background(BlackIceTheme.PRIMARY,BlackIceTheme.BORDER));
        AlertDialog dialog=new AlertDialog.Builder(a).setView(root).create();
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("Create new project",21);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView close=button("×",dialog::dismiss);close.setTextSize(28);close.setContentDescription("Close create new project");header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);
        ScrollView scroll=new ScrollView(a){@Override protected void onMeasure(int w,int h){super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED?Integer.MAX_VALUE:Math.max(dp(64),MeasureSpec.getSize(h)-dp(48)),dp(a.getResources().getConfiguration().screenHeightDp*.58f)),MeasureSpec.AT_MOST));}};
        LinearLayout form=new LinearLayout(a);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(0,dp(16),0,dp(12));scroll.addView(form);root.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        form.addView(text("Project name",14));EditText name=new EditText(a);name.setSingleLine(true);name.setTextSize(16);name.setTextColor(BlackIceTheme.TEXT);name.setText("");name.setHint("Automatic: ICE_01, ICE_02…");name.setContentDescription("Project name");name.setPadding(dp(12),0,dp(12),0);name.setBackground(background(BlackIceTheme.BACKGROUND,BlackIceTheme.BORDER));LinearLayout.LayoutParams input=new LinearLayout.LayoutParams(-1,dp(52));input.topMargin=dp(8);input.bottomMargin=dp(20);form.addView(name,input);
        form.addView(text("Aspect ratio",14));RatioFlow ratios=new RatioFlow();for(String ratio:CanvasFormat.RATIOS){RatioTile tile=new RatioTile(ratio);tiles.add(tile);ratios.addView(tile);}form.addView(ratios,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout actions=new LinearLayout(a);actions.setGravity(Gravity.END);TextView cancel=button("Cancel",dialog::dismiss);actions.addView(cancel);TextView confirm=button("Create project",()->{String n=name.getText().toString().trim();if(n.length()>120){name.setError("Use at most 120 characters");name.requestFocus();return;}project.name=n;dialog.dismiss();create.accept(project);});confirm.setBackground(background(0xFF087DCD,BlackIceTheme.ICE));LinearLayout.LayoutParams primary=new LinearLayout.LayoutParams(-2,dp(48));primary.leftMargin=dp(12);actions.addView(confirm,primary);root.addView(actions);
        dialog.setOnShowListener(d->{dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);dialog.getWindow().setLayout(Math.min(dp(620),a.getResources().getDisplayMetrics().widthPixels-dp(24)),-2);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);});dialog.show();return dialog;
    }
    final class RatioTile extends View {
        final String ratio;final Paint p=new Paint(3);
        RatioTile(String r){super(a);ratio=r;setFocusable(true);setClickable(true);setContentDescription("Aspect ratio "+r);setSelected(project.ratioName().equals(r));setOnClickListener(v->{project.setCanvasRatio(r);for(RatioTile t:tiles){t.setSelected(t==this);t.invalidate();}sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);});}
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo n){super.onInitializeAccessibilityNodeInfo(n);n.setClassName("android.widget.RadioButton");n.setCheckable(true);n.setChecked(isSelected());}
        @Override protected void drawableStateChanged(){super.drawableStateChanged();invalidate();}
        @Override protected void onDraw(Canvas c){float maxW=getWidth()-dp(14),maxH=getHeight()-dp(24),aspect=CanvasFormat.aspect(ratio),w=Math.min(maxW,maxH*aspect),h=w/aspect;RectF r=new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);p.setStyle(Paint.Style.FILL);p.setColor(isSelected()?0xFF123E5F:BlackIceTheme.BACKGROUND);c.drawRoundRect(r,dp(5),dp(5),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(isSelected()||isFocused()?2:1));p.setColor(isSelected()||isFocused()||isPressed()?BlackIceTheme.ICE:BlackIceTheme.BORDER);c.drawRoundRect(r,dp(5),dp(5),p);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(12)*getResources().getConfiguration().fontScale);p.setTextAlign(Paint.Align.CENTER);p.setColor(BlackIceTheme.TEXT);c.drawText(ratio,r.centerX(),r.centerY()-(p.ascent()+p.descent())/2,p);if(isSelected()){p.setColor(BlackIceTheme.ICE);p.setStrokeWidth(dp(2));c.drawLine(r.right-dp(14),r.top+dp(7),r.right-dp(11),r.top+dp(10),p);c.drawLine(r.right-dp(11),r.top+dp(10),r.right-dp(5),r.top+dp(4),p);}}
    }
    final class RatioFlow extends ViewGroup {
        int columns,cell;
        RatioFlow(){super(a);}
        @Override protected void onMeasure(int w,int h){int width=MeasureSpec.getSize(w);int min=dp(88*Math.max(1,getResources().getConfiguration().fontScale));columns=Math.max(1,Math.min(5,width/min));cell=width/columns;for(int i=0;i<getChildCount();i++)getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cell,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(dp(112),MeasureSpec.EXACTLY));setMeasuredDimension(width,((getChildCount()+columns-1)/columns)*dp(112));}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){for(int i=0;i<getChildCount();i++){int x=i%columns*cell,y=i/columns*dp(112);getChildAt(i).layout(x,y,x+cell,y+dp(112));}}
    }
}

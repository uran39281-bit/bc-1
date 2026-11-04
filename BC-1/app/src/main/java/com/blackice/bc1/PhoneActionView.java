package com.blackice.bc1;

import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Short painted frame inside a nonoverlapping 44/48dp focusable native touch target. */
final class PhoneActionView extends FrameLayout {
    final TextView text;
    static int touchHeight(EditorActivity a){return a.dp(Math.max(44,44*a.getResources().getConfiguration().fontScale));}
    PhoneActionView(EditorActivity a,String name,String icon,boolean above,int paintedHeight,Runnable action){
        super(a);setFocusable(true);setClickable(true);setContentDescription(name);setTooltipText(name);
        boolean waves=paintedHeight==32&&!above&&(name.equals("Move & Transform")||name.equals("Blending & Opacity")||name.equals("Effects")||name.endsWith(" & Style")||name.equals("Layer Tools")||name.equals("Audio"));
        text=new TextView(a){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);@Override protected void onDraw(Canvas canvas){if(waves){float w=getWidth(),h=getHeight();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(.6f));p.setColor(0x1842BAFF);for(int i=0;i<3;i++){Path path=new Path();float y=h*(.5f+i*.12f);path.moveTo(0,y);path.cubicTo(w*.35f,y-h*.28f,w*.5f,y+h*.4f,w,y);canvas.drawPath(path,p);}}super.onDraw(canvas);}};text.setText(name);text.setTextSize(above?10:11);text.setTextColor(EditorActivity.TEXT);text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);text.setGravity(Gravity.CENTER);text.setMaxLines(2);text.setEllipsize(android.text.TextUtils.TruncateAt.END);text.setPadding(a.dp(5),0,a.dp(5),0);text.setBackground(a.buttonBackground(false));
        if(icon!=null){android.graphics.drawable.Drawable art=a.icon(icon,BlackIceTheme.ICE);art.setBounds(0,0,a.dp(above?16:20),a.dp(above?16:20));if(above)text.setCompoundDrawables(null,art,null,null);else text.setCompoundDrawables(art,null,null,null);text.setCompoundDrawablePadding(a.dp(above?1:5));}
        text.setDuplicateParentStateEnabled(true);text.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);addView(text,new FrameLayout.LayoutParams(-1,Math.min(touchHeight(a)-a.dp(4),a.dp(paintedHeight*a.getResources().getConfiguration().fontScale)),Gravity.CENTER));
        setForeground(new RippleDrawable(ColorStateList.valueOf(0x337CD5F3),null,null));setOnClickListener(v->action.run());text.setOnClickListener(v->performClick());
        setOnFocusChangeListener((v,focus)->{GradientDrawable bg=a.buttonBackground(isSelected());if(focus)bg.setStroke(a.dp(2),BlackIceTheme.ICE);text.setBackground(bg);});
    }
    @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){return true;}
    void active(EditorActivity a,boolean on){setSelected(on);text.setTextColor(on?BlackIceTheme.ICE:EditorActivity.TEXT);text.setBackground(a.buttonBackground(on));}
    @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);if(text!=null){text.setEnabled(enabled);text.setAlpha(enabled?1:.4f);}}
}

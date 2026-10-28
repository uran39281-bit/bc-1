package com.blackice.bc1;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/** Fixed indicator ruler shared by bounded editor controls. One pointer gesture is one edit. */
final class NumberRuler extends View {
    final float min,max,step;final DoubleSupplier read;final DoubleConsumer write;
    final Runnable begin,end,cancel;final Paint paint=new Paint(3);
    float down,start;boolean dragging;
    NumberRuler(Context context,String label,float min,float max,float step,DoubleSupplier read,
                DoubleConsumer write,Runnable begin,Runnable end,Runnable cancel){
        super(context);this.min=min;this.max=max;this.step=step;this.read=read;this.write=write;
        this.begin=begin;this.end=end;this.cancel=cancel;setFocusable(true);setClickable(true);setContentDescription(label);
    }
    float dp(float n){return n*getResources().getDisplayMetrics().density;}
    void set(double value){write.accept(Math.max(min,Math.min(max,value)));invalidate();}
    void nudge(double delta){begin.run();set(read.getAsDouble()+delta);end.run();}
    @Override protected void onDraw(Canvas c){
        float value=(float)read.getAsDouble(),spacing=dp(12),w=getWidth(),h=getHeight();
        paint.setStrokeWidth(dp(1));paint.setTextSize(dp(10)*getResources().getConfiguration().fontScale);paint.setTextAlign(Paint.Align.CENTER);
        int center=(int)Math.floor(value/step),radius=(int)(w/(2*spacing))+2;
        for(int tick=center-radius;tick<=center+radius;tick++){float n=tick*step;if(n<min||n>max)continue;
            float x=w/2+(n-value)/step*spacing;paint.setColor(BlackIceTheme.MUTED);boolean major=tick%5==0||n==min||n==max;
            c.drawLine(x,h-(major?h*.45f:h*.25f),x,h,paint);
            if(major)c.drawText(String.format(java.util.Locale.US,"%s",Math.abs(n-Math.round(n))<.001?Integer.toString(Math.round(n)):String.format(java.util.Locale.US,"%.1f",n)),x,dp(14),paint);
        }
        paint.setColor(BlackIceTheme.ICE);paint.setStrokeWidth(dp(isFocused()?3:2));c.drawLine(w/2,0,w/2,h,paint);
    }
    @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled())return false;switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:requestFocus();begin.run();start=(float)read.getAsDouble();down=e.getX();dragging=true;getParent().requestDisallowInterceptTouchEvent(true);return true;
        case MotionEvent.ACTION_MOVE:if(dragging)set(start-(e.getX()-down)/dp(12)*step);return true;
        case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:if(dragging){dragging=false;getParent().requestDisallowInterceptTouchEvent(false);if(e.getActionMasked()==MotionEvent.ACTION_CANCEL)cancel.run();else{end.run();performClick();}}return true;
    }return super.onTouchEvent(e);}
    @Override public boolean onKeyDown(int code,KeyEvent e){if(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT){nudge(code==KeyEvent.KEYCODE_DPAD_RIGHT?step:-step);return true;}return super.onKeyDown(code,e);}
    @Override public boolean onGenericMotionEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_SCROLL){nudge(e.getAxisValue(MotionEvent.AXIS_VSCROLL)*step);return true;}return super.onGenericMotionEvent(e);}
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo n){super.onInitializeAccessibilityNodeInfo(n);n.setClassName("android.widget.SeekBar");n.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,min,max,(float)read.getAsDouble()));n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
    @Override public boolean performAccessibilityAction(int action,Bundle args){if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){nudge(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?step:-step);return true;}if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){begin.run();set(args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE));end.run();return true;}return super.performAccessibilityAction(action,args);}
}

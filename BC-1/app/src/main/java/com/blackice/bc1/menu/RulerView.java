package com.blackice.bc1.menu;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;
/** Fixed-center ruler. Finger-right increases value; cancellation delegates rollback. */
public final class RulerView extends View {
 public interface Binding {double value();void begin();void preview(double value);void commit();void cancel();}
 private final Binding binding;private final double min,max,step;private final Paint p=new Paint(3);
 private int pointer=-1;private boolean dragging;private float downX;private double start;
 public RulerView(Context c,String label,double min,double max,double step,Binding b){super(c);if(!Double.isFinite(min)||!Double.isFinite(max)||min>max||!Double.isFinite(step)||step<=0)throw new IllegalArgumentException("Invalid ruler bounds");this.min=min;this.max=max;this.step=step;binding=b;setContentDescription(label);setFocusable(true);setClickable(true);setMinimumHeight(dp(58));}
 private int dp(float v){return MenuPalette.dp(getContext(),v);}private double bounded(double v){return Math.max(min,Math.min(max,v));}
 @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(dp(320),w),resolveSize(dp(64),h));}
 @Override protected void onDraw(Canvas c){super.onDraw(c);double value=binding.value();if(!Double.isFinite(value))return;float spacing=dp(14),mid=getWidth()/2f,bottom=getHeight()-dp(3);long center=(long)Math.floor(value/step);int radius=(int)(getWidth()/spacing/2)+3;
  p.setTextSize(dp(10)*getResources().getConfiguration().fontScale);p.setTextAlign(Paint.Align.CENTER);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(dp(1));
  for(long index=center-radius;index<=center+radius;index++){double n=index*step;if(n<min-1e-8||n>max+1e-8)continue;float x=mid+(float)((n-value)/step)*spacing;boolean major=Math.floorMod(index,5)==0; p.setColor(MenuPalette.MUTED);c.drawLine(x,bottom-dp(major?24:13),x,bottom,p);if(major){String s=Math.abs(n-Math.rint(n))<1e-6?String.format(Locale.US,"%.0f",n):String.format(Locale.US,"%.1f",n);c.drawText(s,x,dp(13),p);}}
  p.setColor(MenuPalette.GREEN);p.setStrokeWidth(dp(3));c.drawLine(mid,bottom-dp(32),mid,bottom,p);c.drawCircle(mid,bottom-dp(32),dp(3),p);
 }
 private void value(double v){binding.preview(bounded(v));invalidate();}
 @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled()){finish(false);return false;}switch(e.getActionMasked()){
  case MotionEvent.ACTION_DOWN:requestFocus();pointer=e.getPointerId(0);start=binding.value();downX=e.getX();dragging=true;binding.begin();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;
  case MotionEvent.ACTION_MOVE:{int i=e.findPointerIndex(pointer);if(dragging&&i>=0)value(start+(e.getX(i)-downX)/dp(14)*step);return dragging;}
  case MotionEvent.ACTION_POINTER_UP:if(e.getPointerId(e.getActionIndex())==pointer){finish(false);return true;}return dragging;
  case MotionEvent.ACTION_UP:if(dragging){int i=e.findPointerIndex(pointer);if(i>=0)value(start+(e.getX(i)-downX)/dp(14)*step);finish(true);performClick();return true;}break;
  case MotionEvent.ACTION_CANCEL:if(dragging){finish(false);return true;}break;
 }return super.onTouchEvent(e);}
 private void finish(boolean commit){if(!dragging)return;dragging=false;pointer=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(commit)binding.commit();else binding.cancel();invalidate();}
 private void nudge(double d){double v=bounded(binding.value()+d);if(v==binding.value())return;binding.begin();binding.preview(v);binding.commit();invalidate();}
 @Override public void setEnabled(boolean enabled){if(!enabled)finish(false);super.setEnabled(enabled);}
 @Override protected void onDetachedFromWindow(){finish(false);super.onDetachedFromWindow();}
 @Override public boolean onKeyDown(int code,KeyEvent e){if(isEnabled()&&(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT)){nudge((code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)*step*(e.isShiftPressed()?10:1));return true;}return super.onKeyDown(code,e);}
 @Override public boolean onGenericMotionEvent(MotionEvent e){if(isEnabled()&&e.getActionMasked()==MotionEvent.ACTION_SCROLL){nudge(e.getAxisValue(MotionEvent.AXIS_VSCROLL)*step);return true;}return super.onGenericMotionEvent(e);}
 @Override public boolean performClick(){super.performClick();return true;}
 @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo n){super.onInitializeAccessibilityNodeInfo(n);n.setClassName("android.widget.SeekBar");n.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,(float)min,(float)max,(float)binding.value()));n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);n.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
 @Override public boolean performAccessibilityAction(int action,Bundle args){if(!isEnabled())return false;if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){nudge(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?step:-step);return true;}if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&args!=null){double v=args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);if(Double.isFinite(v)){binding.begin();value(v);binding.commit();return true;}}return super.performAccessibilityAction(action,args);}
}

package com.blackice.bc1.menu;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

/** Relative two-axis positioning in project pixels, independent of preview zoom or panel size. */
public final class PositionTrackpadView extends View {
 public interface Binding {
  double x();double y();void begin();void preview(double x,double y);void commit();void cancel();
 }
 private static final int ACTION_LEFT=0x7f0b7f00,ACTION_RIGHT=ACTION_LEFT+1,ACTION_UP=ACTION_LEFT+2,ACTION_DOWN=ACTION_LEFT+3;
 private final Binding binding;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private boolean dragging;private int pointer=-1;private float downX,downY,lastX,lastY;private double startX,startY;
 public PositionTrackpadView(Context context,Binding binding){
  super(context);this.binding=binding;setFocusable(true);setClickable(true);setBackground(MenuPalette.surface(context,false));
  setContentDescription("Position trackpad. Drag to move X and Y. Arrow keys move one pixel; Shift moves ten pixels.");
 }
 private int dp(float n){return MenuPalette.dp(getContext(),n);}
 @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(dp(300),w),resolveSize(dp(160),h));}
 @Override protected void onDraw(Canvas c){
  super.onDraw(c);paint.setStrokeWidth(dp(1));paint.setColor(MenuPalette.BORDER);
  for(int x=dp(16);x<getWidth();x+=dp(24))for(int y=dp(16);y<getHeight();y+=dp(24))c.drawPoint(x,y,paint);
  float x=dragging?lastX:getWidth()/2f,y=dragging?lastY:getHeight()/2f;
  paint.setColor(isEnabled()?MenuPalette.ACCENT:MenuPalette.MUTED);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(isFocused()?2:1));
  c.drawCircle(x,y,dp(13),paint);c.drawLine(x-dp(20),y,x+dp(20),y,paint);c.drawLine(x,y-dp(20),x,y+dp(20),paint);
  paint.setStyle(Paint.Style.FILL);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(dp(12)*getResources().getConfiguration().fontScale);
  c.drawText(String.format(Locale.US,"X %.2f px   Y %.2f px",binding.x(),binding.y()),getWidth()/2f,getHeight()-dp(12),paint);
 }
 private void move(float x,float y){lastX=x;lastY=y;double density=getResources().getDisplayMetrics().density;binding.preview(startX+(x-downX)/density,startY-(y-downY)/density);invalidate();}
 @Override public boolean onTouchEvent(MotionEvent event){
  if(!isEnabled()){finish(false);return false;}
  switch(event.getActionMasked()){
   case MotionEvent.ACTION_DOWN:
    requestFocus();pointer=event.getPointerId(0);downX=lastX=event.getX();downY=lastY=event.getY();startX=binding.x();startY=binding.y();dragging=true;
    binding.begin();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;
   case MotionEvent.ACTION_MOVE:{int index=event.findPointerIndex(pointer);if(dragging&&index>=0)move(event.getX(index),event.getY(index));return dragging;}
   case MotionEvent.ACTION_POINTER_UP:if(event.getPointerId(event.getActionIndex())==pointer){finish(false);return true;}return dragging;
   case MotionEvent.ACTION_UP:if(dragging){int index=event.findPointerIndex(pointer);if(index>=0)move(event.getX(index),event.getY(index));finish(true);performClick();return true;}break;
   case MotionEvent.ACTION_CANCEL:if(dragging){finish(false);return true;}break;
  }
  return super.onTouchEvent(event);
 }
 private void finish(boolean commit){if(!dragging)return;dragging=false;pointer=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(commit)binding.commit();else binding.cancel();invalidate();}
 private void nudge(double x,double y){binding.begin();binding.preview(binding.x()+x,binding.y()+y);binding.commit();invalidate();}
 @Override public void setEnabled(boolean enabled){if(!enabled)finish(false);super.setEnabled(enabled);}
 @Override protected void onDetachedFromWindow(){finish(false);super.onDetachedFromWindow();}
 @Override public boolean performClick(){super.performClick();return true;}
 @Override public boolean onKeyDown(int code,KeyEvent event){
  if(isEnabled()){double step=event.isShiftPressed()?10:1;switch(code){
   case KeyEvent.KEYCODE_DPAD_LEFT:nudge(-step,0);return true;case KeyEvent.KEYCODE_DPAD_RIGHT:nudge(step,0);return true;
   case KeyEvent.KEYCODE_DPAD_UP:nudge(0,step);return true;case KeyEvent.KEYCODE_DPAD_DOWN:nudge(0,-step);return true;
  }}return super.onKeyDown(code,event);
 }
 @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo node){
  super.onInitializeAccessibilityNodeInfo(node);
  node.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_LEFT,"Move left one pixel"));node.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_RIGHT,"Move right one pixel"));
  node.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_UP,"Move up one pixel"));node.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_DOWN,"Move down one pixel"));
 }
 @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){
  if(isEnabled()){switch(action){case ACTION_LEFT:nudge(-1,0);return true;case ACTION_RIGHT:nudge(1,0);return true;case ACTION_UP:nudge(0,1);return true;case ACTION_DOWN:nudge(0,-1);return true;}}
  return super.performAccessibilityAction(action,args);
 }
}

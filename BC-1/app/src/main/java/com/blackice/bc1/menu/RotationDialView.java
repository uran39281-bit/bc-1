package com.blackice.bc1.menu;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Locale;
/** Continuous cumulative angle, clockwise-positive; stored values never wrap at 360. */
public final class RotationDialView extends View {
 public interface Binding {double value();void begin();void preview(double degrees);void commit();void cancel();}
 private final Binding binding;private final Paint p=new Paint(3);private boolean dragging;private int pointer=-1;private double last,total;
 public RotationDialView(Context c,Binding b){super(c);binding=b;setFocusable(true);setClickable(true);setContentDescription("Rotation dial. Clockwise increases rotation, including multiple turns.");}
 private int dp(float n){return MenuPalette.dp(getContext(),n);}
 @Override protected void onMeasure(int w,int h){setMeasuredDimension(resolveSize(dp(250),w),resolveSize(dp(210),h));}
 @Override protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.max(0,Math.min(getWidth(),getHeight())/2f-dp(13));p.setColor(MenuPalette.BORDER);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));c.drawCircle(cx,cy,r,p);
  for(int i=0;i<60;i++){double a=i*Math.PI/30;float inner=r-dp(i%5==0?10:5);c.drawLine(cx+(float)Math.sin(a)*inner,cy-(float)Math.cos(a)*inner,cx+(float)Math.sin(a)*r,cy-(float)Math.cos(a)*r,p);}
  double a=Math.toRadians(binding.value()%360);p.setColor(MenuPalette.GREEN);p.setStrokeWidth(dp(2));c.drawLine(cx+(float)Math.sin(a)*r*.4f,cy-(float)Math.cos(a)*r*.4f,cx+(float)Math.sin(a)*r,cy-(float)Math.cos(a)*r,p);p.setStyle(Paint.Style.FILL);c.drawCircle(cx+(float)Math.sin(a)*r,cy-(float)Math.cos(a)*r,dp(6),p);p.setColor(MenuPalette.TEXT);p.setTextSize(dp(17));p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);c.drawText(String.format(Locale.US,"%.2f°",binding.value()),cx,cy+dp(6),p);
 }
 private double angle(float x,float y){return Math.toDegrees(Math.atan2(x-getWidth()/2f,-(y-getHeight()/2f)));}
 private void move(float x,float y){if(Math.hypot(x-getWidth()/2f,y-getHeight()/2f)<dp(12))return;double a=angle(x,y),delta=a-last;while(delta>180)delta-=360;while(delta<-180)delta+=360;total+=delta;last=a;binding.preview(total);invalidate();}
 @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled()){finish(false);return false;}switch(e.getActionMasked()){
  case MotionEvent.ACTION_DOWN:if(Math.hypot(e.getX()-getWidth()/2f,e.getY()-getHeight()/2f)<dp(12))return false;pointer=e.getPointerId(0);last=angle(e.getX(),e.getY());total=binding.value();dragging=true;requestFocus();binding.begin();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);return true;
  case MotionEvent.ACTION_MOVE:{int i=e.findPointerIndex(pointer);if(dragging&&i>=0)move(e.getX(i),e.getY(i));return dragging;}
  case MotionEvent.ACTION_POINTER_UP:if(e.getPointerId(e.getActionIndex())==pointer){finish(false);return true;}return dragging;
  case MotionEvent.ACTION_UP:if(dragging){int i=e.findPointerIndex(pointer);if(i>=0)move(e.getX(i),e.getY(i));finish(true);performClick();return true;}break;
  case MotionEvent.ACTION_CANCEL:if(dragging){finish(false);return true;}break;
 }return super.onTouchEvent(e);}
 private void finish(boolean commit){if(!dragging)return;dragging=false;pointer=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(commit)binding.commit();else binding.cancel();invalidate();}
 @Override public void setEnabled(boolean enabled){if(!enabled)finish(false);super.setEnabled(enabled);}
 @Override protected void onDetachedFromWindow(){finish(false);super.onDetachedFromWindow();}
 @Override public boolean onKeyDown(int code,KeyEvent e){if(isEnabled()&&(code==KeyEvent.KEYCODE_DPAD_LEFT||code==KeyEvent.KEYCODE_DPAD_RIGHT)){binding.begin();binding.preview(binding.value()+(code==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)*(e.isShiftPressed()?15:1));binding.commit();invalidate();return true;}return super.onKeyDown(code,e);}
 @Override public boolean performClick(){super.performClick();return true;}
}

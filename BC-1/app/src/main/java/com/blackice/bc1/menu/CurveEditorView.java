package com.blackice.bc1.menu;
import android.content.Context;
import android.graphics.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
/** Native two-handle Bezier editor. Document state belongs to Model, not View. */
public final class CurveEditorView extends View {
 public interface Model {CubicBezier curve();boolean editable();String intervalLabel();default double progressAt(double u){CubicBezier c=curve();return (c==null?CubicBezier.LINEAR:c).valueAt(u);}}
 public interface Edits {void begin();void preview(CubicBezier curve);void commit();void cancel();}
 private final Model model;private final Edits edits;private final Paint p=new Paint(3);private final RectF plot=new RectF();
 private static final int ACTION_HANDLE_BASE=0x7f0b7e00;
 private int pointer=-1,handle=1;private boolean dragging;
 public CurveEditorView(Context c,Model m,Edits e){super(c);model=m;edits=e;setFocusable(true);setClickable(true);setMinimumHeight(dp(240));}
 private int dp(float n){return MenuPalette.dp(getContext(),n);}private float px(double x){return plot.left+(float)x*plot.width();}private float py(double y){return plot.bottom-(float)y*plot.height();}
 @Override protected void onMeasure(int w,int h){int width=resolveSize(dp(340),w);setMeasuredDimension(width,resolveSize(Math.max(dp(230),Math.round(width*.65f)),h));}
 @Override protected void onDraw(Canvas c){super.onDraw(c);plot.set(dp(47),dp(18),getWidth()-dp(18),getHeight()-dp(46));if(plot.width()<=0||plot.height()<=0)return;
  p.setStyle(Paint.Style.FILL);p.setColor(MenuPalette.BACKGROUND);c.drawRoundRect(plot,dp(5),dp(5),p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));
  for(int i=0;i<=10;i++){p.setColor(i%5==0?0xff315568:0xff153343);c.drawLine(px(i/10d),plot.top,px(i/10d),plot.bottom,p);c.drawLine(plot.left,py(i/10d),plot.right,py(i/10d),p);}
  p.setPathEffect(null);p.setColor(MenuPalette.MUTED);c.drawRect(plot,p);p.setStyle(Paint.Style.FILL);p.setTextSize(dp(11));p.setTextAlign(Paint.Align.RIGHT);
  for(int i=0;i<=2;i++)c.drawText((i*50)+"%",plot.left-dp(8),py(i/2d)+dp(4),p);p.setTextAlign(Paint.Align.CENTER);
  for(int i=0;i<=2;i++)c.drawText((i*50)+"%",px(i/2d),plot.bottom+dp(19),p);c.drawText("Time",plot.centerX(),getHeight()-dp(5),p);
  c.save();c.rotate(-90,dp(12),plot.centerY());c.drawText("Progress",dp(12),plot.centerY(),p);c.restore();
  CubicBezier b=model.curve();if(b==null)b=CubicBezier.LINEAR;int save=c.save();c.clipRect(plot);p.setStyle(Paint.Style.STROKE);p.setColor(MenuPalette.CYAN);p.setStrokeWidth(dp(1));p.setPathEffect(new DashPathEffect(new float[]{dp(4),dp(3)},0));
  c.drawLine(px(0),py(0),px(b.x1),py(b.y1),p);c.drawLine(px(1),py(1),px(b.x2),py(b.y2),p);p.setPathEffect(null);
  Path path=new Path();path.moveTo(px(0),py(0));for(int i=1;i<=160;i++){double t=i/160d;path.lineTo(px(t),py(model.progressAt(t)));}
  p.setColor(model.editable()?MenuPalette.GREEN:MenuPalette.MUTED);p.setStrokeWidth(dp(2.5f));c.drawPath(path,p);c.restoreToCount(save);
  dot(c,px(0),py(0),MenuPalette.GREEN,5);dot(c,px(1),py(1),MenuPalette.GREEN,5);dot(c,px(b.x1),py(b.y1),MenuPalette.CYAN,handle==1&&isFocused()?6:5);dot(c,px(b.x2),py(b.y2),MenuPalette.CYAN,handle==2&&isFocused()?6:5);
  if(!model.editable()){p.setStyle(Paint.Style.FILL);p.setColor(0xc003131e);c.drawRect(plot,p);p.setColor(MenuPalette.MUTED);p.setTextSize(dp(12));p.setTextAlign(Paint.Align.CENTER);c.drawText(model.intervalLabel(),plot.centerX(),plot.centerY(),p);}
 }
 private void dot(Canvas c,float x,float y,int color,float r){p.setStyle(Paint.Style.FILL);p.setColor(color);c.drawCircle(x,y,dp(r),p);p.setColor(MenuPalette.TEXT);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));c.drawCircle(x,y,dp(r),p);}
 private static double clamp(double n){return Math.max(0,Math.min(1,n));}
 private void update(float x,float y){if(plot.width()<=0||plot.height()<=0)return;CubicBezier b=model.curve();if(b==null)b=CubicBezier.LINEAR;double nx=clamp((x-plot.left)/plot.width()),ny=clamp((plot.bottom-y)/plot.height());edits.preview(handle==1?new CubicBezier(nx,ny,b.x2,b.y2):new CubicBezier(b.x1,b.y1,nx,ny));invalidate();}
 @Override public boolean onTouchEvent(MotionEvent e){if(!isEnabled()||!model.editable()){finish(false);return false;}switch(e.getActionMasked()){
  case MotionEvent.ACTION_DOWN:{CubicBezier b=model.curve();if(b==null)b=CubicBezier.LINEAR;double d1=Math.hypot(e.getX()-px(b.x1),e.getY()-py(b.y1)),d2=Math.hypot(e.getX()-px(b.x2),e.getY()-py(b.y2));if(Math.min(d1,d2)>dp(24))return false;handle=d1<=d2?1:2;pointer=e.getPointerId(0);dragging=true;requestFocus();edits.begin();if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;}
  case MotionEvent.ACTION_MOVE:{int i=e.findPointerIndex(pointer);if(dragging&&i>=0)update(e.getX(i),e.getY(i));return dragging;}
  case MotionEvent.ACTION_POINTER_UP:if(e.getPointerId(e.getActionIndex())==pointer){finish(false);return true;}return dragging;
  case MotionEvent.ACTION_UP:if(dragging){int i=e.findPointerIndex(pointer);if(i>=0)update(e.getX(i),e.getY(i));finish(true);performClick();return true;}break;
  case MotionEvent.ACTION_CANCEL:if(dragging){finish(false);return true;}break;
 }return super.onTouchEvent(e);}
 private void finish(boolean commit){if(!dragging)return;dragging=false;pointer=-1;if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);if(commit)edits.commit();else edits.cancel();invalidate();}
 @Override public void setEnabled(boolean enabled){if(!enabled)finish(false);super.setEnabled(enabled);}
 @Override protected void onDetachedFromWindow(){finish(false);super.onDetachedFromWindow();}
 @Override public boolean onKeyDown(int code,KeyEvent e){if(!model.editable())return super.onKeyDown(code,e);if(code==KeyEvent.KEYCODE_1||code==KeyEvent.KEYCODE_2){handle=code==KeyEvent.KEYCODE_1?1:2;invalidate();return true;}
  int dx=code==KeyEvent.KEYCODE_DPAD_RIGHT?1:code==KeyEvent.KEYCODE_DPAD_LEFT?-1:0,dy=code==KeyEvent.KEYCODE_DPAD_UP?1:code==KeyEvent.KEYCODE_DPAD_DOWN?-1:0;
  if(dx!=0||dy!=0){CubicBezier b=model.curve();if(b==null)b=CubicBezier.LINEAR;double step=e.isShiftPressed()?.05:.01,x=(handle==1?b.x1:b.x2)+dx*step,y=(handle==1?b.y1:b.y2)+dy*step;edits.begin();edits.preview(handle==1?new CubicBezier(clamp(x),clamp(y),b.x2,b.y2):new CubicBezier(b.x1,b.y1,clamp(x),clamp(y)));edits.commit();invalidate();return true;}return super.onKeyDown(code,e);
 }
 @Override public boolean performClick(){super.performClick();return true;}
 @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo n){super.onInitializeAccessibilityNodeInfo(n);n.setContentDescription("Easing curve "+model.intervalLabel()+". Handle "+handle+" selected. Keys 1 or 2 select a handle; arrows adjust it.");if(model.editable()){String[] directions={"left","right","down","up"};for(int h=1;h<=2;h++)for(int d=0;d<4;d++)n.addAction(new AccessibilityNodeInfo.AccessibilityAction(ACTION_HANDLE_BASE+(h-1)*4+d,"Move handle "+h+" "+directions[d]));}}
 @Override public boolean performAccessibilityAction(int action,Bundle args){int index=action-ACTION_HANDLE_BASE;if(index>=0&&index<8&&model.editable()){handle=index/4+1;int direction=index%4;CubicBezier b=model.curve();if(b==null)b=CubicBezier.LINEAR;double x=handle==1?b.x1:b.x2,y=handle==1?b.y1:b.y2;x+=direction==0?-.01:direction==1?.01:0;y+=direction==2?-.01:direction==3?.01:0;edits.begin();edits.preview(handle==1?new CubicBezier(clamp(x),clamp(y),b.x2,b.y2):new CubicBezier(b.x1,b.y1,clamp(x),clamp(y)));edits.commit();invalidate();return true;}return super.performAccessibilityAction(action,args);}
}

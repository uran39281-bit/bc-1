package com.blackice.bc1.menu;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Small scalable path icons. Replace with equivalent existing V1Icon assets on integration. */
public final class MenuIconDrawable extends Drawable {
 public enum Kind {BACK,NEXT,SETTINGS,ADD,DIAMOND,LINK,EXPAND,RESET,TRASH,CURVE}
 private final Kind kind;private final Paint paint=new Paint(3);private final int size;private int color,alpha=255;private boolean filled,dot;
 public MenuIconDrawable(Context c,Kind kind,int color){this.kind=kind;this.color=color;size=MenuPalette.dp(c,24);}
 public void mark(int color,boolean filled,boolean dot){this.color=color;this.filled=filled;this.dot=dot;invalidateSelf();}
 @Override public int getIntrinsicWidth(){return size;}@Override public int getIntrinsicHeight(){return size;}
 @Override public void draw(Canvas c){int save=c.save();Rect b=getBounds();c.translate(b.left,b.top);c.scale(b.width()/24f,b.height()/24f);paint.setColor(color);paint.setAlpha(alpha);paint.setStrokeWidth(1.7f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setStyle(Paint.Style.STROKE);Path p=new Path();
  switch(kind){
   case BACK:p.moveTo(14,5);p.lineTo(7,12);p.lineTo(14,19);c.drawPath(p,paint);break;
   case NEXT:p.moveTo(10,5);p.lineTo(17,12);p.lineTo(10,19);c.drawPath(p,paint);break;
   case ADD:c.drawLine(12,4,12,20,paint);c.drawLine(4,12,20,12,paint);break;
   case DIAMOND:p.moveTo(10.5f,3);p.lineTo(18,12);p.lineTo(10.5f,21);p.lineTo(3,12);p.close();if(filled)paint.setStyle(Paint.Style.FILL);c.drawPath(p,paint);if(dot){paint.setStyle(Paint.Style.FILL);c.drawCircle(22,12,1.5f,paint);}break;
   case SETTINGS:c.drawCircle(12,12,6.5f,paint);c.drawCircle(12,12,2.5f,paint);for(int i=0;i<8;i++){c.save();c.rotate(i*45,12,12);c.drawLine(12,1.5f,12,4,paint);c.restore();}break;
   case LINK:c.drawRoundRect(new RectF(2,7,15,17),5,5,paint);c.drawRoundRect(new RectF(9,7,22,17),5,5,paint);break;
   case EXPAND:c.drawLine(4,10,4,4,paint);c.drawLine(4,4,10,4,paint);c.drawLine(14,4,20,4,paint);c.drawLine(20,4,20,10,paint);c.drawLine(4,14,4,20,paint);c.drawLine(4,20,10,20,paint);c.drawLine(14,20,20,20,paint);c.drawLine(20,20,20,14,paint);break;
   case RESET:c.drawArc(new RectF(4,5,21,22),-105,295,false,paint);p.moveTo(6,2);p.lineTo(5,8);p.lineTo(11,7);c.drawPath(p,paint);break;
   case TRASH:c.drawLine(3,6,21,6,paint);c.drawRoundRect(new RectF(7,2,17,6),2,2,paint);p.moveTo(5,7);p.lineTo(6,21);p.lineTo(18,21);p.lineTo(19,7);c.drawPath(p,paint);c.drawLine(10,10,10,18,paint);c.drawLine(14,10,14,18,paint);break;
   case CURVE:p.moveTo(3,20);p.cubicTo(13,20,9,4,21,4);c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);c.drawCircle(3,20,2,paint);c.drawCircle(21,4,2,paint);break;
  }c.restoreToCount(save);
 }
 @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
 @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}

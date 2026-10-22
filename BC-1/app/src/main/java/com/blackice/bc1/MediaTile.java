package com.blackice.bc1;

import android.graphics.*;
import android.view.Gravity;
import android.widget.TextView;

/** One shared square tile geometry on phone and DeX, including the unavailable Effects tile. */
final class MediaTile extends TextView {
    private final EditorActivity a;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    MediaTile(EditorActivity a,String label,String glyph){
        super(a);this.a=a;setText(label);setTextColor(0xffd3eeff);setTextSize(12);setGravity(Gravity.CENTER);setPadding(a.dp(10),a.dp(12),a.dp(10),a.dp(12));setMinHeight(a.dp(120));setMaxLines(3);setClickable(true);setFocusable(true);setBackground(a.buttonBackground(false));
        V1Icon icon=new V1Icon(glyph,0xff64d7ff);icon.setBounds(0,0,a.dp(34),a.dp(34));setCompoundDrawables(null,icon,null,null);setCompoundDrawablePadding(a.dp(12));
    }
    @Override protected void onMeasure(int w,int h){super.onMeasure(w,h);int side=Math.max(a.dp(120),getMeasuredWidth());setMeasuredDimension(getMeasuredWidth(),side);}
    @Override protected void onDraw(Canvas c){
        float w=getWidth(),h=getHeight();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(a.dp(1));paint.setColor(0x2842baff);
        for(int i=0;i<3;i++){Path wave=new Path();float y=h*(.48f+i*.1f);wave.moveTo(0,y);wave.cubicTo(w*.35f,y-h*.28f,w*.45f,y+h*.45f,w,y);c.drawPath(wave,paint);}
        super.onDraw(c);
    }
}

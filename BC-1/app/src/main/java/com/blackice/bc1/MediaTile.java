package com.blackice.bc1;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.widget.TextView;

/** Shared equal-sized category tile. Static waves avoid blur or animation cost during scrubbing. */
final class MediaTile extends TextView {
    private final EditorActivity a;
    MediaTile(EditorActivity a,String label,String icon,boolean enabled,Runnable action){super(a);this.a=a;
        setText(label);setTextSize(13);setTextColor(BlackIceTheme.TEXT);setGravity(Gravity.CENTER);setMaxLines(3);setPadding(a.dp(6),a.dp(10),a.dp(6),a.dp(10));
        Drawable glyph=new V1Icon(icon,BlackIceTheme.ICE);glyph.setBounds(0,0,a.dp(34),a.dp(34));setCompoundDrawables(null,glyph,null,null);setCompoundDrawablePadding(a.dp(12));setMinHeight(a.dp(120));
        setBackground(new Drawable(){final Paint p=new Paint(3);public void draw(Canvas canvas){Rect b=getBounds();float r=a.dp(12);p.setStyle(Paint.Style.FILL);p.setColor(isPressed()?0xFF12364E:0xFF091D2E);canvas.drawRoundRect(b.left+1,b.top+1,b.right-1,b.bottom-1,r,r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1));p.setColor(0xFF315D7B);canvas.drawRoundRect(b.left+1,b.top+1,b.right-1,b.bottom-1,r,r,p);canvas.save();canvas.clipRect(b.left+a.dp(4),b.top+a.dp(4),b.right-a.dp(4),b.bottom-a.dp(4));p.setColor(0x183FAEE0);for(int i=0;i<3;i++){Path wave=new Path();wave.moveTo(b.left,b.height()*.65f+i*a.dp(7));wave.cubicTo(b.width()*.35f,b.height()*.30f+i*a.dp(7),b.width()*.6f,b.height()*1.05f,b.right,b.height()*.7f+i*a.dp(7));canvas.drawPath(wave,p);}canvas.restore();}public void setAlpha(int alpha){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}});
        setContentDescription("Media Settings: "+label.replace('\n',' '));setTooltipText(getContentDescription());setOnClickListener(v->action.run());setFocusable(true);EditorActivity.dim(this,enabled);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec);int height=Math.max(a.dp(120),Math.min(a.dp(190),width));if(a.getResources().getConfiguration().fontScale>1.25f)height=Math.max(height,a.dp(160));super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(height,MeasureSpec.EXACTLY));}
}

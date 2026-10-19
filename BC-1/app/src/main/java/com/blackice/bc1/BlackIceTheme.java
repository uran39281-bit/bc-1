package com.blackice.bc1;

import android.graphics.*;
import android.graphics.drawable.*;

/** Shared presentation tokens. No rendering or project state belongs in this class. */
final class BlackIceTheme {
    static final int BACKGROUND=0xFF050D19,PRIMARY=0xFF081524,SECONDARY=0xFF0B1C2D,
        ELEVATED=0xFF10263A,ACCENT=0xFF00BFFF,ICE=0xFF75D9FF,
        TEXT=0xFFF4F8FF,MUTED=0xFF9BAFC5,BORDER=0xFF163954;
    static GradientDrawable surface(float density,boolean active){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{active?0xFF12456A:0xED0B1C2D,active?0xFF0B2943:0xEE081524});
        d.setCornerRadius(7*density);d.setStroke(Math.max(1,(int)density),active?ACCENT:BORDER);return d;
    }
    /** The single primary action (Export): bright ice-blue fill. */
    static GradientDrawable primary(float density){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xFF1C9BFF,0xFF0A6FD8});
        d.setCornerRadius(7*density);d.setStroke(Math.max(1,(int)density),0xFF63C3FF);return d;
    }
    static Drawable tab(float density,boolean active){
        return new Drawable(){final Paint p=new Paint(3);
            public void draw(Canvas c){Rect b=getBounds();p.setColor(active?0x2200BFFF:0x00000000);c.drawRect(b,p);if(active){p.setColor(ACCENT);c.drawRect(b.left+8*density,b.bottom-2*density,b.right-8*density,b.bottom,p);}}
            public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
    /** Static decoration only on workspace chrome, never on top of preview or clips. */
    static Drawable backdrop(){return new Drawable(){final Paint p=new Paint(3);
        public void draw(Canvas c){Rect b=getBounds();p.setShader(new LinearGradient(0,0,b.width(),b.height(),new int[]{BACKGROUND,PRIMARY,BACKGROUND},null,Shader.TileMode.CLAMP));c.drawRect(b,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x142F90C6);float x=b.width()*.72f,y=b.height()*.12f;for(int i=0;i<3;i++)c.drawCircle(x,y,b.width()*(.12f+i*.045f),p);p.setStyle(Paint.Style.FILL);p.setColor(0x2875D9FF);for(int i=0;i<24;i++)c.drawCircle((i*173%997)*b.width()/997f,(i*97%401)*b.height()/401f,1.1f,p);}
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
    };}
}

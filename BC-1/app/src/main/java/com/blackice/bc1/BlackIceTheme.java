package com.blackice.bc1;

import android.graphics.*;
import android.graphics.drawable.*;

/** Shared presentation tokens. No rendering or project state belongs in this class. */
final class BlackIceTheme {
    static final int BACKGROUND=0xFF07111B,PRIMARY=0xFF0C1825,SECONDARY=0xFF0C1825,
        ELEVATED=0xFF101E2C,ACCENT=0xFF48E59F,ICE=0xFF7CD5F3,
        TEXT=0xFFF2F6FA,MUTED=0xFF97A8BA,BORDER=0xFF213548;
    static GradientDrawable surface(float density,boolean active){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{active?0xFF12456A:0xED0B1C2D,active?0xFF0B2943:0xEE081524});
        d.setCornerRadius(7*density);d.setStroke(Math.max(1,(int)density),active?ACCENT:BORDER);return d;
    }
    /** The single primary action (Export): restrained mint fill. */
    static GradientDrawable primary(float density){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xFF48E59F,0xFF36D991});
        d.setCornerRadius(7*density);d.setStroke(Math.max(1,(int)density),ACCENT);return d;
    }
    static Drawable tab(float density,boolean active){
        return new Drawable(){final Paint p=new Paint(3);
            public void draw(Canvas c){Rect b=getBounds();p.setColor(active?0x1248E59F:0x00000000);c.drawRect(b,p);if(active){p.setColor(ACCENT);c.drawRect(b.left+8*density,b.bottom-2*density,b.right-8*density,b.bottom,p);}}
            public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        };
    }
    /** Static decoration only on workspace chrome, never on top of preview or clips. */
    static Drawable backdrop(){return new Drawable(){final Paint p=new Paint(3);
        public void draw(Canvas c){Rect b=getBounds();p.setShader(new LinearGradient(0,0,b.width(),b.height(),new int[]{BACKGROUND,0xFF122338,BACKGROUND},null,Shader.TileMode.CLAMP));c.drawRect(b,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x107CD5F3);float x=b.width()*.72f,y=b.height()*.12f;for(int i=0;i<3;i++)c.drawCircle(x,y,b.width()*(.12f+i*.045f),p);p.setStyle(Paint.Style.FILL);}
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.OPAQUE;}
    };}
}

package com.blackice.bc1.menu;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.TextView;
import android.view.Gravity;

/** One square target and an optically centered, square path; never a font glyph. */
public final class KeyframeButton extends TextView {
    private final MenuIconDrawable icon;
    private boolean keyed, animated;
    public KeyframeButton(Context context, String label, Runnable action) {
        super(context);
        icon = new MenuIconDrawable(context, MenuIconDrawable.Kind.DIAMOND, MenuPalette.MUTED);
        setGravity(Gravity.CENTER); setFocusable(true); setClickable(true); setText("");
        setPadding(0,0,0,0); setMinWidth(0); setMinHeight(0);
        setBackground(MenuPalette.surface(context,false));setForeground(MenuPalette.interaction(context)); setContentDescription(label);
        setOnClickListener(v -> action.run());
    }
    public void state(boolean animated, boolean keyed) {
        this.animated=animated; this.keyed=keyed; setSelected(keyed);
        icon.mark(animated?MenuPalette.GREEN:MenuPalette.MUTED,keyed,animated&&!keyed); invalidate();
    }
    @Override protected void onMeasure(int w,int h) {
        int side=resolveSize(MenuPalette.dp(getContext(),48),w);
        if(android.view.View.MeasureSpec.getMode(h)!=android.view.View.MeasureSpec.UNSPECIFIED)
            side=Math.min(side,android.view.View.MeasureSpec.getSize(h));
        setMeasuredDimension(side,side);
    }
    @Override protected void onDraw(Canvas canvas) {
        int side=MenuPalette.dp(getContext(),20), x=(getWidth()-side)/2,y=(getHeight()-side)/2;
        icon.setBounds(x,y,x+side,y+side); icon.setAlpha(isEnabled()?255:100); icon.draw(canvas);
    }
}

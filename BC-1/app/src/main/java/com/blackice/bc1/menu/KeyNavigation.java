package com.blackice.bc1.menu;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Effects' joined double-chevron navigation, shared by every property panel. */
public final class KeyNavigation extends LinearLayout {
    public final Button previous, next;
    public KeyNavigation(Context c, int heightDp, Runnable backward, Runnable forward) {
        super(c);setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setBackground(MenuPalette.surface(c,false));
        previous=button(c,"Previous Key",true,heightDp,backward);next=button(c,"Next Key",false,heightDp,forward);
        addView(previous,new LayoutParams(0,MenuPalette.dp(c,heightDp),1));
        View divider=new View(c);divider.setBackgroundColor(MenuPalette.BORDER);
        addView(divider,new LayoutParams(MenuPalette.dp(c,1),MenuPalette.dp(c,28)));
        addView(next,new LayoutParams(0,MenuPalette.dp(c,heightDp),1));
    }
    private Button button(Context c,String label,boolean back,int height,Runnable action){
        Button b=new Button(c);b.setAllCaps(false);b.setText(label);b.setTextSize(12);b.setTypeface(null,Typeface.BOLD);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(MenuPalette.dp(c,6),0,MenuPalette.dp(c,6),0);
        b.setTextColor(MenuPalette.TEXT);b.setBackground(null);b.setContentDescription(label);
        MenuIconDrawable icon=new MenuIconDrawable(c,back?MenuIconDrawable.Kind.DOUBLE_BACK:MenuIconDrawable.Kind.DOUBLE_NEXT,MenuPalette.TEXT);
        b.setCompoundDrawablesRelativeWithIntrinsicBounds(back?icon:null,null,back?null:icon,null);
        b.setOnClickListener(v->action.run());return b;
    }
    public void available(boolean backward,boolean forward){previous.setEnabled(backward);next.setEnabled(forward);previous.setAlpha(backward?1:.4f);next.setAlpha(forward?1:.4f);}
}

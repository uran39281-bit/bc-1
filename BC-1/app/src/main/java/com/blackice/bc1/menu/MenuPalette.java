package com.blackice.bc1.menu;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
/** Presentation-only tokens sampled from the supplied menu references. */
public final class MenuPalette {
 private MenuPalette(){}
 public static android.graphics.drawable.Drawable interaction(Context c){android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();GradientDrawable focus=new GradientDrawable();focus.setColor(0);focus.setCornerRadius(dp(c,7));focus.setStroke(dp(c,1),CYAN);states.addState(new int[]{android.R.attr.state_focused},new android.graphics.drawable.InsetDrawable(focus,dp(c,3)));states.addState(new int[]{},new android.graphics.drawable.ColorDrawable(0));return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x3040bfff),states,null);}

 public static final int GAP_COMPACT=8,GAP_ROW=12,GAP_GROUP=16,PANEL_PADDING=16;
 public static final int BACKGROUND=0xff03131e,SURFACE=0xff061c2a,BORDER=0xff24475b,TEXT=0xfff4f7fa,MUTED=0xff9eb5c9,GREEN=0xff08e7ac,ACCENT=0xff40bfff,CYAN=0xff40bfff,DANGER=0xffff6575;
 public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 public static GradientDrawable surface(Context c,boolean active){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{active?0xff0a3550:SURFACE,active?0xff062a40:BACKGROUND});d.setCornerRadius(dp(c,10));d.setStroke(dp(c,active?1.5f:1),active?GREEN:BORDER);return d;}
 public static GradientDrawable surfaceGreen(Context c){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff073e46,0xff052a37});d.setCornerRadius(dp(c,10));d.setStroke(dp(c,1.5f),GREEN);return d;}
 public static GradientDrawable export(Context c){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xff03e6bb,0xff00d897});d.setCornerRadius(dp(c,10));return d;}
}

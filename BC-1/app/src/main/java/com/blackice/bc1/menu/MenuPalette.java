package com.blackice.bc1.menu;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
/** Presentation-only tokens sampled from the supplied menu references. */
public final class MenuPalette {
 private MenuPalette(){}
 public static final int BACKGROUND=0xff03131e,SURFACE=0xff061c2a,BORDER=0xff24475b,TEXT=0xfff4f7fa,MUTED=0xff9eb5c9,GREEN=0xff08e7ac,CYAN=0xff28c9ee,DANGER=0xffff6575;
 public static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 public static GradientDrawable surface(Context c,boolean active){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{active?0xff073e46:SURFACE,active?0xff052a37:BACKGROUND});d.setCornerRadius(dp(c,10));d.setStroke(dp(c,active?1.5f:1),active?GREEN:BORDER);return d;}
 public static GradientDrawable export(Context c){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xff03e6bb,0xff00d897});d.setCornerRadius(dp(c,10));return d;}
}

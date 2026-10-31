package com.blackice.bc1.menu;
import android.content.Context;
import android.view.*;
import android.widget.*;
/** Measures actual labels/font scale and reflows the numeric field before overlap. */
final class AdaptiveValueRow extends LinearLayout {
 private final Button key,reset;private final TextView label,unit;private final EditText input;private boolean wide,configured;
 AdaptiveValueRow(Context c,Button key,TextView label,EditText input,TextView unit,Button reset){super(c);this.key=key;this.label=label;this.input=input;this.unit=unit;this.reset=reset;setOrientation(VERTICAL);configure(false);}
 private int dp(float n){return MenuPalette.dp(getContext(),n);}
 private void detach(View child){if(child!=null&&child.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)child.getParent()).removeView(child);}
 private void configure(boolean useWide){if(configured&&wide==useWide)return;configured=true;wide=useWide;for(View v:new View[]{key,label,input,unit,reset})detach(v);removeAllViews();LinearLayout first=new LinearLayout(getContext());first.setOrientation(HORIZONTAL);first.setGravity(Gravity.CENTER_VERTICAL);first.addView(key,new LayoutParams(dp(48),dp(48)));LayoutParams name=new LayoutParams(0,LayoutParams.WRAP_CONTENT,1);name.leftMargin=dp(8);first.addView(label,name);
  if(wide){LayoutParams value=new LayoutParams(dp(112),dp(48));value.leftMargin=dp(8);first.addView(input,value);first.addView(unit,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));}
  if(reset!=null){LayoutParams rp=new LayoutParams(dp(68),dp(48));rp.leftMargin=dp(8);first.addView(reset,rp);}addView(first,new LayoutParams(-1,-2));
  if(!wide){LinearLayout second=new LinearLayout(getContext());second.setGravity(Gravity.CENTER_VERTICAL);LayoutParams value=new LayoutParams(0,dp(48),1);second.addView(input,value);LayoutParams suffix=new LayoutParams(LayoutParams.WRAP_CONTENT,-2);suffix.leftMargin=dp(6);second.addView(unit,suffix);LayoutParams row=new LayoutParams(-1,-2);row.topMargin=dp(8);addView(second,row);}
 }
 @Override protected void onMeasure(int widthSpec,int heightSpec){int width=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();float required=dp(48+8+8+112)+label.getPaint().measureText(label.getText().toString())+unit.getPaint().measureText(unit.getText().toString())+unit.getPaddingLeft()+unit.getPaddingRight()+(reset==null?0:dp(8+68));configure(MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&width>=required);super.onMeasure(widthSpec,heightSpec);}
}

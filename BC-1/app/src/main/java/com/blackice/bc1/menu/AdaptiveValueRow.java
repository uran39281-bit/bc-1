package com.blackice.bc1.menu;
import android.content.Context;
import android.view.*;
import android.widget.*;
/** Flexible value row; reflow only when the actual label, value and font cannot fit. */
final class AdaptiveValueRow extends LinearLayout {
 private final Button key,reset;private final TextView label,unit;private final EditText input;
 private final int height;private boolean wide,configured;
 AdaptiveValueRow(Context c,Button key,TextView label,EditText input,TextView unit,Button reset,int heightDp){super(c);this.key=key;this.label=label;this.input=input;this.unit=unit;this.reset=reset;this.height=heightDp;setOrientation(VERTICAL);configure(false);}
 private int dp(float n){return MenuPalette.dp(getContext(),n);}
 private void detach(View child){if(child!=null&&child.getParent() instanceof ViewGroup)((ViewGroup)child.getParent()).removeView(child);}
 private int resetWidth(){return reset==null?0:Math.max(dp(height),Math.round(reset.getPaint().measureText(reset.getText().toString()))+dp(14));}
 private void configure(boolean useWide){
  if(configured&&wide==useWide)return;configured=true;wide=useWide;
  for(View v:new View[]{key,label,input,unit,reset})detach(v);removeAllViews();
  LinearLayout first=new LinearLayout(getContext());first.setGravity(Gravity.CENTER_VERTICAL);
  first.addView(key,new LayoutParams(dp(height),dp(height)));
  LayoutParams name=new LayoutParams(wide?LayoutParams.WRAP_CONTENT:0,LayoutParams.WRAP_CONTENT,wide?0:1);name.leftMargin=dp(6);first.addView(label,name);
  if(wide){LayoutParams value=new LayoutParams(0,dp(height),1);value.leftMargin=dp(8);first.addView(input,value);first.addView(unit,new LayoutParams(-2,-2));}
  if(reset!=null){LayoutParams rp=new LayoutParams(resetWidth(),dp(height));rp.leftMargin=dp(6);first.addView(reset,rp);}addView(first,new LayoutParams(-1,-2));
  if(!wide){LinearLayout second=new LinearLayout(getContext());second.setGravity(Gravity.CENTER_VERTICAL);second.addView(input,new LayoutParams(0,dp(height),1));LayoutParams suffix=new LayoutParams(-2,-2);suffix.leftMargin=dp(6);second.addView(unit,suffix);LayoutParams row=new LayoutParams(-1,-2);row.topMargin=dp(6);addView(second,row);}
 }
 @Override protected void onMeasure(int widthSpec,int heightSpec){
  int width=MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
  float valueWidth=Math.max(dp(70),input.getPaint().measureText(input.getText().toString())+dp(18));
  float required=dp(height+6+8)+label.getPaint().measureText(label.getText().toString())+valueWidth+unit.getPaint().measureText(unit.getText().toString())+unit.getPaddingLeft()+unit.getPaddingRight()+(reset==null?0:dp(6)+resetWidth());
  configure(MeasureSpec.getMode(widthSpec)!=MeasureSpec.UNSPECIFIED&&width>=required);super.onMeasure(widthSpec,heightSpec);
 }
}

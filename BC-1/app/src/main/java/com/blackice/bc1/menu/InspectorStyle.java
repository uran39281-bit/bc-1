package com.blackice.bc1.menu;
import android.content.Context;
import android.view.*;
import android.widget.*;
/** Shared inspector density; never resizes preview, timeline, media browser or icon artwork. */
public final class InspectorStyle {
 private InspectorStyle(){}
 public static int controlDp(Context context,boolean wide){float font=context.getResources().getConfiguration().fontScale;return Math.max(wide?36:48,Math.round((wide?32:44)*font));}
 /** Apply the same density to legacy inspector actions, keeping icon-above-label tiles larger. */
 public static void spacing(View root){
  if(!(root instanceof ViewGroup))return;ViewGroup group=(ViewGroup)root;
  for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(group instanceof LinearLayout&&((LinearLayout)group).getOrientation()==LinearLayout.VERTICAL&&i>0&&child.getLayoutParams() instanceof LinearLayout.LayoutParams){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)child.getLayoutParams();if(child instanceof LinearLayout||child instanceof CollapsibleSection||child instanceof android.widget.EditText||child.isClickable())lp.topMargin=MenuPalette.dp(root.getContext(),MenuPalette.GAP_ROW);}
   if(!(child instanceof KeyframeButton))spacing(child);
  }
 }
 public static void apply(View root,boolean wide){
  if(!wide)return;int h=MenuPalette.dp(root.getContext(),controlDp(root.getContext(),true));
  if(!(root instanceof KeyframeButton)&&root instanceof TextView&&root.isClickable()&&!(root instanceof EditText)){
   TextView t=(TextView)root;
   if(t.getCompoundDrawables()[1]==null&&t.getCompoundDrawables()[3]==null){
    t.setMinHeight(h);t.setMinimumHeight(h);t.setPadding(t.getPaddingLeft(),MenuPalette.dp(root.getContext(),3),t.getPaddingRight(),MenuPalette.dp(root.getContext(),3));
    ViewGroup.LayoutParams lp=root.getLayoutParams();if(lp!=null&&lp.height>=h&&lp.height<=MenuPalette.dp(root.getContext(),56))lp.height=h;
   }
  }
  if(root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i),wide);}
 }
}

package com.blackice.bc1;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import com.blackice.bc1.menu.MenuPalette;
import java.util.function.IntConsumer;

/** One native, inset-constrained modal shell for setup, settings and export. */
final class BlackIceDialog {
    final Activity a;final AlertDialog dialog;final LinearLayout root,content,footer;
    final boolean wide;
    BlackIceDialog(Activity a,String title,String icon,String subtitle){
        this.a=a;wide=a.getResources().getConfiguration().screenWidthDp>=760;
        root=column(a);root.setPadding(dp(20),dp(16),dp(20),dp(16));root.setBackground(MenuPalette.surface(a,false));
        dialog=new com.blackice.bc1.TraceDialogBuilder(a).setView(root).create();
        LinearLayout header=row(a);ImageView symbol=new ImageView(a);symbol.setImageDrawable(new V1Icon(icon,MenuPalette.CYAN));header.addView(symbol,new LinearLayout.LayoutParams(dp(28),dp(28)));
        LinearLayout labels=column(a);TextView heading=text(a,title,21,true);labels.addView(heading);if(subtitle!=null&&!subtitle.isEmpty()){TextView sub=text(a,subtitle,12,false);sub.setTextColor(MenuPalette.MUTED);labels.addView(sub);}
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(0,-2,1);titleLp.leftMargin=dp(12);header.addView(labels,titleLp);
        TextView close=action(a,"×",false,dialog::dismiss);close.setTextSize(24);close.setContentDescription("Close "+title);header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);rule(root);
        content=column(a);content.setPadding(0,dp(16),0,dp(16));
        ScrollView scroll=new ScrollView(a){@Override protected void onMeasure(int w,int h){int available=MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED?Integer.MAX_VALUE:Math.max(dp(64),MeasureSpec.getSize(h)-dp(a.getResources().getConfiguration().screenWidthDp<400&&a.getResources().getConfiguration().fontScale>1.2f?144:88));int limit=dp(Math.max(100,a.getResources().getConfiguration().screenHeightDp-200));super.onMeasure(w,MeasureSpec.makeMeasureSpec(Math.min(available,limit),MeasureSpec.AT_MOST));}};
        scroll.setFillViewport(false);scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,-2));rule(root);
        footer=row(a);if(a.getResources().getConfiguration().screenWidthDp<400&&a.getResources().getConfiguration().fontScale>1.2f)footer.setOrientation(LinearLayout.VERTICAL);footer.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);footer.setPadding(0,dp(12),0,0);root.addView(footer);
        dialog.setOnShowListener(d->{Window window=dialog.getWindow();if(window!=null){ActionTrace.install(window);window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.setLayout(Math.min(dp(wide?980:600),a.getResources().getDisplayMetrics().widthPixels-dp(24)),-2);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=.65f;window.setAttributes(attrs);}});
    }
    int dp(float n){return MenuPalette.dp(a,n);}
    void show(){View focus=a.getCurrentFocus();dialog.setOnDismissListener(d->{if(focus!=null&&focus.isAttachedToWindow())focus.requestFocus();});dialog.show();}
    TextView footer(String title,boolean primary,Runnable action){TextView b=action(a,title,primary,action);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(footer.getOrientation()==LinearLayout.VERTICAL?-1:-2,-2);if(footer.getChildCount()>0){if(footer.getOrientation()==LinearLayout.VERTICAL)lp.topMargin=dp(12);else lp.leftMargin=dp(12);}footer.addView(b,lp);return b;}
    void rule(LinearLayout into){View v=new View(a);v.setBackgroundColor(MenuPalette.BORDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.topMargin=dp(12);into.addView(v,lp);}
    static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    static TextView text(Activity a,String label,int sp,boolean bold){TextView t=new TextView(a);t.setText(label);t.setTextSize(sp);t.setTextColor(MenuPalette.TEXT);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    static TextView action(Activity a,String label,boolean primary,Runnable action){TextView b=text(a,label,14,true);b.setGravity(Gravity.CENTER);b.setMinHeight(MenuPalette.dp(a,48));b.setPadding(MenuPalette.dp(a,12),MenuPalette.dp(a,6),MenuPalette.dp(a,12),MenuPalette.dp(a,6));b.setBackground(primary?MenuPalette.export(a):MenuPalette.surface(a,false));b.setTextColor(primary?MenuPalette.BACKGROUND:MenuPalette.TEXT);b.setFocusable(true);b.setClickable(true);b.setForeground(MenuPalette.interaction(a));b.setContentDescription(label);b.setOnClickListener(v->action.run());return b;}
    static EditText field(Activity a,String label,String value){EditText e=new EditText(a);e.setText(value);e.setTextSize(15);e.setSingleLine(true);e.setTextColor(MenuPalette.TEXT);e.setPadding(MenuPalette.dp(a,12),0,MenuPalette.dp(a,12),0);e.setMinHeight(MenuPalette.dp(a,48));e.setBackground(MenuPalette.surface(a,false));e.setContentDescription(label);return e;}
    static android.widget.ArrayAdapter<String> choiceAdapter(Activity a,String title,String[] names){return new android.widget.ArrayAdapter<String>(a,android.R.layout.simple_list_item_single_choice,names){@Override public View getView(int position,View reuse,android.view.ViewGroup parent){TextView row=(TextView)super.getView(position,reuse,parent);row.setTextColor(MenuPalette.TEXT);row.setMinHeight(MenuPalette.dp(a,48));android.graphics.drawable.Drawable glyph=new V1Icon(iconFor(title),MenuPalette.CYAN);glyph.setBounds(0,0,MenuPalette.dp(a,22),MenuPalette.dp(a,22));row.setCompoundDrawablesRelative(glyph,null,null,null);row.setCompoundDrawablePadding(MenuPalette.dp(a,12));return row;}};}
    static AlertDialog choices(Activity a,String title,String[] names,int selected,IntConsumer choose){
        AlertDialog d=new com.blackice.bc1.TraceDialogBuilder(a).setTitle(title).setSingleChoiceItems(choiceAdapter(a,title,names),selected,(dialog,index)->{dialog.dismiss();choose.accept(index);}).setNegativeButton("Back",null).create();d.show();d.getListView().setBackgroundColor(MenuPalette.BACKGROUND);return d;
    }
    static String iconFor(String label){String l=label.toLowerCase(java.util.Locale.ROOT);if(l.contains("keyboard"))return "Keyboard";if(l.contains("save"))return "Save";if(l.contains("export"))return "Export";if(l.contains("lock"))return "Lock";if(l.contains("resolution")||l.contains("preview")||l.contains("layout"))return "Monitor";if(l.contains("clock"))return "Clock";if(l.contains("project")||l.contains("storage")||l.contains("bundle"))return "Folder";if(l.contains("speech")||l.contains("caption"))return "Audio";return "Settings";}
}

package com.blackice.bc1.menu;

import android.content.Context;
import android.view.View;
import android.view.Gravity;
import android.widget.*;
import java.util.Map;

/** Session-only presentation state. GONE content neither draws nor receives input/focus. */
public final class CollapsibleSection extends LinearLayout {
    private final LinearLayout header;private final TextView heading,detailView,chevron;
    private final View content;
    private final Map<String,Boolean> session;
    private final String key,title;
    private String detail="";
    public CollapsibleSection(Context c,String key,Map<String,Boolean> session,String title,View content,Runnable cancelGesture){
        super(c);this.key=key;this.session=session;this.title=title;this.content=content;setOrientation(VERTICAL);
        setBackground(MenuPalette.surface(c,false));int pad=MenuPalette.dp(c,12);setPadding(pad,pad,pad,pad);
        header=new LinearLayout(c);header.setGravity(Gravity.CENTER_VERTICAL);header.setOrientation(HORIZONTAL);
        LinearLayout labels=new LinearLayout(c);labels.setOrientation(VERTICAL);heading=new TextView(c);heading.setText(title);heading.setTextSize(14);heading.setTextColor(MenuPalette.TEXT);labels.addView(heading);
        detailView=new TextView(c);detailView.setTextSize(12);detailView.setTextColor(MenuPalette.MUTED);labels.addView(detailView);header.addView(labels,new LayoutParams(0,-2,1));
        chevron=new TextView(c);chevron.setTextSize(20);chevron.setTextColor(MenuPalette.CYAN);header.addView(chevron,new LayoutParams(MenuPalette.dp(c,28),-2));
        header.setMinimumHeight(MenuPalette.dp(c,48));header.setFocusable(true);header.setClickable(true);
        header.setOnClickListener(v->{if(Boolean.TRUE.equals(session.get(key)))cancelGesture.run();session.put(key,!Boolean.TRUE.equals(session.get(key)));render();});
        addView(header,new LayoutParams(-1,-2));addView(content,new LayoutParams(-1,-2));render();
    }
    public void context(String text){detail=text==null?"":text;render();}
    private void render(){boolean open=Boolean.TRUE.equals(session.get(key));chevron.setText(open?"▴":"▾");detailView.setText(detail);detailView.setVisibility(detail.isEmpty()?GONE:VISIBLE);header.setContentDescription(title+", "+(open?"expanded":"collapsed")+". "+detail);if(android.os.Build.VERSION.SDK_INT>=30)header.setStateDescription(open?"Expanded":"Collapsed");content.setVisibility(open?VISIBLE:GONE);}
    public boolean expanded(){return content.getVisibility()==VISIBLE;}
}

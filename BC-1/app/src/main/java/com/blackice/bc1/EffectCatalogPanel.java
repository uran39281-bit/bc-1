package com.blackice.bc1;

import android.graphics.*;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import com.blackice.bc1.core.*;
import com.blackice.bc1.editor.*;

/** In-place navigation, separate from imported assets and timeline selection. */
final class EffectCatalogPanel {
    final EditorActivity a;boolean showing;int category=-1,mediaScroll,catalogScroll;String query="";
    private LinearLayout host,results;private ScrollView scroll;private EditText search;
    EffectCatalogPanel(EditorActivity activity){a=activity;}
    void save(Bundle b){capture();b.putBoolean("effect-catalog",showing);b.putInt("effect-category",category);b.putString("effect-query",query);b.putInt("effect-scroll",catalogScroll);b.putInt("browser-scroll",mediaScroll);}
    void restore(Bundle b){if(b==null)return;showing=b.getBoolean("effect-catalog");category=b.getInt("effect-category",-1);query=b.getString("effect-query","");catalogScroll=b.getInt("effect-scroll");mediaScroll=b.getInt("browser-scroll");}
    void capture(){if(showing&&scroll!=null)catalogScroll=scroll.getScrollY();else if(a.mediaBrowser!=null&&a.mediaBrowser.getParent() instanceof ScrollView)mediaScroll=((ScrollView)a.mediaBrowser.getParent()).getScrollY();}
    void prepareLayout(){capture();host=null;results=null;scroll=null;search=null;}
    void open(){if(showing)return;capture();showing=true;category=-1;query="";catalogScroll=0;mount();}
    void mount(){if(!showing){if(a.mediaBrowser!=null&&a.mediaBrowser.getParent() instanceof ScrollView){ScrollView s=(ScrollView)a.mediaBrowser.getParent();s.post(()->s.scrollTo(0,mediaScroll));}return;}if(a.mobile!=null){a.mobile.catalogHost.setVisibility(View.VISIBLE);LinearLayout panel=a.column();a.mobile.catalogHost.removeAllViews();a.mobile.catalogHost.addView(panel,new FrameLayout.LayoutParams(-1,-1));render(panel);a.mobile.dismissed=false;a.mobile.update();}else if(a.mediaBrowser!=null)render(a.mediaBrowser);}
    void back(){if(category>=0){category=-1;catalogScroll=0;query="";if(search!=null)search.setText("");renderResults();}else close();}
    void close(){if(!showing)return;showing=false;host=null;if(a.mobile!=null){a.mobile.catalogHost.setVisibility(View.GONE);a.mobile.catalogHost.removeAllViews();}a.renderMediaBrowser();if(a.mediaBrowser!=null&&a.mediaBrowser.getParent() instanceof ScrollView){ScrollView s=(ScrollView)a.mediaBrowser.getParent();s.post(()->s.scrollTo(0,mediaScroll));}}
    void render(LinearLayout into){if(host==into&&into.getChildCount()>0)return;host=into;into.removeAllViews();
        LinearLayout header=a.row();header.setGravity(Gravity.CENTER_VERTICAL);header.addView(a.headerIcon("Back","Back from Effect Catalog",v->back()),new LinearLayout.LayoutParams(a.dp(44),a.dp(48)));TextView title=a.label("Effect Catalog",15,BlackIceTheme.TEXT,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));header.addView(a.headerIcon("Close","Close Effect Catalog",v->close()),new LinearLayout.LayoutParams(a.dp(44),a.dp(48)));into.addView(header);
        search=new EditText(a);search.setSingleLine(true);search.setTextSize(13);search.setTextColor(BlackIceTheme.TEXT);search.setHintTextColor(BlackIceTheme.MUTED);search.setHint("Search effects...");search.setContentDescription("Search effects...");search.setBackground(a.buttonBackground(false));search.setPadding(a.dp(10),0,a.dp(10),0);search.setText(query);into.addView(search,new LinearLayout.LayoutParams(-1,a.dp(48)));
        scroll=new ScrollView(a);results=a.column();results.setPadding(0,a.dp(8),0,a.dp(8));scroll.addView(results);into.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        // The outer Project Media scroll view must give this mode a viewport rather than an unbounded height.
        if(into.getParent() instanceof ScrollView)((ScrollView)into.getParent()).setFillViewport(true);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){query=s.toString();catalogScroll=0;renderResults();}public void afterTextChanged(Editable e){}});renderResults();scroll.post(()->scroll.scrollTo(0,catalogScroll));
    }
    private void renderResults(){if(results==null)return;results.removeAllViews();
        if(category<0&&query.trim().isEmpty()){
            GridLayout grid=new GridLayout(a);grid.setColumnCount(2);for(int i=0;i<EffectCatalogBrowser.CATEGORIES.length;i++){final int c=i;LinearLayout card=a.column();card.setGravity(Gravity.CENTER);card.setPadding(a.dp(5),a.dp(4),a.dp(5),a.dp(8));card.setBackground(a.buttonBackground(false));card.setFocusable(true);card.setClickable(true);card.setContentDescription(EffectCatalogBrowser.CATEGORIES[i]);card.setOnClickListener(v->{category=c;catalogScroll=0;renderResults();});card.addView(new Illustration(i),new LinearLayout.LayoutParams(-1,a.dp(60)));TextView label=a.label(EffectCatalogBrowser.CATEGORIES[i],12,BlackIceTheme.TEXT,true);label.setGravity(Gravity.CENTER);label.setMinHeight(a.dp(40));card.addView(label,new LinearLayout.LayoutParams(-1,-2));GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=-2;p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(a.dp(3),a.dp(3),a.dp(3),a.dp(3));grid.addView(card,p);}results.addView(grid);
        }else{
            if(category>=0)results.addView(a.label(EffectCatalogBrowser.CATEGORIES[category],14,BlackIceTheme.ICE,true));java.util.List<Integer> ids=EffectCatalogBrowser.entries(category,query);if(ids.isEmpty())results.addView(a.note("No matching effects. Clear the search to browse again."));
            for(int id:ids){TextView item=a.button(EffectCatalog.name(id)+"\n"+(a.clip()!=null&&a.clip().effect==id?"Saved • currently bypassed":"Currently unavailable"),null);item.setTextSize(13);item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);item.setMinHeight(a.dp(64));item.setEnabled(false);item.setContentDescription(EffectCatalog.name(id)+". Processing is currently unavailable.");LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=a.dp(4);results.addView(item,p);}
        }
        if(!query.isEmpty())results.addView(a.button("Clear search",v->search.setText("")));
        TextView availability=a.note("Effect processing is currently bypassed. Saved effects are preserved.");availability.setTextSize(11);results.addView(availability);if(scroll!=null)scroll.scrollTo(0,0);
    }
    /** Native vector category art, decorative only. */
    final class Illustration extends View {
        final int kind;final Paint p=new Paint(3);Illustration(int c){super(a);kind=c;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas c){float w=getWidth(),h=getHeight(),x=w/2,y=h/2,r=Math.min(w,h)*.32f;p.setColor(BlackIceTheme.ICE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(a.dp(1.5f));
            if(kind==0){p.setShader(new LinearGradient(x-r,0,x+r,0,new int[]{0xFF155AA7,0xFF7CD5F3,0xFFEEF8FF},null,Shader.TileMode.CLAMP));p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,p);p.setShader(null);}
            else if(kind==1){for(int i=4;i>=0;i--){p.setColor(Color.argb(45+i*35,124,213,243));c.drawCircle(x+(i-2)*a.dp(3),y,r-i*a.dp(2),p);}}
            else if(kind==2){for(int i=0;i<5;i++){float yy=y-r+i*r/2;c.drawLine(x-r+(i%2)*r/2,yy,x+r+(i%2)*r/2,yy,p);}}
            else if(kind==3){Path path=new Path();path.moveTo(x-r,y);path.lineTo(x,y-r);path.lineTo(x+r,y);path.lineTo(x,y+r);path.close();c.drawPath(path,p);c.drawLine(x-r,y,x+r,y,p);c.drawLine(x,y-r,x,y+r,p);}
            else if(kind==4){for(int yy=-2;yy<=2;yy++)for(int xx=-2;xx<=2;xx++)c.drawCircle(x+xx*r/2,y+yy*r/2,a.dp(1.5f),p);}
            else{c.drawRoundRect(x-r,y-r,x+r,y+r,a.dp(6),a.dp(6),p);p.setStyle(Paint.Style.FILL);c.drawArc(x-r,y-r,x+r,y+r,90,180,true,p);}p.setStyle(Paint.Style.FILL);
        }
    }
}

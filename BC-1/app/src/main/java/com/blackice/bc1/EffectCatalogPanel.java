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
            GridLayout grid=new GridLayout(a);grid.setColumnCount(2);for(int i=0;i<EffectCatalogBrowser.CATEGORIES.length;i++){final int c=i;LinearLayout card=a.column();card.setGravity(Gravity.CENTER);card.setPadding(a.dp(5),a.dp(4),a.dp(5),a.dp(8));card.setBackground(a.buttonBackground(false));card.setFocusable(true);card.setClickable(true);card.setForeground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x337CD5F3),null,null));card.setOnFocusChangeListener((v,focus)->{android.graphics.drawable.GradientDrawable bg=a.buttonBackground(false);bg.setStroke(a.dp(1),focus?BlackIceTheme.ICE:BlackIceTheme.BORDER);v.setBackground(bg);});card.setContentDescription(EffectCatalogBrowser.CATEGORIES[i]);card.setOnClickListener(v->{category=c;catalogScroll=0;renderResults();});card.addView(new Illustration(i),new LinearLayout.LayoutParams(-1,a.dp(a.mobile==null?84:60)));TextView label=a.label(EffectCatalogBrowser.CATEGORIES[i],12,BlackIceTheme.TEXT,true);label.setGravity(Gravity.CENTER);label.setMinHeight(a.dp(40));card.addView(label,new LinearLayout.LayoutParams(-1,-2));GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=-2;p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(a.dp(3),a.dp(3),a.dp(3),a.dp(3));grid.addView(card,p);}results.addView(grid);
        }else{
            if(category>=0)results.addView(a.label(EffectCatalogBrowser.CATEGORIES[category],14,BlackIceTheme.ICE,true));java.util.List<Integer> ids=EffectCatalogBrowser.entries(category,query);if(ids.isEmpty())results.addView(a.note("No matching effects. Clear the search to browse again."));
            EditProject.Clip clip=a.clip();boolean visual=clip!=null&&clip.track<EditProject.AUDIO_FIRST;
            for(int id:ids){boolean applied=visual&&clip.effect==id;TextView item=a.button((applied?"● ":"")+EffectCatalog.name(id)+"\n"+(applied?"Applied • tap to remove":visual?"Tap to apply":"Select a video or picture"),v->apply(id));item.setTextSize(13);item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);item.setMinHeight(a.dp(64));item.setEnabled(visual);item.setContentDescription(EffectCatalog.name(id)+(applied?". Applied. Tap to remove.":visual?". Tap to apply.":". Select a video or picture to apply."));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=a.dp(4);results.addView(item,p);}
        }
        if(!query.isEmpty())results.addView(a.button("Clear search",v->search.setText("")));
        TextView availability=a.note("One effect per video or picture. Adjust its strength in the Effects panel.");availability.setTextSize(11);results.addView(availability);if(scroll!=null)scroll.scrollTo(0,0);
    }
    /** Applies the effect to the selected clip, or removes it when it is already applied (one undo step). */
    private void apply(int id){
        EditProject.Clip c=a.clip();if(c==null||c.track>=EditProject.AUDIO_FIRST||!a.layerEditable())return;
        boolean remove=c.effect==id;a.edit(remove?"Effect removed":"Effect: "+EffectCatalog.name(id),()->{EditProject.Clip target=a.clip();if(target!=null)target.effect=remove?0:id;});
        a.renderInspector();renderResults();
    }
    /** Native vector category art, decorative only. */
    final class Illustration extends View {
        final int kind;final Paint p=new Paint(3);Illustration(int c){super(a);kind=c;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas c){c.save();float fit=Math.min(getWidth()/100f,getHeight()/64f);c.translate((getWidth()-100*fit)/2f,(getHeight()-64*fit)/2f);c.scale(fit,fit);p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(50,28,58,new int[]{0xFF103B60,0xFF040E1B},null,Shader.TileMode.CLAMP));c.drawRoundRect(0,0,100,64,5,5,p);p.setShader(null);
            switch(kind){
                case 0:{ // Prism and separated light rays.
                    int[] spectrum={0xFFFF4971,0xFFFFA34C,0xFFFFFF70,0xFF47ED99,0xFF31CFFF,0xFF8C70FF};
                    for(int i=5;i>=0;i--){p.setColor(spectrum[i]);p.setStrokeWidth(2.2f);c.drawLine(52,34,101,34+i*3.4f,p);}p.setColor(0xFFEDFBFF);p.setStrokeWidth(2.4f);c.drawLine(0,42,45,31,p);
                    Path prism=polygon(33,50,51,9,72,52);p.setShader(new LinearGradient(32,12,72,52,new int[]{0xAA9AEAFF,0x33105CB6,0xCC67CEFF},null,Shader.TileMode.CLAMP));c.drawPath(prism,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setColor(0xFFB7ECFF);p.setStrokeWidth(1.2f);c.drawPath(prism,p);c.drawLine(51,9,50,47,p);c.drawLine(33,50,50,47,p);c.drawLine(50,47,72,52,p);break;}
                case 1:{p.setShader(new RadialGradient(45,25,31,new int[]{0xFF5CDFFF,0xFF128BFA,0x88134BED,0x00134BED},new float[]{0,.5f,.8f,1},Shader.TileMode.CLAMP));c.drawCircle(50,32,31,p);p.setShader(null);break;}
                case 2:{Path triangle=polygon(30,49,51,12,73,49);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(0xFF00DFFF);c.drawPath(triangle,p);c.save();c.translate(4,2);p.setColor(0xCCCD38FF);c.drawPath(triangle,p);c.restore();p.setStrokeWidth(1);for(int i=0;i<9;i++){p.setColor(i%2==0?0xAA12CFFF:0xAAE845F4);float y=15+i*4;c.drawLine(9+(i%3)*8,y,83-(i%2)*13,y,p);}break;}
                case 3:{p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.7f);p.setColor(0xFF27A4DF);c.save();c.rotate(-20,50,32);c.drawOval(10,20,90,44,p);c.restore();p.setStyle(Paint.Style.FILL);p.setColor(0xFF2DDCFF);c.drawPath(polygon(50,12,68,22,50,32,32,22),p);p.setShader(new LinearGradient(32,22,50,52,0xFF2295FF,0xFF123B92,Shader.TileMode.CLAMP));c.drawPath(polygon(32,22,50,32,50,52,32,42),p);p.setShader(new LinearGradient(50,32,68,42,0xFF0C67D8,0xFF35D8FF,Shader.TileMode.CLAMP));c.drawPath(polygon(50,32,68,22,68,42,50,52),p);p.setShader(null);p.setColor(0xFFC8F5FF);c.drawCircle(17,39,2,p);c.drawCircle(84,22,2,p);break;}
                case 4:{for(int row=0;row<3;row++)for(int col=0;col<6;col++){float x=col*20-10+(row%2)*10,y=row*22;p.setColor(new int[]{0xFF153B65,0xFF13609A,0xFF168AC9,0xFF23C6F6}[(row*3+col)%4]);c.drawPath(polygon(x,y+22,x+10,y,x+20,y+22),p);}break;}
                default:{Path mask=new Path();mask.addCircle(50,32,29,Path.Direction.CW);c.save();c.clipPath(mask);p.setShader(new LinearGradient(0,3,0,62,0xFF86C9F9,0xFF173A62,Shader.TileMode.CLAMP));c.drawRect(0,0,100,64,p);p.setShader(null);p.setColor(0xFF15416A);c.drawPath(polygon(10,63,43,15,82,64),p);p.setColor(0xFFE0F4FF);c.drawPath(polygon(28,38,43,15,57,35,47,31,43,24,39,34),p);p.setColor(0xFF07162A);c.drawPath(polygon(0,64,28,43,40,54,63,33,100,64),p);c.restore();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0xFF53C9F5);c.drawCircle(50,32,29,p);}
            }
            p.setStyle(Paint.Style.FILL);p.setShader(null);c.restore();
        }
        Path polygon(float... points){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);path.close();return path;}

    }
}

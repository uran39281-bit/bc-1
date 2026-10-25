package com.blackice.bc1;

import android.graphics.Bitmap;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.*;

/** The unified "+ Add" drawer: Media, Audio, Text, Shapes and Saved Elements. The project stays
 * visible above (phone) or beside (DeX) the drawer while browsing. */
final class AddDrawer {
    static final String[] CATEGORIES={"Media","Audio","Text","Shapes","Saved"};
    private final EditorActivity a;
    private final DeviceMedia gallery;
    int category;
    boolean atStart;
    AddDrawer(EditorActivity activity){a=activity;gallery=new DeviceMedia(activity);}

    void render(LinearLayout into){
        a.binGrid=null;
        if(a.mobile!=null){mobile(into);return;}
        // Categories live here now that the landscape header contains only project actions.
        LinearLayout tabs=a.row();
        for(int i=0;i<CATEGORIES.length;i++){final int index=i;String icon=i==0?"Media":i==1?"Audio":i==2?"Text":i==3?"Shapes":"Saved";
            TextView b=a.iconButton(CATEGORIES[i],v->{category=index;a.mediaKind=index==3?MediaFilter.Kind.SHAPES:index==1?MediaFilter.Kind.AUDIO:MediaFilter.Kind.ALL;a.renderInspector();});b.setTextColor(i==category?EditorActivity.CYAN:EditorActivity.TEXT);b.setBackground(a.navBackground(i==category));b.setSelected(i==category);
            b.setCompoundDrawables(null,a.icon(icon,i==category?EditorActivity.CYAN:EditorActivity.TEXT),null,null);b.setContentDescription("Add "+CATEGORIES[i]);
            tabs.addView(b,new LinearLayout.LayoutParams(0,a.dp(a.wideLayout?60:48),1));}
        into.addView(tabs);
        content(into);
    }
    private void content(LinearLayout into){
        if(category==1)placement(into);
        switch(category){
            case 0:media(into);break;
            case 1:audio(into);break;
            case 2:text(into);break;
            case 3:shapes(into);break;
            default:saved(into);break;
        }
    }
    /** Where new music lands: at the playhead (default) or project start. Media is dragged instead. */
    private void placement(LinearLayout into){
        LinearLayout row=a.row();
        row.addView(choice("At playhead",!atStart,v->{atStart=false;a.renderInspector();}),new LinearLayout.LayoutParams(0,a.dp(48),1));
        row.addView(choice("At project start",atStart,v->{atStart=true;a.renderInspector();}),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(row);
    }
    private TextView choice(String title,boolean on,View.OnClickListener l){TextView b=a.button((on?"● ":"")+title,l);b.setTextSize(11);b.setSelected(on);b.setTextColor(on?EditorActivity.CYAN:EditorActivity.TEXT);b.setBackground(a.buttonBackground(on));return b;}
    long insertTime(){return atStart?0:a.project.playheadMs;}

    private void media(LinearLayout into){
        projectMedia(into,false);
        if(a.wideLayout&&a.mediaBrowser!=null&&a.mediaBrowser.getParent()!=into)into.addView(a.note("Use Import Media in Project Media to open Browse files beside it."));
    }
    /** Project Media in the panel: long-press a card and drag it onto the timeline. Hidden when the
     * desktop media browser already shows it. */
    private void projectMedia(LinearLayout into,boolean audio){
        if(a.mediaBrowser!=null&&a.wideLayout&&a.mediaBrowser.isAttachedToWindow())return;
        a.mediaBrowser=a.column();a.mediaGridColumns=2;a.mediaColumns=a.mediaList?1:2;into.addView(a.mediaBrowser);a.renderMediaBrowser();
    }

    private void audio(LinearLayout into){
        into.addView(a.button("Speech & captions",v->CaptionPanel.show(a)));
        LinearLayout actions=a.row();
        actions.addView(a.button("Music track…",v->a.pick(EditorActivity.MUSIC,"audio/*")),new LinearLayout.LayoutParams(0,a.dp(48),1));
        actions.addView(a.button("Audio layer…",v->{a.mediaImportWindow.filter=2;a.openMediaBrowser();}),new LinearLayout.LayoutParams(0,a.dp(48),1));
        into.addView(actions);
        into.addView(a.button("Manage audio access",v->manageAccess(true)));
        projectMedia(into,true);

        if(a.project.music!=null)a.addAction("Music: "+a.project.music.name,()->a.musicDialog());
    }
    void refresh(){a.mediaImportWindow.refresh();}
    private void manageAccess(boolean audio){
        String[] permissions=audio?DeviceMedia.audioPermissions():DeviceMedia.visualPermissions();
        if(gallery.access(audio).equals("full")){a.openAppSettings();return;}
        a.requestPermissions(permissions,EditorActivity.MEDIA_ACCESS);
    }
    private void text(LinearLayout into){
        into.addView(a.note("Text is added as its own layer at "+(atStart?"the project start":"the playhead")+"."));
        String[][] presets={{"Title","BLACK ICE","1.1"},{"Subtitle","Subtitle","0.7"},{"Caption","Caption text","0.5"}};
        for(String[] p:presets)a.gridAction("+ "+p[0],()->a.addOverlay(0,p[1],Float.parseFloat(p[2])));
        a.flushGrid();
        // The top bar has one Text tab, so shapes and saved elements live here in wide windows.
        if(a.topTabs()){into.addView(a.label("SHAPES",12,EditorActivity.CYAN,true));shapes(into);into.addView(a.label("SAVED ELEMENTS",12,EditorActivity.CYAN,true));saved(into);}
    }
    private void shapes(LinearLayout into){
        a.mediaKind=MediaFilter.Kind.SHAPES;projectMedia(into,false);a.renderMediaGrid();
        if(a.wideLayout&&a.mediaBrowser!=null&&a.mediaBrowser.getParent()!=into)into.addView(a.note("Drag a shape from Project Media onto a visual timeline row."));
    }
    private void mobile(LinearLayout into){
        LinearLayout tabs=a.row();HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);scroll.addView(tabs);into.addView(scroll);
        int[] categories={3,0,1,4};String[] names={"Shapes","Project Media","Audio","Elements"},icons={"Shapes","Media","Audio","Saved"};
        for(int i=0;i<categories.length;i++){final int index=categories[i];TextView b=a.iconButton(names[i],v->{category=index;a.mediaKind=index==3?MediaFilter.Kind.SHAPES:index==1?MediaFilter.Kind.AUDIO:MediaFilter.Kind.ALL;a.renderInspector();});b.setTextSize(11);b.setCompoundDrawables(null,a.icon(icons[i],category==index?EditorActivity.CYAN:EditorActivity.TEXT),null,null);b.setBackground(a.navBackground(category==index));b.setSelected(category==index);b.setContentDescription("Add "+(index==3?"Shapes":index==4?"Saved":names[i]));tabs.addView(b,new LinearLayout.LayoutParams(a.dp(64),a.dp(64)));}
        LinearLayout body=a.row(),items=a.column();body.addView(items,new LinearLayout.LayoutParams(0,-2,1));LinearLayout rail=a.column();rail.setPadding(a.dp(4),a.dp(8),0,0);
        TextView text=a.button("Aa\nText",v->a.mobile.inputText(false));text.setContentDescription("Add Text");rail.addView(text,new LinearLayout.LayoutParams(a.dp(54),a.dp(64)));TextView close=a.button("×",v->a.mobile.dismiss());close.setTextSize(26);close.setContentDescription("Close Add Content");rail.addView(close,new LinearLayout.LayoutParams(a.dp(54),a.dp(56)));body.addView(rail);into.addView(body);
        content(items);
    }

    private void saved(LinearLayout into){
        into.addView(a.note("Saved elements are grouped layers you can reuse. Select layers and choose More → Save as element."));
        List<PresetStore.Preset> elements=a.presets.list(PresetStore.ELEMENT);
        if(elements.isEmpty())into.addView(a.note("No saved elements yet."));
        for(PresetStore.Preset p:elements){LinearLayout row=a.row();
            row.addView(a.button("+ "+p.name,v->a.insertElement(p,insertTime())),new LinearLayout.LayoutParams(0,a.dp(52),1));
            row.addView(a.compactButton("✕","Delete saved element "+p.name,v->a.deletePreset(p)),new LinearLayout.LayoutParams(a.dp(52),a.dp(52)));
            into.addView(row);}
        List<PresetStore.Preset> effects=a.presets.list(PresetStore.EFFECTS),animations=a.presets.list(PresetStore.ANIMATION);
        if(!effects.isEmpty()||!animations.isEmpty())into.addView(a.note(effects.size()+" effect preset(s) and "+animations.size()+" animation preset(s) are available under Presets for a selected layer."));
    }
}

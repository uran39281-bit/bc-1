package com.blackice.bc1;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.EngineSettings;
import java.util.*;

/** Selection-based tool grids and focused adjustment panels. One panel is open at a time; Back returns
 * to the previous panel without touching selection, playhead or timeline scroll. */
final class ToolPanels {
    interface Setter{void set(float value);}
    private final EditorActivity a;
    private final ArrayDeque<Panel> stack=new ArrayDeque<>();
    private Panel current;
    private String owner="";
    private final List<Runnable> valueRefresh=new ArrayList<>();
    private TextView previousOpacity,nextOpacity;
    void refresh(){if(!showing())return;for(Runnable update:valueRefresh)update.run();if(previousOpacity!=null)EditorActivity.dim(previousOpacity,opacityNeighbor(false)>=0);if(nextOpacity!=null)EditorActivity.dim(nextOpacity,opacityNeighbor(true)>=0);}
    private static final class Panel{final String title;final Runnable build;Panel(String t,Runnable b){title=t;build=b;}}
    ToolPanels(EditorActivity activity){a=activity;}

    private String selectionKey(){return a.selected+":"+a.selectedOverlay+":"+a.tab;}
    /** Opens a focused panel; the previous one is kept for Back. */
    void open(String title,Runnable build){if(current!=null)stack.push(current);current=new Panel(title,build);owner=selectionKey();a.renderInspector();}
    void back(){current=stack.isEmpty()?null:stack.pop();a.renderInspector();}
    void reset(){stack.clear();current=null;}
    boolean showing(){return current!=null&&owner.equals(selectionKey());}
    String title(){return current==null?null:current.title;}

    /** Renders the open panel, or the contextual tool grid for the current selection. */
    void render(LinearLayout into) {
        valueRefresh.clear();previousOpacity=null;nextOpacity=null;
        if(current!=null&&!owner.equals(selectionKey()))reset();
        if(current!=null){header(into,current.title);current.build.run();return;}
        if(a.tab==2){effectsHome();return;}
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(a.multiCount()>1){multiTools();return;}
        if(c==null&&o==null){browseHome();return;}
        if(a.wideLayout){landscapeMediaSettings(into);return;}
        if(footage()){footagePanel(into);return;}
        String kind=kind(c,o);
        into.addView(a.note(kind+"  •  "+(c!=null?c.name:o.label())+(locked()?"  •  Layer locked":"")));
        renderCategories(c,o);
        playheadRow(c,o);
        for(String tool:tools(c,o))a.gridAction(tool,()->runTool(tool));
        a.flushGrid();
        into.addView(a.note(c!=null&&c.track==0?"Drag handles to ripple trim; drag the selected body to reorder the main sequence. Drag empty space to scrub.":"Drag handles to trim; drag the selected body to move. Drag empty space to scrub; pinch to zoom."));
    }
    /** Visual footage has one compact contextual home; its tools still use shared editor commands. */
    boolean footage(){EditProject.Clip c=a.clip();return a.tab==1&&a.multiCount()<=1&&(c!=null&&c.track<EditProject.AUDIO_FIRST||a.overlay()!=null&&(a.overlay().imageUri!=null||a.overlay().shape>0));}
    private final Map<String,Boolean> audioSources=new HashMap<>();
    private final Set<String> checkingAudio=new HashSet<>();
    private void checkAudio(EditProject.Clip c){if(c.image||audioSources.containsKey(c.uri)||!checkingAudio.add(c.uri))return;String uri=c.uri;a.io.execute(()->{boolean found=false;try{found=AudioWaveform.hasAudio(a,uri);}catch(Exception ignored){}final boolean result=found;a.main.post(()->{checkingAudio.remove(uri);audioSources.put(uri,result);if(!a.isDestroyed()&&a.clip()!=null&&uri.equals(a.clip().uri)&&footage()&&!showing())a.renderInspector();});});}
    private TextView tile(String icon,String label,String description,Runnable action,boolean enabled){TextView b=a.iconButton(label,v->action.run());b.setTextSize(11);b.setCompoundDrawables(null,a.icon(icon,enabled?EditorActivity.TEXT:EditorActivity.MUTED),null,null);b.setContentDescription(description);b.setTooltipText(description);EditorActivity.dim(b,enabled);return b;}
    private void footagePanel(LinearLayout into){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();boolean still=c==null||c.image,editable=!locked()&&!a.uiLocked;if(c!=null)checkAudio(c);
        LinearLayout title=a.row();title.setGravity(Gravity.CENTER_VERTICAL);TextView heading=a.label("Media Settings",18,EditorActivity.TEXT,true);title.addView(heading,new LinearLayout.LayoutParams(0,a.dp(48),1));
        title.addView(a.headerIcon("Duplicate","Duplicate",v->a.duplicateSelected()),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));title.addView(a.headerIcon("Delete","Delete",v->a.deleteSelection()),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));title.addView(a.headerIcon("More","More actions",v->a.moreMenu()),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));into.addView(title);
        TextView source=a.label((c!=null?c.name:o.label())+(locked()?" • Locked":""),11,EditorActivity.MUTED,false);source.setTooltipText(c!=null?c.name:o.label());source.setSingleLine(true);source.setEllipsize(android.text.TextUtils.TruncateAt.END);source.setContentDescription("Selected footage "+(c!=null?c.name:o.label()));into.addView(source);
        boolean inside=editable&&(c!=null?LayerOps.canSplitOrTrim(a.project,a.selected):a.project.playheadMs-o.startMs>=EditProject.MIN_CLIP_MS&&o.endMs-a.project.playheadMs>=EditProject.MIN_CLIP_MS),audio=editable&&c!=null&&!c.image&&Boolean.TRUE.equals(audioSources.get(c.uri));
        String[] labels={still?"Duration":"Speed","Trim Start","Split","Trim End",c!=null&&c.unmutedVolume>=0?"Unmute":"Mute"};String[] icons={still?"Time":"Speed","TrimStart","Edit","TrimEnd","Mute"};
        int columns=a.getResources().getConfiguration().fontScale>1.25f?3:5;LinearLayout row=null;
        for(int i=0;i<5;i++){final int index=i;if(i%columns==0){row=a.row();into.addView(row);}boolean enabled=i==0?editable:i==4?audio:inside;String desc=i==0?labels[i]:i==4?(still?"Mute unavailable for pictures":audio?labels[i]:"Mute unavailable: source has no audio or is still being checked"):labels[i]+" at playhead";
            TextView b=tile(icons[i],labels[i],desc,()->{if(index==0)runTool(still?"Duration":"Speed");else if(index==4){if(audio&&a.layerEditable())a.edit(c.unmutedVolume>=0?"Clip unmuted":"Clip muted",()->ClipAudio.toggleMute(c));}else playheadEdit(index-1);},enabled);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(68),1);lp.setMargins(a.dp(2),a.dp(6),a.dp(2),a.dp(6));row.addView(b,lp);}
        View divider=new View(a);divider.setBackgroundColor(BlackIceTheme.BORDER);into.addView(divider,new LinearLayout.LayoutParams(-1,a.dp(1)));
        mediaTiles(into,editable,true);
        // Secondary supported operations remain reachable without returning the removed category tiles.
        TextView extra=a.button("More footage tools",v->open("More footage tools",()->{for(String tool:tools(a.clip(),a.overlay()))a.gridAction(tool,()->runTool(tool));a.flushGrid();a.inspector.addView(a.button("Compare before / after",compareView->a.compare(!a.comparing)));}));extra.setContentDescription("Slip, chroma key, audio extraction and animation tools");into.addView(extra,new LinearLayout.LayoutParams(-1,a.dp(48)));
        if(c!=null&&c.track==0)into.addView(a.note("Main sequence: edge dragging uses ripple trim. Trim Start/End at playhead preserve other clip positions."));
    }
    private void landscapeMediaSettings(LinearLayout into){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();boolean visual=c==null||c.track<EditProject.AUDIO_FIRST;if(c!=null&&!c.image)checkAudio(c);
        LinearLayout title=a.row();title.addView(a.label("Media Settings",18,EditorActivity.TEXT,true),new LinearLayout.LayoutParams(0,a.dp(48),1));title.addView(a.headerIcon("More","Selected media actions",v->a.moreMenu()));into.addView(title);
        TextView filename=a.note(c!=null?c.name:o.label());filename.setSingleLine(true);filename.setEllipsize(android.text.TextUtils.TruncateAt.END);filename.setTooltipText(c!=null?c.name:o.label());filename.setContentDescription("Selected media "+(c!=null?c.name:o.label()));into.addView(filename);
        mediaTiles(into,!locked()&&!a.uiLocked,visual);
    }
    private void mediaTiles(LinearLayout into,boolean editable,boolean visual){
        boolean shape=a.overlay()!=null&&a.overlay().shape>0;
        String[] names={"Move & Transform","Blending & Opacity","Effects",shape?"Shape & Style":"Audio"};
        String[] icons={"Transform","Blending & Opacity","Effects",shape?"Shapes":"Volume"};
        for(int row=0;row<2;row++){LinearLayout line=a.row();into.addView(line);for(int col=0;col<2;col++){int index=row*2+col;MediaTile tile=new MediaTile(a,names[index],icons[index]);tile.setOnClickListener(v->{if(index==3){if(shape)open("Shape & Style",this::shapePanel);else open("Audio",this::audioPanel);}else category(names[index]);});tile.setContentDescription("Media Settings: "+names[index]);tile.setTooltipText(names[index]);EditorActivity.dim(tile,editable&&(index==3||visual));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(a.dp(3),a.dp(5),a.dp(3),a.dp(5));line.addView(tile,lp);}}
    }

    private void audioPanel(){
        EditProject.Clip c=a.clip();if(c==null||c.image){a.inspector.addView(a.note("This media has no audio."));return;}
        a.inspector.addView(a.button(c.unmutedVolume>=0?"Unmute":"Mute",v->{if(a.layerEditable())a.edit("Audio mute updated",()->ClipAudio.toggleMute(c));}));
        percent("Volume",c.volume,1,v->{c.volume=v;c.unmutedVolume=-1;},c.motion,Motion.VOLUME);
        if(c.fadeInMs>0||c.fadeOutMs>0)a.inspector.addView(a.note("Saved legacy fades remain active."));
        a.inspector.addView(a.button("Noise reduction",v->AudioToolsPanel.noise(a)));
        a.inspector.addView(a.button("Equalizer",v->AudioToolsPanel.eq(a)));
        a.inspector.addView(a.button("Loudness normalization",v->AudioToolsPanel.normalize(a)));
        a.inspector.addView(a.note("Audio visualizer temporarily disabled"));
        if(c.track<EditProject.AUDIO_FIRST)a.inspector.addView(a.button("Extract Audio",v->runTool("Extract Audio")));
        a.inspector.addView(a.button("Reset Audio Settings",v->{if(a.layerEditable())a.edit("Audio reset",()->{c.volume=1;c.unmutedVolume=-1;c.fadeInMs=0;c.fadeOutMs=0;c.audio=new com.blackice.bc1.audio.AudioSettings();c.motion.curves.get(Motion.VOLUME).clear();});}));
    }
    private boolean locked(){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();return c!=null?LayerOps.locked(a.project,a.selected):LayerOps.locked(o);}
    static String kind(EditProject.Clip c,EditProject.Overlay o){
        if(c!=null)return c.track>=EditProject.AUDIO_FIRST?"Audio":c.image?"Picture":"Video";
        return o.imageUri!=null?"Image layer":o.shape>0?"Shape":"Text";
    }
    private void header(LinearLayout into,String title){
        LinearLayout bar=a.row();bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=a.button("‹  Back",v->back());back.setContentDescription("Back to previous panel");bar.addView(back,new LinearLayout.LayoutParams(-2,a.dp(48)));
        TextView heading=a.label(title,13,EditorActivity.CYAN,true);heading.setPadding(a.dp(10),0,0,0);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        into.addView(bar);
    }
    /** Pictures, videos, audio, text, shapes and image layers each get only their compatible tools. */
    static List<String> tools(EditProject.Clip c,EditProject.Overlay o){
        List<String> t=new ArrayList<>();
        if(c!=null&&c.track>=EditProject.AUDIO_FIRST){Collections.addAll(t,"Split","Trim","Slip","Volume","Noise reduction","Equalizer","Loudness normalization","Duplicate");return t;}
        if(c!=null&&c.image){Collections.addAll(t,"Duration: "+EditorActivity.seconds(c.durationMs())+" s","Transform","Opacity","Effects","Chroma Key","Animation","Duplicate");return t;}
        if(c!=null){Collections.addAll(t,"Split","Trim","Slip","Speed: "+trimZero(c.speed)+"×","Extract Audio","Transform","Opacity","Effects","Chroma Key","Volume","Noise reduction","Equalizer","Loudness normalization","Animation","Duplicate");return t;}
        if(o.imageUri==null&&o.shape==0){Collections.addAll(t,"Edit Text","Font","Size","Color","Alignment","Transform","Opacity","Duration: "+EditorActivity.seconds(o.endMs-o.startMs)+" s","Animation");return t;}
        if(o.shape>0){Collections.addAll(t,"Color","Transform","Opacity","Duration: "+EditorActivity.seconds(o.endMs-o.startMs)+" s","Animation","Duplicate");return t;}
        Collections.addAll(t,"Transform","Opacity","Duration: "+EditorActivity.seconds(o.endMs-o.startMs)+" s","Animation","Duplicate");return t;
    }
    static String trimZero(float v){String s=String.format(Locale.US,"%.2f",v);return s.replaceAll("0+$","").replaceAll("\\.$","");}
    static List<String> categories(EditProject.Clip c,EditProject.Overlay o){
        List<String> list=new ArrayList<>();if(c!=null&&c.track>=EditProject.AUDIO_FIRST)return list;
        list.add("Move & Transform");list.add("Blending & Opacity");if(c!=null)list.add("Effects");
        return list;
    }
    private void renderCategories(EditProject.Clip c,EditProject.Overlay o){
        List<String> list=categories(c,o);if(list.isEmpty())return;
        HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);scroll.setContentDescription("Tool categories");
        LinearLayout chips=a.row();for(String name:list){TextView chip=a.iconButton(name,v->category(name));chip.setTextSize(9);chips.addView(chip,new LinearLayout.LayoutParams(a.dp(name.length()>14?96:80),a.dp(60)));}
        scroll.addView(chips);a.inspector.addView(scroll);
    }
    private void playheadRow(EditProject.Clip c,EditProject.Overlay o){
        boolean valid=c!=null?LayerOps.canSplitOrTrim(a.project,a.selected):o!=null&&a.project.playheadMs-o.startMs>=EditProject.MIN_CLIP_MS&&o.endMs-a.project.playheadMs>=EditProject.MIN_CLIP_MS;
        LinearLayout row=a.row();String[] names={"Trim Start to Playhead","Split at Playhead","Trim End to Playhead"};
        for(int i=0;i<3;i++){final int index=i;TextView b=a.button(names[i],v->playheadEdit(index));b.setTextSize(10);b.setEnabled(valid);b.setAlpha(valid?1:.4f);
            b.setContentDescription(names[i]+(valid?"":" (move the playhead inside the selected layer)"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(52),1);lp.rightMargin=a.dp(3);row.addView(b,lp);}
        a.inspector.addView(row);
    }
    void playheadEdit(int which){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();if(!a.layerEditable())return;
        if(c!=null){if(which==1)a.splitSelected();else a.edit(which==0?"Trimmed start to playhead":"Trimmed end to playhead",()->LayerOps.trimToPlayhead(a.project,a.selected,which==0));return;}
        long t=a.project.playheadMs;
        if(which==0)a.edit("Trimmed start to playhead",()->o.startMs=t);
        else if(which==2)a.edit("Trimmed end to playhead",()->o.endMs=t);
        else a.edit("Layer split",()->{if(!com.blackice.bc1.core.CaptionBuilder.GROUP.equals(o.group)&&a.project.regularOverlayCount()>=20)throw new IllegalArgumentException("Up to 20 text, shape and image layers.");EditProject.Overlay right=o.copy();long offset=t-o.startMs;right.startMs=t;Keyframes.splitAt(right.motion,offset);o.endMs=t;a.project.overlays.add(a.project.overlays.indexOf(o)+1,right);});
    }
    void runTool(String tool){
        if(tool.startsWith("Duration:"))tool="Duration";else if(tool.startsWith("Speed:"))tool="Speed";
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        switch(tool){
            case "Split":if(a.layerEditable())a.splitSelected();return;
            case "Duplicate":a.duplicateSelected();return;
            case "Transform":category("Move & Transform");return;
            case "Effects":category("Effects");return;
            case "Opacity":open("Opacity",()->opacityPanel());return;
            case "Noise reduction":AudioToolsPanel.noise(a);return;
            case "Equalizer":AudioToolsPanel.eq(a);return;
            case "Loudness normalization":AudioToolsPanel.normalize(a);return;
            case "Volume":open("Volume",()->percent("Volume",c.volume,.7f,v->{c.volume=v;c.unmutedVolume=-1;},c.motion,Motion.VOLUME));return;
            case "Duration":open("Duration",this::durationPanel);return;
            case "Trim":open("Trim",this::trimPanel);return;
            case "Slip":open("Slip",this::slipPanel);return;
            case "Speed":open("Speed",this::speedPanel);return;
            case "Fade In":open("Fade In",()->fadePanel(true));return;
            case "Fade Out":open("Fade Out",()->fadePanel(false));return;
            case "Extract Audio":open("Extract Audio",this::extractPanel);return;
            case "Chroma Key":open("Chroma Key",this::chromaPanel);return;
            case "Edit Text":open("Edit Text",this::textPanel);return;
            case "Font":open("Font",this::fontPanel);return;
            case "Size":open("Size",()->percent("Size",o.scale/2f,.35f,v->o.scale=Math.max(.1f,v*2),o.motion,Motion.SCALE,2,.05f));return;
            case "Color":category("Color & Fill");return;
            case "Alignment":open("Alignment",this::alignmentPanel);return;
            case "Animation":open("Animation",this::animationPanel);return;
        }
    }
    void category(String name){
        switch(name){
            case "Move & Transform":open(name,()->a.transformPanel.build(a.inspector));return;
            case "Color & Fill":open(name,this::colorPanel);return;
            case "Border & Shadow":open(name,this::borderPanel);return;
            case "Blending & Opacity":open(name,this::blendPanel);return;
            case "Effects":open(name,this::effectsHome);return;
            case "Presets":open(name,this::presetsPanel);return;
        }
    }

    // ---- Shared value controls -------------------------------------------------------------------
    /** Motion time at the playhead in the selected layer's key time base. */
    long motionTime(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(c!=null){EditProject.Placement p=a.project.placements().get(a.selected);return Math.max(c.inMs,Math.min(c.outMs,c.inMs+Math.round((a.project.playheadMs-p.startMs)*c.speed)));}
        if(o!=null)return Math.max(0,Math.min(o.endMs-o.startMs,a.project.playheadMs-o.startMs));
        return 0;
    }
    /** Half a frame, so a key "at the playhead" survives frame-quantised seeking. */
    long tolerance(){EditProject.Clip c=a.clip();float speed=c==null?1:c.speed;return Math.max(1,Math.round(500f/a.project.frameRate*speed));}
    /** A labelled 0–1 value with slider, exact entry, Reset and an optional keyframe diamond. */
    void percent(String title,float initial,float defaultValue,Setter setter,Motion motion,int property){percent(title,initial,defaultValue,setter,motion,property,1,0);}
    void percent(String title,float initial,float defaultValue,Setter setter,Motion motion,int property,float motionFactor,float minimum){
        float shown=motion==null?initial:motion.value(property,motionTime(),initial*motionFactor)/motionFactor;
        valueRow(title,shown,0,1,defaultValue,100,"%",v->{float stored=Math.max(minimum,v)*motionFactor;
            if(motion==null||Keyframes.set(motion,property,motionTime(),stored,tolerance()))setter.set(Math.max(minimum,v));},motion,property,motionFactor);
    }
    /** Generic row: value in model units; display = value × displayFactor with a unit suffix. */
    void valueRow(String title,float value,float min,float max,float defaultValue,float displayFactor,String unit,Setter apply,Motion motion,int property,float motionFactor){
        LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);
        if(motion!=null)row.addView(diamond(title,motion,property,value*motionFactor),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        TextView label=a.label(title,12,EditorActivity.TEXT,true);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        EditText exact=new EditText(a);exact.setText(format(value*displayFactor));exact.setTextColor(EditorActivity.TEXT);exact.setSingleLine(true);exact.setSelectAllOnFocus(true);
        exact.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);exact.setContentDescription(title+" exact value"+(unit.isEmpty()?"":" in "+unit));exact.setGravity(Gravity.END);
        row.addView(exact,new LinearLayout.LayoutParams(a.dp(88),a.dp(48)));
        if(!unit.isEmpty())row.addView(a.label(unit,11,EditorActivity.MUTED,false));
        a.inspector.addView(row);
        SeekBar slider=new SeekBar(a);slider.setMax(1000);slider.setProgress(Math.round((value-min)/(max-min)*1000));slider.setContentDescription(title);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            // The preview follows the slider live (ICE Lite); one drag is one undo entry.
            public void onProgressChanged(SeekBar s,int p,boolean user){if(!user)return;float v=min+(max-min)*p/1000f;exact.setText(format(v*displayFactor));a.liveAdjust(()->apply.set(v));}
            public void onStartTrackingTouch(SeekBar s){a.pausePlayback();a.beginLiveAdjust();}
            public void onStopTrackingTouch(SeekBar s){float v=min+(max-min)*s.getProgress()/1000f;if(a.liveBefore!=null)a.endLiveAdjust(title+" updated",()->apply.set(v));else commit(title,v,apply);}
        });
        a.inspector.addView(slider,new LinearLayout.LayoutParams(-1,a.dp(44)));
        if(motion!=null)valueRefresh.add(()->{if(a.liveBefore!=null)return;float evaluated=motion.value(property,motionTime(),value*motionFactor)/motionFactor;String formatted=format(evaluated*displayFactor);if(!exact.hasFocus()&&!formatted.contentEquals(exact.getText()))exact.setText(formatted);int progress=Math.round((evaluated-min)/(max-min)*1000);if(progress!=slider.getProgress())slider.setProgress(progress);});
        LinearLayout actions=a.row();
        actions.addView(a.button("Apply",v->{try{float typed=Float.parseFloat(exact.getText().toString().trim())/displayFactor;if(!Float.isFinite(typed)||typed<min-1e-6f||typed>max+1e-6f)throw new NumberFormatException();commit(title,typed,apply);}catch(NumberFormatException e){a.tell("Enter "+format(min*displayFactor)+" to "+format(max*displayFactor)+(unit.isEmpty()?"":" "+unit)+".");}}),new LinearLayout.LayoutParams(0,a.dp(48),1));
        actions.addView(a.button("Reset",v->commit(title,defaultValue,apply)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        a.inspector.addView(actions);
    }
    private void commit(String title,float value,Setter apply){if(!a.layerEditable()){a.renderInspector();return;}a.edit(title+" updated",()->apply.set(value));}
    static String format(float v){return String.format(Locale.US,Math.abs(v)>=100?"%.0f":"%.2f",v);}
    /** ◇ unanimated · cyan ◇ animated between keys · filled ◆ on a key at the playhead. */
    TextView diamond(String title,Motion motion,int property,float current){
        int state=Keyframes.state(motion,property,motionTime(),tolerance());
        TextView d=a.label(state==Keyframes.ON_KEY?"◆":"◇",20,state==Keyframes.STATIC?EditorActivity.MUTED:EditorActivity.CYAN,true);d.setGravity(Gravity.CENTER);
        String[] words={"not animated • tap to animate","animated • tap to add a keyframe here","keyframe at playhead • tap to remove"};
        d.setContentDescription(title+" keyframe: "+words[state]);d.setTooltipText(d.getContentDescription());d.setFocusable(true);
        valueRefresh.add(()->{int currentState=Keyframes.state(motion,property,motionTime(),tolerance());String glyph=currentState==Keyframes.ON_KEY?"◆":"◇";if(!glyph.contentEquals(d.getText()))d.setText(glyph);d.setTextColor(currentState==Keyframes.STATIC?EditorActivity.MUTED:EditorActivity.CYAN);String description=title+" keyframe: "+words[currentState];if(!description.contentEquals(d.getContentDescription())){d.setContentDescription(description);d.setTooltipText(description);}});
        d.setOnClickListener(v->{if(!a.layerEditable())return;long t=motionTime(),tol=tolerance();int currentState=Keyframes.state(motion,property,t,tol);
            a.edit(currentState==Keyframes.ON_KEY?title+" keyframe removed":title+" keyframe added",()->{
                if(currentState==Keyframes.ON_KEY&&motion.curves.get(property).size()==1){a.setStatic(property,Keyframes.disable(motion,property,t,current));}
                else Keyframes.toggle(motion,property,t,motion.value(property,t,current),tol);
            });});
        return d;
    }

    // ---- Panels ----------------------------------------------------------------------------------
    private void opacityPanel(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(c!=null)percent("Opacity",c.opacity,1,v->c.opacity=v,c.motion,Motion.OPACITY);else percent("Opacity",o.opacity,1,v->o.opacity=v,o.motion,Motion.OPACITY);
        a.inspector.addView(a.note("0% is fully transparent. Lower layers show through transparent regions."));
    }
    private long opacityNeighbor(boolean next){
        EditProject.Clip clip=a.clip();EditProject.Overlay overlay=a.overlay();Motion motion=clip!=null?clip.motion:overlay.motion;
        long best=-1,now=a.project.playheadMs;
        for(Motion.Key key:motion.curves.get(Motion.OPACITY)){
            if(clip!=null&&(key.timeMs<clip.inMs||key.timeMs>clip.outMs)||overlay!=null&&(key.timeMs<0||key.timeMs>overlay.endMs-overlay.startMs))continue;
            long time=clip!=null?a.project.placements().get(a.selected).startMs+Math.round((key.timeMs-clip.inMs)/(double)clip.speed):overlay.startMs+key.timeMs;
            if(time<0||time>a.project.durationMs())continue;
            if(next?time>now&&(best<0||time<best):time<now&&(best<0||time>best))best=time;
        }return best;
    }
    private void blendPanel(){
        LinearLayout navigation=a.row();
        for(boolean next:new boolean[]{false,true}){
            long target=opacityNeighbor(next);TextView button=a.button(next?"Next Key  ›":"‹  Previous Key",v->{long destination=opacityNeighbor(next);if(destination>=0){a.pausePlayback();a.seek(destination);a.renderInspector();}});
            if(next)nextOpacity=button;else previousOpacity=button;EditorActivity.dim(button,target>=0);navigation.addView(button,new LinearLayout.LayoutParams(0,a.dp(48),1));
        }
        a.inspector.addView(navigation);
        opacityPanel();EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(c!=null){a.inspector.addView(a.note("ICE Lite blend modes • Normal / Darken / Lighten / Contrast"));
            for(com.blackice.bc1.core.BlendMode mode:com.blackice.bc1.core.BlendMode.values()){a.gridAction((c.blend==mode.ordinal()?"● ":"")+mode.label,()->{if(a.layerEditable())a.edit("Blend mode updated",()->c.blend=mode.ordinal());});}a.flushGrid();
        }else{for(int i=0;i<EditProject.BLENDS.length;i++){final int mode=i;a.gridAction((o.blend==i?"● ":"")+EditProject.BLENDS[i],()->{if(a.layerEditable())a.edit("Blend mode updated",()->o.blend=mode);});}a.flushGrid();}
    }
    private void durationPanel(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        long start=c!=null?a.project.placements().get(a.selected).startMs:o.startMs,end=c!=null?a.project.placements().get(a.selected).endMs:o.endMs;
        a.inspector.addView(a.note(c!=null&&!c.image&&c.track<EditProject.AUDIO_FIRST||c!=null&&c.track>=EditProject.AUDIO_FIRST?"Video and audio are limited to the available source ("+EditorActivity.seconds(Math.round(c.sourceDurationMs/(double)c.speed))+" s at "+trimZero(c.speed)+"×).":"Pictures, text and shapes can extend to any project length."));
        timeField("Duration (seconds)",end-start,value->a.edit("Duration updated",()->{if(c!=null)TimelineEdits.duration(a.project,a.selected,value);else{if(value<EditProject.MIN_CLIP_MS)throw new IllegalArgumentException("Keep at least 0.10 seconds.");o.endMs=Math.addExact(o.startMs,value);}}));
        if(c==null||c.track>0)timeField("Start (seconds)",start,value->a.edit("Start updated",()->{if(c!=null)a.project.setPlacement(a.selected,c.track,value);else{long length=o.endMs-o.startMs;o.startMs=value;o.endMs=value+length;}}));
        else a.inspector.addView(a.note("Main-sequence clips start where the previous clip ends. Move this clip to a layer to set an exact start."));
        timeField("End (seconds)",end,value->a.edit("End updated",()->{if(c!=null)TimelineEdits.duration(a.project,a.selected,value-start);else{if(value-o.startMs<EditProject.MIN_CLIP_MS)throw new IllegalArgumentException("End must be at least 0.10 seconds after the start.");o.endMs=value;}}));
    }
    interface TimeAction{void apply(long ms);}
    void timeField(String title,long ms,TimeAction action){
        LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.addView(a.label(title,12,EditorActivity.TEXT,true),new LinearLayout.LayoutParams(0,-2,1));
        EditText field=new EditText(a);field.setText(EditorActivity.seconds(ms));field.setTextColor(EditorActivity.TEXT);field.setSingleLine(true);field.setSelectAllOnFocus(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);field.setContentDescription(title);field.setGravity(Gravity.END);
        row.addView(field,new LinearLayout.LayoutParams(a.dp(96),a.dp(48)));
        row.addView(a.button("Set",v->{if(!a.layerEditable())return;try{action.apply(EditorActivity.parseMs(field.getText().toString().trim()));}catch(NumberFormatException e){a.tell("Enter a time in seconds, for example 3.50.");}catch(IllegalArgumentException e){a.tell(e.getMessage());}}),new LinearLayout.LayoutParams(-2,a.dp(48)));
        a.inspector.addView(row);
    }
    private void trimPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        a.inspector.addView(a.note("Source length "+EditorActivity.seconds(c.sourceDurationMs)+" s • using "+EditorActivity.seconds(c.inMs)+"–"+EditorActivity.seconds(c.outMs)+" s. The imported file is never changed."));
        timeField("Source in (seconds)",c.inMs,value->a.edit("Trim applied",()->a.project.trim(a.selected,value,c.outMs)));
        timeField("Source out (seconds)",c.outMs,value->a.edit("Trim applied",()->a.project.trim(a.selected,c.inMs,value)));
        durationPanel();
    }
    private void slipPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        if(c.image){a.inspector.addView(a.note("Still pictures have no source segment to slip."));return;}
        a.inspector.addView(a.note("Slip shows a different part of the source while the clip keeps its timeline position and duration.\nAvailable: 0.00–"+EditorActivity.seconds(c.sourceDurationMs-(c.outMs-c.inMs))+" s"));
        timeField("Source in (seconds)",c.inMs,value->a.edit("Source segment slipped",()->TimelineEdits.slip(a.project,a.selected,value)));
        long frame=Math.max(1,Math.round(1000.0/a.project.frameRate));
        LinearLayout row=a.row();String[] names={"−1 s","−1 frame","+1 frame","+1 s"};long[] deltas={-1000,-frame,frame,1000};
        for(int i=0;i<4;i++){final long delta=deltas[i];TextView b=a.button(names[i],v->{if(a.layerEditable())a.edit("Source segment slipped",()->TimelineEdits.slip(a.project,a.selected,c.inMs+Math.round(delta*c.speed)));});b.setTextSize(11);row.addView(b,new LinearLayout.LayoutParams(0,a.dp(48),1));}
        a.inspector.addView(row);
    }
    private void speedPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        valueRow("Speed",c.speed,.25f,4,1,1,"×",v->TimelineEdits.speed(a.project,a.selected,Math.round(v*100)/100f),null,0,1);
        LinearLayout row=a.row();for(float s:new float[]{.5f,1,1.5f,2}){TextView b=a.button(trimZero(s)+"×",v->commit("Speed",s,x->TimelineEdits.speed(a.project,a.selected,x)));row.addView(b,new LinearLayout.LayoutParams(0,a.dp(48),1));}
        a.inspector.addView(row);
        a.inspector.addView(a.note("Speed changes timing explicitly, so the clip may extend past its earlier length. Audio and video stay synchronized."));
    }
    private void fadePanel(boolean in){
        EditProject.Clip c=a.clip();if(c==null)return;long max=c.durationMs();
        valueRow(in?"Fade in":"Fade out",(in?c.fadeInMs:c.fadeOutMs)/1000f,0,max/1000f,0,1,"s",v->{long ms=Math.round(v*1000);if(in)c.fadeInMs=ms;else c.fadeOutMs=ms;},null,0,1);
    }
    private void extractPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        a.inspector.addView(a.note("Creates an independent audio clip on a free audio layer, aligned with this video. It can be trimmed, moved and faded separately."));
        CheckBox mute=new CheckBox(a);mute.setText("Mute the original video's audio");mute.setTextColor(EditorActivity.TEXT);mute.setChecked(true);mute.setMinHeight(a.dp(48));a.inspector.addView(mute);
        a.inspector.addView(a.button("Extract audio",v->a.extractAudio(mute.isChecked())));
    }
    private void chromaPanel(){
        EditProject.Clip c=a.clip();if(c==null)return;
        CheckBox on=new CheckBox(a);on.setText("Remove key color");on.setTextColor(EditorActivity.TEXT);on.setChecked(c.chroma);on.setMinHeight(a.dp(48));
        on.setOnCheckedChangeListener((v,checked)->{if(a.layerEditable())a.edit("Chroma key updated",()->c.chroma=checked);});a.inspector.addView(on);
        swatches("Key color",c.keyColor,new int[]{0xFF00FF00,0xFF00B140,0xFF0047BB,0xFF0000FF},value->c.keyColor=value);
        valueRow("Tolerance",c.keyTolerance,0,1,.25f,100,"%",v->c.keyTolerance=v,null,0,1);
        valueRow("Edge softness",c.keySoftness,.001f,1,.08f,100,"%",v->c.keySoftness=Math.max(.001f,v),null,0,1);
        valueRow("Spill removal",c.keySpill,0,1,.5f,100,"%",v->c.keySpill=v,null,0,1);
    }
    interface ColorSetter{void set(int color);}
    void swatches(String title,int current,int[] colors,ColorSetter setter){
        a.inspector.addView(a.note(title+"  •  "+String.format(Locale.US,"#%06X",current&0xFFFFFF)));
        LinearLayout row=a.row();
        for(int color:colors){View chip=new View(a);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(color);bg.setCornerRadius(a.dp(6));bg.setStroke(a.dp(color==current?3:1),color==current?EditorActivity.CYAN:0xFF294357);chip.setBackground(bg);
            chip.setContentDescription(title+" "+String.format(Locale.US,"#%06X",color&0xFFFFFF));chip.setFocusable(true);chip.setOnClickListener(v->{if(a.layerEditable())a.edit(title+" updated",()->setter.set(color));});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(44),1);lp.rightMargin=a.dp(4);row.addView(chip,lp);}
        a.inspector.addView(row);
        LinearLayout hex=a.row();EditText field=new EditText(a);field.setText(String.format(Locale.US,"#%06X",current&0xFFFFFF));field.setTextColor(EditorActivity.TEXT);field.setSingleLine(true);field.setContentDescription(title+" hex value");
        hex.addView(field,new LinearLayout.LayoutParams(0,a.dp(48),1));
        hex.addView(a.button("Set",v->{if(!a.layerEditable())return;try{int color=Color.parseColor(field.getText().toString().trim());a.edit(title+" updated",()->setter.set(color|0xFF000000));}catch(IllegalArgumentException e){a.tell("Use a color such as #40BFFF.");}}),new LinearLayout.LayoutParams(-2,a.dp(48)));
        a.inspector.addView(hex);
    }
    private static final int[] PALETTE={0xFFFFFFFF,0xFF000000,0xFF40BFFF,0xFF9FEAFF,0xFFFF4D6D,0xFFFFC145,0xFF6BE675,0xFFB388FF};
    private void colorPanel(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(o!=null&&o.imageUri==null){swatches(o.shape>0?"Fill color":"Text color",o.color,PALETTE,value->o.color=value);return;}
        if(o!=null){a.inspector.addView(a.note("Image layers keep their own colors. Use Border & Shadow or Blending & Opacity."));return;}
        a.inspector.addView(a.note("Color adjustments for this "+(c.image?"picture":"video")+". One color effect is active at a time."));
        int[] ids={0,4,9,10,11};
        for(int id:ids)a.gridAction((c.effect==id?"● ":"")+com.blackice.bc1.core.EffectCatalog.name(id),()->{if(a.layerEditable())a.edit("Color updated",()->c.effect=id);});
        a.flushGrid();
        if(c.effect!=0)percent("Strength",c.intensity,.6f,v->c.intensity=v,c.motion,Motion.INTENSITY);
    }
    private void borderPanel(){
        EditProject.Overlay o=a.overlay();if(o==null){a.inspector.addView(a.note("Borders and shadows apply to text, shape and image layers."));return;}
        valueRow("Border width",o.borderWidth,0,1,0,100,"%",v->o.borderWidth=v,null,0,1);
        swatches("Border color",o.borderColor,PALETTE,value->o.borderColor=value);
        valueRow("Shadow",o.shadow,0,1,0,100,"%",v->o.shadow=v,null,0,1);
    }
    private void effectsPanel(){a.inspector.addView(a.note("Effects — Coming Later. Existing effect values are retained in saved projects but bypassed during preview and export."));}
    private void effectsHome(){
        a.inspector.addView(a.button("Styles",v->open("Styles",this::stylesPanel)));
        a.inspector.addView(a.button("Transitions",v->open("Transitions",this::transitionsPanel)));
        a.inspector.addView(a.button("Animation",v->open("Animation",()->{if(a.selectedMotion()==null)a.inspector.addView(a.note("Select a layer in the timeline to animate it."));else animationPanel();})));
    }
    private void stylesPanel(){
        if(a.clip()==null&&a.overlay()==null){a.inspector.addView(a.note("Select a layer to edit its style."));return;}
        if(a.overlay()!=null){a.inspector.addView(a.button("Color & Fill",v->category("Color & Fill")));a.inspector.addView(a.button("Border & Shadow",v->category("Border & Shadow")));}
        a.inspector.addView(a.button("Blending & Opacity",v->category("Blending & Opacity")));
        a.inspector.addView(a.button("Saved presets",v->category("Presets")));
        effectsPanel();
    }
    private void transitionsPanel(){
        EditProject.Clip c=a.clip();
        if(c==null||c.track!=0){a.inspector.addView(a.note("Select a main-sequence clip to set its transition into the next clip."));return;}
        EditProject.Clip next=null;for(int i=a.selected+1;i<a.project.clips.size();i++)if(a.project.clips.get(i).track==0){next=a.project.clips.get(i);break;}
        if(next==null){a.inspector.addView(a.note("Add another main-sequence clip after this one to use a transition."));return;}
        a.inspector.addView(a.note("Transition into "+next.name+". Timing follows the existing main-sequence overlap rules."));
        a.inspector.addView(a.button((c.transition<0?"● ":"")+"None",v->{if(a.layerEditable())a.edit("Transition removed",()->c.transition=-1);}));
        for(int i=0;i<EngineSettings.TRANSITIONS.length;i++){final int type=i;a.inspector.addView(a.button((c.transition==i?"● ":"")+EngineSettings.TRANSITIONS[i],v->{if(a.layerEditable())a.edit("Transition updated",()->c.transition=type);}));}
        valueRow("Transition duration",c.transitionMs/1000f,.1f,3,.5f,1,"s",v->c.transitionMs=Math.round(v*1000),null,0,1);
    }
    private void shapePanel(){
        EditProject.Overlay o=a.overlay();if(o==null||o.shape<=0)return;
        a.inspector.addView(a.button("Shape: "+EditProject.SHAPES[o.shape],v->{int[] ids=ShapeCatalog.ids();String[] names=new String[ids.length];for(int i=0;i<ids.length;i++)names[i]=EditProject.SHAPES[ids[i]];
            new AlertDialog.Builder(a).setTitle("Change shape").setItems(names,(d,index)->{if(a.layerEditable())a.edit("Shape changed",()->{o.shape=ids[index];o.text=EditProject.SHAPES[o.shape];});}).setNegativeButton("Cancel",null).show();}));
        a.inspector.addView(a.button("Move & Transform",v->category("Move & Transform")));
        a.inspector.addView(a.button("Color & Fill",v->category("Color & Fill")));
        a.inspector.addView(a.button("Border & Shadow",v->category("Border & Shadow")));
        a.inspector.addView(a.button("Duration",v->open("Duration",this::durationPanel)));
        a.inspector.addView(a.button("Animation",v->open("Animation",this::animationPanel)));
        a.inspector.addView(a.button("Reset natural proportions",v->{if(a.layerEditable())a.edit("Shape proportions reset",()->{o.aspect=1;o.heightScale=-1;o.skewX=o.skewY=0;o.motion.curves.get(Motion.HEIGHT).clear();o.motion.curves.get(Motion.SKEW_X).clear();o.motion.curves.get(Motion.SKEW_Y).clear();});}));
        a.inspector.addView(a.note("Shapes share a square-sized area and keep their natural proportions. Link dimensions in Move & Transform to resize uniformly."));
    }
    private void textPanel(){
        EditProject.Overlay o=a.overlay();if(o==null)return;
        EditText text=new EditText(a);text.setText(o.text);text.setTextColor(EditorActivity.TEXT);text.setMinLines(2);text.setContentDescription("Layer text");a.inspector.addView(text);
        a.inspector.addView(a.button("Apply text",v->{if(!a.layerEditable())return;String value=text.getText().toString();a.edit("Text updated",()->{if(value.isEmpty()||value.length()>200)throw new IllegalArgumentException("Use 1 to 200 characters.");o.text=value;});}));
    }
    private void fontPanel(){
        EditProject.Overlay o=a.overlay();if(o==null)return;String[] fonts={"sans-serif-medium","serif","monospace"},names={"Sans serif","Serif","Monospace"};
        for(int i=0;i<fonts.length;i++){final String f=fonts[i];a.gridAction((f.equals(o.font)?"● ":"")+names[i],()->{if(a.layerEditable())a.edit("Font updated",()->o.font=f);});}
        a.flushGrid();
    }
    private void alignmentPanel(){
        EditProject.Overlay o=a.overlay();if(o==null)return;String[] names={"Left","Center","Right"};
        for(int i=0;i<3;i++){final int al=i;a.gridAction((o.alignment==i?"● ":"")+names[i],()->{if(a.layerEditable())a.edit("Alignment updated",()->o.alignment=al);});}
        a.flushGrid();
    }
    private void presetsPanel(){
        EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        a.inspector.addView(a.note("Saved presets are available in every project."));
        a.inspector.addView(a.button("Save current effects as preset",v->a.savePreset(PresetStore.EFFECTS,Attributes.EFFECTS|Attributes.STYLE)));
        a.inspector.addView(a.button("Save animation as preset",v->a.savePreset(PresetStore.ANIMATION,Attributes.ANIMATION)));
        for(String kind:new String[]{PresetStore.EFFECTS,PresetStore.ANIMATION}) for(PresetStore.Preset preset:a.presets.list(kind)){
            LinearLayout row=a.row();
            row.addView(a.button((kind.equals(PresetStore.EFFECTS)?"Effects: ":"Animation: ")+preset.name,v->{if(!a.layerEditable())return;Attributes attributes=Attributes.decode(preset.payload);
                a.edit("Preset applied",()->{int applied=c!=null?attributes.applyTo(c,attributes.categories):attributes.applyTo(o,attributes.categories);if(applied==0)throw new IllegalArgumentException("This preset does not apply to the selected layer.");});}),new LinearLayout.LayoutParams(0,a.dp(52),1));
            row.addView(a.compactButton("✕","Delete preset "+preset.name,v->a.deletePreset(preset)),new LinearLayout.LayoutParams(a.dp(52),a.dp(52)));
            a.inspector.addView(row);
        }
    }
    private void browseHome(){
        android.widget.ImageView cube=new android.widget.ImageView(a);cube.setImageDrawable(new V1Icon("Cube",EditorActivity.CYAN));cube.setContentDescription("Nothing selected");
        LinearLayout.LayoutParams cubeLp=new LinearLayout.LayoutParams(a.dp(52),a.dp(52));cubeLp.gravity=android.view.Gravity.CENTER_HORIZONTAL;cubeLp.topMargin=a.dp(18);a.inspector.addView(cube,cubeLp);
        TextView title=a.label("Nothing selected",16,EditorActivity.TEXT,false);title.setGravity(android.view.Gravity.CENTER);title.setPadding(0,a.dp(10),0,a.dp(4));a.inspector.addView(title);
        TextView hint=a.label("Select a clip in the timeline\nor preview to edit it.",12,EditorActivity.MUTED,false);hint.setGravity(android.view.Gravity.CENTER);hint.setPadding(0,0,0,a.dp(18));a.inspector.addView(hint);
        a.menuRow("Add","Add media, audio, text or shape",v->{a.tab=0;a.renderInspector();});
        a.menuRow("Settings","Project settings",v->{a.tab=4;a.renderInspector();});
        a.menuRow("Layers","Layers & visibility",v->{a.tab=3;a.renderInspector();});
        a.menuRow("Loop",a.project.loopInMs>=0?"Clear loop range":"Loop range…",v->a.loopDialog());
        a.menuRow("Flag","Markers & beats",v->open("Markers & beats",this::markersPanel));
    }
    // ---- Animation ---------------------------------------------------------------------------------
    /** Timeline ms → this layer's key time, and back. */
    long toMotion(long timeline){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();if(c!=null){EditProject.Placement p=a.project.placements().get(a.selected);return Math.max(0,c.inMs+Math.round((timeline-p.startMs)*c.speed));}return Math.max(0,timeline-o.startMs);}
    long toTimeline(long motion){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();if(c!=null){EditProject.Placement p=a.project.placements().get(a.selected);return p.startMs+Math.round((motion-c.inMs)/(double)c.speed);}return o.startMs+motion;}
    void keyNavigation(){
        LinearLayout row=a.row();
        row.addView(a.button("◆‹  Previous keyframe",v->a.jumpKeyframe(false)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        row.addView(a.button("Next keyframe  ›◆",v->a.jumpKeyframe(true)),new LinearLayout.LayoutParams(0,a.dp(48),1));
        a.inspector.addView(row);
    }
    private void animationPanel(){
        Motion m=a.selectedMotion();if(m==null)return;EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();long t=motionTime(),tol=tolerance();
        a.inspector.addView(a.note("◇ grey: not animated (edits change the static value) • ◇ blue: animated between keyframes • ◆ keyframe at the playhead. Keyframes follow the layer's content when it is moved, trimmed, split or duplicated."));
        keyNavigation();
        List<String> here=new ArrayList<>();for(int p=0;p<Motion.COUNT;p++)if(Keyframes.keyAt(m,p,t,tol)!=null)here.add(Motion.PROPERTIES[p]);
        a.inspector.addView(a.label(here.isEmpty()?"No keyframe at the playhead":"Keyframes here: "+String.join(", ",here),12,EditorActivity.TEXT,true));
        if(!here.isEmpty()){
            a.inspector.addView(a.note("Easing from this keyframe to the next"));
            for(int i=0;i<Keyframes.EASINGS.length;i++){final int preset=i;a.gridAction(Keyframes.EASINGS[i],()->{if(a.layerEditable())a.edit("Easing updated",()->Keyframes.easeAll(m,t,tol,Keyframes.curve(preset)));});}
            a.gridAction("Custom curve…",()->open("Custom curve",this::curvePanel));a.flushGrid();
            timeField("Move keyframes to (timeline seconds)",a.project.playheadMs,value->a.edit("Keyframes moved",()->Keyframes.moveAll(m,t,toMotion(value),tol)));
            LinearLayout row=a.row();
            row.addView(a.button("Snap to nearest marker",v->{if(!a.layerEditable())return;ArrayList<Long> snapTimes=new ArrayList<>(a.project.markers);for(EditProject.Landmark landmark:a.project.landmarks)snapTimes.add(landmark.timeMs);if(snapTimes.isEmpty()){a.tell("Add a timeline marker first.");return;}long best=snapTimes.get(0);for(long mk:snapTimes)if(Math.abs(mk-a.project.playheadMs)<Math.abs(best-a.project.playheadMs))best=mk;long target=best;a.edit("Keyframes snapped to marker",()->Keyframes.moveAll(m,t,toMotion(target),tol));a.seek(target);}),new LinearLayout.LayoutParams(0,a.dp(48),1));
            row.addView(a.button("Delete keyframes here",v->{if(a.layerEditable())a.edit("Keyframes deleted",()->Keyframes.deleteAll(m,t,tol));}),new LinearLayout.LayoutParams(0,a.dp(48),1));
            a.inspector.addView(row);
        }
        LinearLayout clip=a.row();clip.addView(a.button("Copy keyframes",v->a.copyKeys()),new LinearLayout.LayoutParams(0,a.dp(48),1));clip.addView(a.button("Paste at playhead",v->a.pasteKeys()),new LinearLayout.LayoutParams(0,a.dp(48),1));a.inspector.addView(clip);
        a.inspector.addView(a.note("Properties"));
        float[] current=currentValues();
        int[] props=c!=null?new int[]{Motion.X,Motion.Y,Motion.SCALE,Motion.ROTATION,Motion.OPACITY,Motion.SKEW_X,Motion.SKEW_Y,Motion.ANCHOR_X,Motion.ANCHOR_Y,Motion.INTENSITY}:new int[]{Motion.X,Motion.Y,Motion.SCALE,Motion.ROTATION,Motion.OPACITY,Motion.SKEW_X,Motion.SKEW_Y,Motion.ANCHOR_X,Motion.ANCHOR_Y};
        for(int p:props){LinearLayout row=a.row();row.setGravity(Gravity.CENTER_VERTICAL);row.addView(diamond(Motion.PROPERTIES[p],m,p,current[p]),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
            int state=Keyframes.state(m,p,t,tol);row.addView(a.label(Motion.PROPERTIES[p]+"  ·  "+(state==Keyframes.STATIC?"not animated":m.curves.get(p).size()+" keyframes"),12,state==Keyframes.STATIC?EditorActivity.MUTED:EditorActivity.TEXT,false),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(a.label(format(m.value(p,t,current[p])),12,EditorActivity.TEXT,true));a.inspector.addView(row);}
        a.inspector.addView(a.note("Animation presets (use the layer's current values as the end state)"));
        long start=toMotion(c!=null?a.project.placements().get(a.selected).startMs:o.startMs),end=toMotion(c!=null?a.project.placements().get(a.selected).endMs:o.endMs);
        for(String preset:Keyframes.PRESETS)a.gridAction(preset,()->{if(a.layerEditable())a.edit(preset+" applied",()->Keyframes.preset(m,preset,start,end,currentValues()));});
        a.flushGrid();
        for(PresetStore.Preset saved:a.presets.list(PresetStore.ANIMATION))a.addAction("Saved: "+saved.name,()->{if(!a.layerEditable())return;Attributes attr=Attributes.decode(saved.payload);a.edit("Animation preset applied",()->{if(c!=null)attr.applyTo(c,Attributes.ANIMATION);else attr.applyTo(o,Attributes.ANIMATION);});});
        a.inspector.addView(a.button("Save this animation as a preset",v->a.savePreset(PresetStore.ANIMATION,Attributes.ANIMATION)));
    }
    float[] currentValues(){EditProject.Clip c=a.clip();EditProject.Overlay o=a.overlay();
        if(c!=null)return new float[]{c.x,c.y,c.scale,c.rotation,c.opacity,c.intensity,c.skewX,c.skewY,c.anchorX,c.anchorY,c.heightScale<0?c.scale*c.aspect:c.heightScale,c.volume};
        return new float[]{o.x,o.y,o.scale,o.rotation,o.opacity,0,o.skewX,o.skewY,o.anchorX,o.anchorY,o.heightScale<0?o.scale*o.aspect:o.heightScale,0};}
    private void curvePanel(){
        Motion m=a.selectedMotion();if(m==null)return;long t=motionTime(),tol=tolerance();Motion.Key first=null;for(int p=0;p<Motion.COUNT&&first==null;p++)first=Keyframes.keyAt(m,p,t,tol);
        if(first==null){a.inspector.addView(a.note("Move the playhead onto a keyframe to edit its curve."));return;}
        Motion.Key working=first.copy();
        a.inspector.addView(a.note("Drag the two handles. The curve shapes the interval from this keyframe to the next, for every property keyed here."));
        a.inspector.addView(a.new CurveView(working,()->{}),new LinearLayout.LayoutParams(-1,a.dp(200)));
        a.inspector.addView(a.button("Apply curve",v->{if(a.layerEditable())a.edit("Custom easing applied",()->Keyframes.easeAll(m,t,tol,new float[]{working.x1,working.y1,working.x2,working.y2}));}));
    }
    // ---- Markers ------------------------------------------------------------------------------------
    private void markersPanel(){
        a.inspector.addView(a.note("Up to five project landmarks. Beat-detection cues remain saved separately."));a.inspector.addView(a.button("Manage timeline markers",v->LandmarkControls.show(a)));
        LinearLayout navigation=a.row();navigation.addView(a.button("‹ Previous marker",v->a.jumpMarker(false)),new LinearLayout.LayoutParams(0,a.dp(48),1));navigation.addView(a.button("Next marker ›",v->a.jumpMarker(true)),new LinearLayout.LayoutParams(0,a.dp(48),1));a.inspector.addView(navigation);
        a.inspector.addView(a.button("Detect beats from audio…",v->a.detectBeats()));
        for(int i=0;i<a.project.markers.size();i++){final int index=i;timeField("Beat cue "+(i+1),a.project.markers.get(i),value->a.edit("Beat cue moved",()->{a.project.markers.remove(index);if(!a.project.markers.contains(value))a.project.markers.add(Math.max(0,value));Collections.sort(a.project.markers);}));}
        if(!a.project.markers.isEmpty())a.inspector.addView(a.button("Clear beat cues",v->{if(a.editable())new AlertDialog.Builder(a).setTitle("Clear beat cues?").setMessage("The five timeline landmark slots are preserved.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->a.edit("Beat cues cleared",()->a.project.markers.clear())).show();}));
    }

    private void multiTools(){
        a.inspector.addView(a.note(a.multiCount()+" layers selected. They move and transform together."));
        String[] names={"Align starts","Align ends","Match duration","Close gaps","Group","Ungroup","Duplicate","Delete"};
        for(String n:names)a.gridAction(n,()->a.multiAction(n));
        a.flushGrid();
    }
}

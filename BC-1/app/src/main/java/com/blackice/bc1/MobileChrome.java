package com.blackice.bc1;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import com.blackice.bc1.editor.*;

/** Phone presentation only. All edits, media access, preview and persistence stay in EditorActivity. */
final class MobileChrome {
    final EditorActivity a;
    FrameLayout stage;LinearLayout sheet,content,previewColumn;HorizontalScrollView nav;TextView add,undo,redo,duplicate,badge,clock,snap,handle;
    final java.util.List<TextView> navigation=new java.util.ArrayList<>();
    boolean dismissed,overlayInsert;int activeNav;int selection=Integer.MIN_VALUE;float sheetFraction=.48f;
    MobileChrome(EditorActivity activity){a=activity;}
    int dp(float n){return a.dp(n);}
    TextView control(String icon,String label,View.OnClickListener action){TextView b=a.transportIcon(icon,label,action);b.setMinWidth(dp(48));b.setMinHeight(dp(48));b.setPadding(dp(12),0,dp(12),0);return b;}
    void build(LinearLayout root){
        stage=new FrameLayout(a);root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
        content=a.column();content.setPadding(dp(8),0,dp(8),dp(68));stage.addView(content,new FrameLayout.LayoutParams(-1,-1));
        previewColumn=a.column();content.addView(previewColumn,new LinearLayout.LayoutParams(-1,-2));a.phonePreview=a.buildMonitor();previewColumn.addView(a.phonePreview,new LinearLayout.LayoutParams(-1,dp(220)));
        a.monitor.setClipToOutline(true);GradientDrawable frame=a.buttonBackground(false);frame.setCornerRadius(dp(12));a.monitor.setBackground(frame);
        // Actual preview resolution, never an AI or export-quality claim.
        badge=a.button("",v->quality());badge.setTextSize(11);badge.setContentDescription("Preview resolution");
        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(76),dp(48),Gravity.TOP|Gravity.END);bp.setMargins(0,dp(4),dp(4),0);a.monitor.addView(badge,bp);
        clock=a.label("",11,EditorActivity.TEXT,false);clock.setPadding(dp(10),0,0,0);clock.setGravity(Gravity.CENTER_VERTICAL);clock.setShadowLayer(dp(2),0,0,0xFF000000);a.monitor.addView(clock,new FrameLayout.LayoutParams(-2,dp(48),Gravity.BOTTOM|Gravity.START));
        a.monitor.addView(control("Fullscreen","Fullscreen preview",v->{a.fullscreenPreview=true;a.buildWorkspace();}),new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM|Gravity.END));
        a.phoneTransport=transport();previewColumn.addView(a.phoneTransport,new LinearLayout.LayoutParams(-1,dp(56)));
        a.phoneTimelineTools=new View(a);a.phoneTimelineTools.setVisibility(View.GONE);a.timelineTools=null;
        a.phoneTimelineArea=a.buildTimeline();content.addView(a.phoneTimelineArea,new LinearLayout.LayoutParams(-1,0,1));
        add=a.button("+",v->openAdd(3));add.setTextSize(32);add.setContentDescription("Add content");GradientDrawable circle=BlackIceTheme.primary(a.getResources().getDisplayMetrics().density);circle.setCornerRadius(dp(12));add.setTextColor(BlackIceTheme.ON_ACCENT);add.setBackground(circle);add.setElevation(dp(2));
        FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.BOTTOM|Gravity.END);ap.setMargins(0,0,dp(16),dp(82));stage.addView(add,ap);
        nav=new HorizontalScrollView(a);nav.setHorizontalScrollBarEnabled(false);nav.setFillViewport(true);LinearLayout tabs=a.row();nav.addView(tabs);String[] labels={"Edit","Audio","Text","Effects","Overlay","Captions"};String[] icons={"Edit","Audio","Text","Effects","Media","Captions"};
        for(int i=0;i<labels.length;i++){final int index=i;TextView b=a.iconButton(labels[i],v->navigate(index));b.setTextSize(11);b.setCompoundDrawables(null,a.icon(icons[i],EditorActivity.TEXT),null,null);b.setContentDescription(labels[i].equals("Captions")?"Add manual caption":labels[i]);navigation.add(b);tabs.addView(b,new LinearLayout.LayoutParams(dp(64),dp(68)));}
        stage.addView(nav,new FrameLayout.LayoutParams(-1,dp(68),Gravity.BOTTOM));
        sheet=a.column();sheet.setElevation(dp(12));GradientDrawable bg=new GradientDrawable();bg.setColor(BlackIceTheme.SECONDARY);bg.setStroke(dp(1),BlackIceTheme.BORDER);bg.setCornerRadii(new float[]{dp(24),dp(24),dp(24),dp(24),0,0,0,0});sheet.setBackground(bg);
        handle=a.label("━━",16,BlackIceTheme.ICE,true);handle.setGravity(Gravity.CENTER);handle.setContentDescription("Resize add and editing panel");handle.setFocusable(true);handle.setOnClickListener(v->{sheetFraction=sheetFraction<.55f?.64f:.48f;update();});sheet.addView(handle,new LinearLayout.LayoutParams(-1,dp(48)));
        handle.setOnTouchListener(new View.OnTouchListener(){float y,f;boolean moved;public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){y=e.getRawY();f=sheetFraction;moved=false;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){float dy=y-e.getRawY();moved|=Math.abs(dy)>dp(8);sheetFraction=Math.max(.32f,Math.min(.66f,f+dy/Math.max(1,stage.getHeight())));update();return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP){if(!moved)v.performClick();else if(e.getRawY()-y>dp(100)&&sheetFraction<=.34f)dismiss();return true;}return true;}});
        a.inspector=a.column();a.inspector.setPadding(dp(8),0,dp(8),dp(10));a.phoneDrawer=new ScrollView(a);a.phoneDrawer.setContentDescription("Phone editing drawer");a.phoneDrawer.setFillViewport(true);a.phoneDrawer.addView(a.inspector);sheet.addView(a.phoneDrawer,new LinearLayout.LayoutParams(-1,0,1));
        stage.addView(sheet,new FrameLayout.LayoutParams(-1,dp(300),Gravity.BOTTOM));sheet.setVisibility(View.GONE);
        stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(b-t!=ob-ot||r-l!=or-ol)update();});
    }
    View transport(){LinearLayout row=a.row();row.setGravity(Gravity.CENTER);HorizontalScrollView scroll=new HorizontalScrollView(a);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);scroll.addView(row);
        undo=control("Undo","Undo",v->a.undo(false));redo=control("Redo","Redo",v->a.undo(true));row.addView(undo);row.addView(redo);
        row.addView(control("SkipStart","Previous edit point",v->boundary(false)));
        a.play=control("Play","Play",v->a.togglePlay());GradientDrawable ring=new GradientDrawable();ring.setCornerRadius(dp(12));ring.setColor(EditorActivity.CYAN);a.play.setBackground(ring);row.addView(a.play,new LinearLayout.LayoutParams(dp(50),dp(50)));
        row.addView(control("SkipEnd","Next edit point",v->boundary(true)));duplicate=control("Duplicate","Duplicate",v->a.duplicateSelected());row.addView(duplicate);
        snap=control("Fit","Snapping",v->{a.snapping=!a.snapping;a.getSharedPreferences(EditorActivity.PREFS,0).edit().putBoolean("snapping",a.snapping).apply();sync();});row.addView(snap);return scroll;
    }
    void header(){a.header.removeAllViews();a.header.addView(control("Back","Back",v->back()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        ImageView logo=new ImageView(a);logo.setImageDrawable(new V1Icon("Cube",BlackIceTheme.ICE));logo.setContentDescription("Black Ice logo");a.header.addView(logo,new LinearLayout.LayoutParams(dp(30),dp(36)));
        LinearLayout brand=a.column();brand.setPadding(dp(6),0,dp(4),0);TextView name=a.label("BLACK ICE",17,EditorActivity.TEXT,true);name.setSingleLine(true);name.setAutoSizeTextTypeUniformWithConfiguration(10,17,1,android.util.TypedValue.COMPLEX_UNIT_SP);brand.addView(name);TextView sub=a.label("EDITOR",9,EditorActivity.MUTED,false);sub.setLetterSpacing(.25f);brand.addView(sub);
        a.projectLabel=a.label(a.project.name+(a.dirty?" •":""),11,EditorActivity.MUTED,false);a.projectLabel.setSingleLine(true);a.projectLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);brand.addView(a.projectLabel);brand.setFocusable(true);brand.setMinimumHeight(dp(48));brand.setGravity(Gravity.CENTER_VERTICAL);brand.setContentDescription("Rename project "+a.project.name);brand.setOnClickListener(v->a.numberDialog("Rename project",a.project.name,value->{if(value.isBlank()||value.length()>120)throw new IllegalArgumentException("Use 1–120 characters");a.edit("Project renamed",()->a.project.name=value);}));a.header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings=control("Setup","Settings",v->{dismissed=false;a.tab=4;a.renderInspector();});settings.setText("Settings");settings.setTextSize(10);settings.setPadding(0,0,0,0);settings.setCompoundDrawables(null,a.icon("Setup",EditorActivity.TEXT),null,null);a.header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        a.exportButton=a.button("Export",v->a.exportMenu());a.exportButton.setTextColor(BlackIceTheme.ON_ACCENT);a.exportButton.setTextSize(13);a.exportButton.setPadding(dp(8),0,dp(8),0);V1Icon exportIcon=new V1Icon("Export",BlackIceTheme.ON_ACCENT);exportIcon.setBounds(0,0,dp(18),dp(18));a.exportButton.setCompoundDrawables(exportIcon,null,null,null);a.exportButton.setCompoundDrawablePadding(dp(4));a.exportButton.setBackground(BlackIceTheme.primary(a.getResources().getDisplayMetrics().density));a.header.addView(a.exportButton,new LinearLayout.LayoutParams(dp(88),dp(48)));
    }
    void back(){if(a.fullscreenPreview){a.fullscreenPreview=false;a.buildWorkspace();return;}if(a.panels.showing()){a.panels.back();return;}if(a.tab==0||a.tab==4||a.tab==3){dismiss();return;}if(a.clip()!=null||a.overlay()!=null||a.multiCount()>0){a.deselect();return;}if(dismiss())return;saveAndProjects();}
    void saveAndProjects(){a.saveAndProjects();}
    void quality(){new AlertDialog.Builder(a).setTitle("Preview quality (export unchanged)").setSingleChoiceItems(new String[]{"360p","540p","1080p"},a.project.previewHeight==360?0:a.project.previewHeight==540?1:2,(d,i)->{if(a.editable())a.edit("Preview quality changed",()->a.project.previewHeight=new int[]{360,540,1080}[i]);d.dismiss();}).setNeutralButton("Framing / volume",(d,w)->a.previewOptions()).setNegativeButton("Back",null).show();}
    void openAdd(int category){overlayInsert=false;dismissed=false;a.addDrawer.category=category;a.tab=0;a.renderInspector();}
    void navigate(int index){activeNav=index;dismissed=false;a.panels.reset();switch(index){case 0:a.tab=1;break;case 1:openAdd(1);return;case 2:inputText(false);return;case 3:a.tab=2;break;case 4:openAdd(0);overlayInsert=true;return;case 5:inputText(true);return;default:a.tab=3;}a.renderInspector();}
    void inputText(boolean caption){a.numberDialog(caption?"Add manual caption":"Add text","",value->{if(value.isBlank())throw new IllegalArgumentException("Enter text");a.addOverlay(0,value,caption?.5f:1);});}
    void boundary(boolean next){long now=a.project.playheadMs,best=next?a.project.durationMs():0;for(EditProject.Placement p:a.project.placements())for(long t:new long[]{p.startMs,p.endMs})if(next?t>now&&t<best:t<now&&t>best)best=t;for(EditProject.Overlay o:a.project.overlays)for(long t:new long[]{o.startMs,o.endMs})if(next?t>now&&t<best:t<now&&t>best)best=t;for(long t:a.project.markers)if(next?t>now&&t<best:t<now&&t>best)best=t;a.seek(best);}
    boolean dismiss(){if(sheet.getVisibility()!=View.VISIBLE)return false;dismissed=true;a.tab=1;a.renderInspector();return true;}
    void inspectorTitle(LinearLayout into){if(a.tab==0||a.panels.footage())return;LinearLayout title=a.row();TextView label=a.label(a.tab==0?"ADD CONTENT":a.tab==4?"SETTINGS":"SELECTED LAYER",12,EditorActivity.CYAN,true);title.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));if(a.tab!=0&&(a.clip()!=null||a.overlay()!=null)){TextView compare=a.button("Compare",v->a.compare(!a.comparing));compare.setTextSize(11);compare.setContentDescription("Hold to compare before adjustments; activate to toggle with keyboard");compare.setTooltipText("Hold to compare before adjustments");compare.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)a.compare(true);else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)a.compare(false);return true;});title.addView(compare,new LinearLayout.LayoutParams(dp(72),dp(48)));}title.addView(control("Close","Close media browser and return to timeline",v->dismiss()),new LinearLayout.LayoutParams(dp(48),dp(48)));into.addView(title);}
    void update(){if(stage==null)return;if(a.tab==1)activeNav=0;else if(a.tab==2)activeNav=3;else if(a.tab==0)activeNav=overlayInsert?4:a.addDrawer.category==1?1:a.addDrawer.category==2?2:0;for(int i=0;i<navigation.size();i++){TextView b=navigation.get(i);boolean on=i==activeNav;b.setSelected(on);b.setTextColor(on?EditorActivity.CYAN:EditorActivity.TEXT);for(android.graphics.drawable.Drawable d:b.getCompoundDrawables())if(d!=null)d.setTint(on?EditorActivity.CYAN:EditorActivity.TEXT);b.setBackground(a.navBackground(on));}int selected=a.selected*1000+a.selectedOverlay;if(selection!=selected){selection=selected;dismissed=false;}
        boolean visible=!dismissed&&(a.tab!=1||a.clip()!=null||a.overlay()!=null||a.multiCount()>1);int h=stage.getHeight();if(h<=0)h=dp(a.getResources().getConfiguration().screenHeightDp-150);
        boolean landscape=stage.getWidth()>h;boolean footage=visible&&a.panels.footage();handle.setVisibility(footage?View.GONE:View.VISIBLE);
        int preview=Math.min(dp(a.workspaceFocus==2?320:a.workspaceFocus==1?130:250),Math.max(dp(100),(int)(h*(a.workspaceFocus==2?.50f:a.workspaceFocus==1?.23f:.40f))));int height=Math.min(Math.max(dp(210),(int)(h*sheetFraction)),Math.max(dp(160),h-dp(220)));
        if(footage&&!landscape){
            if(a.workspaceFocus==2)height=Math.max(dp(160),(int)(h*.32f));
            height=Math.min(height,Math.max(dp(160),h-dp(290)));
            preview=Math.min(preview,Math.max(dp(80),h-height-dp(190)));
            if(a.workspaceFocus==1)preview=Math.min(preview,dp(80));
        }
        int sheetWidth=landscape?Math.min(dp(320),stage.getWidth()/2):-1;
        content.setOrientation(landscape?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        content.setPadding(dp(8),0,landscape&&visible?sheetWidth:dp(8),landscape?(visible?0:dp(68)):footage?height:dp(68));
        if(landscape){height=h;preview=Math.max(dp(60),h-(visible?0:dp(68))-dp(56));previewColumn.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));a.phoneTimelineArea.setLayoutParams(new LinearLayout.LayoutParams(0,-1,1));}
        else{previewColumn.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));a.phoneTimelineArea.setLayoutParams(new LinearLayout.LayoutParams(-1,0,1));}
        FrameLayout.LayoutParams sheetLp=(FrameLayout.LayoutParams)sheet.getLayoutParams();sheetLp.width=sheetWidth;sheetLp.gravity=Gravity.BOTTOM|Gravity.END;
        nav.setVisibility(visible?View.GONE:View.VISIBLE);
        a.phonePreview.getLayoutParams().height=preview;a.phonePreview.requestLayout();
        if(sheet.getLayoutParams().height!=height){sheet.getLayoutParams().height=height;sheet.requestLayout();}
        if(visible&&sheet.getVisibility()!=View.VISIBLE){sheet.setVisibility(View.VISIBLE);sheet.setTranslationY(a.reducedMotion?0:dp(32));if(!a.reducedMotion&&android.animation.ValueAnimator.areAnimatorsEnabled())sheet.animate().translationY(0).setDuration(240).start();else sheet.setTranslationY(0);}else if(!visible){sheet.animate().cancel();sheet.setTranslationY(0);sheet.setVisibility(View.GONE);}
        add.setVisibility(visible?View.GONE:View.VISIBLE);if((a.project.clips.isEmpty()&&a.project.overlays.isEmpty())){android.text.SpannableString hint=new android.text.SpannableString("Tap + to add media.");hint.setSpan(new android.text.style.ForegroundColorSpan(EditorActivity.CYAN),4,5,0);a.emptyPreview.setTextColor(EditorActivity.MUTED);a.emptyPreview.setText(hint);}sync();
    }
    void sync(){if(snap!=null){snap.setSelected(a.snapping);snap.setContentDescription("Snapping: "+(a.snapping?"on":"off"));snap.setTooltipText("Snap to clip edges, markers and playhead");snap.setCompoundDrawables(a.icon("Fit",a.snapping?EditorActivity.CYAN:EditorActivity.MUTED),null,null,null);}if(a.play!=null)EditorActivity.dim(a.play,a.project.durationMs()>0);if(clock!=null)clock.setText(EditorActivity.clock(a.project.playheadMs)+" / "+EditorActivity.clock(a.project.durationMs()));if(badge!=null)badge.setText(a.project.previewHeight+"P ▾");if(undo!=null){EditorActivity.dim(undo,!a.undo.isEmpty());EditorActivity.dim(redo,!a.redo.isEmpty());EditorActivity.dim(duplicate,a.clip()!=null||a.overlay()!=null||a.multiCount()>1);}}
}

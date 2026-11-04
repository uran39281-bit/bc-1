package com.blackice.bc1;

import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.*;

/** Native portrait presentation. Wide/DeX and phone-landscape continue through MobileChrome. */
final class PortraitChrome {
    final MobileChrome m;final EditorActivity a;final PhoneWorkspaceState state;
    int rowHeight;private LinearLayout transport;private ListView mediaList;private MediaRows adapter;
    private final ArrayList<PhoneActionView> filters=new ArrayList<>();
    private boolean selectedTransport,updating;
    PortraitChrome(MobileChrome owner){m=owner;a=m.a;state=a.phoneState;rowHeight=a.dp(46);m.dismissed=state.dismissed;}
    int dp(float n){return a.dp(n);}
    void build(LinearLayout root){
        m.stage=new FrameLayout(a);root.addView(m.stage,new LinearLayout.LayoutParams(-1,0,1));
        m.content=a.column();m.content.setPadding(dp(8),dp(4),dp(8),0);m.stage.addView(m.content,new FrameLayout.LayoutParams(-1,-1));
        m.previewColumn=a.column();m.content.addView(m.previewColumn,new LinearLayout.LayoutParams(-1,-2));
        a.phonePreview=a.buildMonitor();m.previewColumn.addView(a.phonePreview,new LinearLayout.LayoutParams(-1,dp(223)));
        a.monitor.setClipToOutline(true);GradientDrawable frame=a.buttonBackground(false);frame.setColor(0xFF030A12);frame.setCornerRadius(dp(16));a.monitor.setBackground(frame);
        // The phone reference has an uncluttered player; its options remain in the project menu.
        a.monitorInfo.setVisibility(View.GONE);a.emptyPreview.setTextSize(12);a.emptyPreview.setTextColor(EditorActivity.MUTED);
        m.clock=a.label("",12,EditorActivity.TEXT,false);m.clock.setPadding(dp(8),0,dp(4),0);m.clock.setGravity(Gravity.CENTER_VERTICAL);a.monitor.addView(m.clock,new FrameLayout.LayoutParams(-2,dp(44),Gravity.BOTTOM|Gravity.START));
        TextView fullscreen=m.control("Fullscreen","Fullscreen preview",v->{a.fullscreenPreview=true;a.buildWorkspace();});fullscreen.setBackground(null);fullscreen.setPadding(dp(10),0,dp(10),0);a.monitor.addView(fullscreen,new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.BOTTOM|Gravity.END));
        a.phoneTransport=makeTransport(false);m.previewColumn.addView(a.phoneTransport,new LinearLayout.LayoutParams(-1,dp(54)));
        // No idle editing toolbar. Commands remain in the project/context menus.
        a.phoneTimelineTools=a.buildTimelineTools();a.phoneTimelineTools.setVisibility(View.GONE);
        a.phoneTimelineArea=a.buildTimeline();a.phoneTimelineArea.setBackgroundColor(0xFF07111B);a.phoneTimelineArea.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(t!=ot||b-t!=ob-ot)m.stage.post(this::update);});m.content.addView(a.phoneTimelineArea,new LinearLayout.LayoutParams(-1,dp(306)));
        if(a.phoneTimelineArea instanceof ScrollView){((ScrollView)a.phoneTimelineArea).setVerticalScrollBarEnabled(false);ScrollView tracks=(ScrollView)a.phoneTimelineArea;int restoreY=state.timelineScroll;tracks.post(()->{if(a.mobile==m&&a.phoneTimelineArea==tracks)tracks.scrollTo(0,restoreY);});}
        m.add=a.button("+",v->openAdd(0));m.add.setTextSize(32);m.add.setContentDescription("Add content");m.add.setTextColor(BlackIceTheme.ON_ACCENT);m.add.setBackground(BlackIceTheme.primary(a.getResources().getDisplayMetrics().density));m.add.setElevation(dp(2));
        m.stage.addView(m.add,new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.BOTTOM|Gravity.END));
        m.sheet=a.column();m.sheet.setElevation(dp(12));m.sheet.setClickable(true);GradientDrawable bg=a.buttonBackground(false);bg.setColor(BlackIceTheme.SECONDARY);bg.setCornerRadii(new float[]{dp(24),dp(24),dp(24),dp(24),0,0,0,0});m.sheet.setBackground(bg);
        m.handle=a.label("",10,BlackIceTheme.ICE,true);m.handle.setContentDescription("Dismiss editing drawer");m.handle.setFocusable(true);m.handle.setOnClickListener(v->dismiss());
        FrameLayout grip=new FrameLayout(a){@Override public boolean onInterceptTouchEvent(MotionEvent e){return true;}};grip.addView(m.handle,new FrameLayout.LayoutParams(-1,-1));View line=new View(a);GradientDrawable pill=new GradientDrawable();pill.setCornerRadius(dp(2));pill.setColor(BlackIceTheme.ICE);line.setBackground(pill);grip.addView(line,new FrameLayout.LayoutParams(dp(40),dp(3),Gravity.CENTER));m.sheet.addView(grip,new LinearLayout.LayoutParams(-1,dp(24)));
        grip.setOnTouchListener(new View.OnTouchListener(){float y;public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){y=e.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP){if(e.getY()-y>dp(24))dismiss();else v.performClick();return true;}return true;}});grip.setOnClickListener(v->dismiss());grip.setContentDescription("Dismiss editing drawer");grip.setFocusable(true);
        m.catalogHost=new FrameLayout(a);m.catalogHost.setVisibility(View.GONE);m.sheet.addView(m.catalogHost,new LinearLayout.LayoutParams(-1,0,1));
        a.inspector=a.column();a.inspector.setPadding(dp(8),0,dp(8),dp(6));a.phoneDrawer=new ScrollView(a);a.phoneDrawer.setFillViewport(true);a.phoneDrawer.setContentDescription("Phone editing drawer");a.phoneDrawer.addView(a.inspector);m.sheet.addView(a.phoneDrawer,new LinearLayout.LayoutParams(-1,0,1));
        m.stage.addView(m.sheet,new FrameLayout.LayoutParams(-1,dp(260),Gravity.BOTTOM));m.sheet.setVisibility(View.GONE);
        // Kept as nonvisible compatibility references, never mounted as a second navigation surface.
        m.nav=new HorizontalScrollView(a);m.nav.setVisibility(View.GONE);
        m.stage.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol||b-t!=ob-ot)update();});
    }
    private View makeTransport(boolean selected){
        transport=a.row();transport.setGravity(Gravity.CENTER);transport.setPadding(0,selected?0:dp(4),0,0);int gap=dp(selected?4:8);
        a.jumpStart=a.jumpEnd=null;if(selected)a.jumpStart=addTransport("SkipStart","Jump to beginning of selected clip",()->a.jump(false),gap);
        addTransport(selected?"StepBack":"SkipStart",selected?"Previous frame":"Previous edit point",()->{if(selected)a.seek(Math.max(0,a.project.playheadMs-Math.round(1000d/a.project.frameRate)));else m.boundary(false);},gap);
        a.play=m.control("Play","Play",v->a.togglePlay());a.play.setMinHeight(0);a.play.setMinimumHeight(0);a.play.setMinWidth(0);a.play.setMinimumWidth(0);GradientDrawable playBg=new GradientDrawable();playBg.setCornerRadius(dp(selected?24:12));playBg.setColor(selected?0xFF0B1721:EditorActivity.CYAN);if(selected)playBg.setStroke(dp(2),EditorActivity.CYAN);a.play.setBackground(playBg);transport.addView(a.play,new LinearLayout.LayoutParams(dp(selected?44:50),dp(selected?44:50)));
        addTransport(selected?"StepForward":"SkipEnd",selected?"Next frame":"Next edit point",()->{if(selected)a.seek(a.project.playheadMs+Math.round(1000d/a.project.frameRate));else m.boundary(true);},gap);
        if(selected)a.jumpEnd=addTransport("SkipEnd","Jump to end of selected clip",()->a.jump(true),gap);
        a.play.setTag(null);a.syncPlayButton();if(selected)a.play.setCompoundDrawables(a.icon(a.player!=null&&a.player.isPlaying()?"Pause":"Play",EditorActivity.TEXT),null,null,null);
        return transport;
    }
    private TextView addTransport(String icon,String label,Runnable run,int gap){TextView b=m.control(icon,label,v->run.run());b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setBackground(null);b.setPadding(dp(10),0,dp(10),0);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(44),dp(48));p.setMargins(gap/2,0,gap/2,0);transport.addView(b,p);return b;}
    void header(){
        a.header.removeAllViews();a.header.setOrientation(LinearLayout.HORIZONTAL);a.header.setPadding(0,0,dp(8),0);a.header.getLayoutParams().height=dp(Math.max(48,44*a.getResources().getConfiguration().fontScale));
        TextView back=m.control("Back","Back to Projects",v->m.saveAndProjects());back.setBackground(null);back.setPadding(dp(10),0,dp(10),0);a.header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(48)));
        a.projectLabel=a.label(a.project.name,12,EditorActivity.TEXT,true);a.projectLabel.setSingleLine(true);a.projectLabel.setEllipsize(TextUtils.TruncateAt.END);a.projectLabel.setGravity(Gravity.CENTER_VERTICAL);a.projectLabel.setContentDescription("Project menu: "+a.project.name);a.projectLabel.setFocusable(true);a.projectLabel.setTooltipText(a.project.name+" • project menu");a.projectLabel.setOnClickListener(v->projectMenu());a.projectLabel.setOnLongClickListener(v->{projectMenu();return true;});a.header.addView(a.projectLabel,new LinearLayout.LayoutParams(0,-1,1));
        TextView clock=a.workClockView();clock.setTextSize(11);clock.setPadding(dp(3),0,dp(3),0);clock.setBackground(null);a.header.addView(clock,new LinearLayout.LayoutParams(Math.max(dp(89),(int)Math.ceil(clock.getPaint().measureText("◷ 00:00:00"))+dp(6)),dp(48)));
        TextView save=a.label((a.projectSession!=null&&a.projectSession.saveFailure!=null)?"Retry":"Save",11,EditorActivity.TEXT,true);save.setGravity(Gravity.CENTER);GradientDrawable saveOutline=a.buttonBackground(false);saveOutline.setStroke(dp(1),0xFFAABDCB);save.setBackground(saveOutline);save.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);save.setContentDescription((a.projectSession!=null&&a.projectSession.saveFailure!=null)?"Retry saving project":"Save project");save.setOnClickListener(v->a.saveNow());FrameLayout saveHit=new FrameLayout(a){@Override public boolean onInterceptTouchEvent(MotionEvent e){return true;}};saveHit.setForeground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x337CD5F3),null,null));saveHit.setContentDescription(save.getContentDescription()+". "+(a.projectSession==null?"Loading project":a.projectSession.status()));saveHit.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);saveHit.setFocusable(true);saveHit.setOnClickListener(v->a.saveNow());saveHit.addView(save,new FrameLayout.LayoutParams(-1,dp(28),Gravity.CENTER));a.header.addView(saveHit,new LinearLayout.LayoutParams(dp(64),dp(48)));
        a.exportButton=a.button("Export",v->a.exportMenu());a.exportButton.setTextSize(12);a.exportButton.setFocusable(false);a.exportButton.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);a.exportButton.setPadding(dp(6),0,dp(6),0);a.exportButton.setMinWidth(0);a.exportButton.setMinimumWidth(0);a.exportButton.setMinHeight(0);a.exportButton.setMinimumHeight(0);a.exportButton.setTextColor(BlackIceTheme.ON_ACCENT);a.exportButton.setBackground(BlackIceTheme.primary(a.getResources().getDisplayMetrics().density));FrameLayout exportHit=new FrameLayout(a){@Override public boolean onInterceptTouchEvent(MotionEvent e){return true;}};exportHit.setForeground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x337CD5F3),null,null));exportHit.setFocusable(true);exportHit.setContentDescription("Export video");exportHit.setOnClickListener(v->a.exportMenu());exportHit.addView(a.exportButton,new FrameLayout.LayoutParams(-1,dp(30),Gravity.CENTER));LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(Math.max(dp(62),(int)Math.ceil(a.exportButton.getPaint().measureText("Export"))+dp(12)),dp(48));ep.leftMargin=dp(6);a.header.addView(exportHit,ep);
    }
    void projectMenu(){String[] commands={"Settings","Project Settings","Preview options","Undo","Redo","Add text","Add manual caption","Saved elements","Layer management","Effects","Snapping: "+(a.snapping?"on":"off"),"Selected layer actions","Media search and sorting","Fit timeline","Timeline markers"};new AlertDialog.Builder(a).setTitle(a.project.name).setItems(commands,(d,i)->{m.dismissed=false;switch(i){case 0:a.tab=4;break;case 1:a.projectSettingsDialog();return;case 2:m.quality();return;case 3:a.undo(false);return;case 4:a.undo(true);return;case 5:m.inputText(false);return;case 6:m.inputText(true);return;case 7:a.tab=0;a.addDrawer.category=4;break;case 8:a.tab=3;break;case 9:a.tab=2;break;case 10:a.snapping=!a.snapping;a.getSharedPreferences(EditorActivity.PREFS,0).edit().putBoolean("snapping",a.snapping).apply();return;case 11:if(a.clip()!=null||a.overlay()!=null||a.multiCount()>1)a.moreMenu();else a.tell("Select a layer on the timeline first.");return;case 12:openAdd(0);libraryOptions();return;case 13:a.fitTimeline();return;case 14:LandmarkControls.show(a);return;}a.renderInspector();}).setNegativeButton("Back",null).show();}
    void capture(){state.dismissed=m.dismissed;if(mediaList!=null&&adapter!=null&&!a.loading&&adapter.projectId.equals(a.project.id)&&mediaList.getChildCount()>0){state.mediaOwner=a.project.id;state.mediaViewState=mediaList.onSaveInstanceState();state.firstMedia=mediaList.getFirstVisiblePosition();state.mediaOffset=mediaList.getChildCount()==0?0:mediaList.getChildAt(0).getTop();}if(a.phoneDrawer!=null&&!state.restoreDrawerScroll){state.drawerScroll=a.phoneDrawer.getScrollY();state.drawerOwner=a.inspectorOwner();}if(a.phoneTimelineArea instanceof ScrollView)state.timelineScroll=((ScrollView)a.phoneTimelineArea).getScrollY();}
    void openAdd(int category){capture();m.dismissed=false;m.overlayInsert=false;a.panels.reset();a.addDrawer.category=category;if(category==3){a.mediaKind=MediaFilter.Kind.SHAPES;a.favoritesOnly=false;}else if(category==1){a.mediaKind=MediaFilter.Kind.AUDIO;a.favoritesOnly=false;}a.tab=0;state.drawer=PhoneWorkspaceState.Drawer.ADD_MEDIA;a.renderInspector();}
    boolean dismiss(){if(m.sheet.getVisibility()!=View.VISIBLE)return false;capture();m.dismissed=true;state.dismissed=true;state.drawer=PhoneWorkspaceState.Drawer.BASE;a.tab=1;a.renderInspector();return true;}
    void back(){if(a.effectCatalog.showing){a.effectCatalog.back();return;}if(a.panels.showing()){a.panels.back();return;}if(dismiss())return;if(a.clip()!=null||a.overlay()!=null||a.multiCount()>1){a.deselect();return;}m.saveAndProjects();}
    void update(){if(updating||m.stage==null||a.mobile!=m||a.isDestroyed())return;updating=true;try{
        String selection=a.selected+":"+a.selectedOverlay+":"+a.multiCount();if(!selection.equals(state.selection)){state.selection=selection;m.dismissed=false;}
        boolean selected=a.clip()!=null||a.overlay()!=null||a.multiCount()>1;
        boolean visible=!m.dismissed&&(a.tab!=1||selected||a.effectCatalog.showing);
        state.drawer=!visible?PhoneWorkspaceState.Drawer.BASE:a.tab==0?PhoneWorkspaceState.Drawer.ADD_MEDIA:PhoneWorkspaceState.Drawer.SELECTED_LAYER;state.dismissed=m.dismissed;
        boolean layerTransport=selected&&state.drawer==PhoneWorkspaceState.Drawer.SELECTED_LAYER;
        if(selectedTransport!=layerTransport){selectedTransport=layerTransport;m.previewColumn.removeView(a.phoneTransport);a.phoneTransport=makeTransport(layerTransport);m.previewColumn.addView(a.phoneTransport,new LinearLayout.LayoutParams(-1,dp(54)));}
        int w=m.stage.getWidth(),h=m.stage.getHeight();if(w<=0||h<=0)return;
        // Six complete native lanes; reserve footer space independently of which drawer is showing.
        int preview=Math.round((w-dp(16))*.65f),footer=dp(80),fixed=dp(4+54+30)+6*rowHeight+footer;
        preview=Math.max(dp(90),Math.min(preview,h-fixed));int balancedPreview=preview;
        // Portrait-specific choices start Balanced; legacy wide/landscape space stays untouched.
        // Opening/closing a drawer never changes these sizes.
        if(a.portraitWorkspaceFocus==1)preview=Math.max(dp(90),Math.round(balancedPreview*.6f));
        else if(a.portraitWorkspaceFocus==2)preview=Math.max(dp(90),Math.min(Math.round(balancedPreview*1.25f),h-fixed));
        if(a.phonePreview.getLayoutParams().height!=preview){a.phonePreview.getLayoutParams().height=preview;a.phonePreview.requestLayout();}
        int timelineHeight=dp(30)+6*rowHeight+(a.portraitWorkspaceFocus==1?balancedPreview-preview:0);
        // Short portrait windows keep the same genuine lanes inside a smaller scroll viewport.
        // Measuring an oversized ScrollView would clip tracks that it incorrectly considers visible.
        timelineHeight=Math.min(timelineHeight,Math.max(dp(44),h-dp(4)-preview-dp(54)-footer));
        if(a.phoneTimelineArea.getLayoutParams().height!=timelineHeight){a.phoneTimelineArea.getLayoutParams().height=timelineHeight;a.phoneTimelineArea.requestLayout();}
        int bottom=Math.max(dp(8),h-(dp(4)+preview+dp(54)+timelineHeight)-dp(70));FrameLayout.LayoutParams ap=(FrameLayout.LayoutParams)m.add.getLayoutParams();ap.rightMargin=dp(16);ap.bottomMargin=bottom;m.add.setLayoutParams(ap);
        // Add starts at the actual third-row top. Selected tools are measured independently.
        int timelineTop=dp(4)+preview+dp(54),scroll=a.phoneTimelineArea instanceof ScrollView?((ScrollView)a.phoneTimelineArea).getScrollY():0;
        int addTop=timelineTop+Math.round(a.timeline.rows().get(2).top)-scroll;
        if(a.timeline.getHeight()>0&&a.phoneTimelineArea.getHeight()>0){int[] timeOrigin=new int[2],stageOrigin=new int[2];a.timeline.getLocationOnScreen(timeOrigin);m.stage.getLocationOnScreen(stageOrigin);addTop=timeOrigin[1]-stageOrigin[1]+Math.round(a.timeline.rows().get(2).top);}
        int selectedHeight=dp(24+44+6+3*44+44+6);int selectedTop=Math.max(addTop,Math.min(addTop+dp(20),h-selectedHeight));
        int top=a.tab==0?addTop:selectedTop;
        // At small heights the drawer scrolls below the player/transport/ruler instead of a
        // minimum sheet height pushing its top over those controls. Reference-sized windows
        // retain the measured row-three anchor and independent selected-layer height.
        int minimumTop=Math.min(h,timelineTop+dp(30)),minimumSheet=Math.min(dp(180),h-minimumTop);
        top=Math.max(minimumTop,Math.min(top,h-minimumSheet));
        FrameLayout.LayoutParams sp=(FrameLayout.LayoutParams)m.sheet.getLayoutParams();sp.height=Math.max(1,h-top);sp.gravity=Gravity.BOTTOM;m.sheet.setLayoutParams(sp);
        m.sheet.setVisibility(visible?View.VISIBLE:View.GONE);m.add.setVisibility(visible?View.GONE:View.VISIBLE);
        boolean catalog=a.effectCatalog.showing;m.catalogHost.setVisibility(catalog?View.VISIBLE:View.GONE);a.phoneDrawer.setVisibility(catalog?View.GONE:View.VISIBLE);
        if(a.project.clips.isEmpty()&&a.project.overlays.isEmpty()){android.text.SpannableString hint=new android.text.SpannableString("Tap + to add media.");hint.setSpan(new android.text.style.ForegroundColorSpan(EditorActivity.CYAN),4,5,0);a.emptyPreview.setText(hint);}sync();
    }finally{updating=false;}}
    void sync(){a.panels.syncPhoneControls();if(m.clock!=null)m.clock.setText(EditorActivity.clock(a.project.playheadMs)+" / "+EditorActivity.clock(a.project.durationMs()));if(a.play!=null){EditorActivity.dim(a.play,a.project.durationMs()>0);if(selectedTransport){boolean playing=a.player!=null&&a.player.isPlaying();a.play.setCompoundDrawables(a.icon(playing?"Pause":"Play",EditorActivity.TEXT),null,null,null);}}}
    void render(LinearLayout into){mediaList=null;adapter=null;filters.clear();
        if(a.tab==0&&(a.addDrawer.category==0||a.addDrawer.category==1||a.addDrawer.category==3)){library(into);return;}
        if(a.tab==0){a.addDrawer.render(into);return;}
        if(a.tab==1||a.tab==2)a.panels.render(into);else if(a.tab==3)a.layersPanel();else a.settingsPanel();
    }
    private void library(LinearLayout into){
        a.binGrid=null;PhoneActionView imp=new PhoneActionView(a,"Import Media",null,false,34,a::openMediaBrowser);android.text.SpannableString importTitle=new android.text.SpannableString("  Import Media");android.graphics.drawable.Drawable importIcon=a.icon("Add",BlackIceTheme.ICE);importIcon.setBounds(0,0,dp(18),dp(18));importTitle.setSpan(new android.text.style.ImageSpan(importIcon,android.text.style.ImageSpan.ALIGN_BOTTOM),0,1,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);imp.text.setText(importTitle);into.addView(imp,new LinearLayout.LayoutParams(-1,PhoneActionView.touchHeight(a)));
        String[] names={"All","Video","Audio","Image","Shapes","Favorites"};String[] icons={"Grid","Video","Audio","Image","Shapes","Star"};
        for(int r=0;r<2;r++){LinearLayout row=a.row();into.addView(row,new LinearLayout.LayoutParams(-1,PhoneActionView.touchHeight(a)));for(int c=0;c<3;c++){int index=r*3+c;PhoneActionView f=new PhoneActionView(a,names[index],icons[index],false,36,()->{capture();state.mediaViewState=null;state.firstMedia=state.mediaOffset=0;if(index==5){a.favoritesOnly=true;a.mediaKind=MediaFilter.Kind.ALL;}else{a.favoritesOnly=false;a.mediaKind=MediaFilter.Kind.values()[index];}refreshLibrary(true);});f.setContentDescription("Filter "+names[index]);filters.add(f);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.setMargins(c==0?0:dp(3),0,c==2?0:dp(3),0);row.addView(f,p);}}
        FrameLayout frame=new FrameLayout(a);frame.setMinimumHeight(dp(104));frame.setPadding(dp(6),dp(6),dp(6),dp(4));frame.setBackground(a.buttonBackground(false));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,0,1);fp.topMargin=dp(8);into.addView(frame,fp);
        mediaList=new ListView(a);mediaList.setDivider(null);mediaList.setDividerHeight(0);mediaList.setClipToPadding(false);mediaList.setContentDescription("Project media library");mediaList.setItemsCanFocus(true);adapter=new MediaRows();mediaList.setAdapter(adapter);frame.addView(mediaList,new FrameLayout.LayoutParams(-1,-1));mediaList.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int s){}public void onScroll(AbsListView v,int f,int count,int total){if(v!=mediaList)return;state.firstMedia=f;state.mediaOffset=v.getChildCount()==0?0:v.getChildAt(0).getTop();}});
        refreshLibrary();if(state.mediaViewState!=null&&state.mediaOwner.equals(a.project.id))mediaList.onRestoreInstanceState(state.mediaViewState);
        // Search/sort/view commands stay accessible without repeating a permanent toolbar in the tray.
        imp.setOnLongClickListener(v->{libraryOptions();return true;});imp.setTooltipText("Import media. Long press for search and sorting.");
    }
    private void libraryOptions(){new AlertDialog.Builder(a).setTitle("Project media").setItems(new String[]{"Search media","Clear search","Import order","Name","Duration","Saved elements","Add text"},(d,i)->{if(i==0)a.numberDialog("Search media",a.mediaQuery,v->{a.mediaQuery=v;state.firstMedia=0;refreshLibrary();});else if(i==1){a.mediaQuery="";refreshLibrary();}else if(i<5){a.mediaSort=i-2;refreshLibrary();}else if(i==5){a.addDrawer.category=4;a.renderInspector();}else m.inputText(false);}).show();}
    void refreshLibrary(){refreshLibrary(false);}
    void refreshLibrary(boolean reset){if(adapter==null)return;adapter.load();if(reset){state.mediaViewState=null;state.firstMedia=state.mediaOffset=0;mediaList.setSelectionFromTop(0,0);}for(int i=0;i<filters.size();i++)filters.get(i).active(a,i==5?a.favoritesOnly:!a.favoritesOnly&&a.mediaKind.ordinal()==i);}
    void refreshThumbnails(){if(mediaList==null)return;for(int i=0;i<mediaList.getChildCount();i++)refreshImages(mediaList.getChildAt(i));}
    private void refreshImages(View v){if(v instanceof ImageView&&v.getTag() instanceof EditProject.Clip){EditProject.Clip c=(EditProject.Clip)v.getTag();if(!c.audioOnly){Bitmap b=a.thumbnails.peek(c);if(b!=null)((ImageView)v).setImageBitmap(b);}}if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)refreshImages(((ViewGroup)v).getChildAt(i));}
    /** Only visible rows are bound; thumbnail decoding stays in the existing asynchronous cache. */
    final class MediaRows extends BaseAdapter {
        final ArrayList<Object> items=new ArrayList<>();int columns=3;String projectId="";
        void load(){String previousProject=projectId;projectId=a.project.id;android.os.Parcelable scroll=mediaList.getChildCount()>0&&previousProject.equals(projectId)?mediaList.onSaveInstanceState():state.mediaOwner.equals(projectId)?state.mediaViewState:null;items.clear();columns=a.getResources().getConfiguration().fontScale>1.3f?2:3;
            if(a.mediaKind==MediaFilter.Kind.SHAPES){for(int id:ShapeCatalog.ids())if(EditProject.SHAPES[id].toLowerCase(Locale.ROOT).contains(a.mediaQuery.toLowerCase(Locale.ROOT)))items.add(id);if(a.mediaSort==1)items.sort((x,y)->EditProject.SHAPES[(Integer)x].compareToIgnoreCase(EditProject.SHAPES[(Integer)y]));}
            else{ArrayList<EditProject.Clip> matches=new ArrayList<>();for(EditProject.Clip c:a.project.library)if((!a.favoritesOnly||a.favoriteMedia.contains(c.uri))&&MediaFilter.matches(c,a.mediaKind,a.mediaQuery))matches.add(c);if(a.mediaSort==1)matches.sort((x,y)->x.name.compareToIgnoreCase(y.name));else if(a.mediaSort==2)matches.sort((x,y)->Long.compare(x.durationMs(),y.durationMs()));items.addAll(matches);}notifyDataSetChanged();if(scroll!=null)mediaList.onRestoreInstanceState(scroll);
        }
        public int getCount(){return Math.max(1,(items.size()+columns-1)/columns);}public Object getItem(int p){return p;}public long getItemId(int p){return p;}
        public View getView(int p,View convert,android.view.ViewGroup parent){LinearLayout row=convert instanceof LinearLayout?(LinearLayout)convert:a.row();row.removeAllViews();row.setPadding(0,0,0,dp(6));if(items.isEmpty()){TextView empty=a.note(a.loading?"Loading project media…":a.project.library.isEmpty()&&a.mediaKind!=MediaFilter.Kind.SHAPES?"Import media to get started.":"No matching media. Long press Import Media to clear search.");row.addView(empty,new LinearLayout.LayoutParams(-1,dp(68)));return row;}
            for(int col=0;col<columns;col++){int at=p*columns+col;Object item=at<items.size()?items.get(at):null;View cell=item==null?new View(a):item instanceof Integer?a.shapeCard((Integer)item,false):a.mediaCard((EditProject.Clip)item,columns,false);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(a.getResources().getConfiguration().fontScale>1.3f?116:96),1);if(col>0)lp.leftMargin=dp(6);row.addView(cell,lp);}return row;}
    }
}

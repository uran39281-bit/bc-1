package com.blackice.bc1;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.effect.SingleInputVideoGraph;
import androidx.media3.transformer.CompositionPlayer;
import androidx.media3.exoplayer.ExoPlayer;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.EngineSettings;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Black Ice V1 workspace, now backed by a persistent, non-destructive video timeline. */
@androidx.annotation.OptIn(markerClass = {androidx.media3.common.util.UnstableApi.class, androidx.media3.common.util.ExperimentalApi.class})
public final class EditorActivity extends Activity {
    static final int BG=BlackIceTheme.BACKGROUND,PANEL=BlackIceTheme.PRIMARY,CARD=BlackIceTheme.SECONDARY,CYAN=BlackIceTheme.ACCENT,TEXT=BlackIceTheme.TEXT,MUTED=BlackIceTheme.MUTED;
    boolean dirty,reducedMotion,favoritesOnly,mediaList,fullscreenPreview; float monitorVolume=1f; LinearLayout timelineTools; int mediaSort,customTimelineHeight; long editRevision;
    final java.util.Set<String> favoriteMedia=new java.util.HashSet<>();
    public static final String PREFS="bc1-editor";
    static final int IMPORT=701,MUSIC=702,OVERLAY=703,EXPORT=704,REPLACE=705,MEDIA_ACCESS=113;
    static final String[] NAV={"Add","Edit","Effects","Layers"};
    final ToolPanels panels=new ToolPanels(this);
    final TransformPanel transformPanel=new TransformPanel(this);
    AddDrawer addDrawer;
    PresetStore presets;
    /** Multi-selection (includes the primary selection when non-empty). */
    final java.util.TreeSet<Integer> multiClips=new java.util.TreeSet<>(),multiOverlays=new java.util.TreeSet<>();
    boolean wideLayout;
    LinearLayout header,gridRow;
    String headerKey="";
    int gridCount;
    Attributes copiedAttributes;
    Keyframes.Clip copiedKeys;
    float previewMoveX=Float.NaN,previewMoveY=Float.NaN,previewZoom=1,previewPanX,previewPanY;
    String compareSnapshot;
    final Handler main=new Handler(Looper.getMainLooper());
    final ExecutorService io=Executors.newSingleThreadExecutor();
    ProjectStore store;
    EditProject project=new EditProject();
    Player player;
    /** Scrubbing sends a seek per touch event. The preview player gets one seek at a time: while it is still
     * buffering a seek, newer positions only update the target, which is sent once it reports ready (and at
     * most one per SEEK_INTERVAL_MS). Overlapping seeks made Media3 flush frames still in flight, failing with
     * "Error processing video frames". SEEK_STALL_MS is a fallback if ready never arrives. */
    static final long SEEK_INTERVAL_MS=90,SEEK_STALL_MS=3000;
    long lastSeekAt;boolean seekQueued,seekInFlight;
    /** Logcat tag for preview player lifecycle, seeks and errors; CI keeps these lines in the job log. */
    static final String PREVIEW_LOG="BC1Preview";
    int previewSeeks;
    final Runnable flushSeek=new Runnable(){@Override public void run(){
        main.removeCallbacks(this);seekQueued=false;if(player==null)return;long now=android.os.SystemClock.uptimeMillis();
        if(seekInFlight&&now-lastSeekAt<SEEK_STALL_MS){seekQueued=true;main.postDelayed(this,SEEK_STALL_MS-(now-lastSeekAt));return;}
        if(seekInFlight){recoverPreview(player,"seek stalled",false);return;}
        long target=seekTarget();if(!seekInFlight&&player.getCurrentPosition()==target&&player.getPlaybackState()==Player.STATE_READY)return;
        lastSeekAt=now;seekInFlight=true;previewSeeks++;android.util.Log.d(PREVIEW_LOG,"seek "+target+" state="+player.getPlaybackState());player.seekTo(target);main.postDelayed(checkSeek,SEEK_STALL_MS);}};
    // A seek can stall without onPlayerError. Watch every seek, including the final one after touch-up.
    final Runnable checkSeek=()->{if(player!=null&&seekInFlight){
        if(player.getPlaybackState()==Player.STATE_READY)seekSettled();
        else recoverPreview(player,"seek watchdog",false);
    }};
    void recoverPreview(Player failed,String reason,boolean autoplay){
        if(player!=failed||!active)return;
        main.removeCallbacks(flushSeek);main.removeCallbacks(checkSeek);seekQueued=false;seekInFlight=false;
        long now=android.os.SystemClock.uptimeMillis();
        if(now-previewRecoveryWindow>30_000){previewRecoveryWindow=now;previewRecoveries=0;}
        if(previewRecoveries>=3){failed.pause();previewHint("Preview unavailable • tap to retry");tell("Preview recovery limit reached: "+reason);return;}
        previewRecoveries++;
        android.util.Log.w(PREVIEW_LOG,"rebuilding preview: "+reason+" target="+project.playheadMs+" attempt="+previewRecoveries);
        if(autoplay)preparePreview(true);else showLive();
    }
    /** The player finished the seek in flight: send the newest target, if any, respecting the interval. */
    void seekSettled(){main.removeCallbacks(checkSeek);seekInFlight=false;if(!seekQueued)return;main.removeCallbacks(flushSeek);long wait=lastSeekAt+SEEK_INTERVAL_MS-android.os.SystemClock.uptimeMillis();if(wait<=0)flushSeek.run();else main.postDelayed(flushSeek,wait);}
    /** One-line preview state for test failure messages and bug reports. */
    String previewDebug(){return "player="+(player==null?"none":player.getClass().getSimpleName()+" state="+player.getPlaybackState()+" pos="+player.getCurrentPosition()+" playWhenReady="+player.getPlayWhenReady()+" error="+(player.getPlayerError()==null?"none":player.getPlayerError().getMessage()))
        +" playhead="+project.playheadMs+" seeksSent="+previewSeeks+" seekQueued="+seekQueued+" inFlight="+seekInFlight+" recoveries="+previewRecoveries+" pending="+previewPending
        +" "+(live==null?"live=none":live.debug())+" liveView="+(liveView==null?"none":liveView.getVisibility()==View.VISIBLE?(liveView.isAvailable()?"visible":"visible-no-surface"):"hidden")+" hint="+(emptyPreview!=null&&emptyPreview.getVisibility()==View.VISIBLE?"'"+emptyPreview.getText().toString().replace('\n',' ')+"'":"hidden")+" status='"+message+"'";}
    /** Automatic preview rebuilds after a player error (e.g. a decoder that failed mid-seek), limited per window. */
    int previewRecoveries;long previewRecoveryWindow;
    PreviewProxy previewProxy;
    SurfaceView surface;
    /** ICE Lite live preview: draws the playhead frame while paused, so edits show at once (no preview preparation). */
    com.blackice.bc1.engine.bc.LivePreview live;
    TextureView liveView;
    /** Project before the current slider drag (one undo entry per drag); null when not dragging. */
    String liveBefore;
    FrameLayout monitor;
    LinearLayout inspector,mediaBrowser,mediaGrid,contextualRow;
    int contextualCount;
    TextView mediaCount;
    int mediaColumns=1;
    String mediaQuery="";
    MediaFilter.Kind mediaKind=MediaFilter.Kind.ALL;
    final ArrayList<TextView> mediaFilterButtons=new ArrayList<>();
    ThumbnailCache thumbnails;
    // Stable observers do not retain obsolete cards/canvases or exhaust the bounded listener list.
    final Runnable mediaThumbnailsReady=this::refreshMediaThumbnails;
    boolean uiLocked,previewPending,previewAutoplay;
    TextView lockButton;
    SeekBar previewScrubber;
    boolean scrubbing;
    static final int AUDIO_LAYER=110,BACKUP=111,RESTORE=112;
    final java.util.Map<String,float[]> waveforms=new java.util.HashMap<>();
    int inspectorWidth=300;
    TextView status,time,play,projectLabel,exportButton,emptyPreview,monitorInfo;
    MobileChrome mobile;
    View phonePreview,phoneTransport,phoneTimelineTools,phoneTimelineArea; ScrollView phoneDrawer;
    TimelineView timeline;
    final Runnable timelineThumbnailsReady=()->{if(!isDestroyed()&&timeline!=null)timeline.invalidate();};
    HorizontalScrollView timelineScroll;
    LayoutPolicy.Preference mode=LayoutPolicy.Preference.AUTO;
    int selected=-1,selectedOverlay=-1,tab=0,workspaceFocus=0;
    View previewControls;
    boolean comparing;
    TextView jumpStart,jumpEnd;
    float pixelsPerSecond=48;
    boolean active,importing,loading=true,snapping=true,exportLaunching,wasExporting;
    long lastExportSeen;
    String message="Opening workspace…";
    final ArrayList<TextView> navTabs=new ArrayList<>();
    final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    final Runnable autosave=()->saveNow();
    final Runnable pulse=new Runnable(){@Override public void run(){
        if(!active)return;
        if(player!=null && player.isPlaying()) {project.playheadMs=Math.min(project.durationMs(),Math.max(0,player.getCurrentPosition()));
            // Preview-only loop range.
            if(project.loopInMs>=0&&project.playheadMs>=project.loopOutMs){player.seekTo(project.loopInMs);project.playheadMs=project.loopInMs;}
            syncPosition();}
        syncPlayButton();
        android.content.SharedPreferences export=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        boolean running=export.getBoolean("running",false);
        long completed=export.getLong("finishedAt",0);
        if(!running&&!exportLaunching&&!loading&&(wasExporting||completed>lastExportSeen)){lastExportSeen=completed;tell(export.getString("status","Export ended"));if(wasExporting)showLive();}
        wasExporting=running;
        if(exportButton!=null)exportButton.setText(running?"Cancel":"Export");
        if(status!=null)status.setText(running?export.getString("status","Exporting…"):message);
        main.postDelayed(this,player!=null&&player.isPlaying()?33:100);
    }};
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        thumbnails=new ThumbnailCache(this);previewProxy=new PreviewProxy(this);live=new com.blackice.bc1.engine.bc.LivePreview(this);addDrawer=new AddDrawer(this);presets=new PresetStore(this);uiLocked=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("ui-locked",false);
        store=new ProjectStore(this);inspectorWidth=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("panel-width",300);
        android.content.SharedPreferences exportState=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        if(exportState.getBoolean("running",false)&&!ExportService.isRunning())exportState.edit().putBoolean("running",false)
                .putBoolean("success",false).putString("status","Previous export was interrupted. Export again to create a complete MP4.").putLong("finishedAt",System.currentTimeMillis()).apply();
        try {mode=LayoutPolicy.Preference.valueOf(getSharedPreferences(PREFS,MODE_PRIVATE).getString("mode","AUTO"));}catch(Exception ignored){}
        snapping=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("snapping",true);
        workspaceFocus=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("workspace-focus",0);
        if(state!=null){transformPanel.linked=state.getBoolean("transform-linked",true);transformPanel.tab=state.getInt("transform-tab",0);mediaSort=state.getInt("media-sort",0);mediaList=state.getBoolean("media-list",false);favoritesOnly=state.getBoolean("media-favorites",false);customTimelineHeight=state.getInt("timeline-height",0);selected=state.getInt("selected",-1);selectedOverlay=state.getInt("selected-overlay",-1);pixelsPerSecond=state.getFloat("timeline-zoom",48);tab=state.getInt("tab",0);mediaQuery=state.getString("media-query","");try{mediaKind=MediaFilter.Kind.valueOf(state.getString("media-kind","ALL"));}catch(IllegalArgumentException ignored){}}
        if(state==null)tab=1;
        buildWorkspace();
        String id=getSharedPreferences(PREFS,MODE_PRIVATE).getString("last-project",null);
        io.execute(()->{
            EditProject loaded=null;String failure=null;
            if(id!=null)try {loaded=store.load(id);}catch(Exception e){failure="Could not reopen the last project. Its file was preserved.";}
            EditProject result=loaded;String error=failure;
            main.post(()->{if(isDestroyed())return;loading=false;if(result!=null)project=result;
                message=error==null?((project.clips.isEmpty()&&project.overlays.isEmpty())?"Import videos or images to begin.":"Project restored • "+project.clips.size()+" clips"):error;
                refreshProject();showLive();
            });
        });
    }
    @Override protected void onStart(){super.onStart();active=true;main.post(pulse);if(!loading)showLive();}
    @Override protected void onStop(){cancelLiveAdjust();active=false;main.removeCallbacks(pulse);main.removeCallbacks(warmPreview);saveNow();releasePreview();if(previewProxy!=null)previewProxy.cancel();if(live!=null)live.releaseDecoders();super.onStop();}
    @Override protected void onDestroy(){main.removeCallbacks(autosave);if(live!=null)live.release();io.shutdown();if(thumbnails!=null)thumbnails.close();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){cancelLiveAdjust();state.putBoolean("transform-linked",transformPanel.linked);state.putInt("transform-tab",transformPanel.tab);state.putInt("media-sort",mediaSort);state.putBoolean("media-list",mediaList);state.putBoolean("media-favorites",favoritesOnly);state.putInt("timeline-height",customTimelineHeight);state.putInt("selected",selected);state.putInt("selected-overlay",selectedOverlay);state.putFloat("timeline-zoom",pixelsPerSecond);state.putInt("tab",tab);state.putString("media-query",mediaQuery);state.putString("media-kind",mediaKind.name());saveNow();super.onSaveInstanceState(state);}
    @Override public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);cancelLiveAdjust();buildWorkspace();}
    boolean exporting(){return getSharedPreferences(ExportService.PREFS,MODE_PRIVATE).getBoolean("running",false)||exportLaunching;}
    boolean editable(){if(uiLocked){tell("UI locked • unlock editing in Settings");return false;}if(loading||importing){tell("Wait for the current import to finish.");return false;}if(exporting()){tell("Wait for export to finish, or cancel it to continue editing.");return false;}return true;}
    void releasePreview(){
        main.removeCallbacks(flushSeek);main.removeCallbacks(checkSeek);seekQueued=false;seekInFlight=false;
        // Only a render the user is waiting for is cancelled; a background one (warmPreview) keeps going.
        boolean waiting=previewPending;previewPending=false;previewAutoplay=false;if(previewProxy!=null&&waiting)previewProxy.cancel();
        if(player!=null){project.playheadMs=Math.min(project.durationMs(),Math.max(0,player.getCurrentPosition()));player.release();player=null;}
    }
    void previewHint(String text){if(emptyPreview!=null){emptyPreview.setText(text);emptyPreview.setBackgroundColor(liveView!=null&&liveView.getVisibility()==View.VISIBLE?0x99000000:Color.BLACK);emptyPreview.setVisibility(View.VISIBLE);}}
    void attachPreview(Player prepared){
        player=prepared;surface.setVisibility(View.VISIBLE);prepared.setVolume(monitorVolume);prepared.setVideoSurfaceView(surface);
        prepared.addListener(new Player.Listener(){
            // The player's first frame replaces the live frame; the live decoders are closed to free the hardware.
            @Override public void onRenderedFirstFrame(){if(player!=prepared)return;if(emptyPreview!=null){emptyPreview.setVisibility(View.GONE);emptyPreview.setBackgroundColor(Color.BLACK);}if(liveView!=null)liveView.setVisibility(View.GONE);if(live!=null)live.releaseDecoders();}
            @Override public void onPlayerError(PlaybackException error){android.util.Log.w(PREVIEW_LOG,"player error ("+(player==prepared?"current":"stale")+" player, recoveries="+previewRecoveries+")",error);if(player!=prepared)return;prepared.pause();main.removeCallbacks(flushSeek);seekInFlight=false;
                main.post(()->recoverPreview(prepared,"player error: "+error.getErrorCodeName(),false));}

            @Override public void onPlaybackStateChanged(int state){if(player==prepared){android.util.Log.d(PREVIEW_LOG,"state "+state+" pos="+prepared.getCurrentPosition()+" playhead="+project.playheadMs);
                if(seekInFlight&&state!=Player.STATE_BUFFERING&&prepared.getPlaybackState()!=Player.STATE_BUFFERING)seekSettled();}if(state==Player.STATE_ENDED&&player==prepared){project.playheadMs=project.durationMs();syncPosition();}}
        });
    }
    /**
     * Shows the frame at the playhead through ICE Lite at once. Edits, slider drags and paused scrubbing never
     * wait for a player: the playback player (and, for layered projects, its rendered proxy) is only prepared
     * when Play is pressed.
     */
    void renderLive(){
        if(live==null||liveView==null||!active||loading||exporting())return;
        if((project.clips.isEmpty()&&project.overlays.isEmpty())){liveView.setVisibility(View.GONE);return;}
        try{
            EditProject snapshot=comparing&&compareSnapshot!=null?ProjectStore.decode(compareSnapshot):ProjectStore.snapshot(project);
            ActiveFeatures.prepareRender(snapshot);snapshot.playheadMs=project.playheadMs;
            // Only the live TextureView owns paused frames. Do not leave the inactive
            // SurfaceView's separate surface/hole covering it on device compositors.
            surface.setVisibility(View.INVISIBLE);
            liveView.setVisibility(View.VISIBLE);if(emptyPreview!=null)emptyPreview.setVisibility(View.GONE);
            live.request(snapshot,project.playheadMs);
        }catch(Exception e){tell("Live preview: "+e.getMessage());}
    }
    /** Live preview after an edit or reload: drop the playback player (Play prepares it again). */
    void showLive(){if(player!=null||previewPending)releasePreview();renderLive();scheduleWarm();}
    /**
     * Layered projects play from a rendered proxy. It is prepared quietly once edits settle, so Play rarely
     * waits; Play joins a render already in flight. Each edit cancels the stale one.
     */
    static final long WARM_MS=1200;
    final Runnable warmPreview=()->{
        if(!active||loading||player!=null||previewPending||liveBefore!=null||comparing||exporting()||(project.clips.isEmpty()&&project.overlays.isEmpty()))return;
        EditProject snapshot=ProjectStore.snapshot(project);ActiveFeatures.prepareRender(snapshot);if(!PreviewProxy.required(snapshot))return;
        previewProxy.prepare(snapshot,new PreviewProxy.Listener(){
            @Override public void onProgress(int percent){}
            @Override public void onReady(File file){android.util.Log.d(PREVIEW_LOG,"playback render ready");}
            @Override public void onError(String error){android.util.Log.w(PREVIEW_LOG,"background playback render failed: "+error);}
        });
    };
    void scheduleWarm(){main.removeCallbacks(warmPreview);if(previewProxy!=null&&!previewPending)previewProxy.cancel();main.postDelayed(warmPreview,WARM_MS);}
    void preparePreview(boolean playWhenReady) {
        if(!active||loading||exporting())return;
        long position=project.playheadMs;releasePreview();project.playheadMs=position;
        if((project.clips.isEmpty()&&project.overlays.isEmpty())){syncPosition();return;}
        try {
            EditProject snapshot=ProjectStore.snapshot(project);if(comparing&&compareSnapshot!=null){snapshot=ProjectStore.decode(compareSnapshot);snapshot.playheadMs=project.playheadMs;}ActiveFeatures.prepareRender(snapshot);
            previewHint("Preparing preview…");
            if(PreviewProxy.required(snapshot)){
                previewPending=true;previewAutoplay=playWhenReady;
                previewProxy.prepare(snapshot,new PreviewProxy.Listener(){
                    @Override public void onProgress(int percent){if(!active||!previewPending)return;previewHint((percent<0?"Preparing preview…":"Preparing preview • "+percent+"%")+"\nTap to cancel");}
                    @Override public void onReady(File file){
                        if(!active||!previewPending)return;previewPending=false;
                        ExoPlayer prepared=new ExoPlayer.Builder(EditorActivity.this).build();attachPreview(prepared);
                        prepared.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true);
                        prepared.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)),Math.min(project.playheadMs,Math.max(0,project.durationMs()-1)));
                        prepared.prepare();prepared.setPlayWhenReady(previewAutoplay);previewAutoplay=false;
                    }
                    @Override public void onError(String error){previewPending=false;previewAutoplay=false;tell("Preview preparation failed: "+error);previewHint("Preview unavailable • tap to retry");}
                });
            }else{
                android.util.Log.i(PREVIEW_LOG,"prepare live preview at "+position);
                CompositionPlayer prepared=new CompositionPlayer.Builder(this)
                        // The live preview always has one clip (larger projects use the transcoded proxy). Media3's
                        // MultipleInputVideoGraph only flushes its per-input stages on a seek, not the compositor, so
                        // held output textures failed FinalShaderProgramWrapper's free-texture check; the single-input
                        // graph flushes its whole pipeline.
                        .setVideoGraphFactory(new SingleInputVideoGraph.Factory(CompositionFactory.frameProcessor()))
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true).build();
                attachPreview(prepared);prepared.setComposition(CompositionFactory.buildLive(this,snapshot),Math.min(position,Math.max(0,project.durationMs()-1)));
                prepared.prepare();prepared.setPlayWhenReady(playWhenReady);
            }
        }catch(Exception e){releasePreview();tell("Cannot prepare preview: "+e.getMessage());previewHint("Preview unavailable • tap to retry");}
    }
    void remember(){try {undo.addLast(ProjectStore.encode(project));while(undo.size()>30)undo.removeFirst();redo.clear();}catch(Exception e){tell(e.getMessage());}}
    void changed(String text) {
        project.validate();dirty=true;editRevision++;message=text;refreshProject();showLive();main.removeCallbacks(autosave);main.postDelayed(autosave,300);
    }
    void saveNow() {
        main.removeCallbacks(autosave);if(loading||liveBefore!=null)return;
        if(player!=null)project.playheadMs=Math.min(project.durationMs(),Math.max(0,player.getCurrentPosition()));
        EditProject snapshot=ProjectStore.snapshot(project);long savingRevision=editRevision;
        io.execute(()->{try{store.save(snapshot);main.post(()->{if(isDestroyed()||!project.id.equals(snapshot.id))return;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("last-project",snapshot.id).apply();if(savingRevision==editRevision){dirty=false;renderHeader();}});}
            catch(Exception e){main.post(()->tell("Save failed: "+e.getMessage()));}});
    }
    void undo(boolean forward) {
        if(!editable())return;ArrayDeque<String> from=forward?redo:undo,to=forward?undo:redo;
        if(from.isEmpty()){tell(forward?"Nothing to redo.":"Nothing to undo.");return;}
        try {to.addLast(ProjectStore.encode(project));project=ProjectStore.decode(from.removeLast());changed(forward?"Edit restored":"Edit undone");}
        catch(Exception e){tell(e.getMessage());}
    }
    void refreshProject() {
        if(selected>=project.clips.size())selected=-1;if(selectedOverlay>=project.overlays.size())selectedOverlay=-1;
        if(projectLabel!=null)projectLabel.setText(project.name+(dirty?" •":"")+"  ▾");
        if(timeline!=null){timeline.getLayoutParams().height=Math.round(timeline.contentHeight());timeline.requestLayout();timeline.invalidate();}
        for(EditProject.Clip clip:project.clips)if(clip.track>=EditProject.AUDIO_FIRST)loadWaveform(clip,true);
        if(monitor!=null)monitor.requestLayout();if(emptyPreview!=null)emptyPreview.setVisibility((project.clips.isEmpty()&&project.overlays.isEmpty())?View.VISIBLE:View.GONE);renderInspector();renderMediaBrowser();syncPosition();
    }
    void syncPosition(){if(transformPanel!=null)transformPanel.refresh();updatePlayheadTools();if(mobile!=null)mobile.sync();if(previewControls!=null)previewControls.invalidate();if(time!=null)time.setText(clock(project.playheadMs)+"  /  "+clock(project.durationMs()));if(timeline!=null)timeline.syncScroll();if(previewScrubber!=null&&!scrubbing)previewScrubber.setProgress((int)(1000*project.playheadMs/Math.max(1,project.durationMs())));}
    long seekTarget(){return Math.min(project.playheadMs,Math.max(0,project.durationMs()-1));}
    void seek(long ms){
        if(liveBefore!=null)return;
        project.playheadMs=Math.max(0,Math.min(ms,project.durationMs()));
        // Paused scrubbing goes through the live preview; seeking during playback still uses the player.
        if(player!=null&&!player.getPlayWhenReady()&&live!=null){releasePreview();project.playheadMs=Math.max(0,Math.min(ms,project.durationMs()));}
        if(player==null){renderLive();syncPosition();return;}
        if(player!=null){
            player.pause();main.removeCallbacks(flushSeek);
            long now=android.os.SystemClock.uptimeMillis(),wait=lastSeekAt+SEEK_INTERVAL_MS-now;
            if(wait<=0&&!seekInFlight)flushSeek.run();else{seekQueued=true;if(!seekInFlight)main.postDelayed(flushSeek,wait);else main.postDelayed(flushSeek,Math.max(0,SEEK_STALL_MS-(now-lastSeekAt)));}
        }
        syncPosition();
    }
    void togglePlay(){if(previewPending){previewAutoplay=!previewAutoplay;tell(previewAutoplay?"Playback will start when the preview is ready.":"Preview will stay paused when ready.");return;}if(player==null){if((project.clips.isEmpty()&&project.overlays.isEmpty()))tell("Import a video or image first.");else preparePreview(true);return;}if(player.getPlayWhenReady())player.pause();else{if(seekQueued){main.removeCallbacks(flushSeek);seekQueued=false;player.seekTo(seekTarget());}if(player.getPlaybackState()==Player.STATE_ENDED||project.playheadMs>=project.durationMs())player.seekTo(0);player.play();}}

    void buildWorkspace() {
        // Layout switches keep selection, playhead and zoom (fields) and the layer list's vertical scroll.
        int timeScroll=timelineScroll!=null?timelineScroll.getScrollX():0;
        int layerScroll=timelineScroll!=null&&timelineScroll.getParent() instanceof ScrollView?((ScrollView)timelineScroll.getParent()).getScrollY():0;
        Configuration c=getResources().getConfiguration();android.view.Display display=getWindowManager().getDefaultDisplay();
        boolean wide=LayoutPolicy.resolve(mode,c.screenWidthDp,c.screenHeightDp,c.smallestScreenWidthDp,display!=null&&display.getDisplayId()!=0)==LayoutPolicy.Workspace.WIDE;
        mobile=null;phonePreview=null;phoneTransport=null;phoneTimelineTools=null;phoneTimelineArea=null;phoneDrawer=null;navTabs.clear();mediaFilterButtons.clear();mediaBrowser=null;mediaGrid=null;mediaCount=null;previewScrubber=null;scrubbing=false;LinearLayout root=column();root.setBackground(BlackIceTheme.backdrop());
        wideLayout=wide;reducedMotion=getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("reduced-motion",false);favoriteMedia.clear();favoriteMedia.addAll(getSharedPreferences(PREFS,MODE_PRIVATE).getStringSet("favorite-media",java.util.Collections.emptySet()));header=row();header.setPadding(dp(10),0,dp(8),0);header.setGravity(Gravity.CENTER_VERTICAL);header.setBackground(BlackIceTheme.backdrop());headerKey="";
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(wide?56:Math.max(64,Math.min(88,64*c.fontScale)))));renderHeader();
        if(fullscreenPreview){root.addView(buildMonitor(),new LinearLayout.LayoutParams(-1,0,1));root.addView(transport(true));root.addView(button("Exit fullscreen",v->{fullscreenPreview=false;buildWorkspace();}));inspector=column();}
        else if(wide) {
            LinearLayout body=row();root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
            // Narrower wide windows have no section tabs in the top bar, so the rail carries them instead.
            if(!topTabs()){LinearLayout rail=column();rail.setPadding(dp(4),dp(6),dp(4),0);
                String[] names=NAV;
                for(int i=0;i<names.length;i++){final int index=i;TextView b=iconButton(names[i],v->{tab=index;renderInspector();});navTabs.add(b);rail.addView(b,new LinearLayout.LayoutParams(-1,dp(c.screenHeightDp<550?52:64)));}
                ScrollView railScroll=new ScrollView(this);railScroll.setFillViewport(false);railScroll.addView(rail);body.addView(railScroll,new LinearLayout.LayoutParams(dp(76),-1));}
            if(c.screenWidthDp>=1100){int width=Math.min(380,Math.max(240,Math.round(c.screenWidthDp*.21f)));mediaGridColumns=width>=300?3:2;mediaColumns=mediaList?1:mediaGridColumns;ScrollView library=new ScrollView(this);mediaBrowser=column();mediaBrowser.setPadding(dp(14),dp(14),dp(14),dp(10));library.addView(mediaBrowser);library.setBackground(panelBackground());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(width),-1);lp.setMargins(dp(8),dp(6),dp(6),dp(4));body.addView(library,lp);renderMediaBrowser();}
            LinearLayout center=column();center.setPadding(dp(4),dp(4),dp(6),dp(4));body.addView(center,new LinearLayout.LayoutParams(0,-1,1));
            center.addView(buildMonitor(),new LinearLayout.LayoutParams(-1,0,1));center.addView(transport(c.screenHeightDp<550));
            ScrollView tools=new ScrollView(this);tools.setFillViewport(false);inspector=column();inspector.setPadding(dp(10),dp(10),dp(10),dp(12));tools.addView(inspector);tools.setBackground(panelBackground());
            View divider=new View(this);divider.setBackground(splitter(true));divider.setContentDescription("Drag to resize tool panel");body.addView(divider,new LinearLayout.LayoutParams(dp(8),-1));
            LinearLayout.LayoutParams toolsLp=new LinearLayout.LayoutParams(dp(Math.min(inspectorWidth,c.screenWidthDp<1000?250:500)),-1);toolsLp.setMargins(0,dp(6),dp(8),dp(4));body.addView(tools,toolsLp);
            divider.setOnTouchListener(new View.OnTouchListener(){float down;int initial;public boolean onTouch(View v,MotionEvent e){if(uiLocked)return true;if(e.getAction()==MotionEvent.ACTION_DOWN){down=e.getRawX();initial=tools.getWidth();return true;}if(e.getAction()==MotionEvent.ACTION_MOVE){int width=Math.max(dp(180),Math.min(body.getWidth()/2,initial+(int)((getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("panel-left",false)?-1:1)*(down-e.getRawX()))));tools.getLayoutParams().width=width;tools.requestLayout();inspectorWidth=Math.round(width/getResources().getDisplayMetrics().density);return true;}if(e.getAction()==MotionEvent.ACTION_UP)getSharedPreferences(PREFS,MODE_PRIVATE).edit().putInt("panel-width",inspectorWidth).apply();return true;}});
            if(getSharedPreferences(PREFS,MODE_PRIVATE).getBoolean("panel-left",false)){body.removeView(tools);body.removeView(divider);body.addView(tools,1);body.addView(divider,2);}
            body.setOnDragListener((v,event)->{if(uiLocked||event.getLocalState()!=tools)return false;if(event.getAction()==android.view.DragEvent.ACTION_DROP){boolean left=event.getX()<body.getWidth()/2f;body.removeView(tools);body.removeView(divider);if(left){body.addView(tools,1);body.addView(divider,2);}else{body.addView(divider);body.addView(tools);}getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("panel-left",left).apply();return true;}return true;});
            final View timelineArea=buildTimeline();
            View timelineDivider=new View(this);timelineDivider.setBackground(splitter(false));timelineDivider.setContentDescription("Resize timeline height");
            root.addView(timelineDivider,new LinearLayout.LayoutParams(-1,dp(8)));root.addView(buildTimelineTools());
            int requestedTimelineHeight=customTimelineHeight>0?customTimelineHeight:Math.min(240,timelineHeight(c,true));
            // Keep one scrollable track plus ruler while reserving a usable preview in short DeX windows.
            int availableTimelineHeight=c.screenHeightDp<450?Math.min(88,requestedTimelineHeight):requestedTimelineHeight;
            root.addView(timelineArea,new LinearLayout.LayoutParams(-1,dp(availableTimelineHeight)));
            timelineDivider.setOnTouchListener(new View.OnTouchListener(){float startY;int initial;public boolean onTouch(View v,MotionEvent e){if(uiLocked)return true;if(e.getActionMasked()==MotionEvent.ACTION_DOWN){startY=e.getRawY();initial=timelineArea.getHeight();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){int h=Math.max(dp(110),Math.min(dp(c.screenHeightDp*.45f),initial+(int)(startY-e.getRawY())));timelineArea.getLayoutParams().height=h;timelineArea.requestLayout();customTimelineHeight=Math.round(h/getResources().getDisplayMetrics().density);return true;}return true;}});

        } else {
            mobile=new MobileChrome(this);mobile.build(root);renderHeader();

        }
        status=label(message,10,MUTED,false);status.setSingleLine(true);status.setPadding(dp(10),0,dp(10),dp(3));status.setOnClickListener(v->new AlertDialog.Builder(this).setMessage(status.getText()).setPositiveButton("OK",null).show());root.addView(status,new LinearLayout.LayoutParams(-1,dp(wide&&c.screenHeightDp<450?16:24)));
        root.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        setContentView(root);root.requestApplyInsets();if(timelineScroll!=null)timelineScroll.post(()->timeline.syncScroll());if(layerScroll>0&&timelineScroll!=null)timelineScroll.post(()->{if(timelineScroll.getParent() instanceof ScrollView)((ScrollView)timelineScroll.getParent()).scrollTo(0,layerScroll);});if(player!=null)player.setVideoSurfaceView(surface);renderInspector();syncPosition();if(previewPending)previewHint("Preparing preview…\nTap to cancel");
    }
    View buildMonitor() {
        if(surface==null){surface=new SurfaceView(this);surface.setContentDescription("Video project preview");}
        if(surface.getParent()!=null)((android.view.ViewGroup)surface.getParent()).removeView(surface);
        if(liveView==null){liveView=new TextureView(this);liveView.setSurfaceTextureListener(live);liveView.setContentDescription("Live project preview");liveView.setVisibility(View.GONE);}
        if(liveView.getParent()!=null)((android.view.ViewGroup)liveView.getParent()).removeView(liveView);
        monitor=new FrameLayout(this){@Override protected void onMeasure(int wSpec,int hSpec){
            int w=View.MeasureSpec.getSize(wSpec),h=View.MeasureSpec.getSize(hSpec);float ratio=project.portrait?9f/16:16f/9;
            int fit=Math.min(w,(int)(h*ratio));
            // Navigation zoom / pan changes only the view of the frame, never a layer's scale or position.
            int sw=Math.round(fit*previewZoom),sh=(int)(sw/ratio),offX=Math.round(previewPanX*sw),offY=Math.round(previewPanY*sh);
            FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)surface.getLayoutParams();
            if(params.width!=Math.max(1,sw)||params.height!=Math.max(1,sh)||params.leftMargin!=offX||params.topMargin!=offY){params.width=Math.max(1,sw);params.height=Math.max(1,sh);params.leftMargin=offX;params.topMargin=offY;params.rightMargin=0;params.bottomMargin=0;surface.setLayoutParams(params);}
            FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)liveView.getLayoutParams();
            if(lp.width!=params.width||lp.height!=params.height||lp.leftMargin!=params.leftMargin||lp.topMargin!=params.topMargin){lp.width=params.width;lp.height=params.height;lp.leftMargin=params.leftMargin;lp.topMargin=params.topMargin;lp.gravity=params.gravity;liveView.setLayoutParams(lp);}
            super.onMeasure(wSpec,hSpec);
        }};
        monitor.setBackgroundColor(0xFF02060C);monitor.addView(surface,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));monitor.addView(liveView,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        emptyPreview=label("START YOUR FIRST CUT\n\n+ Import video or images",14,CYAN,true);emptyPreview.setGravity(Gravity.CENTER);emptyPreview.setContentDescription("Import video or images");emptyPreview.setFocusable(true);emptyPreview.setBackgroundColor(Color.BLACK);emptyPreview.setOnClickListener(v->{if((project.clips.isEmpty()&&project.overlays.isEmpty())){if(mobile!=null)mobile.openAdd(3);else pick(IMPORT,"*/*");}else if(previewPending){releasePreview();previewHint("Preview cancelled • tap to prepare");}else showLive();});emptyPreview.setVisibility((project.clips.isEmpty()&&project.overlays.isEmpty())?View.VISIBLE:View.GONE);monitor.addView(emptyPreview,new FrameLayout.LayoutParams(-1,-1));
        previewControls=new PreviewEditor(this);monitor.addView(previewControls,new FrameLayout.LayoutParams(-1,-1));
        TextView compare=button("Compare",null);compare.setTextSize(10);compare.setContentDescription("Hold to compare with the layer before the current adjustments");compare.setTooltipText(compare.getContentDescription());
        compare.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN)compare(true);else if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)compare(false);return true;});
        if(wideLayout)monitor.addView(compare,new FrameLayout.LayoutParams(-2,dp(40),Gravity.BOTTOM|Gravity.END));
        TextView info=monitorInfo=label(project.name+"  ·  "+(project.portrait?"9:16":"16:9")+"  ·  "+project.frameRate+" fps",11,0xFFC9D6E3,false);info.setPadding(dp(10),dp(6),dp(8),0);info.setShadowLayer(dp(3),0,0,0xCC000000);info.setSingleLine(true);info.setEllipsize(android.text.TextUtils.TruncateAt.END);if(wideLayout)monitor.addView(info,new FrameLayout.LayoutParams(-2,dp(28),Gravity.TOP|Gravity.START));
        TextView viewOptions=button("Fit ▾",v->previewOptions());viewOptions.setTextSize(11);viewOptions.setContentDescription("Preview scaling, volume and fullscreen");FrameLayout.LayoutParams viewLp=new FrameLayout.LayoutParams(dp(68),dp(34),Gravity.TOP|Gravity.END);viewLp.setMargins(0,dp(8),dp(8),0);if(wideLayout||fullscreenPreview)monitor.addView(viewOptions,viewLp);
        return monitor;
    }
    void previewOptions(){new AlertDialog.Builder(this).setTitle("Preview view only").setItems(new String[]{"Fit canvas","Fill viewport (cropped)","100% preview pixels","Preview volume","Fullscreen preview"},(d,i)->{
        if(i<3){resetPreviewView();float ratio=project.portrait?9f/16:16f/9;float fit=Math.max(1,Math.min(monitor.getWidth(),monitor.getHeight()*ratio));if(i==1)previewZoom=Math.max(monitor.getWidth()/fit,monitor.getHeight()*ratio/fit);if(i==2)previewZoom=(project.portrait?project.previewHeight:project.previewHeight*ratio)/fit;monitor.requestLayout();}
        else if(i==3){LinearLayout f=form();SeekBar volume=new SeekBar(this);volume.setMax(100);volume.setProgress(Math.round(monitorVolume*100));volume.setContentDescription("Preview volume");f.addView(volume);new AlertDialog.Builder(this).setTitle("Preview volume (export unchanged)").setView(f).setPositiveButton("Apply",(x,w)->{monitorVolume=volume.getProgress()/100f;if(player!=null)player.setVolume(monitorVolume);}).setNegativeButton("Cancel",null).show();}
        else {fullscreenPreview=true;buildWorkspace();}
    }).show();}
    SeekBar zoomSlider;
    final ArrayList<TextView> playheadTools=new ArrayList<>();
    void updatePlayheadTools(){EditProject.Clip c=clip();EditProject.Overlay o=overlay();boolean valid=(c!=null||o!=null)&&!selectionLocked()&&!uiLocked&&(c!=null?LayerOps.canSplitOrTrim(project,selected):project.playheadMs-o.startMs>=EditProject.MIN_CLIP_MS&&o.endMs-project.playheadMs>=EditProject.MIN_CLIP_MS);for(TextView tool:playheadTools)dim(tool,valid);}
    View buildTimelineTools(){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);timelineTools=row();timelineTools.setGravity(Gravity.CENTER_VERTICAL);timelineTools.setPadding(dp(8),dp(2),dp(8),dp(2));scroll.addView(timelineTools);scroll.setBackgroundColor(0xFF07101A);renderTimelineTools();return scroll;}
    /** Editing tools on the left (dimmed until a layer that supports them is selected); zoom, snapping and
     * fit on the right. These are the only timeline zoom controls. */
    void renderTimelineTools(){if(timelineTools==null)return;timelineTools.removeAllViews();playheadTools.clear();EditProject.Clip c=clip();EditProject.Overlay o=overlay();boolean any=c!=null||o!=null;
        if(any&&!wideLayout)for(String tool:new String[]{"Split","Trim","Duplicate","Transform","Animation"}){boolean supported=any&&ToolPanels.tools(c,o).stream().anyMatch(t->t.equals(tool));TextView b=headerIcon(tool,tool,v->{tab=1;panels.runTool(tool);});dim(b,supported);timelineTools.addView(b);}
        TextView delete=headerIcon("Delete","Delete selected layer",v->deleteSelection());dim(delete,any);if(any)timelineTools.addView(delete);
        timelineTools.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        if(wideLayout){String[] icons={"Edit","TrimStart","TrimEnd"};String[] labels={"Split at playhead","Trim Start at playhead","Trim End at playhead"};int[] operations={1,0,2};
            boolean valid=any&&!selectionLocked()&&!uiLocked&&(c!=null?LayerOps.canSplitOrTrim(project,selected):project.playheadMs-o.startMs>=EditProject.MIN_CLIP_MS&&o.endMs-project.playheadMs>=EditProject.MIN_CLIP_MS);
            for(int i=0;i<3;i++){final int op=operations[i];TextView b=headerIcon(icons[i],labels[i],v->panels.playheadEdit(op));dim(b,valid);playheadTools.add(b);timelineTools.addView(b);}}
        timelineTools.addView(compactButton("−","Zoom out timeline",v->zoomTimeline(1/1.5f,Float.NaN)));
        zoomSlider=new SeekBar(this);zoomSlider.setMax(1000);zoomSlider.setContentDescription("Timeline zoom");zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){if(user)setTimelineZoom((float)(8*Math.pow(300,p/1000.0)));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        timelineTools.addView(zoomSlider,new LinearLayout.LayoutParams(dp(wideLayout?140:90),dp(40)));syncZoomSlider();
        timelineTools.addView(compactButton("+","Zoom in timeline",v->zoomTimeline(1.5f,Float.NaN)));
        TextView snap=headerIcon("Magnet","Snapping: "+(snapping?"on":"off"),v->{snapping=!snapping;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("snapping",snapping).apply();renderTimelineTools();});snap.setSelected(snapping);snap.setCompoundDrawables(icon("Magnet",snapping?CYAN:MUTED),null,null,null);timelineTools.addView(snap);
        timelineTools.addView(headerIcon("Fit","Fit timeline",v->fitTimeline()));timelineTools.addView(headerIcon("Setup","Timeline settings",v->{tab=4;renderInspector();}));
    }
    void syncZoomSlider(){if(zoomSlider!=null)zoomSlider.setProgress((int)Math.round(1000*Math.log(pixelsPerSecond/8)/Math.log(300)));}
    static void dim(View v,boolean enabled){v.setEnabled(enabled);v.setAlpha(enabled?1f:.38f);}
    View transport(boolean singleRow) {
        // Time on the left, playback in the centre; Undo / Redo only where the top bar has none.
        LinearLayout controls=row();controls.setGravity(Gravity.CENTER_VERTICAL);controls.setPadding(dp(6),0,dp(6),0);
        time=label("",singleRow?11:13,TEXT,false);time.setTypeface(Typeface.MONOSPACE);time.setGravity(Gravity.CENTER_VERTICAL);
        // One line on narrow phones too: shrink rather than wrap.
        time.setMaxLines(1);time.setAutoSizeTextTypeUniformWithConfiguration(8,singleRow?11:13,1,android.util.TypedValue.COMPLEX_UNIT_SP);controls.addView(time,new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout center=row();center.setGravity(Gravity.CENTER);
        jumpStart=transportIcon("SkipStart","Jump to beginning",v->jump(false));center.addView(jumpStart);
        center.addView(transportIcon("StepBack","Previous frame",v->seek(project.playheadMs-Math.round(1000d/project.frameRate))));
        play=new TextView(this);play.setGravity(Gravity.CENTER);play.setFocusable(true);play.setContentDescription("Play");play.setTooltipText("Play / pause (Space)");play.setOnClickListener(v->togglePlay());
        GradientDrawable ring=new GradientDrawable();ring.setShape(GradientDrawable.OVAL);ring.setColor(0xFF0B2338);ring.setStroke(dp(2),CYAN);play.setBackground(ring);play.setCompoundDrawables(icon("Play",TEXT),null,null,null);play.setPadding(dp(15),0,dp(11),0);
        LinearLayout.LayoutParams playLp=new LinearLayout.LayoutParams(dp(singleRow?44:52),dp(singleRow?44:52));playLp.setMargins(dp(8),0,dp(8),0);center.addView(play,playLp);
        center.addView(transportIcon("StepForward","Next frame",v->seek(project.playheadMs+Math.round(1000d/project.frameRate))));
        jumpEnd=transportIcon("SkipEnd","Jump to end",v->jump(true));center.addView(jumpEnd);
        controls.addView(center,new LinearLayout.LayoutParams(-2,dp(56)));
        LinearLayout end=row();end.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
        if(!wideLayout){end.addView(transportIcon("Undo","Undo",v->undo(false)));end.addView(transportIcon("Redo","Redo",v->undo(true)));}
        end.addView(transportIcon("Fullscreen","Fullscreen preview",v->{fullscreenPreview=!fullscreenPreview;buildWorkspace();}));
        controls.addView(end,new LinearLayout.LayoutParams(0,dp(48),1));
        LinearLayout transport=column();
        if(getResources().getConfiguration().screenHeightDp>=780){
            previewScrubber=new SeekBar(this);previewScrubber.setMax(1000);previewScrubber.setContentDescription("Preview scrubber");
            previewScrubber.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar slider,int position,boolean user){if(user&&time!=null)time.setText(clock(position*project.durationMs()/1000)+"  /  "+clock(project.durationMs()));}
                public void onStartTrackingTouch(SeekBar slider){scrubbing=true;if(player!=null)player.pause();}
                public void onStopTrackingTouch(SeekBar slider){scrubbing=false;seek(slider.getProgress()*project.durationMs()/1000);}
            });transport.addView(previewScrubber,new LinearLayout.LayoutParams(-1,dp(26)));
        }
        transport.addView(controls);return transport;
    }
    TextView transportIcon(String icon,String description,View.OnClickListener l){TextView b=compactButton("",description,l);b.setBackground(null);b.setCompoundDrawables(icon(icon,TEXT),null,null,null);b.setPadding(dp(11),0,dp(11),0);b.setMinWidth(dp(44));b.setMinHeight(dp(44));return b;}
    void syncPlayButton(){boolean playing=player!=null&&player.isPlaying();if(play==null||Boolean.valueOf(playing).equals(play.getTag()))return;play.setTag(playing);play.setContentDescription(playing?"Pause":"Play");play.setCompoundDrawables(icon(playing?"Pause":"Play",mobile!=null?BlackIceTheme.ON_ACCENT:TEXT),null,null,null);play.setPadding(dp(playing?13:15),0,dp(playing?13:11),0);}
    void clipPercent(boolean volume){EditProject.Clip c=clip();if(c==null)return;percentPanel(volume?"Volume":"Opacity",volume?c.volume:c.opacity,volume?.7f:1f,value->{if(volume)c.volume=value;else c.opacity=value;});}
    void percentPanel(String title,float initial,float defaultValue,ValueAction action){
        if(!editable())return;LinearLayout f=form();EditText numeric=field(f,title+" (%)",String.valueOf(Math.round(initial*100)));numeric.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.round(initial*100));slider.setContentDescription(title);f.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){if(user)numeric.setText(String.valueOf(p));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        f.addView(button("Reset",v->{slider.setProgress(Math.round(defaultValue*100));numeric.setText(String.valueOf(Math.round(defaultValue*100)));}));
        dialog(title,f,()->{float value=parseFloat(numeric);if(value<0||value>100)throw new IllegalArgumentException("Use 0 to 100%.");edit(title+" updated",()->action.apply(value/100));},null);
    }
    void durationDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;numberDialog("Duration (seconds)",seconds(c.durationMs()),value->edit("Duration updated",()->TimelineEdits.duration(project,selected,parseMs(value))));}
    void slipDialog(){EditProject.Clip c=clip();if(c==null||c.image||!editable())return;numberDialog("Slip: source in (seconds)",seconds(c.inMs),value->edit("Source segment slipped",()->TimelineEdits.slip(project,selected,parseMs(value))));}
    void effectsDialog(){tell("Effects — Coming Later");}
    void splitSelected(){if(clip()!=null)edit("Clip split",()->project.split(selected,project.playheadMs));}
    /** Failed/no-op commands do not consume undo or discard redo. */
    void edit(String message,Runnable operation){
        if(!editable())return;pausePlayback();CrashLogs.operation(message);String before;try{before=ProjectStore.encode(project);}catch(Exception e){tell(e.getMessage());return;}
        int selection=selected;
        try{operation.run();project.validate();if(!before.equals(ProjectStore.encode(project))){pushUndo(before);changed(message);}}
        catch(Exception e){CrashLogs.record("Edit rejected",e);try{project=ProjectStore.decode(before);selected=selection;refreshProject();}catch(Exception impossible){throw new IllegalStateException(impossible);}tell(e.getMessage());}
    }
    void pushUndo(String snapshot){undo.addLast(snapshot);while(undo.size()>30)undo.removeFirst();redo.clear();}
    TextView compactButton(String title,String description,View.OnClickListener listener){TextView b=button(title,listener);b.setPadding(dp(5),0,dp(5),0);b.setContentDescription(description);b.setTooltipText(description);return b;}
    void jump(boolean end){EditProject.Clip c=clip();EditProject.Overlay o=overlay();long target=o!=null?(end?o.endMs:o.startMs):c==null?(end?project.durationMs():0):(end?project.placements().get(selected).endMs:project.placements().get(selected).startMs);seek(target);}
    void updateJumpLabels(){String noun=clip()==null&&overlay()==null?"project":"selected clip";if(jumpStart!=null&&jumpEnd!=null){jumpStart.setContentDescription("Jump to beginning of "+noun);jumpStart.setTooltipText(jumpStart.getContentDescription());jumpEnd.setContentDescription("Jump to end of "+noun);jumpEnd.setTooltipText(jumpEnd.getContentDescription());}}
    int timelineHeight(Configuration c,boolean wide){int normal=wide?(c.screenHeightDp<450?88:c.screenHeightDp<550?120:Math.min(280,Math.round(c.screenHeightDp*.28f))):(c.screenHeightDp>=780?200:160);return workspaceFocus==1?Math.min(normal+100,Math.round(c.screenHeightDp*.45f)):workspaceFocus==2?100:normal;}
    void chooseSpace(){new AlertDialog.Builder(this).setTitle("Workspace space").setSingleChoiceItems(new String[]{"Balanced","Expand timeline","Expand preview"},workspaceFocus,(d,i)->{if(uiLocked){tell("Unlock editing in Settings to change layout");return;}workspaceFocus=i;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putInt("workspace-focus",i).apply();d.dismiss();buildWorkspace();}).setNegativeButton("Back",null).show();}
    /** Display scale only; media timing never changes. The time under the anchor (viewport x, or the
     * playhead when NaN) stays in place. */
    void zoomTimeline(float factor,float anchor){
        if(timeline==null||!Float.isFinite(factor)||factor<=0)return;float before=timeline.timelineScale();pixelsPerSecond=Math.max(8,Math.min(2400,pixelsPerSecond*factor));
        timeline.zoomAround(before,anchor);syncZoomSlider();
    }
    void setTimelineZoom(float value){zoomTimeline(value/pixelsPerSecond,Float.NaN);}
    /** Fit a time range while retaining the centered playhead and current preview frame. */
    void fitTimelineRange(long start,long end){
        float left=Math.max(dp(8),timeline.pad()-timeline.headerWidth()-dp(14));
        float right=Math.max(dp(8),timeline.viewport()-timeline.pad()-dp(24));
        float before=Math.max(0,project.playheadMs-start)/1000f,after=Math.max(0,end-project.playheadMs)/1000f;
        float scale=Math.min(before>0?left/before:Float.POSITIVE_INFINITY,after>0?right/after:Float.POSITIVE_INFINITY);
        pixelsPerSecond=Math.max(8,Math.min(2400,scale/dp(1)));timeline.requestLayout();timeline.reveal(start);syncZoomSlider();
    }
    void fitTimeline(){if(timeline!=null&&project.durationMs()>0)fitTimelineRange(0,project.durationMs());}
    /** Zoom to Selection: frames the selected layers. */
    void zoomToSelection(){
        if(timeline==null)return;long start=Long.MAX_VALUE,end=0;for(int i:selectedClips()){EditProject.Placement pl=project.placements().get(i);start=Math.min(start,pl.startMs);end=Math.max(end,pl.endMs);}
        for(int i:selectedOverlays()){EditProject.Overlay o=project.overlays.get(i);start=Math.min(start,o.startMs);end=Math.max(end,o.endMs);}
        if(start==Long.MAX_VALUE){tell("Select a layer to zoom to it.");return;}
        fitTimelineRange(start,end);
    }
    void toggleMarkerAt(long time){if(!editable())return;long tolerance=Math.max(1,Math.round(dp(6)*1000/timeline.timelineScale()));edit("Marker toggled",()->LayerOps.toggleMarker(project,Math.max(0,time),tolerance));}
    /** Row-handle reordering: moves one row to the target position, one swap at a time, in one undo step. */
    void reorderRows(int from,int to){
        java.util.List<int[]> rows=layerRows();if(from<0||to<0||from>=rows.size()||to>=rows.size())return;int[] row=rows.get(from),target=rows.get(to);
        if(row[0]!=target[0]||(row[0]==0&&(EditProject.audioTrack(row[1])!=EditProject.audioTrack(target[1])||target[1]==0))){tell(row[0]==1?"Text, shape and image layers stay above video layers.":"Reorder within visual layers or audio layers; the main sequence stays at the bottom.");return;}
        if(!editable())return;
        edit("Layer order changed",()->{
            if(row[0]==1){LayerOps.moveOverlay(project,row[1],target[1]);selectedOverlay=-1;}
            else if(!EditProject.audioTrack(row[1])){int step=target[1]>row[1]?1:-1;for(int t=row[1];t!=target[1];t+=step){if(project.tracks[t].locked||project.tracks[t+step].locked)throw new IllegalArgumentException("Unlock the layers to reorder them.");LayerOps.swapTracks(project,t,t+step);}}
            else{int step=target[1]>row[1]?1:-1;for(int t=row[1];t!=target[1];t+=step)LayerOps.swapTracks(project,t,t+step);}
        });
    }
    View buildTimeline(){ScrollView vertical=new ScrollView(this);timelineScroll=new HorizontalScrollView(this){@Override public boolean onInterceptTouchEvent(MotionEvent e){return false;}};timelineScroll.setFillViewport(true);timelineScroll.setContentDescription("Timeline scroll");timeline=new TimelineView(this);timelineScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->timeline.invalidate());vertical.setOnScrollChangeListener((v,x,y,oldX,oldY)->timeline.invalidate());timelineScroll.addView(timeline,new HorizontalScrollView.LayoutParams(-2,Math.round(timeline.contentHeight())));timelineScroll.setHorizontalScrollBarEnabled(false);timelineScroll.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(r-l!=or-ol)timelineScroll.post(()->{if(timeline!=null){timeline.requestLayout();timeline.syncScroll();}});});vertical.addView(timelineScroll);return vertical;}
    EditProject.Clip clip(){return selected<0||selected>=project.clips.size()?null:project.clips.get(selected);}
    /** Reserve the phone's usable space for choosing assets; never recreate the player surface. */
    String appVersion(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(android.content.pm.PackageManager.NameNotFoundException e){return "unknown";}}
    void sizePhoneDrawer(){
        if(mobile!=null){mobile.update();return;}
        if(phoneDrawer==null)return;boolean browse=tab==0;
        Configuration c=getResources().getConfiguration();
        phonePreview.getLayoutParams().height=dp(browse?80:workspaceFocus==1?110:workspaceFocus==2?Math.min(280,c.screenHeightDp*.35f):Math.min(190,Math.max(110,c.screenHeightDp*.22f)));
        phoneTimelineArea.getLayoutParams().height=dp(browse?88:workspaceFocus==1?Math.min(260,c.screenHeightDp*.3f):workspaceFocus==2?100:Math.min(180,Math.max(100,c.screenHeightDp*.19f)));
        phonePreview.requestLayout();phoneTimelineArea.requestLayout();
        phoneTransport.setVisibility(browse?View.GONE:View.VISIBLE);
        phoneTimelineTools.setVisibility(browse?View.GONE:View.VISIBLE);
        if(emptyPreview!=null&&(project.clips.isEmpty()&&project.overlays.isEmpty()))emptyPreview.setText(browse?"+ Import video or images":"START YOUR FIRST CUT\n\n+ Import video or images");
    }
    void renderInspector() {
        if(inspector==null)return;sizePhoneDrawer();renderTimelineTools();if(selected>=0)selectedOverlay=-1;pruneSelection();renderHeader();updateMediaSelection();if(timeline!=null)timeline.invalidate();if(previewControls!=null)previewControls.invalidate();updateJumpLabels();inspector.removeAllViews();contextualRow=null;contextualCount=0;gridRow=null;gridCount=0;
        for(int i=0;i<navTabs.size();i++){navTabs.get(i).setTextColor(i==tab?CYAN:TEXT);navTabs.get(i).setBackground(navBackground(i==tab));navTabs.get(i).setSelected(i==tab);for(android.graphics.drawable.Drawable icon:navTabs.get(i).getCompoundDrawables())if(icon!=null)icon.setTint(i==tab?CYAN:TEXT);}
        if(mobile!=null){mobile.inspectorTitle(inspector);}
        else if(!wideLayout&&tab==0){
            LinearLayout title=row();title.setGravity(Gravity.CENTER_VERTICAL);title.addView(label("ADD MEDIA",13,CYAN,true),new LinearLayout.LayoutParams(0,dp(44),1));
            TextView close=button("Done",v->{tab=1;renderInspector();});close.setContentDescription("Close media browser and return to timeline");title.addView(close,new LinearLayout.LayoutParams(dp(72),dp(44)));inspector.addView(title);
        }else{
        if(!wideLayout){
        LinearLayout inspectorTabs=row();inspectorTabs.setGravity(Gravity.CENTER_VERTICAL);for(int i=0;i<2;i++){final boolean settings=i==1;boolean on=settings?tab==4:tab!=4;TextView t=button(settings?"Project":"Inspector",v->{tab=settings?4:1;panels.reset();renderInspector();});t.setTextSize(13);t.setTextColor(on?TEXT:MUTED);t.setBackground(navBackground(on));inspectorTabs.addView(t,new LinearLayout.LayoutParams(0,dp(44),1));}LinearLayout.LayoutParams tabsLp=new LinearLayout.LayoutParams(-1,-2);tabsLp.bottomMargin=dp(10);inspector.addView(inspectorTabs,tabsLp);
        }
        }
        if(!reducedMotion&&android.animation.ValueAnimator.areAnimatorsEnabled()){inspector.animate().cancel();inspector.setAlpha(.8f);inspector.animate().alpha(1).setDuration(180).start();}else inspector.setAlpha(1);
        if(!panels.footage()&&(wideLayout||mobile!=null)&&(clip()!=null||overlay()!=null||multiCount()>1)&&tab!=0){LinearLayout actions=row();TextView selectedName=label(multiCount()>1?multiCount()+" layers selected":clip()!=null?clip().name:overlay().label(),12,TEXT,true);selectedName.setSingleLine(true);selectedName.setEllipsize(android.text.TextUtils.TruncateAt.END);selectedName.setOnClickListener(v->renameSelection());inspector.addView(selectedName);actions.addView(headerIcon("Close","Deselect",v->deselect()));actions.addView(headerIcon(multiCount()>1?"Group":"Duplicate",multiCount()>1?"Group selected layers":"Duplicate",v->{if(multiCount()>1)multiAction("Group");else duplicateSelected();}));actions.addView(headerIcon("Delete","Delete",v->deleteSelection()));actions.addView(headerIcon("More","More actions",v->moreMenu()));inspector.addView(actions);}
        String[] headings={"ADD",wideLayout?"Media Settings":"CLIP EDITOR","Effects — Coming Later","LAYERS","SETTINGS"};
        TextView heading=label(headings[tab],12,CYAN,true);if((wideLayout||!(tab==1&&clip()==null&&overlay()==null&&multiCount()<=1))&&!(tab==0&&topTabs()))if((wideLayout||tab!=0)&&!panels.footage())inspector.addView(heading);heading.setContentDescription(headings[tab]+". Long press and drag to dock the tool panel in wide mode.");heading.setOnLongClickListener(v->{if(uiLocked)return false;if(inspector.getParent() instanceof ScrollView){v.startDragAndDrop(ClipData.newPlainText("panel","tools"),new View.DragShadowBuilder(v),inspector.getParent(),0);return true;}return false;});
        if(tab==0)addDrawer.render(inspector);
        else if(tab==1||tab==2)panels.render(inspector);
        else if(tab==3)layersPanel();
        else settingsPanel();
    }
    /** Project settings open on demand from the top bar instead of occupying a permanent tab. */
    void projectSettingsDialog(){
        String[] entries={"Project name: "+project.name,"Canvas: "+(project.portrait?"1080 × 1920 / 9:16":"1920 × 1080 / 16:9"),"Frame rate: "+project.frameRate+" fps","Background: Black","Project storage"};
        new AlertDialog.Builder(this).setTitle("Project Settings").setItems(entries,(d,i)->{
            if(i==0)numberDialog("Project name",project.name,name->edit("Project renamed",()->{if(name.isBlank()||name.length()>80)throw new IllegalArgumentException("Use 1–80 characters");project.name=name;}));
            else if(i==1)new AlertDialog.Builder(this).setTitle("Canvas aspect ratio").setSingleChoiceItems(new String[]{"1920 × 1080 • 16:9","1080 × 1920 • 9:16"},project.portrait?1:0,(dialog,index)->{dialog.dismiss();edit("Project canvas updated",()->project.portrait=index==1);}).setNegativeButton("Back",null).show();
            else if(i==2){int[] rates=com.blackice.bc1.core.ExportPlanner.FRAME_RATES;String[] labels=new String[rates.length];for(int j=0;j<rates.length;j++)labels[j]=rates[j]+" fps";new AlertDialog.Builder(this).setTitle("Project frame rate").setItems(labels,(dialog,index)->edit("Frame rate updated",()->project.frameRate=rates[index])).show();}
            else if(i==3)tell("The current engine uses a black project background. Add a full-frame shape for a different background.");
            else new AlertDialog.Builder(this).setTitle("Project storage").setMessage("Projects and imported media are saved privately in this app. Use Settings → Export project + media bundle to choose a backup destination.").setPositiveButton("OK",null).show();
        }).setNegativeButton("Back",null).show();
    }
    void settingsPanel(){
        inspector.addView(button("‹  Back to editing",v->{tab=selected>=0||selectedOverlay>=0?1:0;renderInspector();}));
        addAction("Project Settings",this::projectSettingsDialog);

            select("Preview resolution (short edge)",new String[]{"360p • Performance","540p • Balanced","1080p • Quality"},project.previewHeight==360?0:project.previewHeight==540?1:2,index->{remember();project.previewHeight=new int[]{360,540,1080}[index];changed("Preview quality updated");});
            addAction("Workspace layout: "+new String[]{"Auto • phone / tablet / DeX","Phone portrait","Wide tablet / DeX"}[mode.ordinal()],()->chooseMode());
            {android.content.SharedPreferences exportPrefs=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
            com.blackice.bc1.core.ExportEngine engine=com.blackice.bc1.core.ExportEngine.resolve(exportPrefs.getString(com.blackice.bc1.core.ExportEngine.PREF_KEY,null),getPackageName());
            select("Export resolution",com.blackice.bc1.core.ExportPlanner.RESOLUTION_LABELS,com.blackice.bc1.core.ExportPlanner.resolutionIndex(exportPrefs.getInt(com.blackice.bc1.core.ExportPlanner.PREF_RESOLUTION,1080)),index->{if(exporting()){tell("Wait for the current export to finish.");renderInspector();return;}exportPrefs.edit().putInt(com.blackice.bc1.core.ExportPlanner.PREF_RESOLUTION,com.blackice.bc1.core.ExportPlanner.SHORT_EDGES[index]).apply();tell("Export resolution: "+com.blackice.bc1.core.ExportPlanner.RESOLUTION_LABELS[index]);renderInspector();});
            select("Export codec",com.blackice.bc1.core.VideoCodec.Choice.labels(),com.blackice.bc1.core.VideoCodec.Choice.parse(exportPrefs.getString(com.blackice.bc1.core.VideoCodec.Choice.PREF_KEY,null)).ordinal(),index->{if(exporting()){tell("Wait for the current export to finish.");renderInspector();return;}com.blackice.bc1.core.VideoCodec.Choice choice=com.blackice.bc1.core.VideoCodec.Choice.values()[index];exportPrefs.edit().putString(com.blackice.bc1.core.VideoCodec.Choice.PREF_KEY,choice.name()).apply();tell("Export codec: "+choice.label);renderInspector();});
            select("Export engine",com.blackice.bc1.core.ExportEngine.labels(),engine.ordinal(),index->{if(exporting()){tell("Wait for the current export to finish.");renderInspector();return;}exportPrefs.edit().putString(com.blackice.bc1.core.ExportEngine.PREF_KEY,com.blackice.bc1.core.ExportEngine.values()[index].name()).apply();tell("Export engine: "+com.blackice.bc1.core.ExportEngine.values()[index].label);renderInspector();});}
            addAction("Lock editing and layout: "+(uiLocked?"ON":"OFF"),this::toggleLock);
            addAction("Reduced motion: "+(reducedMotion?"ON":"OFF"),()->{reducedMotion=!reducedMotion;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("reduced-motion",reducedMotion).apply();renderInspector();});
            addAction("Preview / timeline space",this::chooseSpace);
            addAction("Snap to playhead, clip edges and markers: "+(snapping?"ON":"OFF"),()->{snapping=!snapping;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("snapping",snapping).apply();renderInspector();});
            addAction("Save project",()->{saveNow();tell("Saving project…");});
            addAction("Export project + media bundle",()->{if(!editable())return;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/zip").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"Black-Ice-project.bc1zip");startActivityForResult(i,BACKUP);});
            addAction("Restore project bundle",()->pick(RESTORE,"*/*"));
            addAction("Projects / New / Rename",()->projectsMenu());
            addAction("Diagnostics → Crash Logs",()->CrashLogs.show(this));
            addAction("Export diagnostics",()->exportDiagnostics());
            addAction("Device video capabilities",()->deviceVideoReport());
            addAction("Engine benchmark (8 layers × 1080p60)",()->engineBenchmark());

            inspector.addView(note("BC-1 v"+appVersion()+"\nExport: H.264 or HEVC MP4 + AAC, 1080p to 4K, 24 to 120 fps (device permitting); Media3 or ICE Lite engine. Lower-rate sources keep their cadence.\nSpace: play • Ctrl+Z/Y: undo/redo • Ctrl+S: save\nS: split • arrows: frame step • Ctrl+D: duplicate\nCtrl+wheel: zoom • Shift+wheel: scroll • right-click: clip menu\n3D Page Curl remains excluded."));
    }
    /** Layer rows, topmost first: text/shape/image layers, video layers (highest first), the main sequence, then audio. */
    java.util.List<int[]> layerRows(){
        java.util.List<int[]> rows=new ArrayList<>();
        java.util.Set<String> shown=new java.util.HashSet<>();
        for(int i=project.overlays.size()-1;i>=0;i--){String g=project.overlays.get(i).group;if(g!=null&&collapsedGroups.contains(g)){if(!shown.add(g))continue;}rows.add(new int[]{1,i});}
        // One empty visual layer and one empty audio layer stay visible as drop targets for new layers.
        int freeVisual=-1,freeAudio=-1;for(int t=1;t<=EditProject.LAST_LAYER&&freeVisual<0;t++)if(trackEmpty(t))freeVisual=t;for(int t=EditProject.AUDIO_FIRST;t<=EditProject.AUDIO_LAST&&freeAudio<0;t++)if(trackEmpty(t))freeAudio=t;
        for(int t=EditProject.LAST_LAYER;t>=0;t--){if(t==0||!trackEmpty(t)||t==freeVisual)rows.add(new int[]{0,t});}
        for(int t=EditProject.AUDIO_FIRST;t<=EditProject.AUDIO_LAST;t++){if(!trackEmpty(t)||t==freeAudio)rows.add(new int[]{0,t});}
        return rows;
    }
    boolean trackEmpty(int track){for(EditProject.Clip c:project.clips)if(c.track==track)return false;return true;}
    String rowName(int[] row){if(row[0]==1){EditProject.Overlay o=project.overlays.get(row[1]);if(o.group!=null&&collapsedGroups.contains(o.group))return "Group ("+groupSize(o.group)+")";return o.label();}return project.tracks[row[1]].name;}
    /** Collapsed text/shape/image groups take one timeline row; members still move and transform together. */
    final java.util.Set<String> collapsedGroups=new java.util.HashSet<>();
    int groupSize(String group){int n=0;for(EditProject.Overlay o:project.overlays)if(group.equals(o.group))n++;for(EditProject.Clip c:project.clips)if(group.equals(c.group))n++;return n;}
    void toggleGroupCollapsed(String group){if(!collapsedGroups.remove(group))collapsedGroups.add(group);refreshProject();}
    void layersPanel(){
        inspector.addView(note("Higher rows render above lower rows; transparent areas reveal layers underneath. The eye hides a layer in preview and export; Solo isolates it in the preview only. Locks prevent moving, trimming, editing and deletion."));
        for(int[] r:layerRows()){
            boolean overlayRow=r[0]==1;EditProject.Overlay o=overlayRow?project.overlays.get(r[1]):null;EditProject.Track t=overlayRow?null:project.tracks[r[1]];
            boolean audio=!overlayRow&&EditProject.audioTrack(r[1]),hidden=overlayRow?o.hidden:audio?t.muted:t.hidden,solo=overlayRow?o.solo:t.solo,locked=overlayRow?o.locked:t.locked;
            LinearLayout row=row();row.setGravity(Gravity.CENTER_VERTICAL);row.setAlpha(hidden?.55f:1);row.setPadding(0,dp(2),0,dp(2));
            View swatch=new View(this);android.graphics.drawable.GradientDrawable sw=new android.graphics.drawable.GradientDrawable();sw.setColor(overlayRow?o.color:t.color);sw.setCornerRadius(dp(3));swatch.setBackground(sw);swatch.setContentDescription("Layer color: tap to change");swatch.setFocusable(true);
            swatch.setOnClickListener(v->layerColor(r));row.addView(swatch,new LinearLayout.LayoutParams(dp(10),dp(44)));
            TextView name=label(rowName(r)+(overlayRow?"":"  ·  "+(r[1]==0?"main":audio?"audio":"video")),12,TEXT,true);name.setPadding(dp(8),0,dp(4),0);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setMinHeight(dp(48));name.setGravity(Gravity.CENTER_VERTICAL);
            name.setContentDescription("Layer "+rowName(r)+": tap to select, long-press to rename");name.setOnClickListener(v->{if(overlayRow)selectOverlay(r[1]);else{for(int i=0;i<project.clips.size();i++)if(project.clips.get(i).track==r[1]){selectClip(i,false);return;}}});
            name.setOnLongClickListener(v->{renameRow(r);return true;});
            if(overlayRow&&o.group!=null){boolean collapsed=collapsedGroups.contains(o.group);TextView fold=compactButton(collapsed?"▸":"▾",(collapsed?"Expand":"Collapse")+" group",v->toggleGroupCollapsed(o.group));row.addView(fold,new LinearLayout.LayoutParams(dp(40),dp(48)));}
            row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            row.addView(rowToggle(audio?"Mute":hidden?"Hidden":"Eye",(audio?(hidden?"Unmute ":"Mute "):(hidden?"Show ":"Hide "))+rowName(r),hidden,v->rowFlag(r,"hide")));
            row.addView(rowToggle("Solo",(solo?"Stop soloing ":"Solo ")+rowName(r),solo,v->rowFlag(r,"solo")));
            row.addView(rowToggle(locked?"Lock":"Unlock",(locked?"Unlock ":"Lock ")+rowName(r),locked,v->rowFlag(r,"lock")));
            row.addView(compactButton("▲","Move "+rowName(r)+" up",v->moveRow(r,true)),new LinearLayout.LayoutParams(dp(44),dp(48)));
            row.addView(compactButton("▼","Move "+rowName(r)+" down",v->moveRow(r,false)),new LinearLayout.LayoutParams(dp(44),dp(48)));
            inspector.addView(row);
        }
        if(project.music!=null)inspector.addView(note("Music: "+project.music.name+" (always below the timeline layers)"));
        inspector.addView(note("Use the up/down controls to reorder without changing timing. Long-press a timeline header for layer options."));
    }
    TextView rowToggle(String icon,String description,boolean on,View.OnClickListener l){TextView b=compactButton("",description,l);b.setCompoundDrawables(icon(icon,on?CYAN:TEXT),null,null,null);b.setPadding(dp(11),0,dp(11),0);b.setSelected(on);b.setBackground(buttonBackground(on));b.setMinWidth(dp(44));return b;}
    void rowFlag(int[] r,String flag){
        if(!editable())return;
        edit(flag.equals("solo")?"Solo changed (preview only)":flag.equals("lock")?"Layer lock changed":"Layer visibility changed",()->{
            if(r[0]==1){EditProject.Overlay o=project.overlays.get(r[1]);if(flag.equals("solo"))o.solo=!o.solo;else if(flag.equals("lock"))o.locked=!o.locked;else o.hidden=!o.hidden;}
            else{EditProject.Track t=project.tracks[r[1]];if(flag.equals("solo"))t.solo=!t.solo;else if(flag.equals("lock"))t.locked=!t.locked;else if(EditProject.audioTrack(r[1]))t.muted=!t.muted;else t.hidden=!t.hidden;}});
    }
    void renameRow(int[] r){if(!editable())return;numberDialog("Layer name",rowName(r),name->{if(name.isBlank()||name.length()>60)throw new IllegalArgumentException("Use a name of 1 to 60 characters.");edit("Layer renamed",()->{if(r[0]==1)project.overlays.get(r[1]).name=name;else project.tracks[r[1]].name=name;});});}
    static final int[] LAYER_COLORS={0xFF40BFFF,0xFF9FEAFF,0xFF6BE675,0xFFFFC145,0xFFFF4D6D,0xFFB388FF,0xFF8CA3B8};
    void layerColor(int[] r){if(!editable())return;String[] names={"Ice blue","Frost","Green","Amber","Red","Violet","Slate"};
        new AlertDialog.Builder(this).setTitle("Layer color").setItems(names,(d,i)->edit("Layer color changed",()->{if(r[0]==1){EditProject.Overlay o=project.overlays.get(r[1]);if(o.shape>0||o.imageUri==null)tell("Text and shape layers use their own fill color; row color follows it.");}else project.tracks[r[1]].color=LAYER_COLORS[i];})).show();}
    /** Reorders rows without touching timing: visual layers swap tracks, overlays change stacking order. */
    void moveRow(int[] r,boolean up){
        if(!editable())return;
        if(r[0]==1){int to=up?r[1]+1:r[1]-1;if(to<0||to>=project.overlays.size()){tell(up?"Already the top layer.":"Text, shape and image layers stay above video layers.");return;}
            if(project.overlays.get(r[1]).locked){tell("Layer locked • unlock it to reorder");return;}
            edit("Layer order changed",()->{LayerOps.moveOverlay(project,r[1],to);if(selectedOverlay==r[1])selectedOverlay=to;else if(selectedOverlay==to)selectedOverlay=r[1];});return;}
        int track=r[1];int target=EditProject.audioTrack(track)?(up?track-1:track+1):(up?track+1:track-1);
        if(track==0||target<1||target>=EditProject.TRACK_COUNT||EditProject.audioTrack(track)!=EditProject.audioTrack(target)){tell(track==0?"The main sequence stays at the bottom of the visual stack.":"No further layer in that direction.");return;}
        if(project.tracks[track].locked||project.tracks[target].locked){tell("Unlock both layers to reorder them.");return;}
        edit("Layer order changed",()->LayerOps.swapTracks(project,track,target));
    }
    // ---- Selection, top bar and contextual helpers --------------------------------------------------
    int multiCount(){return multiClips.size()+multiOverlays.size();}
    void pruneSelection(){multiClips.removeIf(i->i>=project.clips.size());multiOverlays.removeIf(i->i>=project.overlays.size());if(multiCount()==1){multiClips.clear();multiOverlays.clear();}}
    void deselect(){compareSnapshot=null;selected=-1;selectedOverlay=-1;multiClips.clear();multiOverlays.clear();panels.reset();renderInspector();}
    void baseline(){try{compareSnapshot=ProjectStore.encode(project);}catch(Exception e){compareSnapshot=null;}}
    void selectClip(int index,boolean seekToStart){if(index!=selected)baseline();selected=index;selectedOverlay=-1;multiClips.clear();multiOverlays.clear();tab=1;if(seekToStart)seek(project.placements().get(index).startMs);renderInspector();}
    void selectOverlay(int index){if(selected>=0||index!=selectedOverlay)baseline();selected=-1;selectedOverlay=index;multiClips.clear();multiOverlays.clear();tab=1;renderInspector();}
    /** Ctrl/Shift-click or long-press: add or remove a layer from the multi-selection; groups select together. */
    void toggleMulti(int clipIndex,int overlayIndex){
        if(multiCount()==0){if(selected>=0)multiClips.add(selected);if(selectedOverlay>=0)multiOverlays.add(selectedOverlay);}
        java.util.Set<Integer> set=clipIndex>=0?multiClips:multiOverlays;int index=clipIndex>=0?clipIndex:overlayIndex;
        if(!set.remove(index)){set.add(index);LayerOps.expandGroups(project,multiClips,multiOverlays);}
        if(clipIndex>=0){selected=multiClips.contains(clipIndex)?clipIndex:multiClips.isEmpty()?-1:multiClips.first();if(selected>=0)selectedOverlay=-1;}
        else if(selected<0)selectedOverlay=multiOverlays.contains(overlayIndex)?overlayIndex:multiOverlays.isEmpty()?-1:multiOverlays.first();
        if(multiCount()==1){selected=multiClips.isEmpty()?-1:multiClips.first();selectedOverlay=multiOverlays.isEmpty()?-1:multiOverlays.first();multiClips.clear();multiOverlays.clear();}
        tab=1;panels.reset();renderInspector();
    }
    java.util.List<Integer> selectedClips(){java.util.List<Integer> l=new ArrayList<>(multiClips);if(l.isEmpty()&&selected>=0)l.add(selected);return l;}
    java.util.List<Integer> selectedOverlays(){java.util.List<Integer> l=new ArrayList<>(multiOverlays);if(l.isEmpty()&&selectedOverlay>=0&&selected<0)l.add(selectedOverlay);return l;}
    /** Workspace lock (Settings) and per-layer locks are separate; both block edits. */
    boolean layerEditable(){if(!editable())return false;for(int i:selectedClips())if(LayerOps.locked(project,i)){tell("Layer locked • unlock it in Layers to edit");return false;}for(int i:selectedOverlays())if(project.overlays.get(i).locked){tell("Layer locked • unlock it in Layers to edit");return false;}return true;}
    void pausePlayback(){if(player!=null)player.pause();}
    /** Slider drag start: remember the project for one undo entry and switch to the live preview. */
    void beginLiveAdjust(){if(!layerEditable())return;try{liveBefore=ProjectStore.encode(project);}catch(Exception e){liveBefore=null;return;}if(player!=null||previewPending)releasePreview();main.removeCallbacks(warmPreview);previewProxy.cancel();}
    /** During a drag: apply the value and redraw at once, without validation or undo. */
    void liveAdjust(Runnable apply){if(liveBefore==null)return;try{apply.run();if(previewControls!=null)previewControls.invalidate();renderLive();}catch(RuntimeException ignored){}}
    void cancelLiveAdjust(){transformPanel.clearGesture();String before=liveBefore;liveBefore=null;if(before==null)return;try{project=ProjectStore.decode(before);}catch(Exception e){throw new IllegalStateException(e);}renderInspector();renderLive();if(previewControls!=null)previewControls.invalidate();}
    /** Drag end: validate and record a single undo entry from the drag start. */
    void endLiveAdjust(String message,Runnable apply){
        String before=liveBefore;liveBefore=null;if(before==null)return;
        try{apply.run();project.validate();if(!before.equals(ProjectStore.encode(project))){pushUndo(before);changed(message);}else showLive();}
        catch(Exception e){CrashLogs.record("Edit rejected",e);try{project=ProjectStore.decode(before);}catch(Exception ignored){}changed(e.getMessage()==null?"Value not applied":e.getMessage());}
    }
    /** Rebuilds the top bar: project / Settings / Export normally; layer name / Duplicate / Delete / More when selected. */
    void renderHeader(){
        if(monitorInfo!=null)monitorInfo.setText(project.name+"  ·  "+(project.portrait?"1080 × 1920 · 9:16":"1920 × 1080 · 16:9")+"  ·  "+project.frameRate+" fps");if(header==null)return;if(mobile!=null){mobile.header();return;}EditProject.Clip c=clip();EditProject.Overlay o=overlay();int count=multiCount();
        String key=count>1?"multi"+count:c!=null?"clip"+selected+c.name:o!=null?"overlay"+selectedOverlay+o.label():"none"+project.name+wideLayout;
        key+=":"+dirty+":"+tab+":"+addDrawer.category;if(key.equals(headerKey))return;headerKey=key;header.removeAllViews();Configuration cfg=getResources().getConfiguration();
        if(!wideLayout&&(count>1||c!=null||o!=null)){
            header.addView(compactButton("✕","Deselect",v->deselect()),new LinearLayout.LayoutParams(dp(48),dp(48)));
            TextView name=label(count>1?count+" layers selected":(c!=null?c.name:o.label()),14,TEXT,true);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(8),0,dp(4),0);
            name.setContentDescription(count>1?name.getText():"Selected layer "+name.getText()+". Tap to rename.");name.setMinHeight(dp(48));name.setGravity(Gravity.CENTER_VERTICAL);if(count<=1)name.setOnClickListener(v->renameSelection());
            header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            header.addView(headerIcon(count>1?"Group":"Duplicate",count>1?"Group selected layers":"Duplicate",v->{if(count>1)multiAction("Group");else duplicateSelected();}));
            header.addView(headerIcon("Delete","Delete",v->deleteSelection()));
            header.addView(headerIcon("More","More actions",v->moreMenu()));
            exportButton=null;projectLabel=null;return;
        }
        ImageView logo=new ImageView(this);logo.setImageDrawable(new V1Icon("Cube",BlackIceTheme.ICE));logo.setContentDescription("Black Ice cube logo");header.addView(logo,new LinearLayout.LayoutParams(dp(38),dp(38)));
        LinearLayout brand=column();brand.setPadding(dp(8),0,dp(4),0);TextView wordmark=label("BLACK ICE",wideLayout?17:14,TEXT,true);wordmark.setLetterSpacing(wideLayout?.22f:.08f);wordmark.setSingleLine(true);wordmark.setAutoSizeTextTypeUniformWithConfiguration(10,wideLayout?17:14,1,android.util.TypedValue.COMPLEX_UNIT_SP);brand.addView(wordmark);TextView sub=label("EDITOR",9,MUTED,false);sub.setLetterSpacing(.35f);brand.addView(sub);
        projectLabel=label(project.name+(dirty?" •":"")+"  ▾",10,MUTED,false);projectLabel.setMaxLines(1);projectLabel.setEllipsize(android.text.TextUtils.TruncateAt.END);brand.addView(projectLabel);brand.setOnClickListener(v->projectsMenu());brand.setContentDescription("Project menu");brand.setFocusable(true);brand.setMinimumHeight(dp(52));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        if(wideLayout){
            header.addView(headerIcon("Undo","Undo",v->undo(false)));header.addView(headerIcon("Redo","Redo",v->undo(true)));
            View gap=new View(this);header.addView(gap,new LinearLayout.LayoutParams(dp(10),1));
            TextView save=labelledIcon("Save","Save","Save project",v->{saveNow();tell("Saving project…");});header.addView(save);
        }
        header.addView(labelledIcon("Settings","Settings","Settings",v->{tab=4;renderInspector();}));
        exportButton=button("Export",v->exportMenu());exportButton.setTextSize(14);exportButton.setTextColor(BlackIceTheme.ON_ACCENT);exportButton.setBackground(BlackIceTheme.primary(getResources().getDisplayMetrics().density));LinearLayout.LayoutParams exportLp=new LinearLayout.LayoutParams(-2,dp(48));exportLp.leftMargin=dp(8);exportButton.setPadding(dp(22),0,dp(22),0);header.addView(exportButton,exportLp);
    }
    /** Top-bar section tabs exist only in roomy wide windows. */
    boolean topTabs(){return wideLayout&&getResources().getConfiguration().screenWidthDp>=1100;}
    TextView labelledIcon(String icon,String title,String description,View.OnClickListener l){TextView b=button(title,l);b.setContentDescription(description);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT);b.setCompoundDrawables(icon(icon,TEXT),null,null,null);b.setCompoundDrawablePadding(dp(8));b.setPadding(dp(12),0,dp(14),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(44));lp.leftMargin=dp(4);b.setLayoutParams(lp);return b;}
    android.graphics.drawable.Drawable panelBackground(){GradientDrawable d=new GradientDrawable();d.setColor(0xF0081320);d.setCornerRadius(dp(8));d.setStroke(dp(1),0xFF132A3D);return d;}
    /** Thin divider with a grip in the middle, for the resizable panel and timeline edges. */
    android.graphics.drawable.Drawable splitter(boolean vertical){return new android.graphics.drawable.Drawable(){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        public void draw(Canvas c){Rect b=getBounds();p.setColor(0xFF0F2131);if(vertical)c.drawRect(b.centerX()-dp(.5f),b.top,b.centerX()+dp(.5f),b.bottom,p);else c.drawRect(b.left,b.centerY()-dp(.5f),b.right,b.centerY()+dp(.5f),p);
            p.setColor(0xFF3A5A74);if(vertical)c.drawRoundRect(b.centerX()-dp(1.5f),b.centerY()-dp(16),b.centerX()+dp(1.5f),b.centerY()+dp(16),dp(2),dp(2),p);else c.drawRoundRect(b.centerX()-dp(16),b.centerY()-dp(1.5f),b.centerX()+dp(16),b.centerY()+dp(1.5f),dp(2),dp(2),p);}
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};}
    /** Inspector list entry: leading icon, left-aligned label, full width. */
    TextView menuRow(String icon,String title,View.OnClickListener l){TextView b=button(title,l);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT);b.setCompoundDrawables(icon(icon,TEXT),null,null,null);b.setCompoundDrawablePadding(dp(14));b.setPadding(dp(16),0,dp(12),0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.topMargin=dp(8);inspector.addView(b,lp);return b;}
    TextView headerIcon(String icon,String description,View.OnClickListener l){TextView b=compactButton("",description,l);b.setCompoundDrawables(icon(icon,TEXT),null,null,null);b.setPadding(dp(12),0,dp(12),0);b.setMinWidth(dp(48));return b;}
    android.graphics.drawable.Drawable icon(String name,int color){V1Icon icon=new V1Icon(name,color);icon.setBounds(0,0,dp(22),dp(22));return icon;}
    /** Two-column (three in wide mode) grid of equal tool buttons. */
    void gridAction(String title,Runnable action){int columns=wideLayout?3:2;if(gridRow==null||gridCount%columns==0){gridRow=row();LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(5);inspector.addView(gridRow,rp);}
        TextView b=button(title,v->action.run());b.setTextSize(11);LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(wideLayout?44:54),1);cell.rightMargin=dp(4);gridRow.addView(b,cell);gridCount++;}
    void flushGrid(){if(gridRow!=null){int columns=wideLayout?3:2;while(gridCount%columns!=0){gridRow.addView(new View(this),new LinearLayout.LayoutParams(0,dp(wideLayout?44:54),1));gridCount++;}}gridRow=null;gridCount=0;}
    void setStatic(int property,float value){EditProject.Clip c=clip();EditProject.Overlay o=overlay();
        if(c!=null)switch(property){case Motion.X:c.x=value;break;case Motion.Y:c.y=value;break;case Motion.HEIGHT:c.heightScale=value;break;case Motion.SCALE:c.scale=value;break;case Motion.ROTATION:c.rotation=value;break;case Motion.OPACITY:c.opacity=value;break;case Motion.INTENSITY:c.intensity=value;break;case Motion.SKEW_X:c.skewX=value;break;case Motion.SKEW_Y:c.skewY=value;break;case Motion.ANCHOR_X:c.anchorX=value;break;case Motion.ANCHOR_Y:c.anchorY=value;break;}
        else if(o!=null)switch(property){case Motion.X:o.x=value;break;case Motion.Y:o.y=value;break;case Motion.HEIGHT:o.heightScale=value;break;case Motion.SCALE:o.scale=value;break;case Motion.ROTATION:o.rotation=value;break;case Motion.OPACITY:o.opacity=value;break;case Motion.SKEW_X:o.skewX=value;break;case Motion.SKEW_Y:o.skewY=value;break;case Motion.ANCHOR_X:o.anchorX=value;break;case Motion.ANCHOR_Y:o.anchorY=value;break;}
    }
    void renameSelection(){EditProject.Clip c=clip();EditProject.Overlay o=overlay();if((c==null&&o==null)||!layerEditable())return;
        numberDialog("Layer name",c!=null?c.name:o.label(),name->{if(name.isBlank()||name.length()>60)throw new IllegalArgumentException("Use a name of 1 to 60 characters.");edit("Layer renamed",()->{if(c!=null)c.name=name;else o.name=name;});});}
    void deleteSelection(){
        java.util.List<Integer> clips=selectedClips(),overlays=selectedOverlays();if(clips.isEmpty()&&overlays.isEmpty()||!layerEditable())return;
        edit((clips.size()+overlays.size())>1?"Layers deleted • Undo is available":"Layer deleted • Undo is available",()->{
            java.util.List<Integer> cs=new ArrayList<>(clips),os=new ArrayList<>(overlays);cs.sort(java.util.Collections.reverseOrder());os.sort(java.util.Collections.reverseOrder());
            for(int i:cs)project.clips.remove(i);for(int i:os)project.overlays.remove(i);selected=-1;selectedOverlay=-1;multiClips.clear();multiOverlays.clear();});
        panels.reset();
    }
    void moreMenu(){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();boolean multi=multiCount()>1;java.util.List<String> items=new ArrayList<>();
        if(multi)java.util.Collections.addAll(items,"Align starts","Align ends","Match duration","Close gaps on selected tracks","Group","Ungroup","Copy attributes…","Paste attributes…","Save as element","Duplicate","Delete");
        else{
            if(o!=null){if(o.imageUri==null&&o.shape==0)java.util.Collections.addAll(items,"Edit Text","Font","Size","Color","Alignment");else if(o.shape>0)items.add("Color");items.add("Duration");}
            if(c!=null&&c.image)items.add("Duration");
            items.add("Rename");if(c!=null&&c.track<EditProject.AUDIO_FIRST||o!=null&&o.imageUri!=null)items.add("Replace media…");
            java.util.Collections.addAll(items,"Copy attributes…","Paste attributes…","Copy keyframes at playhead","Paste keyframes at playhead","Save as element","Duplicate Above","Duplicate After");
            if(c!=null&&!c.image){items.add("Playback Speed: "+ToolPanels.trimZero(c.speed)+"×");items.add("Slip");}
            if(c!=null||o!=null)items.add("Animation");
            if(c!=null)items.add("Move to layer / start time…");
            if(c!=null||o!=null)items.add("Close gaps on this track");
            items.add(selectionLocked()?"Unlock layer":"Lock layer");items.add(selectionHidden()?"Show layer":(c!=null&&c.track>=EditProject.AUDIO_FIRST?"Mute layer":"Hide layer"));
            if(c==null||c.track<EditProject.AUDIO_FIRST)java.util.Collections.addAll(items,"Locate selected layer","Center on canvas");
            items.add("Select multiple…");items.add("Delete");
        }
        String[] names=items.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(multi?multiCount()+" layers":"More").setItems(names,(d,i)->moreAction(names[i])).setNegativeButton("Close",null).show();
    }
    boolean selectionLocked(){EditProject.Clip c=clip();EditProject.Overlay o=overlay();return c!=null?project.tracks[c.track].locked:o!=null&&o.locked;}
    boolean selectionHidden(){EditProject.Clip c=clip();EditProject.Overlay o=overlay();return c!=null?(c.track>=EditProject.AUDIO_FIRST?project.tracks[c.track].muted:project.tracks[c.track].hidden):o!=null&&o.hidden;}
    void moreAction(String name){
        if(name.startsWith("Playback Speed:")){panels.runTool("Speed");return;}
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();
        switch(name){
            case "Slip":case "Animation":case "Edit Text":case "Font":case "Size":case "Color":case "Alignment":case "Duration":panels.runTool(name);break;
            case "Rename":renameSelection();break;
            case "Replace media…":if(layerEditable())pick(REPLACE,c!=null?(c.image?"image/*":"video/*"):"image/*");break;
            case "Copy attributes…":copyAttributes();break;
            case "Paste attributes…":pasteAttributes();break;
            case "Copy keyframes at playhead":copyKeys();break;
            case "Paste keyframes at playhead":pasteKeys();break;
            case "Save as element":saveElement();break;
            case "Duplicate Above":duplicate(true);break;
            case "Duplicate After":case "Duplicate":duplicateSelected();break;
            case "Move to layer / start time…":placementDialog();break;
            case "Close gaps on this track":if(layerEditable()){if(c!=null)edit("Gaps closed",()->LayerOps.closeGaps(project,java.util.List.of(c.track)));else tell("Text, shape and image layers each have their own row.");}break;
            case "Lock layer":case "Unlock layer":toggleTrackFlag(c,o,"lock");break;
            case "Hide layer":case "Show layer":case "Mute layer":toggleTrackFlag(c,o,"hide");break;
            case "Locate selected layer":locateSelected();break;
            case "Center on canvas":if(layerEditable())edit("Centered on canvas",()->alignLayer(0,0,true,true));break;
            case "Select multiple…":tell("Long-press timeline clips (or Ctrl/Shift-click) to add them to the selection.");if(multiCount()==0){if(selected>=0)multiClips.add(selected);else if(selectedOverlay>=0)multiOverlays.add(selectedOverlay);}renderInspector();break;
            case "Delete":deleteSelection();break;
            default:multiAction(name);break;
        }
    }
    /** Layer-row flags are edits too (undoable), but are allowed on locked rows so they can be unlocked. */
    void toggleTrackFlag(EditProject.Clip c,EditProject.Overlay o,String flag){
        if(!editable())return;
        edit(flag.equals("lock")?"Layer lock changed":"Layer visibility changed",()->{
            if(c!=null){EditProject.Track t=project.tracks[c.track];if(flag.equals("lock"))t.locked=!t.locked;else if(c.track>=EditProject.AUDIO_FIRST)t.muted=!t.muted;else t.hidden=!t.hidden;}
            else if(o!=null){if(flag.equals("lock"))o.locked=!o.locked;else o.hidden=!o.hidden;}});
    }
    void multiAction(String name){
        java.util.List<Integer> clips=selectedClips(),overlays=selectedOverlays();if(!layerEditable())return;
        long reference=clips.isEmpty()?project.overlays.get(overlays.get(0)).startMs:project.placements().get(selected>=0?selected:clips.get(0)).startMs;
        long referenceEnd=clips.isEmpty()?project.overlays.get(overlays.get(0)).endMs:project.placements().get(selected>=0?selected:clips.get(0)).endMs;
        switch(name){
            case "Align starts":edit("Starts aligned",()->LayerOps.align(project,clips,overlays,false,reference));break;
            case "Align ends":edit("Ends aligned",()->LayerOps.align(project,clips,overlays,true,referenceEnd));break;
            case "Match duration":edit("Durations matched",()->{java.util.List<String> skipped=LayerOps.matchDuration(project,clips,overlays,referenceEnd-reference);if(!skipped.isEmpty())main.post(()->tell("Kept source limits for: "+String.join(", ",skipped)));});break;
            case "Close gaps":case "Close gaps on selected tracks":{java.util.Set<Integer> tracks=new java.util.TreeSet<>();for(int i:clips)tracks.add(project.clips.get(i).track);edit("Gaps closed on "+tracks.size()+" track(s)",()->LayerOps.closeGaps(project,tracks));break;}
            case "Group":edit("Layers grouped",()->LayerOps.group(project,clips,overlays));break;
            case "Ungroup":edit("Layers ungrouped",()->LayerOps.ungroup(project,clips,overlays));break;
            case "Save as element":saveElement();break;
            case "Copy attributes…":copyAttributes();break;
            case "Paste attributes…":pasteAttributes();break;
            case "Duplicate":edit("Layers duplicated",()->{java.util.List<Integer> cs=new ArrayList<>(clips);cs.sort(java.util.Collections.reverseOrder());for(int i:cs)LayerOps.duplicateAbove(project,i);java.util.List<Integer> os=new ArrayList<>(overlays);os.sort(java.util.Collections.reverseOrder());for(int i:os)LayerOps.duplicateOverlay(project,i,false);multiClips.clear();multiOverlays.clear();});break;
            case "Delete":deleteSelection();break;
        }
    }
    static final String[] ATTRIBUTE_NAMES=Attributes.NAMES;
    void copyAttributes(){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null){tell("Select a layer to copy from.");return;}
        boolean[] chosen={true,true,o!=null,false};
        new AlertDialog.Builder(this).setTitle("Copy attributes").setMultiChoiceItems(ATTRIBUTE_NAMES,chosen,(d,i,on)->chosen[i]=on).setPositiveButton("Copy",(d,w)->{
            int mask=0;for(int i=0;i<4;i++)if(chosen[i])mask|=1<<i;copiedAttributes=c!=null?Attributes.of(c,mask):Attributes.of(o,mask);tell("Copied: "+Attributes.names(copiedAttributes.categories));}).setNegativeButton("Cancel",null).show();
    }
    void pasteAttributes(){
        if(copiedAttributes==null){tell("Copy attributes from a layer first.");return;}if(!layerEditable())return;
        java.util.List<String> names=new ArrayList<>();java.util.List<Integer> masks=new ArrayList<>();for(int i=0;i<4;i++)if((copiedAttributes.categories&(1<<i))!=0){names.add(ATTRIBUTE_NAMES[i]);masks.add(1<<i);}
        boolean[] chosen=new boolean[names.size()];java.util.Arrays.fill(chosen,true);
        new AlertDialog.Builder(this).setTitle("Paste attributes").setMultiChoiceItems(names.toArray(new String[0]),chosen,(d,i,on)->chosen[i]=on).setPositiveButton("Paste",(d,w)->{
            int mask=0;for(int i=0;i<chosen.length;i++)if(chosen[i])mask|=masks.get(i);int requested=mask;
            edit("Attributes pasted",()->{int applied=0;for(int i:selectedClips())applied|=copiedAttributes.applyTo(project.clips.get(i),requested);for(int i:selectedOverlays())applied|=copiedAttributes.applyTo(project.overlays.get(i),requested);if(applied==0)throw new IllegalArgumentException("None of the copied attributes apply to this layer.");});}).setNegativeButton("Cancel",null).show();
    }
    Motion selectedMotion(){EditProject.Clip c=clip();EditProject.Overlay o=overlay();return c!=null?c.motion:o!=null?o.motion:null;}
    void copyKeys(){Motion m=selectedMotion();if(m==null)return;copiedKeys=Keyframes.copy(m,panels.motionTime(),panels.tolerance(),Keyframes.all());tell(copiedKeys.isEmpty()?"No keyframes to copy on this layer.":"Keyframes copied ("+copiedKeys.keys.size()+" properties)");}
    void pasteKeys(){Motion m=selectedMotion();if(m==null)return;if(copiedKeys==null||copiedKeys.isEmpty()){tell("Copy keyframes first.");return;}if(!layerEditable())return;long t=panels.motionTime();edit("Keyframes pasted",()->Keyframes.paste(m,copiedKeys,t));}
    void jumpKeyframe(boolean next){
        Motion m=selectedMotion();if(m==null){tell("Select an animated layer to step between keyframes.");return;}
        long t=Keyframes.adjacent(m,panels.motionTime(),next,Keyframes.all());if(t<0){tell(next?"No later keyframe":"No earlier keyframe");return;}
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();long timeline=c!=null?project.placements().get(selected).startMs+Math.round((t-c.inMs)/(double)c.speed):o.startMs+t;
        seek(timeline);renderInspector();
    }
    void savePreset(String kind,int categories){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null)return;Attributes attributes=c!=null?Attributes.of(c,categories):Attributes.of(o,categories);
        if(attributes.categories==0){tell("Nothing to save for this layer.");return;}
        numberDialog("Preset name",kind.equals(PresetStore.ANIMATION)?"My animation":"My look",name->{presets.save(name,kind,attributes.encode());tell("Preset saved");renderInspector();});
    }
    void deletePreset(PresetStore.Preset preset){new AlertDialog.Builder(this).setTitle("Delete “"+preset.name+"”?").setPositiveButton("Delete",(d,w)->{try{presets.delete(preset.id);renderInspector();}catch(IllegalArgumentException e){tell(e.getMessage());}}).setNegativeButton("Cancel",null).show();}
    void saveElement(){
        java.util.List<Integer> clips=new ArrayList<>(),overlays=new ArrayList<>();for(int i:selectedClips())if(project.clips.get(i).track<EditProject.AUDIO_FIRST)clips.add(i);overlays.addAll(selectedOverlays());
        if(clips.isEmpty()&&overlays.isEmpty()){tell("Select visual layers to save as an element.");return;}
        String payload=PresetStore.element(project,clips,overlays);numberDialog("Element name","My element",name->{presets.save(name,PresetStore.ELEMENT,payload);tell("Saved element • find it in + Add → Saved");});
    }
    void insertElement(PresetStore.Preset preset,long at){edit("Element added",()->{int added=PresetStore.insertElement(project,preset.payload,at);selected=-1;selectedOverlay=-1;multiClips.clear();multiOverlays.clear();tell("Added "+added+" layer(s)");});}
    void addOverlay(int shape,String text,float scale){
        if(!editable())return;if(project.overlays.size()>=20){tell("Up to 20 text, shape and image layers.");return;}
        long at=addDrawer.insertTime();
        edit(shape>0?EditProject.SHAPES[shape]+" added":"Text added",()->{EditProject.Overlay o=new EditProject.Overlay();o.shape=shape;if(shape==0)o.text=text;else{o.text=EditProject.SHAPES[shape];o.y=0;o.color=0xFF40BFFF;}o.scale=Math.max(.1f,Math.min(2,scale*.7f));
            o.startMs=at;o.endMs=at+Math.max(EditProject.MIN_CLIP_MS,Math.min(3000,Math.max(project.durationMs()-at,3000)));project.overlays.add(o);selected=-1;selectedOverlay=project.overlays.size()-1;tab=1;});
    }
    void toggleMarker(){if(!editable())return;long tolerance=Math.max(1,Math.round(dp(6)*1000/(timeline==null?dp(48):timeline.timelineScale())));long t=project.playheadMs;edit("Marker toggled",()->LayerOps.toggleMarker(project,t,tolerance));}
    void jumpMarker(boolean next){long now=project.playheadMs,best=-1;for(long m:project.markers){if(next&&m>now&&(best<0||m<best))best=m;if(!next&&m<now&&(best<0||m>best))best=m;}if(best<0){tell(next?"No later marker":"No earlier marker");return;}seek(best);}
    /** Approximate beat markers from the selected audio clip (or the music track), added in one undo step. */
    void detectBeats(){
        EditProject.Clip c=clip();boolean useClip=c!=null&&!c.image;if(!useClip&&project.music==null){tell("Select a video or audio clip, or add music, to detect beats.");return;}if(!editable())return;
        String uri=useClip?c.uri:project.music.uri;long sourceDuration=useClip?c.sourceDurationMs:project.music.sourceDurationMs;tell("Detecting beats…");
        long in=useClip?c.inMs:project.music.inMs,out=useClip?c.outMs:project.music.outMs,start=useClip?project.placements().get(selected).startMs:project.music.startMs;float speed=useClip?c.speed:1;boolean loop=!useClip&&project.music.loop;
        io.execute(()->{try{float[] peaks=waveforms.containsKey(uri)?waveforms.get(uri):AudioWaveform.read(this,uri,sourceDuration);java.util.List<Long> beats=Beats.detect(peaks,sourceDuration,250);
            main.post(()->{waveforms.put(uri,peaks);long duration=project.durationMs();java.util.TreeSet<Long> times=new java.util.TreeSet<>(project.markers);
                for(long offset=start;offset<duration;offset+=Math.max(EditProject.MIN_CLIP_MS,Math.round((out-in)/speed))){for(long b:beats)if(b>=in&&b<out){long t=offset+Math.round((b-in)/speed);if(t<=duration)times.add(t);}if(!loop)break;}
                java.util.List<Long> list=new ArrayList<>(times);if(list.size()>500)list=list.subList(0,500);java.util.List<Long> result=list;
                edit("Beat markers added ("+Math.max(0,result.size()-project.markers.size())+")",()->{project.markers.clear();project.markers.addAll(result);});});
        }catch(Exception e){main.post(()->tell("Beat detection: "+e.getMessage()));}});
    }
    void loopDialog(){
        if(project.loopInMs>=0){edit("Loop range cleared",()->{project.loopInMs=-1;project.loopOutMs=-1;});return;}
        EditProject.Clip c=clip();long start=c!=null?project.placements().get(selected).startMs:project.playheadMs,end=c!=null?project.placements().get(selected).endMs:Math.min(project.durationMs(),project.playheadMs+2000);
        LinearLayout f=form();EditText in=field(f,"Loop start (seconds)",seconds(start)),out=field(f,"Loop end (seconds)",seconds(end));f.addView(note("Playback repeats this range in the preview. Export is unaffected."));
        dialog("Loop range",f,()->{long a=parseMs(in.getText().toString().trim()),b=parseMs(out.getText().toString().trim());edit("Loop range set",()->{project.loopInMs=a;project.loopOutMs=b;});},null);
    }
    void openAppSettings(){try{startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.fromParts("package",getPackageName(),null)));}catch(ActivityNotFoundException e){tell("Open Android Settings → Apps → Black Ice to manage media access.");}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==MEDIA_ACCESS){addDrawer.refresh();renderInspector();}}
    /** Replaces media, keeping timing, transforms, effects and animation; explains source-length conflicts. */
    void replaceMedia(Uri uri){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null)return;importing=true;tell("Copying replacement media…");
        io.execute(()->{try{EditProject.Clip fresh=new MediaImporter(this).importClip(uri,false,o!=null?1024:2048);main.post(()->{importing=false;if(isDestroyed())return;
            if(o!=null){edit("Media replaced",()->o.imageUri=fresh.uri);return;}
            if(c.image!=fresh.image){tell(c.image?"Replace a picture with another picture.":"Replace a video with another video.");return;}
            long needed=c.outMs-c.inMs;
            Runnable apply=()->edit("Media replaced",()->{c.uri=fresh.uri;c.name=fresh.name;c.width=fresh.width;c.height=fresh.height;
                if(c.image){c.sourceDurationMs=Math.max(fresh.sourceDurationMs,c.outMs);return;}
                c.sourceDurationMs=fresh.sourceDurationMs;if(c.outMs>fresh.sourceDurationMs){long length=Math.min(needed,fresh.sourceDurationMs);c.inMs=Math.max(0,fresh.sourceDurationMs-length);if(c.outMs-c.inMs>length||c.outMs>fresh.sourceDurationMs){c.inMs=0;c.outMs=length;}}});
            if(!c.image&&fresh.sourceDurationMs<needed)new AlertDialog.Builder(this).setTitle("Replacement is shorter").setMessage("The new video is "+seconds(fresh.sourceDurationMs)+" s, but this clip uses "+seconds(needed)+" s of source. Replacing will shorten the clip to "+seconds(fresh.sourceDurationMs)+" s; later main-sequence clips move earlier. Transforms, effects and animation are kept.")
                .setPositiveButton("Shorten and replace",(d,w)->apply.run()).setNegativeButton("Cancel",null).show();
            else apply.run();});}catch(Exception e){main.post(()->{importing=false;tell("Replace failed: "+e.getMessage());});}});
    }
    // ---- Transform helpers used by TransformPanel and preview editing -------------------------------
    int[] exportSize(){return project.portrait?new int[]{1080,1920}:new int[]{1920,1080};}
    double frameAspect(){int[] s=exportSize();return s[0]/(double)s[1];}
    float aspectOf(){return transformPanel.current().aspect;}
    void setAspect(float aspect){EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c!=null&&c.heightScale>=0||o!=null&&o.heightScale>=0)transformPanel.write(Motion.HEIGHT,transformPanel.current().scale*aspect);if(c!=null)c.aspect=aspect;else if(o!=null)o.aspect=aspect;}
    void setFlip(boolean horizontal){EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c!=null){if(horizontal)c.flipH=!c.flipH;else c.flipV=!c.flipV;}else if(o!=null){if(horizontal)o.flipH=!o.flipH;else o.flipV=!o.flipV;}}
    /** Local content half-size in frame units (before the layer's own transform). */
    double[] layerHalf(){return layerHalf(clip(),overlay());}
    double[] layerHalf(EditProject.Clip c,EditProject.Overlay o){
        double A=frameAspect();
        if(c!=null)return c.width>0&&c.height>0?TransformMath.fittedHalf(A,c.width/(double)c.height):new double[]{A/2,.5};
        if(o==null)return new double[]{A/2,.5};
        if(o.shape>0){double h=Math.min(A,1)*.2;return new double[]{h,o.shape==4?Math.max(.004,o.borderWidth*.025):h};}
        if(o.imageUri!=null)return new double[]{A*.225,A*.225*.75};
        double size=Math.min(A,1)*.065;String[] lines=o.text.split("\n",-1);int longest=1;for(String l:lines)longest=Math.max(longest,l.length());
        return new double[]{Math.min(A*.45,size*.3*longest),size*.65*lines.length};
    }
    void moveAnchor(float ax,float ay){TransformMath.Transform t=transformPanel.current();float[] p=TransformMath.repositionForAnchor(t,frameAspect(),ax,ay);transformPanel.write(Motion.X,p[0]);transformPanel.write(Motion.Y,p[1]);transformPanel.write(Motion.ANCHOR_X,ax);transformPanel.write(Motion.ANCHOR_Y,ay);}
    void alignLayer(int horizontal,int vertical,boolean x,boolean y){double[] half=layerHalf();float[] p=TransformMath.align(transformPanel.current(),frameAspect(),half[0],half[1],horizontal,vertical,x,y);if(x)transformPanel.write(Motion.X,p[0]);if(y)transformPanel.write(Motion.Y,p[1]);}
    void nudge(int pixelsX,int pixelsY){if(clip()==null&&overlay()==null||!layerEditable())return;int[] size=exportSize();TransformMath.Transform t=transformPanel.current();float x=t.x+pixelsX*TransformMath.pixelX(size[0]),y=t.y+pixelsY*TransformMath.pixelY(size[1]);edit("Nudged",()->{transformPanel.write(Motion.X,x);transformPanel.write(Motion.Y,y);});}
    /** Fit (0), Fill (1) or Original Size (2); source dimensions are measured once when unknown. */
    void fitLayer(int mode){
        EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null||!layerEditable())return;
        if(c!=null&&(c.width<=0||c.height<=0)){tell("Measuring source size…");io.execute(()->{int[] size=MediaImporter.measure(this,c.uri,c.image);main.post(()->{if(size==null){tell("Cannot read this media's size.");return;}c.width=size[0];c.height=size[1];fitLayer(mode);});});return;}
        double A=frameAspect();int[] export=exportSize();
        edit(new String[]{"Fit","Fill","Original size"}[mode]+" applied",()->{
            float scale;
            if(c!=null){double content=c.width/(double)c.height;scale=mode==0?1:mode==1?TransformMath.fillScale(A,content):TransformMath.originalScale(export[0],export[1],c.width,c.height);}
            else{double[] half=layerHalf();double fit=Math.min(A/2/half[0],.5/half[1]),fill=Math.max(A/2/half[0],.5/half[1]);scale=(float)(transformPanel.current().scale*(mode==0?fit:mode==1?fill:1/transformPanel.current().scale));}
            transformPanel.write(Motion.SCALE,TransformMath.clamp(scale,.05f,4));setAspect(1);
            if(mode<2){transformPanel.write(Motion.X,0);transformPanel.write(Motion.Y,0);}
        });
    }
    /** Pans the preview so an offscreen layer is visible; the layer itself is not moved. */
    void locateSelected(){
        if(clip()==null&&overlay()==null)return;double[] half=layerHalf();TransformMath.Transform t=transformPanel.current();double A=frameAspect();
        // Bounds in a view where the frame spans [0,A] × [0,1]; zoom out until frame and layer both fit.
        float[] b=TransformMath.bounds(TransformMath.corners(t,(float)A,1,half[0],half[1]));
        float left=Math.min(0,b[0]),top=Math.min(0,b[1]),right=Math.max((float)A,b[2]),bottom=Math.max(1,b[3]);
        previewZoom=TransformMath.clamp(Math.min((float)A/(right-left),1/(bottom-top)),.2f,1);
        previewPanX=(float)(.5-(left+right)/2/A);previewPanY=.5f-(top+bottom)/2;if(monitor!=null)monitor.requestLayout();
        tell(TransformMath.offscreen(t,A,half[0],half[1])?"Layer is outside the frame • preview zoomed out to show it (tap Fit view to reset)":"Selected layer is in view");if(previewControls!=null)previewControls.invalidate();
    }
    void resetPreviewView(){previewZoom=1;previewPanX=0;previewPanY=0;if(monitor!=null)monitor.requestLayout();if(previewControls!=null)previewControls.invalidate();}
    /** Before/after: hold Compare to preview the project as it was when the current layer was selected. */
    void compare(boolean start){
        if(start){if(compareSnapshot==null){tell("Select a layer and adjust it to compare before and after.");return;}comparing=true;showLive();}
        else if(comparing){comparing=false;showLive();}
        if(previewControls!=null)previewControls.invalidate();
    }
    void previewMove(float x,float y){previewMoveX=x;previewMoveY=y;if(previewControls!=null)previewControls.invalidate();}
    EditProject.Overlay overlay(){return selected<0&&selectedOverlay>=0&&selectedOverlay<project.overlays.size()?project.overlays.get(selectedOverlay):null;}
    void overlayTools(){EditProject.Overlay o=overlay();inspector.addView(note(o.imageUri==null?"Text: "+o.text:"Image overlay"));
        if(o.imageUri==null){addAction("Edit Text",()->numberDialog("Edit Text",o.text,value->edit("Text updated",()->o.text=value)));
            addAction("Font",()->new AlertDialog.Builder(this).setTitle("Font").setItems(new String[]{"Sans serif","Serif","Monospace"},(d,i)->edit("Font updated",()->o.font=new String[]{"sans-serif-medium","serif","monospace"}[i])).show());
            addAction("Size",()->numberDialog("Text size (0.1 to 2)",String.valueOf(o.scale),value->edit("Text size updated",()->o.scale=Float.parseFloat(value))));
            addAction("Color",()->numberDialog("Text color (#RRGGBB)",String.format(Locale.US,"#%06X",o.color&0xFFFFFF),value->edit("Text color updated",()->o.color=Color.parseColor(value))));
            addAction("Alignment",()->new AlertDialog.Builder(this).setTitle("Alignment at text anchor").setItems(new String[]{"Left","Center","Right"},(d,i)->edit("Alignment updated",()->o.alignment=i)).show());}
        addAction("Duration: "+seconds(o.endMs-o.startMs)+" s",()->numberDialog("Duration (seconds)",seconds(o.endMs-o.startMs),value->edit("Duration updated",()->{long length=parseMs(value);if(length<100)throw new IllegalArgumentException("Keep at least 0.10 seconds");o.endMs=Math.addExact(o.startMs,length);})));
        addAction("Transform",()->{LinearLayout f=form();EditText x=field(f,"X (−1 to 1)",String.valueOf(o.x)),y=field(f,"Y (−1 to 1)",String.valueOf(o.y)),size=field(f,"Scale (0.1 to 2)",String.valueOf(o.scale));dialog("Overlay transform",f,()->edit("Transform updated",()->{o.x=parseFloat(x);o.y=parseFloat(y);o.scale=parseFloat(size);}),null);});
        addAction("Opacity",()->percentPanel("Opacity",o.opacity,1,value->o.opacity=value));
        addAction("Keyframes",()->motionDialog(o.motion,Math.max(0,project.playheadMs-o.startMs),o.endMs-o.startMs,new float[]{o.x,o.y,o.scale,0,o.opacity,0},false));
        addAction("Timing / advanced",()->overlayDialog(o));
        addAction("Remove overlay",()->edit("Overlay removed",()->{project.overlays.remove(o);selectedOverlay=-1;}));
    }
    /** Runs ICE Lite on a generated 8-layer 1080p60 project and reports speed and a 15-minute estimate. */
    void engineBenchmark(){
        if(exporting()){tell("Wait for the current export to finish.");return;}
        java.util.concurrent.atomic.AtomicBoolean stop=new java.util.concurrent.atomic.AtomicBoolean();
        AlertDialog running=new AlertDialog.Builder(this).setTitle("Engine benchmark").setMessage("Generating a 1080p60 test clip, then exporting 8 layers of it for 10 seconds…\nKeep the app open.")
                .setNegativeButton("Stop",(d,w)->stop.set(true)).setCancelable(false).show();
        new Thread(()->{String report;
            try{report=com.blackice.bc1.engine.bc.EngineBenchmark.run(this,8,60,10_000,stop,p->main.post(()->{if(running.isShowing())running.setMessage("Exporting 8 layers × 1080p60… "+p+"%");})).describe();}
            catch(java.util.concurrent.CancellationException e){report=null;}
            catch(Throwable t){report="Benchmark failed: "+t;}
            final String text=report;
            main.post(()->{if(running.isShowing())running.dismiss();if(text==null||isDestroyed())return;
                new AlertDialog.Builder(this).setTitle("Engine benchmark").setMessage(text)
                    .setPositiveButton("Copy result",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("BC-1 engine benchmark",text));tell("Benchmark result copied");})
                    .setNegativeButton("Close",null).show();});
        },"bc1-benchmark").start();
    }
    void deviceVideoReport(){
        io.execute(()->{String report;try{report=com.blackice.bc1.engine.bc.DeviceVideoReport.build(this);}catch(RuntimeException e){report="Codec query failed: "+e.getMessage();}final String text=report;
            main.post(()->{if(isDestroyed())return;new AlertDialog.Builder(this).setTitle("Device video capabilities").setMessage(text)
                .setPositiveButton("Copy report",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("BC-1 device video capabilities",text));tell("Report copied");})
                .setNegativeButton("Close",null).show();});});
    }
    void exportDiagnostics(){
        android.content.SharedPreferences prefs=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        String report="BC-1 v"+appVersion()+" • Android API "+android.os.Build.VERSION.SDK_INT+"\n\n"+
                prefs.getString(ExportService.LAST_FAILURE,"No export failure recorded.")+"\n\n"+
                "Last failure is retained after successful exports. This summary excludes media names, paths and exception messages.";
        new AlertDialog.Builder(this).setTitle("Export diagnostics").setMessage(report)
                .setPositiveButton("Copy report",(d,w)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(android.content.ClipData.newPlainText("BC-1 export diagnostics",report));tell("Export diagnostics copied");})
                .setNegativeButton("Close",null).show();
    }
    void moveClip(int delta){if(!editable())return;int next=selected+delta;if(next<0||next>=project.clips.size())return;remember();project.move(selected,delta);selected=next;changed("Clip moved");}
    void trimDialog() {
        EditProject.Clip c=clip();if(c==null||!editable())return;if(c.image){durationDialog();return;}
        LinearLayout form=form();EditText start=field(form,"Source in (seconds)",seconds(c.inMs));EditText end=field(form,c.image?"Source out / duration (seconds)":"Source out (seconds)",seconds(c.outMs));
        form.addView(note("Source length: "+seconds(c.sourceDurationMs)+" s. Edits do not change the imported file."));
        dialog("Trim clip",form,()->{long in=parseMs(start.getText().toString()),out=parseMs(end.getText().toString());
            edit("Trim applied",()->project.trim(selected,in,out));},null);
    }
    void overlayDialog(EditProject.Overlay existing) {
        if(!editable())return;if(existing==null&&project.overlays.size()>=20){tell("This preview supports up to 20 overlays.");return;}EditProject.Overlay o=existing==null?new EditProject.Overlay():existing;
        if(existing==null){o.startMs=project.playheadMs;o.endMs=Math.max(o.startMs+100,Math.min(project.durationMs(),o.startMs+3000));}
        LinearLayout form=form();EditText text=o.imageUri==null?field(form,"Text (up to 200 characters)",o.text):null;
        EditText start=field(form,"Start (seconds)",seconds(o.startMs)),end=field(form,"End (seconds)",seconds(o.endMs));
        EditText x=field(form,"Horizontal: −1 left, 0 center, 1 right",String.valueOf(o.x));
        EditText y=field(form,"Vertical: −1 bottom, 0 center, 1 top",String.valueOf(o.y));
        EditText scale=field(form,"Size: 0.1 to 2",String.valueOf(o.scale));EditText opacity=field(form,"Opacity: 0 to 1",String.valueOf(o.opacity));
        dialog(existing==null?"Add overlay":"Edit overlay",form,()->{
            long s=parseMs(start.getText().toString()),e=parseMs(end.getText().toString());
            float xx=parseFloat(x),yy=parseFloat(y),ss=parseFloat(scale),aa=parseFloat(opacity);String title=text==null?o.text:text.getText().toString();
            if(s<0||e<=s||Math.abs(xx)>1||Math.abs(yy)>1||ss<.1f||ss>2||aa<0||aa>1||title.length()>200)throw new IllegalArgumentException("Check the time range and position / size limits.");
            remember();o.startMs=s;o.endMs=e;o.x=xx;o.y=yy;o.scale=ss;o.opacity=aa;o.text=title;if(existing==null)project.overlays.add(o);changed("Overlay saved");
        },existing==null?null:()->{remember();project.overlays.remove(o);changed("Overlay removed");});
    }
    void musicDialog() {
        EditProject.Music m=project.music;LinearLayout form=form();form.addView(note(m.name));
        EditText in=field(form,"Source in (seconds)",seconds(m.inMs)),out=field(form,"Source out (seconds)",seconds(m.outMs));
        EditText start=field(form,"Start on timeline (seconds)",seconds(m.startMs)),volume=field(form,"Music volume: 0 to 1",String.valueOf(m.volume));
        CheckBox loop=new CheckBox(this);loop.setText("Loop trimmed music to end of project");loop.setTextColor(TEXT);loop.setChecked(m.loop);form.addView(loop);
        dialog("Music track",form,()->{long a=parseMs(in.getText().toString()),b=parseMs(out.getText().toString()),s=parseMs(start.getText().toString());float v=parseFloat(volume);
            if(a<0||b>m.sourceDurationMs||b-a<100||s<0||v<0||v>1)throw new IllegalArgumentException("Choose a valid audio range and volume.");
            remember();m.inMs=a;m.outMs=b;m.startMs=s;m.volume=v;m.loop=loop.isChecked();changed("Music updated");
        },()->{remember();project.music=null;changed("Music removed");});
    }
    void projectsMenu() {
        if(!editable())return;new AlertDialog.Builder(this).setTitle("Black Ice projects").setItems(new String[]{"New project","Recent projects","Rename project","Save project","Undo","Redo"},(d,index)->{
            if(index==0)numberDialog("New project name","Untitled project",name->{if(name.isBlank()||name.length()>120)throw new IllegalArgumentException("Use a name from 1 to 120 characters.");saveNow();releasePreview();project=new EditProject();project.name=name;undo.clear();redo.clear();selected=-1;selectedOverlay=-1;changed("New project ready");});
            if(index==1){saveNow();io.execute(()->{java.util.List<ProjectStore.Entry> recent=store.recent();main.post(()->{if(isDestroyed())return;
                if(recent.isEmpty()){tell("No saved projects yet.");return;}
                new AlertDialog.Builder(this).setTitle("Recent projects").setItems(recent.stream().map(e->e.name).toArray(String[]::new),(x,i)->openProject(recent.get(i).id)).show();});});}
            if(index==2)numberDialog("Project name",project.name,name->{if(name.isBlank()||name.length()>120)throw new IllegalArgumentException("Use a name from 1 to 120 characters.");remember();project.name=name;changed("Project renamed");});
            if(index==3){saveNow();tell("Saving project…");}if(index==4)undo(false);if(index==5)undo(true);
        }).show();
    }
    void openProject(String id){loading=true;releasePreview();io.execute(()->{try{EditProject loaded=store.load(id);main.post(()->{if(isDestroyed())return;loading=false;project=loaded;undo.clear();redo.clear();selected=-1;selectedOverlay=-1;changed("Project opened");});}catch(Exception e){main.post(()->{loading=false;tell("Cannot open project: "+e.getMessage());});}});}
    void chooseMode(){if(uiLocked){tell("Unlock to change workspace layout");return;}new AlertDialog.Builder(this).setTitle("Workspace layout").setSingleChoiceItems(new String[]{"Auto • phone / tablet / DeX","Phone portrait workspace","Wide tablet / DeX workspace"},mode.ordinal(),(d,i)->{mode=LayoutPolicy.Preference.values()[i];getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString("mode",mode.name()).apply();d.dismiss();buildWorkspace();}).show();}
    void pick(int request,String type) {
        if(!editable())return;if(request==OVERLAY&&project.overlays.size()>=20){tell("This preview supports up to 20 overlays.");return;}Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType(type).addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if(request==IMPORT){intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","image/*"});intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);}
        try{startActivityForResult(intent,request);}catch(ActivityNotFoundException e){tell("No document picker is installed.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;
        if(request==CrashLogs.EXPORT){io.execute(()->{try(java.io.OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(CrashLogs.report(this).getBytes(java.nio.charset.StandardCharsets.UTF_8));main.post(()->tell("Diagnostic report exported"));}catch(Exception e){main.post(()->tell("Report export failed: "+e.getClass().getSimpleName()));}});return;}
        if(request==BACKUP||request==RESTORE){if(data.getData()!=null)bundle(request,data.getData());return;}
        if(request==EXPORT){if(data.getData()!=null){
            try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(SecurityException ignored){/* Some providers grant access for the current task only. */}
            startExport(data.getData());}return;}
        if(request==REPLACE){if(data.getData()!=null)replaceMedia(data.getData());return;}
        if(request!=IMPORT&&request!=MUSIC&&request!=OVERLAY&&request!=AUDIO_LAYER)return;
        java.util.List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());
        importUris(uris,request);
    }
    /** Copies media into the app. Video, pictures and audio layers go to Project Media, ready to drag onto
     * the timeline; music and overlay pictures are placed directly. One undo step. */
    void importUris(java.util.List<Uri> uris,int request){
        if(uris.isEmpty()||!editable())return;importing=true;tell("Importing media copies…");long at=addDrawer.insertTime();
        io.execute(()->{
            java.util.List<EditProject.Clip> imported=new ArrayList<>();java.util.List<String> errors=new ArrayList<>();
            for(Uri uri:uris)try{imported.add(new MediaImporter(this).importClip(uri,(request==MUSIC||request==AUDIO_LAYER),request==OVERLAY?1024:2048));}catch(Exception e){errors.add(e.getMessage());}
            main.post(()->{importing=false;if(isDestroyed())return;remember();long cursor=at;
                int binned=0;
                for(EditProject.Clip c:imported){
                    if(request==IMPORT||request==AUDIO_LAYER){if(project.libraryIndex(c.uri)>=0)continue;if(project.library.size()>=EditProject.LIBRARY_LIMIT){errors.add("Project Media holds up to "+EditProject.LIBRARY_LIMIT+" items.");continue;}project.library.add(EditProject.asset(c));binned++;}
                    else if(request==MUSIC){EditProject.Music m=new EditProject.Music();m.uri=c.uri;m.name=c.name;m.sourceDurationMs=c.sourceDurationMs;m.outMs=c.outMs;m.startMs=at;project.music=m;}
                    else if(request==OVERLAY){EditProject.Overlay o=new EditProject.Overlay();o.imageUri=c.uri;o.startMs=cursor;o.endMs=Math.max(o.startMs+100,Math.min(Math.max(project.durationMs(),o.startMs+3000),o.startMs+3000));project.overlays.add(o);selected=-1;selectedOverlay=project.overlays.size()-1;}
                }
                if(request!=IMPORT&&request!=AUDIO_LAYER&&(selected>=0||selectedOverlay>=0))tab=1;
                if(binned>0){mediaKind=MediaFilter.Kind.ALL;favoritesOnly=false;mediaQuery="";}
                changed(!errors.isEmpty()?"Import issue: "+String.join("; ",errors):request==IMPORT||request==AUDIO_LAYER?"Added "+binned+" item(s) to Project Media • drag onto the timeline":"Imported "+imported.size()+" item(s)");
                if(!errors.isEmpty())new AlertDialog.Builder(this).setTitle("Import result").setMessage(message).setPositiveButton("OK",null).show();
            });
        });
    }
    /** Places a copy of a Project Media item on a track at a time (from a drop or Add at playhead).
     * An occupied layer falls back to another free layer of the same kind. */
    void dropMedia(EditProject.Clip asset,int track,long start){
        if(!editable())return;if(project.clips.size()>=100){tell("The 100-clip project limit was reached.");return;}
        edit("Added "+asset.name+" to "+project.tracks[track].name,()->{
            EditProject.Clip c=asset.copy();c.track=0;c.startMs=0;int target=track;
            if(c.audioOnly&&target<EditProject.AUDIO_FIRST||!c.audioOnly&&target>=EditProject.AUDIO_FIRST)target=c.audioOnly?EditProject.AUDIO_FIRST:0;
            long end=start+c.durationMs();
            if(project.tracks[target].locked||target>0&&!LayerOps.free(project,target,start,end,-1)){int first=target>=EditProject.AUDIO_FIRST?EditProject.AUDIO_FIRST:1,last=target>=EditProject.AUDIO_FIRST?EditProject.AUDIO_LAST:EditProject.LAST_LAYER,found=-1;for(int t=first;t<=last&&found<0;t++)if(!project.tracks[t].locked&&LayerOps.free(project,t,start,end,-1))found=t;if(found<0)throw new IllegalArgumentException("No free "+(target>=EditProject.AUDIO_FIRST?"audio":"video")+" layer at "+seconds(start)+" s.");target=found;}
            if(target>=EditProject.AUDIO_FIRST){c.track=target;c.startMs=start;project.clips.add(c);selected=project.clips.size()-1;}
            else{if(c.audioOnly)throw new IllegalArgumentException("Audio belongs on an audio layer.");c.track=0;project.clips.add(project.lastBase()+1,c);selected=TimelineEdits.place(project,project.lastBase(),target,start);}
            selectedOverlay=-1;multiClips.clear();multiOverlays.clear();tab=1;
        });
        if(project.clips.size()>0&&clip()!=null&&clip().track>=EditProject.AUDIO_FIRST)loadWaveform(clip(),true);
    }
    void exportMenu() {
        if(exporting()){ExportWorkspace.progress(this);return;}
        if(loading||importing)return;if((project.clips.isEmpty()&&project.overlays.isEmpty())){tell("Import at least one video or image before exporting.");return;}
        ExportWorkspace.show(this);
    }

    /** Requested export size and rate; the device check at export start may step this down. */
    String exportSummary(){
        android.content.SharedPreferences prefs=getSharedPreferences(ExportService.PREFS,MODE_PRIVATE);
        int edge=prefs.getInt(com.blackice.bc1.core.ExportPlanner.PREF_RESOLUTION,1080);
        com.blackice.bc1.core.ExportPlanner.Plan plan=ExportOptions.plan(project,prefs);
        return plan.width+" × "+plan.height+" • "+plan.describe()+" + AAC • "+(plan.bitrate/1_000_000)+" Mb/s"
                +String.format(java.util.Locale.US,"\nAbout %.2f GB",plan.estimatedBytes(project.durationMs())/1e9);
    }
    void startExport(Uri destination) {
        if(exporting()){tell("An export is already running.");return;}saveNow();releasePreview();main.removeCallbacks(warmPreview);previewProxy.cancel();if(live!=null)live.releaseDecoders();exportLaunching=true;
        EditProject snapshot=ProjectStore.snapshot(project);io.execute(()->{try{
            File file=new File(getCacheDir(),"export-project-"+System.currentTimeMillis()+".json");try(FileOutputStream out=new FileOutputStream(file)){out.write(ProjectStore.encode(snapshot).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
            main.post(()->{exportLaunching=false;try{getSharedPreferences(ExportService.PREFS,MODE_PRIVATE).edit().putBoolean("running",true).putBoolean("success",false).putInt("percent",-1).putLong("outputBytes",0).putLong("startedAt",System.currentTimeMillis()).putString("status","Preparing export…").apply();startForegroundService(new Intent(this,ExportService.class).putExtra("project",file.getAbsolutePath()).putExtra("destination",destination.toString()));message="Export started";ExportWorkspace.progress(this);}catch(Exception e){file.delete();getSharedPreferences(ExportService.PREFS,MODE_PRIVATE).edit().putBoolean("running",false).putString("status","Could not start export").apply();tell("Cannot start export: "+e.getMessage());}});
        }catch(Exception e){main.post(()->{exportLaunching=false;tell("Cannot prepare export: "+e.getMessage());});}});
    }
    void saveAndProjects(){
        if(loading||importing)return;EditProject snapshot=ProjectStore.snapshot(project);
        io.execute(()->{try{store.save(snapshot);main.post(()->{if(!isDestroyed())projectsMenu();});}
            catch(Exception error){main.post(()->{if(!isDestroyed())new AlertDialog.Builder(this).setTitle("Could not save project").setMessage("Your edits remain open. Retry saving before leaving.").setPositiveButton("Retry",(dialog,which)->saveAndProjects()).setNegativeButton("Keep editing",null).show();});}});
    }
    /** System gesture Back follows the same state hierarchy as the toolbar and keyboard. */
    @SuppressWarnings("deprecation") @Override public void onBackPressed(){
        if(fullscreenPreview){fullscreenPreview=false;buildWorkspace();return;}
        if(mobile!=null){mobile.back();return;}
        if(panels.showing()){panels.back();return;}
        if(clip()!=null||overlay()!=null||multiCount()>0){deselect();return;}
        if(tab!=1){tab=1;renderInspector();return;}
        saveAndProjects();
    }
    @Override public boolean onKeyDown(int key,KeyEvent event){
        if(key==KeyEvent.KEYCODE_BACK&&mobile!=null){mobile.back();return true;}
        if(key==KeyEvent.KEYCODE_BACK&&!wideLayout&&!fullscreenPreview&&tab==0){tab=1;renderInspector();return true;}
        if(key==KeyEvent.KEYCODE_BACK&&fullscreenPreview){fullscreenPreview=false;buildWorkspace();return true;}
        if(key==KeyEvent.KEYCODE_BACK&&panels.showing()){panels.back();return true;}
        if(key==KeyEvent.KEYCODE_BACK&&(clip()!=null||overlay()!=null)){deselect();return true;}
        if(getCurrentFocus() instanceof EditText)return super.onKeyDown(key,event);
        if(event.isCtrlPressed()&&key==KeyEvent.KEYCODE_S){saveNow();return true;}
        if(event.isCtrlPressed()&&(key==KeyEvent.KEYCODE_Z||key==KeyEvent.KEYCODE_Y)){undo(key==KeyEvent.KEYCODE_Y||event.isShiftPressed());return true;}
        if(key==KeyEvent.KEYCODE_SPACE&&event.getRepeatCount()==0){togglePlay();return true;}
        if(key==KeyEvent.KEYCODE_D&&event.isCtrlPressed()){duplicateSelected();return true;}
        if(event.isCtrlPressed()&&key==KeyEvent.KEYCODE_C){copyAttributes();return true;}
        if(event.isCtrlPressed()&&key==KeyEvent.KEYCODE_V){pasteAttributes();return true;}
        if(key==KeyEvent.KEYCODE_S&&!event.isCtrlPressed()){splitSelected();return true;}
        if(key==KeyEvent.KEYCODE_ESCAPE){deselect();return true;}
        if(key==KeyEvent.KEYCODE_MOVE_HOME||key==KeyEvent.KEYCODE_MOVE_END){jump(key==KeyEvent.KEYCODE_MOVE_END);return true;}
        if(event.isCtrlPressed()&&(key==KeyEvent.KEYCODE_EQUALS||key==KeyEvent.KEYCODE_MINUS)){zoomTimeline(key==KeyEvent.KEYCODE_EQUALS?1.5f:1/1.5f,Float.NaN);return true;}
        boolean arrow=key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN;
        // Transform mode: arrows nudge the selected layer by one project pixel, Shift by ten.
        if(arrow&&"Move & Transform".equals(panels.title())&&panels.showing()){int step=event.isShiftPressed()?10:1;nudge(key==KeyEvent.KEYCODE_DPAD_LEFT?-step:key==KeyEvent.KEYCODE_DPAD_RIGHT?step:0,key==KeyEvent.KEYCODE_DPAD_UP?step:key==KeyEvent.KEYCODE_DPAD_DOWN?-step:0);return true;}
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){long frame=Math.round(1000.0/project.frameRate);seek(project.playheadMs+(key==KeyEvent.KEYCODE_DPAD_LEFT?-1:1)*(event.isShiftPressed()?1000:frame));return true;}
        if(key==KeyEvent.KEYCODE_FORWARD_DEL||key==KeyEvent.KEYCODE_DEL){deleteSelection();return true;}
        if(key==KeyEvent.KEYCODE_M&&!event.isCtrlPressed()){toggleMarker();return true;}
        if(key==KeyEvent.KEYCODE_COMMA||key==KeyEvent.KEYCODE_PERIOD){jumpMarker(key==KeyEvent.KEYCODE_PERIOD);return true;}
        if(key==KeyEvent.KEYCODE_LEFT_BRACKET||key==KeyEvent.KEYCODE_RIGHT_BRACKET){jumpKeyframe(key==KeyEvent.KEYCODE_RIGHT_BRACKET);return true;}
        return super.onKeyDown(key,event);
    }
    /** Duplicate after when free, otherwise onto an unlocked layer without moving other clips. */
    void duplicateSelected(){if(multiCount()>1){multiAction("Duplicate");return;}duplicate(false);}
    void duplicate(boolean above){EditProject.Clip c=clip();EditProject.Overlay o=overlay();if(c==null&&o==null||!layerEditable())return;
        if(c!=null)edit(above?"Duplicated above":"Clip duplicated",()->{selected=above?LayerOps.duplicateAbove(project,selected):LayerOps.duplicateSafe(project,selected);});
        else edit(above?"Duplicated above":"Layer duplicated",()->selectedOverlay=LayerOps.duplicateOverlay(project,selectedOverlay,!above));}
    void placementDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;LinearLayout f=form();EditText track=field(f,"0: main sequence · 1–"+EditProject.LAST_LAYER+": video layers · "+EditProject.AUDIO_FIRST+"–"+EditProject.AUDIO_LAST+": audio",String.valueOf(c.track)),start=field(f,"Timeline start (seconds)",seconds(project.placements().get(selected).startMs));dialog("Layer placement",f,()->{int t=Integer.parseInt(track.getText().toString());long ms=parseMs(start.getText().toString());edit("Layer placement updated",()->selected=TimelineEdits.place(project,selected,t,ms));},null);}
    void fadeDialog(){EditProject.Clip c=clip();LinearLayout f=form();EditText in=field(f,"Fade in (seconds)",seconds(c.fadeInMs)),out=field(f,"Fade out (seconds)",seconds(c.fadeOutMs));dialog("Audio fades",f,()->{long a=parseMs(in.getText().toString()),b=parseMs(out.getText().toString());if(a<0||b<0||a>c.durationMs()||b>c.durationMs())throw new IllegalArgumentException("Fades must fit inside the clip");remember();c.fadeInMs=a;c.fadeOutMs=b;changed("Audio fades updated");},null);}
    void extractAudio(boolean muteOriginal){EditProject.Clip source=clip();if(source==null||source.image||!layerEditable())return;
        {long start=project.placements().get(selected).startMs;importing=true;io.execute(()->{try{if(!AudioWaveform.hasAudio(this,source.uri))throw new IllegalArgumentException("This source has no audio track");main.post(()->{importing=false;if(isDestroyed())return;try{if(project.clips.size()>=100)throw new IllegalArgumentException("The 100-clip limit was reached");EditProject.Clip audio=source.copy();audio.name=source.name+" • audio";audio.track=project.freeAudioTrack(start,audio.durationMs());audio.startMs=start;audio.transition=-1;remember();project.clips.add(audio);if(muteOriginal)source.volume=0;audio.fadeInMs=0;audio.fadeOutMs=0;selected=project.clips.size()-1;panels.reset();changed("Audio extracted independently");loadWaveform(audio);}catch(Exception e){tell(e.getMessage());}});}catch(Exception e){main.post(()->{importing=false;tell(e.getMessage());});}});}
    }
    final java.util.Set<String> waveformsLoading=new java.util.HashSet<>();
    void loadWaveform(EditProject.Clip c){loadWaveform(c,false);}
    /** Audio layers show real PCM waveforms; decoding runs once per source on the worker thread. */
    void loadWaveform(EditProject.Clip c,boolean quiet){if(waveforms.containsKey(c.uri)||!waveformsLoading.add(c.uri))return;if(!quiet)tell("Decoding audio waveform…");String uri=c.uri;long duration=c.sourceDurationMs;io.execute(()->{try{float[] peaks=AudioWaveform.read(this,uri,duration);main.post(()->{waveformsLoading.remove(uri);waveforms.put(uri,peaks);if(timeline!=null)timeline.invalidate();if(!quiet)tell("Waveform ready");});}catch(Exception e){main.post(()->{waveformsLoading.remove(uri);if(!quiet)tell("Waveform: "+e.getMessage());});}});}
    void bundle(int request,Uri uri){if(!editable())return;saveNow();EditProject snapshot=ProjectStore.snapshot(project);importing=true;tell(request==BACKUP?"Saving project and media…":"Restoring project and media…");io.execute(()->{try{if(request==BACKUP){ProjectBundle.write(this,snapshot,getContentResolver().openOutputStream(uri,"wt"));main.post(()->{importing=false;tell("Project bundle saved");});}else{EditProject restored=ProjectBundle.read(this,getContentResolver().openInputStream(uri));main.post(()->{importing=false;if(isDestroyed())return;releasePreview();project=restored;undo.clear();redo.clear();selected=-1;selectedOverlay=-1;changed("Project restored as a new copy");});}}catch(Exception e){if(request==BACKUP)try{android.provider.DocumentsContract.deleteDocument(getContentResolver(),uri);}catch(Exception ignored){}main.post(()->{importing=false;tell("Bundle failed: "+e.getMessage());});}});}
    void chromaDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;LinearLayout f=form();CheckBox enabled=new CheckBox(this);enabled.setText("Enable chroma key");enabled.setTextColor(TEXT);enabled.setChecked(c.chroma);f.addView(enabled);EditText color=field(f,"Key color (#RRGGBB)",String.format(java.util.Locale.ROOT,"#%06X",c.keyColor&0xFFFFFF)),t=field(f,"Tolerance 0–1",String.valueOf(c.keyTolerance)),soft=field(f,"Edge softness 0.001–1",String.valueOf(c.keySoftness)),spill=field(f,"Spill removal 0–1",String.valueOf(c.keySpill));dialog("Chroma key",f,()->{int rgb=Color.parseColor(color.getText().toString());float a=parseFloat(t),b=parseFloat(soft),z=parseFloat(spill);if(a<0||a>1||b<.001f||b>1||z<0||z>1)throw new IllegalArgumentException("Check key limits");remember();c.chroma=enabled.isChecked();c.keyColor=rgb;c.keyTolerance=a;c.keySoftness=b;c.keySpill=z;changed("Chroma key updated");},null);}
    void transformDialog(){EditProject.Clip c=clip();if(c==null||!editable())return;new AlertDialog.Builder(this).setTitle("Transform & motion").setItems(new String[]{"Static transform","Keyframes / curve editor"},(d,i)->{
        if(i==1){motionDialog(c.motion,Math.max(c.inMs,Math.min(c.outMs,Math.round((project.playheadMs-project.placements().get(selected).startMs)*c.speed)+c.inMs)),c.sourceDurationMs,new float[]{c.x,c.y,c.scale,c.rotation,c.opacity,c.intensity},true);return;}
        LinearLayout f=form();EditText x=field(f,"X: −2 to 2",String.valueOf(c.x)),y=field(f,"Y: −2 to 2",String.valueOf(c.y)),scale=field(f,"Scale: 0.05 to 4",String.valueOf(c.scale)),rotation=field(f,"Rotation (degrees)",String.valueOf(c.rotation));dialog("Transform",f,()->{float a=parseFloat(x),b=parseFloat(y),z=parseFloat(scale),r=parseFloat(rotation);if(!Motion.allowed(0,a)||!Motion.allowed(1,b)||!Motion.allowed(2,z)||!Motion.allowed(3,r))throw new IllegalArgumentException("Transform out of range");remember();c.x=a;c.y=b;c.scale=z;c.rotation=r;changed("Transform updated");},null);
    }).show();}
    void motionDialog(Motion motion,long current,long limit,float[] defaults,boolean effects){String[] properties=effects?Motion.PROPERTIES:java.util.Arrays.copyOf(Motion.PROPERTIES,5);new AlertDialog.Builder(this).setTitle("Animate property").setItems(properties,(d,property)->{
        java.util.List<Motion.Key> keys=motion.curves.get(property);String[] choices=new String[keys.size()+1];choices[0]="+ Key at playhead";for(int n=0;n<keys.size();n++)choices[n+1]=seconds(keys.get(n).timeMs)+" s → "+keys.get(n).value;
        new AlertDialog.Builder(this).setTitle(properties[property]+" • source-time keys").setItems(choices,(dialog,index)->keyDialog(motion,property,index==0?new Motion.Key(current,motion.value(property,current,defaults[property])):keys.get(index-1).copy(),limit,index==0?null:keys.get(index-1))).show();
    }).show();}
    void keyDialog(Motion motion,int property,Motion.Key key,long limit,Motion.Key existing){LinearLayout f=form();EditText time=field(f,"Source time (seconds)",seconds(key.timeMs)),value=field(f,Motion.PROPERTIES[property]+" value",String.valueOf(key.value));
        EditText x1=field(f,"Curve X1 (0–1)",String.valueOf(key.x1)),y1=field(f,"Curve Y1 (0–1)",String.valueOf(key.y1)),x2=field(f,"Curve X2 (0–1)",String.valueOf(key.x2)),y2=field(f,"Curve Y2 (0–1)",String.valueOf(key.y2));
        CurveView curve=new CurveView(key,()->{x1.setText(String.valueOf(key.x1));y1.setText(String.valueOf(key.y1));x2.setText(String.valueOf(key.x2));y2.setText(String.valueOf(key.y2));});f.addView(curve,new LinearLayout.LayoutParams(-1,dp(180)));
        LinearLayout presets=row();String[] names={"Linear","Ease in","Ease out","Smooth"};float[][] points={{0,0,1,1},{.42f,0,1,1},{0,0,.58f,1},{.42f,0,.58f,1}};for(int i=0;i<names.length;i++){final int n=i;presets.addView(button(names[i],v->{float[] a=points[n];key.x1=a[0];key.y1=a[1];key.x2=a[2];key.y2=a[3];x1.setText(""+a[0]);y1.setText(""+a[1]);x2.setText(""+a[2]);y2.setText(""+a[3]);curve.invalidate();}),new LinearLayout.LayoutParams(0,dp(48),1));}f.addView(presets);f.addView(note("Drag the blue handles. This curve controls the interval after this key."));
        dialog("Keyframe / curve",f,()->{long t=parseMs(time.getText().toString());float val=parseFloat(value);Motion.Key candidate=new Motion.Key(t,val);candidate.x1=parseFloat(x1);candidate.y1=parseFloat(y1);candidate.x2=parseFloat(x2);candidate.y2=parseFloat(y2);Motion check=motion.copy();if(existing!=null)check.curves.get(property).removeIf(k->k.timeMs==existing.timeMs);check.put(property,candidate);check.validate();if(t>limit)throw new IllegalArgumentException("Key must fit inside the source");remember();if(existing!=null)motion.curves.get(property).remove(existing);motion.put(property,candidate);changed("Keyframe saved");},existing==null?null:()->{remember();motion.curves.get(property).remove(existing);changed("Keyframe removed");});
    }
    final class CurveView extends View{final Motion.Key key;final Runnable update;final Paint p=new Paint(3);int handle;CurveView(Motion.Key k,Runnable u){super(EditorActivity.this);key=k;update=u;setContentDescription("Cubic Bézier curve: drag either handle");}protected void onDraw(Canvas c){float w=getWidth()-dp(24),h=getHeight()-dp(24),m=dp(12);c.drawColor(PANEL);p.setColor(MUTED);p.setStrokeWidth(dp(1));c.drawLine(m,h+m,w+m,m,p);p.setColor(CYAN);p.setStrokeWidth(dp(2));float px=m,py=h+m;for(int i=1;i<=80;i++){float x=i/80f,y=Motion.ease(x,key);c.drawLine(px,py,m+x*w,m+(1-y)*h,p);px=m+x*w;py=m+(1-y)*h;}c.drawCircle(m+key.x1*w,m+(1-key.y1)*h,dp(6),p);c.drawCircle(m+key.x2*w,m+(1-key.y2)*h,dp(6),p);}public boolean onTouchEvent(MotionEvent e){float x=Math.max(0,Math.min(1,(e.getX()-dp(12))/(getWidth()-dp(24)))),y=Math.max(0,Math.min(1,1-(e.getY()-dp(12))/(getHeight()-dp(24))));if(e.getAction()==MotionEvent.ACTION_DOWN){handle=Math.hypot(x-key.x1,y-key.y1)<Math.hypot(x-key.x2,y-key.y2)?1:2;getParent().requestDisallowInterceptTouchEvent(true);}if(handle==1){key.x1=x;key.y1=y;}else{key.x2=x;key.y2=y;}update.run();invalidate();if(e.getAction()==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);performClick();}return true;}public boolean performClick(){super.performClick();return true;}}

    void toggleLock(){uiLocked=!uiLocked;getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("ui-locked",uiLocked).apply();renderInspector();tell(uiLocked?"UI locked • playback and seeking remain available":"UI unlocked");}
    void renderMediaBrowser(){
        if(mediaBrowser==null)return;
        mediaBrowser.removeAllViews();mediaFilterButtons.clear();
        LinearLayout title=row();title.setGravity(Gravity.CENTER_VERTICAL);LinearLayout titles=column();titles.addView(label("Project Media",15,TEXT,true));titles.addView(label("Import, then drag media onto the timeline.",10,MUTED,false));title.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        title.addView(headerIcon("Sort","Sort media",v->new AlertDialog.Builder(this).setTitle("Sort media").setSingleChoiceItems(new String[]{"Import order","Name","Duration"},mediaSort,(d,n)->{mediaSort=n;d.dismiss();renderMediaGrid();}).show()));
        mediaBrowser.addView(title);
        LinearLayout addTools=row();addTools.addView(button("Add content",v->{tab=0;renderInspector();}),new LinearLayout.LayoutParams(0,dp(48),1));addTools.addView(button("Layers",v->{tab=3;renderInspector();}),new LinearLayout.LayoutParams(0,dp(48),1));mediaBrowser.addView(addTools);
        TextView importButton=button("Import Media",v->pick(IMPORT,"*/*"));importButton.setContentDescription("+ Import media");importButton.setCompoundDrawables(icon("Add",TEXT),null,null,null);importButton.setCompoundDrawablePadding(dp(8));importButton.setTextSize(13);
        LinearLayout.LayoutParams importLp=new LinearLayout.LayoutParams(-1,dp(48));importLp.topMargin=dp(10);importLp.bottomMargin=dp(8);mediaBrowser.addView(importButton,importLp);
        LinearLayout filters=row();filters.setBaselineAligned(false);
        String[] names={"All","Video","Audio","Image"};
        for(MediaFilter.Kind kind:MediaFilter.Kind.values()){
            TextView filter=button(names[kind.ordinal()],v->{mediaKind=kind;renderMediaGrid();});
            filter.setTextSize(11);filter.setPadding(dp(3),0,dp(3),0);
            filter.setContentDescription("Filter media: "+names[kind.ordinal()]);
            mediaFilterButtons.add(filter);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(38),1);lp.rightMargin=dp(3);filters.addView(filter,lp);
        }
        TextView favorites=button("Favorites",v->{favoritesOnly=!favoritesOnly;renderMediaGrid();});favorites.setTextSize(11);favorites.setPadding(dp(3),0,dp(3),0);favorites.setTag("favorites");filters.addView(favorites,new LinearLayout.LayoutParams(0,dp(38),1.3f));
        mediaBrowser.addView(filters);
        LinearLayout searchRow=row();searchRow.setGravity(Gravity.CENTER_VERTICAL);
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(12);
        search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setHint("Search media…");
        search.setContentDescription("Search project media");search.setText(mediaQuery);
        search.setPadding(dp(10),0,dp(8),0);search.setBackground(buttonBackground(false));search.setCompoundDrawables(icon("Search",MUTED),null,null,null);search.setCompoundDrawablePadding(dp(8));
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(0,dp(42),1);searchLp.topMargin=dp(8);searchRow.addView(search,searchLp);
        TextView layout=headerIcon(mediaList?"Grid":"List",mediaList?"Grid / List: show grid":"Grid / List: show list",v->{mediaList=!mediaList;mediaColumns=mediaList?1:mediaGridColumns;renderMediaBrowser();});LinearLayout.LayoutParams layoutLp=new LinearLayout.LayoutParams(dp(44),dp(42));layoutLp.topMargin=dp(8);layoutLp.leftMargin=dp(6);searchRow.addView(layout,layoutLp);
        mediaBrowser.addView(searchRow);
        mediaCount=note("");mediaCount.setTextSize(10);mediaBrowser.addView(mediaCount);
        mediaGrid=column();mediaGrid.setContentDescription("Project media grid");mediaBrowser.addView(mediaGrid);
        search.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){mediaQuery=s.toString();renderMediaGrid();}
            public void afterTextChanged(android.text.Editable value){}
        });
        renderMediaGrid();
    }
    int mediaGridColumns=3;
    void renderMediaGrid(){
        if(mediaGrid==null)return;
        mediaGrid.removeAllViews();
        for(int i=0;i<mediaFilterButtons.size();i++){
            TextView filter=mediaFilterButtons.get(i);boolean chosen=i==mediaKind.ordinal();
            filter.setSelected(chosen);filter.setTextColor(chosen?CYAN:TEXT);filter.setBackground(buttonBackground(chosen));
        }
        View favorites=mediaBrowser==null?null:mediaBrowser.findViewWithTag("favorites");if(favorites instanceof TextView){((TextView)favorites).setTextColor(favoritesOnly?CYAN:TEXT);favorites.setBackground(buttonBackground(favoritesOnly));favorites.setSelected(favoritesOnly);}
        ArrayList<EditProject.Clip> matches=new ArrayList<>();
        for(EditProject.Clip asset:project.library)if((!favoritesOnly||favoriteMedia.contains(asset.uri))&&MediaFilter.matches(asset,mediaKind,mediaQuery))matches.add(asset);
        if(mediaSort==1)matches.sort((a,b)->a.name.compareToIgnoreCase(b.name));else if(mediaSort==2)matches.sort((a,b)->Long.compare(a.durationMs(),b.durationMs()));
        mediaCount.setText(matches.size()+" / "+project.library.size()+" items");
        if(matches.isEmpty()){
            mediaGrid.addView(note(project.library.isEmpty()?"Import media to build your project.":"No matching media"));
            return;
        }
        mediaRows(mediaGrid,matches,mediaColumns,mediaList);
    }
    /** Media cards in rows; shared by the desktop browser and the Add panel. */
    void mediaRows(LinearLayout into,java.util.List<EditProject.Clip> items,int columns,boolean list){
        for(int start=0;start<items.size();start+=columns){
            LinearLayout row=row();row.setBaselineAligned(false);
            for(int column=0;column<columns;column++){
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(list?64:columns>=3?104:124),1);
                if(column>0)cell.leftMargin=dp(8);
                if(start+column<items.size())row.addView(mediaCard(items.get(start+column),columns,list),cell);
                else row.addView(new View(this),cell);
            }
            LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(-1,-2);line.bottomMargin=dp(10);into.addView(row,line);
        }
    }
    boolean onTimeline(EditProject.Clip asset){for(EditProject.Clip c:project.clips)if(c.uri.equals(asset.uri))return true;return false;}
    /** A Project Media card: thumbnail with duration badge and name. Long-press and drag it onto a timeline
     * row; tap for actions (or to select the clip already using it). */
    View mediaCard(EditProject.Clip asset,int columns,boolean list){
        LinearLayout card=list?row():column();card.setPadding(0,0,0,0);
        card.setTag(asset);boolean chosen=clip()!=null&&clip().uri.equals(asset.uri);
        card.setSelected(chosen);
        FrameLayout frame=new FrameLayout(this);GradientDrawable frameBg=new GradientDrawable();frameBg.setColor(0xFF0B1826);frameBg.setCornerRadius(dp(6));frameBg.setStroke(dp(chosen?2:1),chosen?CYAN:BlackIceTheme.BORDER);frame.setBackground(frameBg);frame.setClipToOutline(true);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setTag(asset);
        boolean audio=asset.audioOnly;
        if(audio){image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);image.setImageDrawable(new Waveform(asset.uri));}
        else{image.setImageDrawable(new V1Icon("Media",MUTED));Bitmap thumb=thumbnails.get(asset,mediaThumbnailsReady);if(thumb!=null)image.setImageBitmap(thumb);}
        frame.addView(image,new FrameLayout.LayoutParams(-1,-1));
        TextView badge=label(asset.image?"IMG":clockShort(asset.durationMs()),9,TEXT,true);badge.setPadding(dp(5),dp(1),dp(5),dp(1));GradientDrawable badgeBg=new GradientDrawable();badgeBg.setColor(0xCC050B14);badgeBg.setCornerRadius(dp(3));badge.setBackground(badgeBg);
        FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);badgeLp.setMargins(dp(4),dp(4),dp(4),dp(4));frame.addView(badge,badgeLp);
        if(onTimeline(asset)){View dot=new View(this);GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(CYAN);dot.setBackground(d);FrameLayout.LayoutParams dotLp=new FrameLayout.LayoutParams(dp(7),dp(7),Gravity.TOP|Gravity.START);dotLp.setMargins(dp(6),dp(6),0,0);frame.addView(dot,dotLp);}
        if(favoriteMedia.contains(asset.uri)){TextView star=label("★",11,0xFFFFC145,true);FrameLayout.LayoutParams starLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);starLp.setMargins(0,dp(2),dp(5),0);frame.addView(star,starLp);}
        card.addView(frame,list?new LinearLayout.LayoutParams(dp(88),-1):new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout details=list?column():card;if(list){details.setPadding(dp(10),0,0,0);details.setGravity(Gravity.CENTER_VERTICAL);card.addView(details,new LinearLayout.LayoutParams(0,-1,1));}
        TextView name=label(asset.name,11,chosen?CYAN:TEXT,false);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);name.setPadding(list?0:dp(2),dp(4),0,0);details.addView(name);
        if(list){TextView detail=label((audio?"Audio":asset.image?"Image":"Video")+" · "+clock(asset.durationMs()),10,MUTED,false);detail.setSingleLine(true);details.addView(detail);}
        card.setFocusable(true);card.setContentDescription("Media "+asset.name);card.setTooltipText(asset.name+" • long-press and drag onto the timeline");
        card.setOnLongClickListener(v->{if(uiLocked){tell("UI locked • unlock editing in Settings");return true;}
            v.startDragAndDrop(ClipData.newPlainText("media",asset.name),new View.DragShadowBuilder(frame),asset,0);tell("Drop "+asset.name+" on a timeline row");return true;});
        if(wideLayout){card.setTooltipText(asset.name+" • drag onto the timeline");
            card.setOnTouchListener(new View.OnTouchListener(){float x,y;boolean started;
                public boolean onTouch(View v,MotionEvent e){
                    if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();started=false;return false;}
                    if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!started){float dx=e.getX()-x,dy=e.getY()-y;int slop=ViewConfiguration.get(EditorActivity.this).getScaledTouchSlop();boolean mouse=e.getToolType(0)==MotionEvent.TOOL_TYPE_MOUSE;
                        if(Math.hypot(dx,dy)>slop&&(mouse||Math.abs(dx)>Math.abs(dy))){if(!editable())return true;v.getParent().requestDisallowInterceptTouchEvent(true);started=v.startDragAndDrop(ClipData.newPlainText("media",asset.name),new View.DragShadowBuilder(frame),asset,0);return started;}}
                    if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){v.getParent().requestDisallowInterceptTouchEvent(false);return started;}return started;
                }});
        }
        card.setOnClickListener(v->mediaActions(asset));
        return card;
    }
    /** Tap on a Project Media item: select the clip using it, or add it at the playhead. */
    void mediaActions(EditProject.Clip asset){
        if(mobile!=null&&mobile.overlayInsert&&!asset.audioOnly){mediaAction(asset,"Add as new layer at playhead");return;}
        java.util.List<String> items=new ArrayList<>();int used=-1;for(int i=0;i<project.clips.size()&&used<0;i++)if(project.clips.get(i).uri.equals(asset.uri))used=i;
        if(used>=0){selectClip(used,true);renderMediaGrid();return;}
        items.add(asset.audioOnly?"Add to an audio layer at playhead":"Add to main sequence at playhead");if(!asset.audioOnly)items.add("Add as new layer at playhead");
        items.add(favoriteMedia.contains(asset.uri)?"Remove favorite":"Add favorite");if(used<0)items.add("Remove from Project Media");
        String[] names=items.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(asset.name).setMessage("Tip: long-press and drag media onto any timeline row.").setItems(names,(d,n)->mediaAction(asset,names[n])).setNegativeButton("Close",null).show();
    }
    void mediaAction(EditProject.Clip asset,String action){
        long at=project.playheadMs;
        if(action.startsWith("Add to")){dropMedia(asset,asset.audioOnly?(timeline==null?4:timeline.dropTrackFor(asset,-1,at)):0,at);return;}
        if(action.startsWith("Add as new layer")){int track=-1;for(int t=1;t<=EditProject.LAST_LAYER&&track<0;t++)if(LayerOps.free(project,t,at,at+asset.durationMs(),-1))track=t;if(track<0){tell("No free video layer at "+seconds(at)+" s.");return;}dropMedia(asset,track,at);return;}
        if(action.contains("favorite")){if(!favoriteMedia.remove(asset.uri))favoriteMedia.add(asset.uri);getSharedPreferences(PREFS,MODE_PRIVATE).edit().putStringSet("favorite-media",new java.util.HashSet<>(favoriteMedia)).apply();renderMediaBrowser();if(tab==0)renderInspector();return;}
        if(action.startsWith("Remove"))edit("Removed "+asset.name+" from Project Media",()->{int i=project.libraryIndex(asset.uri);if(i>=0)project.library.remove(i);});
    }
    /** Placeholder waveform for audio cards (real peaks once decoded). */
    final class Waveform extends android.graphics.drawable.Drawable{final String uri;final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Waveform(String uri){this.uri=uri;}
        public void draw(Canvas c){Rect b=getBounds();float[] peaks=waveforms.get(uri);p.setColor(CYAN);p.setStrokeWidth(Math.max(1,dp(1.5f)));int bars=Math.max(8,b.width()/dp(3));for(int i=0;i<bars;i++){float v=peaks!=null?peaks[(int)((long)i*peaks.length/bars)]:(float)(.25+.6*Math.abs(Math.sin(i*.7)*Math.cos(i*.23)));float x=b.left+(i+.5f)*b.width()/bars,h=Math.max(dp(2),v*b.height()*.7f);c.drawLine(x,b.centerY()-h/2,x,b.centerY()+h/2,p);}}
        public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}}
    void updateMediaSelection(){
        if(mediaGrid==null)return;
        EditProject.Clip c=clip();
        for(int row=0;row<mediaGrid.getChildCount();row++)if(mediaGrid.getChildAt(row) instanceof ViewGroup){
            ViewGroup cells=(ViewGroup)mediaGrid.getChildAt(row);
            for(int i=0;i<cells.getChildCount();i++){
                View card=cells.getChildAt(i);if(!(card.getTag() instanceof EditProject.Clip))continue;
                boolean chosen=c!=null&&c.uri.equals(((EditProject.Clip)card.getTag()).uri);
                if(card.isSelected()!=chosen){renderMediaGrid();return;}
            }
        }
    }
    /** The Add panel's copy of Project Media (phone and compact windows). */
    LinearLayout binGrid;
    void refreshMediaThumbnails(){if(!isDestroyed()){refreshMediaThumbnails(mediaGrid);refreshMediaThumbnails(binGrid);}}
    void refreshMediaThumbnails(LinearLayout grid){
        if(grid==null)return;
        // Update current images in place; decoding must not recreate cards, focus or scroll state.
        for(int row=0;row<grid.getChildCount();row++)if(grid.getChildAt(row) instanceof ViewGroup){
            ViewGroup cells=(ViewGroup)grid.getChildAt(row);
            for(int i=0;i<cells.getChildCount();i++)if(cells.getChildAt(i) instanceof ViewGroup){
                ViewGroup card=(ViewGroup)cells.getChildAt(i);
                if(card.getChildCount()==0||!(card.getChildAt(0) instanceof FrameLayout))continue;
                FrameLayout frame=(FrameLayout)card.getChildAt(0);if(frame.getChildCount()==0||!(frame.getChildAt(0) instanceof ImageView))continue;
                ImageView image=(ImageView)frame.getChildAt(0);
                if(!(image.getTag() instanceof EditProject.Clip))continue;
                EditProject.Clip asset=(EditProject.Clip)image.getTag();if(asset.audioOnly)continue;
                Bitmap bitmap=thumbnails.peek(asset);if(bitmap!=null)image.setImageBitmap(bitmap);
            }
        }
    }
    static String clockShort(long ms){return String.format(Locale.US,"%02d:%02d",ms/60000,(ms/1000)%60);}
    void tell(String text){message=text==null?"Something went wrong.":text;if(status!=null)status.setText(message);}
    int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    TextView label(String text,float size,int color,boolean bold){TextView v=new TextView(this);v.setText(text);v.setTextSize(size);v.setTextColor(color);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    TextView note(String text){TextView v=label(text,12,MUTED,false);v.setPadding(0,dp(7),0,dp(10));return v;}
    TextView button(String title,View.OnClickListener listener){TextView b=label(title,12,TEXT,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(12),dp(6),dp(12),dp(6));b.setMinWidth(dp(48));b.setMinHeight(dp(wideLayout?36:48));b.setMaxLines(2);b.setFocusable(true);b.setContentDescription(title);b.setBackground(buttonBackground(false));b.setTooltipText(title);b.setOnClickListener(listener);b.setOnHoverListener((v,e)->{v.setAlpha(e.getAction()==MotionEvent.ACTION_HOVER_EXIT?1f:.82f);return false;});return b;}
    TextView iconButton(String name,View.OnClickListener action){TextView view=button(name,action);view.setTextSize(10);view.setTypeface(Typeface.DEFAULT);view.setPadding(dp(6),dp(3),dp(6),dp(3));V1Icon icon=new V1Icon(name,TEXT);icon.setBounds(0,0,dp(22),dp(22));view.setCompoundDrawables(null,icon,null,null);view.setCompoundDrawablePadding(dp(3));view.setBackground(navBackground(false));return view;}
    android.graphics.drawable.Drawable navBackground(boolean selected){return BlackIceTheme.tab(getResources().getDisplayMetrics().density,selected);}
    GradientDrawable buttonBackground(boolean selected){return BlackIceTheme.surface(getResources().getDisplayMetrics().density,selected); }
    void addAction(String title,Runnable action){TextView b=button(title,v->action.run());if(tab==1&&!title.startsWith("Duration:")){if(contextualRow==null||contextualCount%2==0){contextualRow=row();LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(6);inspector.addView(contextualRow,rowParams);}LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(56),1);cell.rightMargin=dp(4);contextualRow.addView(b,cell);contextualCount++;return;}LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(6);inspector.addView(b,params);}
    interface IndexAction{void apply(int index);}
    interface ValueAction{void apply(float value);}
    interface TextAction{void apply(String text);}
    void select(String label,String[] values,int current,IndexAction action){addAction(label+": "+values[current],()->new AlertDialog.Builder(this).setTitle(label).setSingleChoiceItems(values,current,(d,i)->{d.dismiss();if(editable())action.apply(i);}).show());}
    void slider(String name,float value,ValueAction action){TextView title=note(name+"  "+Math.round(value*100)+"%");inspector.addView(title);SeekBar slider=new SeekBar(this);slider.setMax(100);slider.setProgress(Math.round(value*100));slider.setContentDescription(name);inspector.addView(slider,new LinearLayout.LayoutParams(-1,dp(48)));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){title.setText(name+"  "+p+"%");}public void onStartTrackingTouch(SeekBar s){if(player!=null)player.pause();}public void onStopTrackingTouch(SeekBar s){if(editable())action.apply(s.getProgress()/100f);}});}
    LinearLayout form(){LinearLayout l=column();l.setPadding(dp(18),dp(8),dp(18),dp(10));return l;}
    EditText field(LinearLayout form,String title,String value){form.addView(note(title));EditText e=new EditText(this);e.setText(value);e.setTextColor(TEXT);e.setSingleLine(false);e.setMinHeight(dp(48));form.addView(e);return e;}
    void dialog(String title,LinearLayout form,Runnable apply,Runnable delete){ScrollView scroll=new ScrollView(this);scroll.addView(form);AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("Apply",null).setNegativeButton("Cancel",null);if(delete!=null)b.setNeutralButton("Remove",(d,w)->{if(editable())delete.run();});AlertDialog dialog=b.create();dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(!editable())return;try{apply.run();dialog.dismiss();}catch(Exception e){Toast.makeText(this,e.getMessage()==null?"Check the entered values.":e.getMessage(),Toast.LENGTH_LONG).show();}}));dialog.show();}
    void numberDialog(String title,String value,TextAction action){LinearLayout form=form();EditText input=field(form,title,value);dialog(title,form,()->action.apply(input.getText().toString().trim()),null);}
    static long parseMs(String value){double seconds=Double.parseDouble(value);if(!Double.isFinite(seconds)||Math.abs(seconds)>7_200_000)throw new IllegalArgumentException("Enter a valid time in seconds.");return Math.round(seconds*1000);}
    static float parseFloat(EditText field){float value=Float.parseFloat(field.getText().toString());if(!Float.isFinite(value))throw new IllegalArgumentException("Enter a finite number.");return value;}
    static String seconds(long ms){return String.format(Locale.US,"%.2f",ms/1000d);}
    static String clock(long ms){return String.format(Locale.US,"%02d:%02d.%02d",ms/60000,(ms/1000)%60,(ms%1000)/10);}
}

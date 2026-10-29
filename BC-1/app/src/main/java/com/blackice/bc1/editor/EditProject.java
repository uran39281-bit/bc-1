package com.blackice.bc1.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Portable edit decision list. Times are integer milliseconds; media is never modified. */
public final class EditProject {
    /** Saved-file schema. Since schema 5: eight visual tracks (0 main, 1–7 layers) and audio on tracks 8–11 (was 4–7). */
    public static final int SCHEMA = 11;
    /** Generated caption overlays (group {@link com.blackice.bc1.core.CaptionBuilder#GROUP}); not counted as regular overlays. */
    public static final int MAX_CAPTIONS=2000;
    public String appliedCaptionJobId="";
    public int regularOverlayCount(){int n=0;for(Overlay o:overlays)if(!com.blackice.bc1.core.CaptionBuilder.GROUP.equals(o.group))n++;return n;}
    /** Visual tracks: 0 is the main sequence, 1..VISUAL_TRACKS-1 are independent video/image layers. */
    public static final int VISUAL_TRACKS = 8;
    public static final int AUDIO_FIRST = 8, AUDIO_LAST = 11, TRACK_COUNT = 12;
    public static boolean audioTrack(int track) { return track >= AUDIO_FIRST; }
    /** Highest independent visual layer track. */
    public static final int LAST_LAYER = VISUAL_TRACKS - 1;
    public static final long MIN_CLIP_MS = 100;
    public String id = UUID.randomUUID().toString(), name = "Untitled project";
    public boolean portrait;
    /** Optional additive metadata. Empty retains legacy portrait geometry and project compatibility. */
    public String canvasRatio="";
    public String ratioName(){return canvasRatio==null||canvasRatio.isEmpty()?(portrait?"9:16":"16:9"):canvasRatio;}
    public void setCanvasRatio(String ratio){if(!com.blackice.bc1.core.CanvasFormat.valid(ratio))throw new IllegalArgumentException("Unsupported canvas ratio");canvasRatio=ratio;portrait=com.blackice.bc1.core.CanvasFormat.aspect(ratio)<1;}
    public int[] canvasSize(int edge){return com.blackice.bc1.core.CanvasFormat.size(edge,ratioName());}
    public float canvasAspect(){int[] size=canvasSize(1080);return (float)size[0]/size[1];}
    public String canvasLabel(){int[] size=canvasSize(1080);return size[0]+" × "+size[1]+" • "+ratioName();}
    public int previewHeight = 540;
    public long playheadMs;
    public final List<Clip> clips = new ArrayList<>();
    public final List<Overlay> overlays = new ArrayList<>();
    /** Imported media not yet (or no longer) on the timeline. Entries are clip templates: only identity,
     * source and size fields are meaningful; placing one copies it onto a track. */
    public final List<Clip> library = new ArrayList<>();
    public static final int LIBRARY_LIMIT = 100;
    public Music music;
    public final List<Landmark> landmarks=new ArrayList<>();
    public static final int[] MARKER_COLORS={0xFF40BFFF,0xFFFFCB66,0xFFDD9AFF,0xFF62E1B1,0xFFFF809B};
    public static final class Landmark {
        public String id=UUID.randomUUID().toString(),label="";public long timeMs;public int slot;
    }
    public Landmark addLandmark(long time){
        if(landmarks.size()>=5)throw new IllegalArgumentException("Five markers maximum. Delete a marker to free a slot.");
        Landmark marker=new Landmark();marker.timeMs=Math.max(0,time);boolean[] used=new boolean[5];for(Landmark m:landmarks)used[m.slot]=true;
        while(used[marker.slot])marker.slot++;marker.label="Marker "+(marker.slot+1);landmarks.add(marker);return marker;
    }
    /** Editor frame rate: timeline frame grid, nudging and export cadence (24–120 fps). */
    public int frameRate = 30;
    /** Row metadata for tracks 0–11 (see SCHEMA); older projects are remapped when decoded. */
    public final Track[] tracks = new Track[TRACK_COUNT];
    /** Beat / cue markers in timeline milliseconds, kept sorted. */
    public final List<Long> markers = new ArrayList<>();
    /** Preview-only loop range; negative when unset. Never affects export. */
    public long loopInMs = -1, loopOutMs = -1;
    public EditProject(){for(int t=0;t<tracks.length;t++)tracks[t]=new Track(Track.defaultName(t));}

    /** Model-boundary snapshot: preserve identities, detach every mutable graph. */
    public EditProject copy(){EditProject p=new EditProject();p.id=id;p.name=name;p.appliedCaptionJobId=appliedCaptionJobId;p.portrait=portrait;p.canvasRatio=canvasRatio;p.previewHeight=previewHeight;p.playheadMs=playheadMs;p.frameRate=frameRate;p.loopInMs=loopInMs;p.loopOutMs=loopOutMs;
        for(int i=0;i<tracks.length;i++)p.tracks[i]=tracks[i].copy();p.markers.addAll(markers);
        for(Clip c:clips){Clip n=c.copy();n.id=c.id;p.clips.add(n);}for(Clip c:library){Clip n=c.copy();n.id=c.id;p.library.add(n);}for(Overlay o:overlays){Overlay n=o.copy();n.id=o.id;p.overlays.add(n);}
        for(Landmark m:landmarks){Landmark n=new Landmark();n.id=m.id;n.label=m.label;n.timeMs=m.timeMs;n.slot=m.slot;p.landmarks.add(n);}
        if(music!=null){Music n=new Music();n.uri=music.uri;n.name=music.name;n.sourceDurationMs=music.sourceDurationMs;n.inMs=music.inMs;n.outMs=music.outMs;n.startMs=music.startMs;n.volume=music.volume;n.loop=music.loop;p.music=n;}return p;
    }
    public static final class Track {
        public String name; public int color;
        public boolean hidden, solo, locked, muted, collapsed;
        public Track(String name){this.name=name;color=0xFF40BFFF;}
        public static String defaultName(int t){return t==0?"Main":t<AUDIO_FIRST?"Layer "+t:"Audio "+(t-AUDIO_FIRST+1);}
        public Track copy(){Track c=new Track(name);c.color=color;c.hidden=hidden;c.solo=solo;c.locked=locked;c.muted=muted;c.collapsed=collapsed;return c;}
    }

    public static final class Clip {
        public com.blackice.bc1.audio.AudioSettings audio=new com.blackice.bc1.audio.AudioSettings();
        public String id = UUID.randomUUID().toString(), uri, name;
        public boolean image, audioOnly;
        public int track; public long startMs,fadeInMs,fadeOutMs;
        /** Main sequence only: empty time before this clip. A gap also suppresses the transition into it. */
        public long gapMs;
        public float x,y,scale=1,rotation,keyTolerance=.25f,keySoftness=.08f,keySpill=.5f;
        /** Height relative to width scale (1 keeps proportions), skew in degrees, anchor in position units. */
        public float heightScale=-1; // -1 retains legacy scale × aspect animation
        public float aspect=1,skewX,skewY,anchorX,anchorY; public boolean flipH,flipV;
        /** Source pixel size when known (0 = unknown, measured lazily for Fit/Fill). */
        public int width,height;
        /** Optional group identifier shared by layers that move together. */
        public String group;
        public int keyColor=0xFF00FF00; public boolean chroma;
        public Motion motion=new Motion();
        public long sourceDurationMs, inMs, outMs;
        public int effect, transition = -1, easing = 1;
        /** Blend mode over the layers below (core.BlendMode id; 0 normal). Rendered by ICE Lite. */
        public int blend;
        public float unmutedVolume=-1;
        public float intensity = .6f, opacity = 1f, volume = .7f,speed=1f;
        public long transitionMs = 500;
        public long durationMs() { return Math.round((outMs - inMs)/(double)speed); }
        public Clip copy() {
            Clip c = new Clip(); c.audio=audio.copy();c.gapMs=gapMs; c.uri=uri; c.name=name; c.image=image;c.audioOnly=audioOnly;
            c.track=track;c.startMs=startMs;c.fadeInMs=fadeInMs;c.fadeOutMs=fadeOutMs;c.x=x;c.y=y;c.scale=scale;c.rotation=rotation;c.chroma=chroma;c.keyColor=keyColor;c.keyTolerance=keyTolerance;c.keySoftness=keySoftness;c.keySpill=keySpill;c.motion=motion.copy();
            c.heightScale=heightScale;c.aspect=aspect;c.skewX=skewX;c.skewY=skewY;c.anchorX=anchorX;c.anchorY=anchorY;c.flipH=flipH;c.flipV=flipV;c.width=width;c.height=height;c.group=group;
            c.sourceDurationMs=sourceDurationMs; c.inMs=inMs; c.outMs=outMs;
            c.effect=effect; c.transition=transition; c.easing=easing; c.blend=blend;
            c.intensity=intensity; c.opacity=opacity; c.volume=volume;c.unmutedVolume=unmutedVolume; c.transitionMs=transitionMs;c.speed=speed;
            return c;
        }
    }
    public static final class Overlay {
        public String visualizerClipId;public int visualizerStyle;
        public String id = UUID.randomUUID().toString(), text = "BLACK ICE", imageUri;
        public long startMs, endMs = 3000;
        public float x=0, y=-.65f, scale=.7f, opacity=1f; public Motion motion=new Motion();
        public FillStyle fill;
        public String font="sans-serif-medium";public int color=0xFFFFFFFF,alignment=1;
        /** 0 text/image; 1 and above index {@link #SHAPES} (vector shapes). */
        public int shape;
        public String name, group;
        public boolean hidden, solo, locked, flipH, flipV;
        public float heightScale=-1;
        public float rotation, aspect=1, skewX, skewY, anchorX, anchorY, borderWidth, shadow;
        public int borderColor=0xFF000000, blend;
        public Overlay copy(){Overlay o=new Overlay();o.visualizerClipId=visualizerClipId;o.visualizerStyle=visualizerStyle;o.text=text;o.imageUri=imageUri;o.startMs=startMs;o.endMs=endMs;o.x=x;o.y=y;o.scale=scale;o.opacity=opacity;o.motion=motion.copy();o.font=font;o.color=color;o.fill=fill==null?null:fill.copy();o.alignment=alignment;o.shape=shape;o.name=name;o.group=group;o.hidden=hidden;o.solo=solo;o.locked=locked;o.flipH=flipH;o.flipV=flipV;o.rotation=rotation;o.heightScale=heightScale;o.aspect=aspect;o.skewX=skewX;o.skewY=skewY;o.anchorX=anchorX;o.anchorY=anchorY;o.borderWidth=borderWidth;o.shadow=shadow;o.borderColor=borderColor;o.blend=blend;return o;}
        public String label(){if(visualizerClipId!=null)return "Audio visualizer (disabled)";if(name!=null&&!name.isEmpty())return name;if(imageUri!=null)return "Image";if(shape>0)return SHAPES[shape];return text;}
    }
    public static final String[] SHAPES={"Text","Square","Circle","Triangle","Line","Cross","Arc","Speech bubble","Pie","Hexagon","Flower","Diagonal arrow","Trapezoid","Half moon","Rectangle","Star","Thick line","Polygon","Decorative rectangle","Oval","Diamond","Octagon","Rhombus","Heart","Half circle","Arrow","Three-leaf clover","Monkey head"};
    public static final String[] BLENDS={"Normal","Multiply","Screen","Overlay","Add","Darken","Lighten"};
    public static final class Music {
        public String uri, name;
        public long sourceDurationMs, inMs, outMs, startMs;
        public float volume=.3f;
        public boolean loop=true;
    }
    public static final class Placement {
        public final Clip clip;
        public final int index;
        public final long startMs, endMs, incomingMs, outgoingMs;
        Placement(Clip clip, int index, long start, long incoming, long outgoing) {
            this.clip=clip; this.index=index; startMs=start; endMs=start+clip.durationMs();
            incomingMs=incoming; outgoingMs=outgoing;
        }
    }
    /** Adds a library entry for every timeline source that lacks one (older projects, extracted audio). */
    public void ensureLibrary(){
        for(Clip c:clips){if(library.size()>=LIBRARY_LIMIT)return;if(libraryIndex(c.uri)<0)library.add(asset(c));}
    }
    public int libraryIndex(String uri){for(int i=0;i<library.size();i++)if(library.get(i).uri.equals(uri))return i;return -1;}
    /** A fresh, full-length library template for the clip's source. */
    public static Clip asset(Clip source){
        Clip a=new Clip();a.uri=source.uri;a.name=source.name;a.image=source.image;a.audioOnly=source.audioOnly;
        a.width=source.width;a.height=source.height;a.sourceDurationMs=source.sourceDurationMs;
        a.inMs=0;a.outMs=source.image?Math.max(MIN_CLIP_MS,Math.min(source.sourceDurationMs,source.outMs-source.inMs)):source.sourceDurationMs;
        if(a.outMs<MIN_CLIP_MS){a.outMs=Math.max(MIN_CLIP_MS,source.outMs);a.sourceDurationMs=Math.max(a.sourceDurationMs,a.outMs);}
        return a;
    }
    public int lastBase(){for(int n=clips.size()-1;n>=0;n--)if(clips.get(n).track==0)return n;return -1;}
    private int nextBase(int i){for(int n=i+1;n<clips.size();n++)if(clips.get(n).track==0)return n;return -1;}
    public long overlapAfter(int i) {
        if(i<0||i>=clips.size()||clips.get(i).track!=0||clips.get(i).transition<0)return 0;
        int n=nextBase(i);if(n<0||clips.get(n).gapMs>0)return 0;
        return Math.max(0,Math.min(clips.get(i).transitionMs,Math.min(clips.get(i).durationMs()/2,clips.get(n).durationMs()/2)));
    }
    public List<Placement> placements() {
        List<Placement> result=new ArrayList<>();long start=0,incoming=0;
        for(int i=0;i<clips.size();i++){Clip c=clips.get(i);long out=overlapAfter(i);
            if(c.track==0&&c.gapMs>0){start+=c.gapMs;incoming=0;}
            Placement p=new Placement(c,i,c.track==0?start:c.startMs,c.track==0?incoming:0,out);result.add(p);
            if(c.track==0){start=p.endMs-out;incoming=out;}}
        return result;
    }
    public long durationMs(){long total=0;for(Placement p:placements())total=Math.max(total,p.endMs);for(Overlay o:overlays)total=Math.max(total,o.endMs);return total;}
    public int freeAudioTrack(long start,long duration){for(int t=AUDIO_FIRST;t<=AUDIO_LAST;t++){boolean free=true;for(Placement p:placements())if(p.clip.track==t&&p.startMs<start+duration&&p.endMs>start)free=false;if(free)return t;}throw new IllegalArgumentException("All four audio tracks are occupied at this time.");}
    public void setPlacement(int index,int track,long start){Clip c=clips.get(index);int old=c.track;long oldStart=c.startMs,oldGap=c.gapMs;c.track=track;c.startMs=start;if(track!=0)c.gapMs=0;try{validate();}catch(RuntimeException e){c.track=old;c.startMs=oldStart;c.gapMs=oldGap;throw e;}}
    public void trim(int index,long in,long out) {
        Clip c=clips.get(index);
        if(in<0 || (!c.image&&out>c.sourceDurationMs) || out-in<MIN_CLIP_MS*c.speed || out>7_200_000_000L) throw new IllegalArgumentException("Keep at least 0.10 seconds inside the source.");
        long oldIn=c.inMs,oldOut=c.outMs,oldSource=c.sourceDurationMs,fadeIn=c.fadeInMs,fadeOut=c.fadeOutMs;
        c.inMs=in;c.outMs=out;if(c.image)c.sourceDurationMs=Math.max(c.sourceDurationMs,out);
        c.fadeInMs=Math.min(c.fadeInMs,c.durationMs());c.fadeOutMs=Math.min(c.fadeOutMs,c.durationMs());
        try{validate();}catch(RuntimeException e){c.inMs=oldIn;c.outMs=oldOut;c.sourceDurationMs=oldSource;c.fadeInMs=fadeIn;c.fadeOutMs=fadeOut;throw e;}
    }
    public void split(int index,long timelineMs) {
        if(clips.size()>=100)throw new IllegalArgumentException("This preview supports up to 100 clips.");
        Placement p=placements().get(index); long offset=timelineMs-p.startMs;
        if(offset<MIN_CLIP_MS || p.clip.durationMs()-offset<MIN_CLIP_MS)
            throw new IllegalArgumentException("Place the playhead inside the selected clip, at least 0.10 seconds from either edge.");
        Clip right=p.clip.copy(); right.gapMs=0; right.inMs+=Math.round(offset*(double)p.clip.speed);if(right.track>0)right.startMs=p.startMs+offset;
        p.clip.outMs=right.inMs; p.clip.transition=-1;p.clip.fadeInMs=Math.min(p.clip.fadeInMs,p.clip.durationMs());p.clip.fadeOutMs=0;right.fadeInMs=0;right.fadeOutMs=Math.min(right.fadeOutMs,right.durationMs());
        clips.add(index+1,right);
    }
    public void move(int index,int delta) {
        int next=index+delta; if(next<0 || next>=clips.size()) return;
        Clip c=clips.remove(index); clips.add(next,c);
    }
    public long snap(long timeMs,long thresholdMs) {
        long target=Math.max(0,Math.min(durationMs(),timeMs)), distance=thresholdMs+1;
        for(Placement p:placements()) for(long edge:new long[]{p.startMs,p.endMs}) {
            long d=Math.abs(edge-timeMs); if(d<=thresholdMs && d<distance) {target=edge; distance=d;}
        }
        return target;
    }
    public void validate() {
        if(!com.blackice.bc1.core.CanvasFormat.valid(ratioName()))throw new IllegalArgumentException("Unsupported canvas ratio.");
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,80}") || name==null || name.length()>120)
            throw new IllegalArgumentException("Invalid project identity.");
        if(library.size()>LIBRARY_LIMIT) throw new IllegalArgumentException("The media bin holds up to "+LIBRARY_LIMIT+" items.");
        for(Clip a:library) if(a.uri==null||a.name==null||a.sourceDurationMs<0) throw new IllegalArgumentException("Invalid media bin entry.");
        if(clips.size()>100 || regularOverlayCount()>20) throw new IllegalArgumentException("Preview supports up to 100 clips and 20 overlays.");
        if(overlays.size()-regularOverlayCount()>MAX_CAPTIONS) throw new IllegalArgumentException("Use at most "+MAX_CAPTIONS+" captions.");
        if(previewHeight!=360 && previewHeight!=540 && previewHeight!=1080) throw new IllegalArgumentException("Invalid preview resolution.");
        if(frameRate!=24&&frameRate!=25&&frameRate!=30&&frameRate!=50&&frameRate!=60&&frameRate!=120) throw new IllegalArgumentException("Use 24, 25, 30, 50, 60 or 120 fps.");
        for(Track t:tracks) if(t==null||t.name==null||t.name.isEmpty()||t.name.length()>60) throw new IllegalArgumentException("Every layer needs a name of up to 60 characters.");
        if(landmarks.size()>5)throw new IllegalArgumentException("Five markers maximum.");
        java.util.HashSet<Integer> slots=new java.util.HashSet<>();
        for(Landmark m:landmarks)if(m.timeMs<0||m.slot<0||m.slot>=5||!slots.add(m.slot)||m.label==null||m.label.length()>80)throw new IllegalArgumentException("Invalid landmark.");
        if(markers.size()>500) throw new IllegalArgumentException("Use at most 500 markers.");
        for(int i=0;i<markers.size();i++) if(markers.get(i)<0||(i>0&&markers.get(i)<=markers.get(i-1))) throw new IllegalArgumentException("Markers must be distinct times from zero.");
        if((loopInMs<0)!=(loopOutMs<0)||(loopInMs>=0&&loopOutMs-loopInMs<MIN_CLIP_MS)) throw new IllegalArgumentException("Loop range needs at least 0.10 seconds.");
        for(Clip c:clips) {
            c.audio.validate();
            c.motion.validate();
            if(!(c.heightScale==-1||Motion.allowed(Motion.HEIGHT,c.heightScale))||!(c.aspect>=.05f&&c.aspect<=20)||!(Math.abs(c.skewX)<=80)||!(Math.abs(c.skewY)<=80)||!Motion.allowed(Motion.ANCHOR_X,c.anchorX)||!Motion.allowed(Motion.ANCHOR_Y,c.anchorY)||c.width<0||c.height<0||(c.group!=null&&c.group.length()>40)) throw new IllegalArgumentException("Invalid transform: "+c.name);
            if(c.gapMs<0||c.gapMs>7_200_000_000L||(c.track!=0&&c.gapMs!=0))throw new IllegalArgumentException("Gaps belong to main-sequence clips: "+c.name);
            if(c.audioOnly&&!audioTrack(c.track))throw new IllegalArgumentException("Audio-only media belongs on an audio lane.");
            if(c.track<0||c.track>=TRACK_COUNT||c.startMs<0||c.fadeInMs<0||c.fadeOutMs<0||c.fadeInMs>c.durationMs()||c.fadeOutMs>c.durationMs()||!Motion.allowed(0,c.x)||!Motion.allowed(1,c.y)||!Motion.allowed(2,c.scale)||!Motion.allowed(3,c.rotation)||!unit(c.keyTolerance)||!unit(c.keySoftness)||c.keySoftness<.001f||!unit(c.keySpill)||(audioTrack(c.track)&&c.image))throw new IllegalArgumentException("Invalid track, transform or fade.");
            if(c.uri==null || c.name==null || c.inMs<0 || c.outMs>c.sourceDurationMs || c.durationMs()<MIN_CLIP_MS || !Float.isFinite(c.speed)||c.speed<.25f||c.speed>4||(c.image&&c.speed!=1)
                    || !com.blackice.bc1.core.EffectCatalog.valid(c.effect) || !com.blackice.bc1.core.BlendMode.valid(c.blend) || c.transition< -1 || c.transition>6 || c.easing<0 || c.easing>1
                    || !Float.isFinite(c.unmutedVolume)||c.unmutedVolume < -1||c.unmutedVolume>1 || c.transitionMs<0 || c.transitionMs>5000 || !unit(c.intensity) || !unit(c.opacity) || !unit(c.volume))
                throw new IllegalArgumentException("Invalid clip: "+c.name);
        }
        List<Placement> ps=placements();
        for(int i=0;i<ps.size();i++)for(int n=i+1;n<ps.size();n++){Placement a=ps.get(i),b=ps.get(n);if(a.clip.track>0&&a.clip.track==b.clip.track&&a.startMs<b.endMs&&b.startMs<a.endMs)throw new IllegalArgumentException("Clips on the same layer cannot overlap. Use another layer or move the start time.");}
        for(Overlay o:overlays){o.motion.validate();if(o.fill!=null)o.fill.validate();if(!(o.heightScale==-1||Motion.allowed(Motion.HEIGHT,o.heightScale))||o.shape<0||o.shape>=SHAPES.length||o.blend<0||o.blend>=BLENDS.length||!(o.aspect>=.05f&&o.aspect<=20)||!(Math.abs(o.skewX)<=80)||!(Math.abs(o.skewY)<=80)||!Float.isFinite(o.rotation)||!Motion.allowed(Motion.ANCHOR_X,o.anchorX)||!Motion.allowed(Motion.ANCHOR_Y,o.anchorY)||!(o.borderWidth>=0&&o.borderWidth<=1)||!(o.shadow>=0&&o.shadow<=1)||(o.name!=null&&o.name.length()>60)||(o.group!=null&&o.group.length()>40))throw new IllegalArgumentException("Invalid overlay style.");}
        for(Overlay o:overlays) if(o.startMs<0 || o.endMs<=o.startMs || !unit(o.opacity)
                || !Float.isFinite(o.scale) || o.scale<.1f || o.scale>Motion.MAX_SCALE || !Float.isFinite(o.x) || Math.abs(o.x)>100000
                || !Float.isFinite(o.y) || Math.abs(o.y)>100000 || o.text==null || o.text.length()>200 || o.alignment<0||o.alignment>2 || !("sans-serif-medium".equals(o.font)||"serif".equals(o.font)||"monospace".equals(o.font)))
            throw new IllegalArgumentException("Invalid overlay.");
        if(music!=null && (music.uri==null || music.inMs<0 || music.outMs>music.sourceDurationMs
                || music.outMs-music.inMs<MIN_CLIP_MS || music.startMs<0 || !unit(music.volume)))
            throw new IllegalArgumentException("Invalid music trim.");
        playheadMs=Math.max(0,Math.min(playheadMs,durationMs()));
    }
    private static boolean unit(float f) { return Float.isFinite(f) && f>=0 && f<=1; }
}

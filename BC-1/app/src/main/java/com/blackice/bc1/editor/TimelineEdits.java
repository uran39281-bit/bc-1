package com.blackice.bc1.editor;

/** Portable, validated timeline operations. UI gestures commit one history entry. */
public final class TimelineEdits {
    private TimelineEdits() {}
    public static void duration(EditProject p,int index,long duration) {
        EditProject.Clip c=p.clips.get(index);
        if(duration<EditProject.MIN_CLIP_MS || duration>7_200_000_000L)
            throw new IllegalArgumentException("Duration must be at least 0.10 seconds.");
        p.trim(index,c.inMs,Math.addExact(c.inMs,Math.round(duration*(double)c.speed)));
    }
    public static void slip(EditProject p,int index,long sourceIn) {
        EditProject.Clip c=p.clips.get(index);
        if(c.image)throw new IllegalArgumentException("Still images have no source segment to slip.");
        p.trim(index,sourceIn,Math.addExact(sourceIn,c.outMs-c.inMs));
    }
    public static void edge(EditProject p,int index,boolean left,long deltaMs) {
        EditProject.Clip c=p.clips.get(index);
        long oldStart=c.startMs;
        // Main sequence trims ripple; independent tracks retain the opposite edge.
        if(left && c.track>0)c.startMs=Math.addExact(c.startMs,deltaMs);
        try {
            if(c.startMs<0)throw new IllegalArgumentException("A clip cannot start before zero.");
            if(c.image){long length=c.durationMs()+(left?-deltaMs:deltaMs);duration(p,index,length);}
            else p.trim(index,c.inMs+(left?Math.round(deltaMs*(double)c.speed):0),c.outMs+(left?0:Math.round(deltaMs*(double)c.speed)));
        }catch(RuntimeException e){c.startMs=oldStart;throw e;}
    }
    public static void speed(EditProject p,int index,float speed){
        EditProject.Clip c=p.clips.get(index);if(c.image||!Float.isFinite(speed)||speed<.25f||speed>4)throw new IllegalArgumentException("Use a video/audio speed from 0.25× to 4×.");
        float before=c.speed;long fadeIn=c.fadeInMs,fadeOut=c.fadeOutMs;c.speed=speed;c.fadeInMs=Math.min(fadeIn,c.durationMs());c.fadeOutMs=Math.min(fadeOut,c.durationMs());
        try{p.validate();}catch(RuntimeException e){c.speed=before;c.fadeInMs=fadeIn;c.fadeOutMs=fadeOut;throw e;}
    }
    /** Moves a clip to a track and timeline start, returning its new index. Layers 1–7 keep the exact start.
     * The main sequence (0) stays ordered: the clip is inserted by time with the empty space before it kept
     * as a gap; later main clips shift right only when it would otherwise overlap them. Every other
     * main-sequence clip keeps its timeline position. Callers restore a snapshot if this throws. */
    public static int place(EditProject p,int index,int track,long start) {
        EditProject.Clip c=p.clips.get(index);
        if(track<0||track>=EditProject.TRACK_COUNT)throw new IllegalArgumentException("No such layer.");
        if(EditProject.audioTrack(c.track)!=EditProject.audioTrack(track))throw new IllegalArgumentException(EditProject.audioTrack(c.track)?"Audio stays on audio layers.":"Video and pictures stay on visual layers.");
        start=Math.max(0,start);
        java.util.Map<EditProject.Clip,Long> kept=new java.util.IdentityHashMap<>();
        for(EditProject.Placement pl:p.placements())if(pl.clip.track==0&&pl.clip!=c)kept.put(pl.clip,pl.startMs);
        p.clips.remove(index);c.gapMs=0;c.track=track;c.startMs=track==0?0:start;
        int at=index;
        if(track==0){
            long center=start+c.durationMs()/2;at=-1;
            for(int i=0;i<p.clips.size();i++){EditProject.Clip m=p.clips.get(i);if(m.track==0&&center<=kept.get(m)+m.durationMs()/2){at=i;break;}}
            if(at<0){int last=p.lastBase();at=last<0?Math.min(index,p.clips.size()):last+1;}
            kept.put(c,start);
        }
        p.clips.add(at,c);
        regap(p,kept);
        p.validate();
        return at;
    }
    /** Rebuilds main-sequence gaps so each clip starts at its wanted time, or as soon after it as the clips
     * before allow (transitions still overlap clips that touch). */
    static void regap(EditProject p,java.util.Map<EditProject.Clip,Long> wanted) {
        long end=0;int previous=-1;
        for(int i=0;i<p.clips.size();i++){EditProject.Clip m=p.clips.get(i);if(m.track!=0)continue;
            Long want=wanted.get(m);m.gapMs=previous<0?Math.max(0,want==null?0:want):Math.max(0,(want==null?end:want)-end);
            long begin=m.gapMs>0||previous<0?end+m.gapMs:end-p.overlapAfter(previous);
            end=begin+m.durationMs();previous=i;}
    }
    /** Snaps to zero, the playhead, markers and clip edges; returns the input unchanged when none lies within the screen-space tolerance. */
    public static long snap(EditProject p,int excluded,long time,long tolerance) {
        long target=time,distance=tolerance+1;
        long[] fixed={0,p.playheadMs};
        for(long edge:fixed){long d=Math.abs(edge-time);if(d<=tolerance&&d<distance){target=edge;distance=d;}}
        for(EditProject.Landmark marker:p.landmarks){long edge=marker.timeMs,d=Math.abs(edge-time);if(d<=tolerance&&d<distance){target=edge;distance=d;}}
        for(long edge:p.markers){long d=Math.abs(edge-time);if(d<=tolerance&&d<distance){target=edge;distance=d;}}
        for(EditProject.Placement placement:p.placements())if(placement.index!=excluded)
            for(long edge:new long[]{placement.startMs,placement.endMs}){
                long d=Math.abs(edge-time);if(d<=tolerance&&d<distance){target=edge;distance=d;}
            }
        return target;
    }
    public static double rulerStep(double pixelsPerSecond,double minimumPixels) {return rulerStep(pixelsPerSecond,minimumPixels,30);}
    /** Coarse zoom labels whole seconds; closer views step to half seconds, then frames of the project rate. */
    public static double rulerStep(double pixelsPerSecond,double minimumPixels,int fps) {
        double[] steps={1d/fps,2d/fps,5d/fps,10d/fps,.5,1,2,5,10,15,30,60,120,300,600,1800,3600};
        for(double step:steps)if(step*pixelsPerSecond>=minimumPixels)return step;
        return 3600*Math.ceil(minimumPixels/pixelsPerSecond/3600);
    }
    /** "4.0s" / "1.5s" at second and half-second steps; "1s 12f" at frame steps; m:ss beyond a minute. */
    public static String rulerLabel(long ms,double stepSeconds,int fps) {
        if(stepSeconds>=60) return String.format(java.util.Locale.US,"%d:%02d",ms/60000,(ms/1000)%60);
        if(stepSeconds>=.5) return String.format(java.util.Locale.US,"%.1fs",ms/1000d);
        long frames=Math.round(ms*fps/1000.0);
        return frames%fps==0?(frames/fps)+"s":(frames/fps)+"s "+(frames%fps)+"f";
    }
}

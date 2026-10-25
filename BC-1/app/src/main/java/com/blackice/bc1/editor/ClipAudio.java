package com.blackice.bc1.editor;
/** Reversible clip mute; volume remains the effective gain used by preview and export. */
public final class ClipAudio {
    private ClipAudio(){}
    /** Source-time volume animation multiplied by legacy timeline fades. */
    public static float gain(EditProject.Clip c,double localMs,boolean muted){
        if(muted||c.unmutedVolume>=0)return 0;
        float value=c.motion.value(Motion.VOLUME,c.inMs+Math.round(localMs*c.speed),c.volume);
        double envelope=1;
        if(c.fadeInMs>0)envelope=Math.min(envelope,localMs/c.fadeInMs);
        if(c.fadeOutMs>0)envelope=Math.min(envelope,(c.durationMs()-localMs)/c.fadeOutMs);
        return value*(float)Math.pow(10,c.audio.normalizationDb/20.0)*(float)Math.max(0,Math.min(1,envelope));
    }
    public static void toggleMute(EditProject.Clip c){
        if(c.image)throw new IllegalArgumentException("Pictures have no audio");
        if(c.unmutedVolume>=0){c.volume=c.unmutedVolume;c.unmutedVolume=-1;}
        else{c.unmutedVolume=c.volume;c.volume=0;}
    }
}

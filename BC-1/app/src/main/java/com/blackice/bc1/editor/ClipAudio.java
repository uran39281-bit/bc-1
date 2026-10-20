package com.blackice.bc1.editor;
/** Reversible clip mute; volume remains the effective gain used by preview and export. */
public final class ClipAudio {
    private ClipAudio(){}
    public static void toggleMute(EditProject.Clip c){
        if(c.image)throw new IllegalArgumentException("Pictures have no audio");
        if(c.unmutedVolume>=0){c.volume=c.unmutedVolume;c.unmutedVolume=-1;}
        else{c.unmutedVolume=c.volume;c.volume=0;}
    }
}

package com.blackice.bc1.editor;

/** Pure transform editing arithmetic shared with tests. Values never wrap or round during edits. */
public final class TransformEdits {
    private TransformEdits() {}
    public static float pointerDelta(float before,float after){return (after-before+540)%360-180;}
    public static float[] dimensions(float width,float height,boolean horizontal,float target,boolean linked){
        float w=horizontal?target:linked?width*target/height:width;
        float h=!horizontal?target:linked?height*target/width:height;
        if(!Motion.allowed(Motion.WIDTH,w)||!Motion.allowed(Motion.HEIGHT,h))
            throw new IllegalArgumentException("Width supports 5–400%; height supports 0.25–8000%. Zero scale is not supported by this renderer.");
        return new float[]{w,h};
    }
    /** Lazily expose the legacy coupled scale curve without changing its time/easing or visual result. */
    public static void materialize(Motion m,int property,float staticOverride,float aspect){
        if(staticOverride>=0||!m.curves.get(property).isEmpty())return;
        for(Motion.Key key:m.curves.get(Motion.SCALE)){Motion.Key copy=key.copy();if(property==Motion.HEIGHT)copy.value*=aspect;m.put(property,copy);}
    }
    public static long frameTime(long time,int fps){return Math.round(Math.round(time*fps/1000d)*1000d/fps);}
}

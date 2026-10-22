package com.blackice.bc1.editor;

/** Editing math. Legacy uniform-scale curves remain untouched until independent dimensions are edited. */
public final class TransformEdits {
    private TransformEdits() {}
    public static void separateHeight(EditProject.Clip c) {
        if(c.heightScale>=0)return;
        copyHeight(c.motion,c.aspect);c.heightScale=c.scale*c.aspect;
    }
    public static void separateHeight(EditProject.Overlay o) {
        if(o.heightScale>=0)return;
        copyHeight(o.motion,o.aspect);o.heightScale=o.scale*o.aspect;
    }
    private static void copyHeight(Motion m,float aspect) {
        if(!m.curves.get(Motion.HEIGHT).isEmpty())return;
        for(Motion.Key key:m.curves.get(Motion.SCALE)){Motion.Key h=key.copy();h.value*=aspect;m.put(Motion.HEIGHT,h);}
    }
    /** Wrapped pointer delta; accumulation is deliberately never normalized. */
    public static float angleDelta(float before,float after){return ((after-before+540)%360)-180;}
    public static float[] scalePair(float width,float height,int property,float value,boolean linked) {
        float w=property==Motion.SCALE?value:width,h=property==Motion.HEIGHT?value:height;
        if(linked){if(!(width>0&&height>0))throw new IllegalArgumentException("Both dimensions must be above zero before linking.");if(property==Motion.SCALE)h=height*(value/width);else w=width*(value/height);}
        if(!Motion.allowed(Motion.SCALE,w)||!Motion.allowed(Motion.HEIGHT,h))throw new IllegalArgumentException("Scale is outside the supported range (Width 5–400%, Height 0.25–8000%). Zero is not supported by this renderer.");
        return new float[]{w,h};
    }
}

package com.blackice.bc1.menu;

/** Incremental clamping consumes overscroll, so reversal responds on the next sample. */
public final class RulerGesture {
    private double value, pointer;
    public RulerGesture(double value,double pointer){this.value=value;this.pointer=pointer;}
    public double move(double x,double unitsPerPixel,double min,double max){
        value=Math.max(min,Math.min(max,value+(x-pointer)*unitsPerPixel));pointer=x;return value;
    }
}

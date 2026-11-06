package com.blackice.bc1.editor;
/** Shared quick-insertion placement; drag/drop keeps its explicit drop start. */
public final class QuickInsertion {
 private QuickInsertion(){}
 public static long start(long playhead,long duration){if(playhead<0||duration<=0)throw new IllegalArgumentException("Invalid insertion time");return Math.max(0,playhead-duration/2);}
 public static long frame(long time,int fps){return Math.max(0,Math.round(time*fps/1000d));}
 public static long snapped(long time,int fps){return Math.round(frame(time,fps)*1000d/fps);}
}

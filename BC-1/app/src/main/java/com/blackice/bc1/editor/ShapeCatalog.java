package com.blackice.bc1.editor;

/** Stable saved shape IDs; the browser order can change without reinterpreting old projects. */
public final class ShapeCatalog {
    private ShapeCatalog() {}
    private static final int[] ORDER={1,14,3,2,19,9,20,21,22,15,23,13,5,4,24,25,26,27,6,7,8,10,11,12,16,17,18};
    public static int[] ids(){return ORDER.clone();}
    public static final class Drag {
        public final int shape; public final String projectId;
        public Drag(int shape,String projectId){if(shape<=0||shape>=EditProject.SHAPES.length)throw new IllegalArgumentException("Unknown shape");this.shape=shape;this.projectId=projectId;}
    }
    /** A vector shape is its own layer, never a reference to an imported media file. */
    public static EditProject.Overlay create(int shape,long at){
        if(shape<=0||shape>=EditProject.SHAPES.length||at<0||at>Long.MAX_VALUE-3000)throw new IllegalArgumentException("Invalid shape placement");
        EditProject.Overlay o=new EditProject.Overlay();o.shape=shape;o.text=EditProject.SHAPES[shape];o.color=0xFF40BFFF;o.y=0;o.scale=.7f;o.aspect=1;o.heightScale=-1;o.startMs=at;o.endMs=at+3000;return o;
    }
}

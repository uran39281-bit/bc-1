package com.blackice.bc1.character;

import android.graphics.*;
import java.nio.FloatBuffer;

/** Rebuild masked source pixels from the aligned clean plate; skin the PNG into the same video frame. */
public final class PersonCompositor {
    private static final Paint PAINT=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private PersonCompositor() {}
    public static Bitmap render(int w,int h,int[] pixels,int[] plate,FloatBuffer alpha,int mw,int mh,
                                Bitmap art,CharacterMesh mesh,float[] joints) {
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int i=y*w+x;float confidence=alpha.get((y*mh/h)*mw+x*mw/w);
                if(confidence>.1f)pixels[i]=plate[i];
            }
            Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);out.setPixels(pixels,0,w,0,0,w,h);
            mesh.pose(joints);new Canvas(out).drawBitmapMesh(art,CharacterMesh.GRID,CharacterMesh.GRID,mesh.vertices,0,null,0,PAINT);
        return out;
    }
}

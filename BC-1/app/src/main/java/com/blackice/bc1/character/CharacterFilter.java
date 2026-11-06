package com.blackice.bc1.character;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import com.google.mediapipe.framework.image.*;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Single-subject, static-camera replacement. First source frame must be an empty, matching clean plate. */
public final class CharacterFilter implements AutoCloseable {
    private final PoseLandmarker detector;
    private final Bitmap art;
    private final CharacterMesh mesh;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private int[] plate,pixels;
    private boolean replaced;
    private final Context context;
    private final Uri plateUri;
    public CharacterFilter(Context c, Uri image, Uri background) throws IOException {
        context=c;plateUri=background;
        art=loadSprite(c,image);mesh=new CharacterMesh(art.getWidth(),art.getHeight());
        detector=PoseLandmarker.createFromOptions(c,PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("character/pose-lite.task").build())
            .setNumPoses(2).setOutputSegmentationMasks(true).build());
    }
    private static Bitmap loadSprite(Context c,Uri uri)throws IOException{
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;
        try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opt);}
        if(!"image/png".equals(opt.outMimeType)||opt.outWidth<16||opt.outHeight<16)
            throw new IOException("Choose a transparent, full-body PNG in a front-facing T-pose.");
        opt.inJustDecodeBounds=false;opt.inSampleSize=1;
        while(Math.max(opt.outWidth,opt.outHeight)/opt.inSampleSize>1024)opt.inSampleSize*=2;
        opt.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap b;try(InputStream in=c.getContentResolver().openInputStream(uri)){b=BitmapFactory.decodeStream(in,null,opt);}
        if(b==null)throw new IOException("Could not open the character PNG.");
        int w=b.getWidth(),h=b.getHeight(),left=w,right=-1,top=h,bottom=-1,transparent=0;
        int[] row=new int[w];
        for(int y=0;y<h;y++){b.getPixels(row,0,w,0,y,w,1);for(int x=0;x<w;x++){
            if((row[x]>>>24)<16){transparent++;continue;}
            left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
        }}
        if(right-left<8||bottom-top<8||transparent<w*h/20){b.recycle();throw new IOException("Use a character cutout with a transparent background.");}
        Bitmap cropped=Bitmap.createBitmap(b,left,top,right-left+1,bottom-top+1);
        if(b!=cropped)b.recycle();return cropped;
    }
    public Bitmap apply(Bitmap frame,long ptsUs){
        int w=frame.getWidth(),h=frame.getHeight();float scale=Math.min(1,480f/Math.max(w,h));
        Bitmap analysis=Bitmap.createScaledBitmap(frame,Math.max(1,Math.round(w*scale)),Math.max(1,Math.round(h*scale)),true);
        if(analysis==frame)analysis=frame.copy(Bitmap.Config.ARGB_8888,false);
        MPImage input=new BitmapImageBuilder(analysis).build();
        PoseLandmarkerResult result;
        try{result=detector.detect(input);}finally{input.close();if(!analysis.isRecycled())analysis.recycle();}
        List<MPImage> masks=result.segmentationMasks().orElse(Collections.emptyList());
        try{
            if(plate==null && plateUri!=null){
                try{loadPlate(w,h);}catch(IOException e){throw new IllegalArgumentException("Could not open the empty background image.",e);}
            }
            if(plate==null){
                if(!result.landmarks().isEmpty())throw new IllegalArgumentException("The first frame shows a person. Start with the empty scene.");
                plate=new int[w*h];pixels=new int[w*h];frame.getPixels(plate,0,w,0,0,w,h);return frame;
            }
            if(result.landmarks().size()>1)throw new IllegalArgumentException("More than one person detected. Use a solo shot.");
            frame.getPixels(pixels,0,w,0,0,w,h);
            if(result.landmarks().isEmpty()){
                // Allow an empty lead-in/out, but never knowingly bake a lost actor back into the video.
                int changed=0,total=0;
                for(int i=0;i<pixels.length;i+=13){int p=pixels[i],q=plate[i];total++;
                    if(Math.abs((p>>16&255)-(q>>16&255))+Math.abs((p>>8&255)-(q>>8&255))+Math.abs((p&255)-(q&255))>90)changed++;}
                if(changed>total*.025)throw new IllegalArgumentException("Could not track the performer at "+ptsUs/1000+" ms. Use a clear, stationary shot.");
                return frame;
            }
            if(masks.size()!=1)throw new IllegalStateException("No person mask was returned.");
            List<NormalizedLandmark> lm=result.landmarks().get(0);
            int[] ids={0,0,0,12,14,16,11,13,15,24,26,28,23,25,27};float[] joints=new float[30];
            for(int k=0;k<ids.length;k++){
                NormalizedLandmark n=lm.get(ids[k]);if(!Float.isFinite(n.x())||!Float.isFinite(n.y())||n.visibility().orElse(1f)<.35f)
                    throw new IllegalArgumentException("Full body must be visible at "+ptsUs/1000+" ms.");
                joints[2*k]=n.x()*w;joints[2*k+1]=n.y()*h;
            }
            for(int i=0;i<2;i++){joints[2+i]=(joints[6+i]+joints[12+i])*.5f;joints[4+i]=(joints[18+i]+joints[24+i])*.5f;}
            MPImage mask=masks.get(0);int mw=mask.getWidth(),mh=mask.getHeight();
            FloatBuffer alpha=ByteBufferExtractor.extract(mask).order(ByteOrder.nativeOrder()).asFloatBuffer();
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){
                int i=y*w+x;float confidence=alpha.get((y*mh/h)*mw+x*mw/w);
                if(confidence>.1f)pixels[i]=plate[i];
            }
            Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);out.setPixels(pixels,0,w,0,0,w,h);
            mesh.pose(joints);new Canvas(out).drawBitmapMesh(art,CharacterMesh.GRID,CharacterMesh.GRID,mesh.vertices,0,null,0,paint);
            replaced=true;return out;
        } finally{for(MPImage m:masks)m.close();}
    }
    private void loadPlate(int w,int h)throws IOException{
        BitmapFactory.Options opt=new BitmapFactory.Options();opt.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(plateUri)){BitmapFactory.decodeStream(in,null,opt);}
        if(opt.outWidth<=0||opt.outHeight<=0||Math.abs((double)opt.outWidth*h/(opt.outHeight*(double)w)-1)>.02)
            throw new IllegalArgumentException("The background must have the same orientation and aspect ratio as the video.");
        opt.inJustDecodeBounds=false;opt.inSampleSize=1;
        while(opt.outWidth/opt.inSampleSize>2*w)opt.inSampleSize*=2;
        Bitmap b;try(InputStream in=context.getContentResolver().openInputStream(plateUri)){b=BitmapFactory.decodeStream(in,null,opt);}
        if(b==null)throw new IOException("Unreadable background.");
        Bitmap sized=Bitmap.createScaledBitmap(b,w,h,true);plate=new int[w*h];pixels=new int[w*h];
        sized.getPixels(plate,0,w,0,0,w,h);if(b!=sized)b.recycle();sized.recycle();
    }
    public void requireReplacement(){if(!replaced)throw new IllegalArgumentException("No full-body performer was found in the video.");}
    @Override public void close(){detector.close();art.recycle();}
}

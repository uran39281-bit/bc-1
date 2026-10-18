package com.blackice.bc1.editor;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** Draws text, shape and image overlays with Android Canvas. Shared by the Media3 graph and ICE Lite,
 * so both engines produce the same titles and graphics. Images are decoded once and cached. */
public final class OverlayPainter {
    private final Context context;
    private final Map<String,Bitmap> images=new HashMap<>();
    public OverlayPainter(Context context){this.context=context.getApplicationContext();}
    /** Draws one text, image or shape layer through the same transform as video layers (y-down pixels). */
    public void draw(Canvas canvas,int width,int height,EditProject.Overlay o,long local) throws IOException {
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        TransformMath.Transform t=TransformMath.of(o,local);float opacity=o.motion.value(Motion.OPACITY,local,o.opacity);
        double[] m=TransformMath.matrix(t);float ax=t.anchorX*width/2f,ay=-t.anchorY*height/2f,cx=(t.x+1)*width/2f,cy=(1-t.y)*height/2f;
        float a=(float)m[0],b=(float)-m[1],c=(float)-m[2],d=(float)m[3];
        Matrix matrix=new Matrix();matrix.setValues(new float[]{a,b,cx+ax-(a*ax+b*ay),c,d,cy+ay-(c*ax+d*ay),0,0,1});
        canvas.save();canvas.concat(matrix);
        paint.reset();paint.setFlags(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);paint.setAlpha(Math.round(opacity*255));
        if(o.blend>0) paint.setXfermode(new PorterDuffXfermode(new PorterDuff.Mode[]{PorterDuff.Mode.SRC_OVER,PorterDuff.Mode.MULTIPLY,PorterDuff.Mode.SCREEN,PorterDuff.Mode.OVERLAY,PorterDuff.Mode.ADD,PorterDuff.Mode.DARKEN,PorterDuff.Mode.LIGHTEN}[o.blend]));
        float unit=Math.min(width,height),stroke=o.borderWidth*unit*.05f;
        if(o.shadow>0) paint.setShadowLayer(o.shadow*unit*.04f,0,o.shadow*unit*.015f,Color.argb(Math.round(200*opacity),0,0,0));
        if(o.imageUri!=null) {
            Bitmap image=images.get(o.imageUri);
            if(image==null) try(java.io.InputStream in=context.getContentResolver().openInputStream(Uri.parse(o.imageUri))) {
                image=BitmapFactory.decodeStream(in); if(image==null) throw new java.io.IOException("Cannot decode overlay image"); images.put(o.imageUri,image);
            }
            float iw=width*.45f,ih=iw*image.getHeight()/image.getWidth();RectF r=new RectF(-iw/2,-ih/2,iw/2,ih/2);
            canvas.drawBitmap(image,null,r,paint);
            if(stroke>0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(o.borderColor);paint.setAlpha(Math.round(opacity*255));canvas.drawRect(r,paint);}
        } else if(o.shape>0) {
            float half=unit*.2f;paint.setColor(o.color);paint.setAlpha(Math.round(opacity*Color.alpha(o.color)));
            Path path=new Path();
            if(o.shape==1) path.addRect(-half,-half,half,half,Path.Direction.CW);
            else if(o.shape==2) path.addOval(-half,-half,half,half,Path.Direction.CW);
            else if(o.shape==3){path.moveTo(0,-half);path.lineTo(half,half);path.lineTo(-half,half);path.close();}
            else path.addRect(-half,-Math.max(1.5f,stroke)/2-unit*.006f,half,Math.max(1.5f,stroke)/2+unit*.006f,Path.Direction.CW);
            canvas.drawPath(path,paint);
            if(stroke>0&&o.shape!=4){paint.clearShadowLayer();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(o.borderColor);paint.setAlpha(Math.round(opacity*255));canvas.drawPath(path,paint);}
        } else {
            paint.setColor(o.color); paint.setAlpha(Math.round(opacity*255)); paint.setTypeface(Typeface.create(o.font,Typeface.BOLD));
            paint.setTextAlign(new Paint.Align[]{Paint.Align.LEFT,Paint.Align.CENTER,Paint.Align.RIGHT}[o.alignment]);
            // Size and the 90%-width fit are computed at the displayed scale, then drawn through the matrix.
            float scale=Math.max(.05f,t.scale),size=unit*.065f*scale;
            if(o.shadow<=0) paint.setShadowLayer(3/scale,0,2/scale,Color.BLACK);
            String[] lines=o.text.split("\n",-1);
            for(int line=0;line<lines.length;line++) {
                String text=lines[line]; paint.setTextSize(size); float measure=paint.measureText(text);
                float fitted=measure>width*.9f?size*width*.9f/measure:size;paint.setTextSize(fitted/scale);
                float y=(line-(lines.length-1)/2f)*size*1.25f/scale;
                if(stroke>0){Paint outline=new Paint(paint);outline.clearShadowLayer();outline.setStyle(Paint.Style.STROKE);outline.setStrokeWidth(stroke/scale);outline.setColor(o.borderColor);outline.setAlpha(Math.round(opacity*255));canvas.drawText(text,0,y,outline);}
                canvas.drawText(text,0,y,paint);
            }
        }
        paint.reset();canvas.restore();
    }
    public void release(){for(Bitmap b:images.values())b.recycle();images.clear();}
}

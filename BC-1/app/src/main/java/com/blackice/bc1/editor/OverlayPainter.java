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
    private static final int TILE=512,SAMPLES=2,GUTTER=2;
    private Bitmap shapeTile;
    private final Paint downsample=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    /** Supersample vector edges in bounded, reusable tiles. Logical geometry and video resolution
     * stay unchanged. A tile (including filter gutters) uses at most 4.1 MiB, even at 70× scale/4K.
     * Blend modes retain their existing destination-dependent Canvas path. */
    public void draw(Canvas canvas,int width,int height,EditProject.Overlay o,long local) throws IOException {
        if(o.shape<=0||o.blend!=0||o.visualizerClipId!=null){drawDirect(canvas,width,height,o,local);return;}
        RectF bounds=new RectF();LayerGeometry.shapePath(o,width,height).computeBounds(bounds,true);
        Matrix matrix=LayerGeometry.canvasMatrix(TransformMath.of(o,local),width,height);matrix.mapRect(bounds);
        if(o.borderWidth>0||o.shadow>0)bounds.set(0,0,width,height);else bounds.inset(-2,-2);
        if(!bounds.intersect(0,0,width,height))return;
        int left=Math.max(0,(int)Math.floor(bounds.left)),top=Math.max(0,(int)Math.floor(bounds.top));
        int right=Math.min(width,(int)Math.ceil(bounds.right)),bottom=Math.min(height,(int)Math.ceil(bounds.bottom));
        int edge=TILE*SAMPLES+GUTTER*2;
        if(shapeTile==null)shapeTile=Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888);
        Canvas raster=new Canvas(shapeTile);Rect source=new Rect();RectF target=new RectF();
        for(int y=top;y<bottom;y+=TILE)for(int x=left;x<right;x+=TILE){
            int w=Math.min(TILE,right-x),h=Math.min(TILE,bottom-y);target.set(x,y,x+w,y+h);if(canvas.quickReject(target,Canvas.EdgeType.AA))continue;
            shapeTile.eraseColor(Color.TRANSPARENT);raster.save();raster.translate(GUTTER-x*SAMPLES,GUTTER-y*SAMPLES);raster.scale(SAMPLES,SAMPLES);
            drawDirect(raster,width,height,o,local);raster.restore();source.set(GUTTER,GUTTER,GUTTER+w*SAMPLES,GUTTER+h*SAMPLES);canvas.drawBitmap(shapeTile,source,target,downsample);
        }
    }
    private void drawDirect(Canvas canvas,int width,int height,EditProject.Overlay o,long local) throws IOException {
        if(o.visualizerClipId!=null)return; // Disabled while retaining old project layers.
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        TransformMath.Transform t=TransformMath.of(o,local);float opacity=o.motion.value(Motion.OPACITY,local,o.opacity);
        Matrix matrix=LayerGeometry.canvasMatrix(t,width,height);
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
            RectF r=LayerGeometry.imageRect(width,image.getWidth(),image.getHeight());
            canvas.drawBitmap(image,null,r,paint);
            if(stroke>0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(o.borderColor);paint.setAlpha(Math.round(opacity*255));canvas.drawRect(r,paint);}
        } else if(o.shape>0) {
            float half=unit*.2f;fill(paint,o,local,opacity,half,half);
            Path path=LayerGeometry.shapePath(o,width,height);
            canvas.drawPath(path,paint);
            if(stroke>0&&o.shape!=4){paint.clearShadowLayer();paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setColor(o.borderColor);paint.setAlpha(Math.round(opacity*255));canvas.drawPath(path,paint);}
        } else {
            fill(paint,o,local,opacity,width*.45f,unit*.1f); paint.setTypeface(Typeface.create(o.font,Typeface.BOLD));
            paint.setTextAlign(new Paint.Align[]{Paint.Align.LEFT,Paint.Align.CENTER,Paint.Align.RIGHT}[o.alignment]);
            // Size and the 90%-width fit are computed at the displayed scale, then drawn through the matrix.
            float scale=Math.max(.05f,t.scale),size=unit*.065f*scale;
            if(o.shadow<=0) paint.setShadowLayer(3/scale,0,2/scale,Color.BLACK);
            String[] lines=o.text.split("\n",-1);
            for(int line=0;line<lines.length;line++) {
                String text=lines[line]; paint.setTextSize(size); float measure=paint.measureText(text);
                float fitted=measure>width*.9f?size*width*.9f/measure:size;paint.setTextSize(fitted/scale);
                float y=(line-(lines.length-1)/2f)*size*1.25f/scale;
                if(stroke>0){Paint outline=new Paint(paint);outline.clearShadowLayer();outline.setShader(null);outline.setStyle(Paint.Style.STROKE);outline.setStrokeWidth(stroke/scale);outline.setColor(o.borderColor);outline.setAlpha(Math.round(opacity*255));canvas.drawText(text,0,y,outline);}
                canvas.drawText(text,0,y,paint);
            }
        }
        paint.reset();canvas.restore();
    }
    private static void fill(Paint paint,EditProject.Overlay o,long time,float opacity,float halfW,float halfH){
        FillStyle f=o.fill;if(f==null||!f.gradient){int color=f==null?o.color:(int)f.solid.at(time);paint.setColor(color);paint.setAlpha(Math.round(opacity*Color.alpha(color)));return;}
        java.util.List<FillStyle.Stop> stops=f.ordered(time);int[] colors=new int[stops.size()];float[] positions=new float[stops.size()];for(int i=0;i<stops.size();i++){colors[i]=(int)stops.get(i).color.at(time);positions[i]=(float)stops.get(i).position.at(time);}
        double angle=Math.toRadians(f.angle.at(time));float dx=(float)Math.cos(angle),dy=(float)Math.sin(angle),extent=Math.abs(dx)*halfW+Math.abs(dy)*halfH;
        paint.setColor(Color.WHITE);paint.setShader(new LinearGradient(-dx*extent,-dy*extent,dx*extent,dy*extent,colors,positions,Shader.TileMode.CLAMP));paint.setAlpha(Math.round(opacity*255));
    }
    public void release(){for(Bitmap b:images.values())b.recycle();images.clear();if(shapeTile!=null){shapeTile.recycle();shapeTile=null;}}
}

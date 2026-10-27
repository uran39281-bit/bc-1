package com.blackice.bc1.editor;

import android.graphics.*;

/** Authoritative overlay paths and local bounds in frame-height units (local y points down). */
public final class LayerGeometry {
    private LayerGeometry(){}
    public static Path shapePath(EditProject.Overlay o,int width,int height){float unit=Math.min(width,height),stroke=o.borderWidth*unit*.05f;return ShapePaths.create(o.shape,unit*.2f,Math.max(1.5f,stroke)/2+unit*.006f);}
    public static RectF imageRect(int width,int imageWidth,int imageHeight){float w=width*.45f,h=w*imageHeight/Math.max(1,imageWidth);return new RectF(-w/2,-h/2,w/2,h/2);}
    public static Matrix canvasMatrix(TransformMath.Transform t,int width,int height){double[] m=TransformMath.matrix(t);float ax=t.anchorX*width/2f,ay=-t.anchorY*height/2f,cx=(t.x+1)*width/2f,cy=(1-t.y)*height/2f;float a=(float)m[0],b=(float)-m[1],c=(float)-m[2],d=(float)m[3];Matrix out=new Matrix();out.setValues(new float[]{a,b,cx+ax-(a*ax+b*ay),c,d,cy+ay-(c*ax+d*ay),0,0,1});return out;}
    public static RectF shapeBounds(EditProject.Overlay o,int width,int height){RectF b=new RectF();shapePath(o,width,height).computeBounds(b,true);b.set(b.left/height,b.top/height,b.right/height,b.bottom/height);return b;}
    public static RectF fittedBounds(double frameAspect,int width,int height){double[] h=TransformMath.fittedHalf(frameAspect,width>0&&height>0?width/(double)height:frameAspect);return new RectF((float)-h[0],(float)-h[1],(float)h[0],(float)h[1]);}
    public static float[] corners(TransformMath.Transform t,double frameAspect,float viewW,float viewH,RectF b){float[] local={b.left,b.top,b.right,b.top,b.right,b.bottom,b.left,b.bottom};for(int i=0;i<4;i++){double[] point=TransformMath.forward(t,frameAspect,local[2*i],-local[2*i+1]);local[2*i]=(float)((.5+point[0]/frameAspect)*viewW);local[2*i+1]=(float)((.5-point[1])*viewH);}return local;}
}

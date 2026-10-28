package com.blackice.bc1.editor;

/** Frame a visual layer in canonical frame units. No preview pixels or UI state enter this math. */
public final class CanvasFraming {
    private CanvasFraming() {}
    public static TransformMath.Transform solve(TransformMath.Transform original,double aspect,
            double left,double bottom,double right,double top,boolean fill,float minimumScale) {
        double w=right-left,h=top-bottom;
        if(!(aspect>0&&w>0&&h>0)||!Double.isFinite(aspect+w+h))
            throw new IllegalArgumentException("Content bounds are not available yet.");
        double[] m=TransformMath.matrix(original);
        double det=m[0]*m[3]-m[1]*m[2];
        if(!Double.isFinite(det)||Math.abs(det)<1e-12)
            throw new IllegalArgumentException("This transform cannot be framed.");
        // Fill is containment in the layer's inverse basis, including skew and unequal axes.
        double factor=fill?Math.max((Math.abs(m[3]/det)*aspect+Math.abs(m[1]/det))/w,
                                  (Math.abs(m[2]/det)*aspect+Math.abs(m[0]/det))/h)
                          :Math.min(aspect/(Math.abs(m[0])*w+Math.abs(m[1])*h),
                                         1/(Math.abs(m[2])*w+Math.abs(m[3])*h));
        TransformMath.Transform result=original.copy();
        double width=original.scale*factor,height=original.scale*original.aspect*factor;
        if(!Double.isFinite(factor)||factor<=0||width<minimumScale||width>Motion.MAX_SCALE||
                height<.0025||height>Motion.MAX_HEIGHT)
            throw new IllegalArgumentException("Framing would exceed this layer's supported size.");
        result.scale=(float)width;
        m=TransformMath.matrix(result);
        double cx=(left+right)/2,cy=(bottom+top)/2,ax=original.anchorX*aspect/2,ay=original.anchorY/2;
        result.x=(float)((-ax-m[0]*(cx-ax)-m[1]*(cy-ay))*2/aspect);
        result.y=(float)((-ay-m[2]*(cx-ax)-m[3]*(cy-ay))*2);
        if(!Motion.allowed(Motion.X,result.x)||!Motion.allowed(Motion.Y,result.y))
            throw new IllegalArgumentException("Framing would exceed this layer's supported position.");
        return result;
    }
    public static boolean same(TransformMath.Transform a,TransformMath.Transform b) {
        return close(a.x,b.x)&&close(a.y,b.y)&&close(a.scale,b.scale)&&close(a.scale*a.aspect,b.scale*b.aspect);
    }
    private static boolean close(float a,float b){return Math.abs(a-b)<=1e-5f*Math.max(1,Math.max(Math.abs(a),Math.abs(b)));}
}

package com.blackice.bc1.editor;
import org.junit.Test;
import static org.junit.Assert.*;

public class CanvasFramingTest {
    @Test public void squareFitsAndFillsExactProjectDimensions(){
        TransformMath.Transform t=new TransformMath.Transform();double a=1920d/1080,w=1000d/1080;
        TransformMath.Transform fit=CanvasFraming.solve(t,a,-w/2,-w/2,w/2,w/2,false,.05f);
        TransformMath.Transform fill=CanvasFraming.solve(t,a,-w/2,-w/2,w/2,w/2,true,.05f);
        assertEquals(1080,1000*fit.scale,.001);assertEquals(1920,1000*fill.scale,.001);
        assertTrue(CanvasFraming.same(fit,CanvasFraming.solve(fit,a,-w/2,-w/2,w/2,w/2,false,.05f)));
    }
    @Test public void rotatedSkewedFlippedOffCenterBoundsHaveCorrectContainment(){
        double a=16d/9,l=-.3,r=.7,b=-.2,t=.4;
        for(float angle:new float[]{0,45,-45,90,405,123})for(boolean flip:new boolean[]{false,true})for(float skew:new float[]{0,17}){
            TransformMath.Transform original=new TransformMath.Transform();original.rotation=angle;original.flipH=flip;original.flipV=!flip;original.scale=1.7f;original.aspect=.6f;original.anchorX=.8f;original.anchorY=-.7f;original.skewX=skew;original.skewY=-skew/3;
            for(boolean fill:new boolean[]{false,true}){
                TransformMath.Transform result=CanvasFraming.solve(original,a,l,b,r,t,fill,.05f);
                assertEquals(angle,result.rotation,0);assertEquals(original.aspect,result.aspect,0);assertEquals(original.flipH,result.flipH);assertEquals(original.anchorX,result.anchorX,0);
                double[] center=TransformMath.forward(result,a,(l+r)/2,(b+t)/2);assertEquals(0,center[0],1e-6);assertEquals(0,center[1],1e-6);
                if(fill){for(double x:new double[]{-a/2,a/2})for(double y:new double[]{-.5,.5}){double[] p=TransformMath.inverse(result,a,x,y);assertTrue(p[0]>=l-1e-6&&p[0]<=r+1e-6&&p[1]>=b-1e-6&&p[1]<=t+1e-6);}}
                else{for(double x:new double[]{l,r})for(double y:new double[]{b,t}){double[] p=TransformMath.forward(result,a,x,y);assertTrue(Math.abs(p[0])<=a/2+1e-6&&Math.abs(p[1])<=.5+1e-6);}}
            }
        }
    }
    @Test public void invalidAndExcessiveGeometryIsRejectedWithoutMutation(){
        TransformMath.Transform t=new TransformMath.Transform();t.x=.25f;
        for(double size:new double[]{0,Double.NaN,1e-12})try{CanvasFraming.solve(t,16d/9,0,0,size,size,false,.05f);fail();}catch(IllegalArgumentException expected){}
        assertEquals(.25f,t.x,0);assertEquals(1,t.scale,0);
    }
}

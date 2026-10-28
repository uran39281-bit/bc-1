package com.blackice.bc1.editor;
import org.junit.Test;
import static org.junit.Assert.*;

public class EasingIntervalTest {
    @Test public void presetsHaveExactSamplesAndUnwrappedRotation(){
        float[][] expected={{25,50,75},{6.25f,25,56.25f},{43.75f,75,93.75f},{12.5f,50,87.5f}};
        for(int preset=0;preset<4;preset++){Motion m=new Motion();Motion.Key a=new Motion.Key(1000,0);Keyframes.ease(a,preset);m.put(Motion.ROTATION,a);m.put(Motion.ROTATION,new Motion.Key(5000,100));
            for(int i=0;i<3;i++)assertEquals(expected[preset][i],m.value(Motion.ROTATION,2000+i*1000,0),1e-6);
            assertEquals(0,m.value(Motion.ROTATION,1000,99),0);assertEquals(100,m.value(Motion.ROTATION,5000,99),0);
        }
        Motion m=new Motion();m.put(Motion.ROTATION,new Motion.Key(0,-90));m.put(Motion.ROTATION,new Motion.Key(1000,450));assertEquals(180,m.value(Motion.ROTATION,500,0),0);
    }
    @Test public void splitRetainsOriginalCurveAndStableKeyIdentity(){
        for(int easing=0;easing<4;easing++){Motion m=new Motion();Motion.Key first=new Motion.Key(0,0);Keyframes.ease(first,easing);m.put(Motion.X,first);m.put(Motion.X,new Motion.Key(4000,1));Motion split=m.copy();Keyframes.splitAt(split,1377);split.validate();
            assertEquals(2,split.curves.get(Motion.X).size());assertEquals(-1377,split.curves.get(Motion.X).get(0).timeMs);assertEquals(first.id,split.curves.get(Motion.X).get(0).id);
            for(long t=0;t<=2600;t+=13)assertEquals(m.value(Motion.X,t+1377,0),split.value(Motion.X,t,0),1e-7);
            Keyframes.ease(split.curves.get(Motion.X).get(0),(easing+1)%4);assertEquals(easing,Keyframes.easingOf(m.curves.get(Motion.X).get(0)));
        }
    }
    @Test public void newShapesAreStaticAndCopiesPreserveEasingThroughSerialization()throws Exception{
        EditProject p=new EditProject();EditProject.Overlay shape=new EditProject.Overlay();shape.shape=1;p.overlays.add(shape);assertFalse(shape.motion.animated());
        Motion.Key key=new Motion.Key(0,0);Keyframes.ease(key,3);shape.motion.put(Motion.X,key);shape.motion.put(Motion.X,new Motion.Key(1000,1));
        EditProject snapshot=p.copy();assertEquals(shape.id,snapshot.overlays.get(0).id);EditProject restored=ProjectStore.decode(ProjectStore.encode(snapshot));
        assertEquals(key.id,restored.overlays.get(0).motion.curves.get(Motion.X).get(0).id);assertEquals(.125f,restored.overlays.get(0).motion.value(Motion.X,250,0),0);
    }
    @Test public void legacyBezierDoesNotBecomeQuadratic(){Motion.Key k=new Motion.Key(0,0);k.easingToNext=-1;k.x1=.42f;k.y1=0;k.x2=1;k.y2=1;assertEquals(-1,Keyframes.easingOf(k));assertTrue(Math.abs(Motion.ease(.5f,k)-.25f)>.05f);}
}

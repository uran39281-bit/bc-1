package com.blackice.bc1.editor;
import org.junit.Test;
import org.json.*;
import static org.junit.Assert.*;

public class FillEasingTest {
    @Test public void scalarAndExistingColorInterpolationUseExactPresetProgress(){
        double[][] expected={{25,50,75},{6.25,25,56.25},{43.75,75,93.75},{12.5,50,87.5}};
        for(int preset=0;preset<4;preset++){
            FillStyle.Track angle=new FillStyle.Track(0,false),color=new FillStyle.Track(0xFFFF0000,true);
            angle.toggle(0);angle.set(4000,100);color.toggle(0);color.set(4000,0xFF0000FF);angle.ease(0,preset);color.ease(0,preset);
            for(int i=0;i<3;i++){assertEquals(expected[preset][i],angle.at(1000L*(i+1)),1e-9);assertEquals(FillStyle.mix(0xFFFF0000,0xFF0000FF,expected[preset][i]/100),(int)color.at(1000L*(i+1)));}
            assertEquals(0,angle.at(0),0);assertEquals(100,angle.at(4000),0);
        }
    }
    @Test public void splitRecoveryRoundTripAndCopiesRetainOriginalSamplingAndIds()throws Exception{
        for(int preset=0;preset<4;preset++){
            FillStyle fill=new FillStyle(0xFF123456);fill.angle.toggle(0);fill.angle.set(4000,405);fill.angle.ease(0,preset);fill.solid.toggle(0);fill.solid.set(4000,0x80ABCDEF);fill.solid.ease(0,preset);
            String id=fill.angle.interval(0).id;FillStyle split=fill.copy();split.split(1377);split=FillStyle.read(split.json());assertEquals(id,split.angle.interval(-1377).id);
            for(long t=0;t<2600;t+=13){assertEquals(fill.angle.at(t+1377),split.angle.at(t),1e-9);assertEquals(fill.solid.at(t+1377),split.solid.at(t),0);}
            split.angle.ease(-1377,(preset+1)%4);assertEquals(preset,fill.angle.interval(0).easing);
        }
    }
    @Test public void legacyFillKeysStayLinearAndStaticPropertiesStayStatic()throws Exception{
        FillStyle.Track track=new FillStyle.Track(0,false);track.read(new JSONObject("{\"base\":0,\"keys\":[[0,0],[4000,100]]}"));assertEquals(25,track.at(1000),0);assertEquals(0,track.interval(0).easing);
        FillStyle.Track staticTrack=new FillStyle.Track(0,false);try{staticTrack.ease(0,1);fail();}catch(IllegalArgumentException expected){}assertTrue(staticTrack.keys.isEmpty());
        try{track.ease(4000,2);fail();}catch(IllegalArgumentException expected){}assertEquals(0,track.interval(0).easing);
    }
}

package com.blackice.bc1.character;

import android.content.Context;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.engine.bc.ReverseTranscoder;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/** Exercises source removal, sprite drawing, forward video encoding and original AAC packet timing on-device.
 * Fixed landmarks/mask isolate the renderer from the ARM-only native detector. */
public class CharacterRenderTest {
    @Test public void bakedReplacementRemovesMaskedPixelsAndKeepsAudio() throws Exception{
        var inst=InstrumentationRegistry.getInstrumentation();Context c=inst.getTargetContext();
        File src=File.createTempFile("swap-source",".mp4",c.getCacheDir()),out=File.createTempFile("swap-result",".mp4",c.getCacheDir());
        Bitmap art=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
        new Canvas(art).drawColor(Color.GREEN);
        try{
            try(InputStream in=inst.getContext().getAssets().open("numbered.mp4");OutputStream os=new FileOutputStream(src)){in.transferTo(os);}
            CharacterMesh mesh=new CharacterMesh(64,64);
            float[] pose=CharacterMesh.REST.clone();
            for(int i=0;i<pose.length;i++)pose[i]=pose[i]*64+(i%2==0?128:58);
            // First quarter belongs to the original person. The new pose is in a different region.
            FloatBuffer mask=FloatBuffer.wrap(new float[]{1,0,0,0});
            ReverseTranscoder.replacePerson(c,Uri.fromFile(src).toString(),2000,out,(frame,pts)->{
                int w=frame.getWidth(),h=frame.getHeight();int[] pix=new int[w*h],plate=new int[w*h];
                frame.getPixels(pix,0,w,0,0,w,h);Arrays.fill(plate,Color.BLUE);
                return PersonCompositor.render(w,h,pix,plate,mask,4,1,art,mesh,pose);
            },()->false,p->{});
            assertEquals(packets(src),packets(out));
            MediaMetadataRetriever r=new MediaMetadataRetriever();
            try{
                r.setDataSource(out.getPath());Bitmap b=r.getFrameAtTime(1_000_000,MediaMetadataRetriever.OPTION_CLOSEST);
                assertNotNull(b);assertTrue(Color.blue(b.getPixel(32,90))>210);
                assertTrue(Color.green(b.getPixel(160,90))>210);
                int bg=b.getPixel(300,90);assertTrue(Math.abs(Color.red(bg)-Color.blue(bg))<20);
                b.recycle();
            }finally{r.release();}
        }finally{art.recycle();src.delete();out.delete();}
    }
    private static List<String> packets(File f)throws Exception{
        MediaExtractor e=new MediaExtractor();List<String> result=new ArrayList<>();
        try{e.setDataSource(f.getPath());for(int i=0;i<e.getTrackCount();i++)if("audio/mp4a-latm".equals(e.getTrackFormat(i).getString(MediaFormat.KEY_MIME))){e.selectTrack(i);break;}
            ByteBuffer buf=ByteBuffer.allocate(65536);
            while(e.getSampleTime()>=0){buf.clear();int n=e.readSampleData(buf,0);byte[] data=new byte[n];buf.position(0);buf.get(data);
                result.add(e.getSampleTime()+":"+Arrays.hashCode(data));if(!e.advance())break;}
            assertFalse(result.isEmpty());return result;
        }finally{e.release();}
    }
}

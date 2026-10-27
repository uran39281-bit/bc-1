package com.blackice.bc1.engine.bc;
import android.media.MediaFormat;
/** Decoded display dimensions. Codec crop edges are inclusive; SurfaceTexture handles UV cropping. */
public final class VideoGeometry {
 private VideoGeometry(){}
 public static int[] displaySize(MediaFormat format,int rotation){int width=format.getInteger(MediaFormat.KEY_WIDTH),height=format.getInteger(MediaFormat.KEY_HEIGHT);
  if(format.containsKey("crop-left")&&format.containsKey("crop-right")){int cropped=format.getInteger("crop-right")-format.getInteger("crop-left")+1;if(cropped>0&&cropped<=width)width=cropped;}
  if(format.containsKey("crop-top")&&format.containsKey("crop-bottom")){int cropped=format.getInteger("crop-bottom")-format.getInteger("crop-top")+1;if(cropped>0&&cropped<=height)height=cropped;}
  return Math.floorMod(rotation,180)==90?new int[]{height,width}:new int[]{width,height};
 }
}

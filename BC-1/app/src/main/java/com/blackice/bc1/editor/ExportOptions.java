package com.blackice.bc1.editor;
import android.content.SharedPreferences;
import com.blackice.bc1.core.*;
import com.blackice.bc1.engine.bc.EncoderCaps;
/** Encoder options shared by the screen and service; never mutate the editable project. */
public final class ExportOptions {
 public static ExportPlanner.Plan plan(EditProject project,SharedPreferences prefs){
  int fps=prefs.getInt("export-fps",project.frameRate);if(!ExportPlanner.validFrameRate(fps))fps=project.frameRate;
  ExportPlanner.Plan p=EncoderCaps.plan(prefs.getInt(ExportPlanner.PREF_RESOLUTION,1080),fps,project.portrait,VideoCodec.Choice.parse(prefs.getString(VideoCodec.Choice.PREF_KEY,null)));
  int quality=prefs.getInt("export-quality",2);double[] factors={.5,.75,1,1.5};
  int bitrate=quality==4?prefs.getInt("export-bitrate",p.bitrate):(int)Math.min(150000000,Math.max(500000,p.bitrate*factors[Math.max(0,Math.min(3,quality))]));
  return p.withBitrate(bitrate);
 }
}

package com.blackice.bc1.core;
import com.blackice.bc1.editor.EditProject;
import java.util.List;
public final class CaptionTiming {
 private CaptionTiming(){}
 public static void shift(EditProject project,long delta){
  List<EditProject.Overlay> cues=SubtitleFiles.captions(project);if(cues.isEmpty())throw new IllegalArgumentException("Generate captions first.");
  long limit=project.durationMs();for(EditProject.Overlay c:cues){long s=Math.addExact(c.startMs,delta),e=Math.addExact(c.endMs,delta);if(s<0||e>limit)throw new IllegalArgumentException("The shift would move captions outside the project.");}
  for(EditProject.Overlay c:cues){c.startMs+=delta;c.endMs+=delta;}
 }
}

package com.blackice.bc1;
import com.blackice.bc1.editor.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class TraceValuesTest {
 @Test public void traceIncludesStackControlsAndCurvesButNoMediaOrTypedText(){
  EditProject p=new EditProject();EditProject.Clip c=new EditProject.Clip();c.uri="PRIVATE_PATH";c.name="PRIVATE_NAME";p.clips.add(c);String first=c.addEffect(48),second=c.addEffect(49);EffectInstance e=c.effect(second).copy(false);e.params=new float[]{.2f,.3f};e.motion.put(Motion.INTENSITY,new Motion.Key(500,.7f));c.updateEffect(e);
  Map<String,Object> values=ActionTrace.values(p);String text=values.toString();assertFalse(text.contains("PRIVATE"));assertTrue(text.contains(first));assertTrue(text.contains(second));assertTrue(text.contains("[0.2, 0.3]"));assertTrue(text.contains("timeMs=500"));
 }
}

package com.blackice.bc1;
import android.graphics.*;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class TransformControlsTest {
 @Test public void controlsUseLiveTransactionsAndKeepIndependentKeys()throws Exception{
  WorkspaceV2Test h=new WorkspaceV2Test();String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
  try{h.shell("wm size 1080x2340");h.shell("wm density 440");
   File f=new File(h.inst.getTargetContext().getFilesDir(),"transform-fixture.png");Bitmap b=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888);b.eraseColor(Color.CYAN);try(FileOutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
   EditProject p=new EditProject();EditProject.Clip c=new MediaImporter(h.inst.getTargetContext()).importClip(android.net.Uri.fromFile(f),false);c.outMs=3000;p.clips.add(c);new ProjectStore(h.inst.getTargetContext()).save(p);
   h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","PHONE").putBoolean("ui-locked",false).commit();
   try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);scene.onActivity(a->a.selectClip(0,false));Thread.sleep(500);
    scene.onActivity(a->{java.util.List<View> tiles=new java.util.ArrayList<>();collect(a.inspector,MediaTile.class,tiles);assertEquals(4,tiles.size());for(View tile:tiles){assertEquals(tiles.get(0).getWidth(),tile.getWidth());assertEquals(tiles.get(0).getHeight(),tile.getHeight());}assertTrue(tiles.get(0).getHeight()>=a.dp(48));});h.capture("v141-phone-media");
    scene.onActivity(a->{a.panels.category("Move & Transform");assertEquals(3,TransformPanel.TABS.length);assertNull(WorkspaceV2Test.text(a.inspector,"Skew"));a.transformPanel.tab=1;a.renderInspector();a.clip().rotation=-360;a.transformPanel.increment(45);assertEquals(-405,a.clip().rotation,.001);a.transformPanel.increment(-90);assertEquals(-315,a.clip().rotation,.001);a.undo(false);assertEquals(-405,a.clip().rotation,.001);a.undo(true);assertEquals(-315,a.clip().rotation,.001);
      a.clip().rotation=0;a.renderInspector();View dial=find(a.inspector,TransformPanel.Dial.class);dial.layout(0,0,300,300);int undo=a.undo.size();long t=android.os.SystemClock.uptimeMillis();send(dial,t,t,0,150,20);for(int i=1;i<=32;i++){double r=Math.toRadians(i*45);send(dial,t,t+i*10,2,150+(float)Math.sin(r)*130,150-(float)Math.cos(r)*130);}assertEquals(-1440,a.clip().rotation,.1);send(dial,t,t+400,1,150,20);assertEquals(undo+1,a.undo.size());a.undo(false);assertEquals(0,a.clip().rotation,.001);
      a.transformPanel.tab=0;a.renderInspector();View ruler=find(a.inspector,TransformPanel.ValueRuler.class);ruler.layout(0,0,300,56);t+=500;send(ruler,t,t,0,100,20);send(ruler,t,t+40,2,160,20);assertTrue("Rightward strip drag reveals lower values",a.clip().x<0);send(ruler,t,t+50,2,100,20);assertEquals("Equal opposite drags return exactly",0,a.clip().x,1e-6);send(ruler,t,t+60,2,160,20);a.seek(2000);assertEquals(0,a.project.playheadMs);send(ruler,t,t+80,3,160,20);assertEquals(0,a.clip().x,0);
      a.clip().motion.put(Motion.X,new Motion.Key(0,.2f));a.clip().motion.put(Motion.X,new Motion.Key(1000,.6f));a.clip().rotation=-720;a.transformPanel.resetAll();assertEquals(2,a.clip().motion.curves.get(Motion.X).size());assertEquals(0,a.clip().motion.value(Motion.X,0,0),0);assertEquals(-720,a.clip().rotation,0);
      a.transformPanel.tab=2;a.transformPanel.linked=true;a.renderInspector();a.transformPanel.commit("Scale fixture",()->a.transformPanel.writeDimensions(2,1,false));a.transformPanel.commit("Linked edit",()->a.transformPanel.writeDimensions(3,1.5f,true));assertEquals(3,a.transformPanel.current().scale,0);assertEquals(1.5f,a.transformPanel.current().scale*a.transformPanel.current().aspect,1e-6);a.undo(false);assertEquals(2,a.transformPanel.current().scale,0);
      a.clip().motion.put(Motion.HEIGHT,new Motion.Key(1000,1));a.transformPanel.commit("Paired animation",()->a.transformPanel.writeDimensions(3,1.5f,true));assertNotNull(Keyframes.keyAt(a.clip().motion,Motion.SCALE,0,0));assertNotNull(Keyframes.keyAt(a.clip().motion,Motion.HEIGHT,0,0));assertNotNull(Keyframes.keyAt(a.clip().motion,Motion.HEIGHT,1000,0));
    });Thread.sleep(300);h.capture("v141-phone-scale");scene.onActivity(a->{a.transformPanel.tab=1;a.renderInspector();});Thread.sleep(200);h.capture("v141-phone-rotation");
    h.shell("wm size 1920x1080");h.shell("wm density 160");h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","WIDE").commit();scene.recreate();h.waitLoaded(scene);scene.onActivity(a->{a.selectClip(0,false);a.panels.reset();a.renderInspector();});Thread.sleep(500);h.capture("v141-wide-media");scene.onActivity(a->{a.transformPanel.tab=0;a.panels.category("Move & Transform");});Thread.sleep(200);h.capture("v141-wide-position");
   }
  }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
 }
 private static void collect(View v,Class<?> type,java.util.List<View> out){if(type.isInstance(v))out.add(v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)collect(g.getChildAt(i),type,out);}}
 private static View find(View v,Class<?> type){java.util.List<View> out=new java.util.ArrayList<>();collect(v,type,out);return out.isEmpty()?null:out.get(0);}
 private static void send(View v,long down,long time,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,time,action,x,y,0);try{v.dispatchTouchEvent(e);}finally{e.recycle();}}
}

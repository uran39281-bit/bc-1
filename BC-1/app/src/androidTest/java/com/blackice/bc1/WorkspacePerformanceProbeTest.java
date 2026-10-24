package com.blackice.bc1;
import android.app.Instrumentation;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
/** Same test is copied into the baseline checkout for a same-emulator comparison. */
@RunWith(AndroidJUnit4.class)
public class WorkspacePerformanceProbeTest {
    final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    String shell(String command)throws Exception{try(ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(in.readAllBytes());}}
    String override(String value){for(String line:value.split("\n"))if(line.startsWith("Override"))return line.substring(line.indexOf(':')+1).trim();return "reset";}
    View described(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)v).getChildCount();i++){View found=described(((android.view.ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
    @Test public void measureSearchBurstAndScrolling()throws Exception{
        String size=override(shell("wm size")),density=override(shell("wm density"));HandlerThread thread=new HandlerThread("perf-probe");thread.start();List<Long> frames=Collections.synchronizedList(new ArrayList<>());
        try{for(boolean portrait:new boolean[]{false,true}){frames.clear();shell("wm size "+(portrait?"1080x1920":"1920x1080"));shell("wm density "+(portrait?"320":"160"));android.content.Context context=inst.getTargetContext();EditProject p=new EditProject();p.name="Performance probe";
            File image=new File(context.getFilesDir(),"performance-probe.png");Bitmap bitmap=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.BLUE);try(OutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            for(int i=0;i<100;i++){EditProject.Clip c=new EditProject.Clip();c.name="Asset "+i;File asset=new File(context.getFilesDir(),"performance-probe-"+i+".png");java.nio.file.Files.copy(image.toPath(),asset.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);c.uri=Uri.fromFile(asset).toString();c.image=true;c.sourceDurationMs=c.outMs=1000;p.library.add(c);}
            new ProjectStore(context).save(p);context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").putBoolean("ui-locked",false).commit();
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                boolean[] loaded={false};long deadline=SystemClock.elapsedRealtime()+15000;while(!loaded[0]&&SystemClock.elapsedRealtime()<deadline){scenario.onActivity(a->loaded[0]=!a.loading);Thread.sleep(50);}assertTrue(loaded[0]);Thread.sleep(1500);
                scenario.onActivity(a->a.getWindow().addOnFrameMetricsAvailableListener((window,metrics,dropped)->frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)),new Handler(thread.getLooper())));
                if(portrait){scenario.onActivity(a->{a.tab=4;a.renderInspector();});inst.waitForIdleSync();}
                long start=SystemClock.elapsedRealtime();if(!portrait)scenario.onActivity(a->{EditText search=(EditText)described(a.getWindow().getDecorView(),"Search project media");assertNotNull(search);for(int i=0;i<24;i++)search.setText(i%2==0?"Asset 1":"Asset");});inst.waitForIdleSync();Thread.sleep(200);long searchMs=portrait?-1:SystemClock.elapsedRealtime()-start;
                frames.clear();for(int i=0;i<60;i++){final int y=(i%30)*45;scenario.onActivity(a->{android.view.ViewParent parent=(portrait?a.inspector:a.mediaBrowser).getParent();while(parent!=null&&!(parent instanceof ScrollView))parent=parent.getParent();if(parent instanceof ScrollView)((ScrollView)parent).scrollTo(0,y);});Thread.sleep(16);}Thread.sleep(200);
                List<Long> values;synchronized(frames){values=new ArrayList<>(frames);}Collections.sort(values);long slow=values.stream().filter(ns->ns>16666667).count();double p95=values.isEmpty()?0:values.get((int)Math.min(values.size()-1,Math.floor(values.size()*.95)))/1000000.0;
                String phase=InstrumentationRegistry.getArguments().getString("phase","current");String result="PERFORMANCE phase="+phase+" layout="+(portrait?"portrait-settings":"landscape-project-media")+" searchBurstMs="+searchMs+" frames="+values.size()+" over16ms="+slow+" p95ms="+p95;System.out.println(result);android.util.Log.i("BC1Performance",result);
                assertFalse("Measured actual frames",values.isEmpty());
            }
        }}finally{shell("wm size "+size);shell("wm density "+density);thread.quitSafely();}
    }
}

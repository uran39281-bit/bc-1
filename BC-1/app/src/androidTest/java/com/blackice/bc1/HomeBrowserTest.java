package com.blackice.bc1;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class HomeBrowserTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private final Context context=inst.getTargetContext();
    private final ProjectStore store=new ProjectStore(context);
    private final java.util.List<String> ids=new ArrayList<>();
    private EditProject fixture(String name)throws Exception{
        EditProject p=new EditProject();p.name=name;ids.add(p.id);
        File picture=new File(context.getFilesDir(),"home-fixture.png");
        Bitmap bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);
        Paint paint=new Paint();paint.setShader(new LinearGradient(0,0,320,180,new int[]{0xff053360,0xff29bfff,0xff121e42},null,Shader.TileMode.CLAMP));c.drawRect(0,0,320,180,paint);
        paint.setShader(null);paint.setColor(0xffc4f2ff);Path mountain=new Path();mountain.moveTo(0,180);mountain.lineTo(90,45);mountain.lineTo(160,130);mountain.lineTo(230,25);mountain.lineTo(320,180);c.drawPath(mountain,paint);
        try(OutputStream out=new FileOutputStream(picture)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
        EditProject.Clip clip=new EditProject.Clip();clip.name="Local image";clip.uri=Uri.fromFile(picture).toString();clip.image=true;clip.sourceDurationMs=clip.outMs=24000;p.clips.add(clip);store.save(p);return p;
    }
    @After public void clean()throws Exception{for(String id:ids)try{store.delete(id);}catch(Exception ignored){} }
    @Test public void duplicateRenameDeletePreserveEditsAndSource()throws Exception{
        EditProject original=fixture("Preserved source");original.clips.get(0).rotation=405;original.portrait=true;original.previewHeight=360;store.save(original);
        EditProject copy=store.duplicate(original.id);ids.add(copy.id);assertNotEquals(original.id,copy.id);assertEquals(original.clips.get(0).uri,copy.clips.get(0).uri);assertEquals(405,copy.clips.get(0).rotation,0);assertTrue(copy.portrait);
        store.rename(copy.id,"Renamed copy");assertEquals("Renamed copy",store.load(copy.id).name);assertEquals("Preserved source",store.load(original.id).name);
        store.delete(copy.id);assertTrue(new File(Uri.parse(original.clips.get(0).uri).getPath()).exists());assertEquals(24000,store.load(original.id).durationMs());
        try{store.delete("../escape");fail();}catch(IOException expected){}
    }
    @Test public void invalidBundleDoesNotChangeProjectInventory()throws Exception{
        fixture("Import guard");int before=store.browser().size();
        try{ProjectBundle.read(context,new ByteArrayInputStream("invalid".getBytes(StandardCharsets.UTF_8)));fail();}catch(IOException expected){}
        assertEquals(before,store.browser().size());
    }
    @Test public void bundleRestoresIndependentProjectAndMedia()throws Exception{
        EditProject p=fixture("Portable");ByteArrayOutputStream bytes=new ByteArrayOutputStream();ProjectBundle.write(context,p,bytes);
        EditProject restored=ProjectBundle.read(context,new ByteArrayInputStream(bytes.toByteArray()));ids.add(restored.id);
        assertNotEquals(p.id,restored.id);assertNotEquals(p.clips.get(0).uri,restored.clips.get(0).uri);assertEquals(p.durationMs(),restored.durationMs());assertTrue(new File(Uri.parse(restored.clips.get(0).uri).getPath()).exists());
    }
    @Test public void damagedProjectsRemainVisible()throws Exception{
        String id=UUID.randomUUID().toString();ids.add(id);File f=new File(context.getFilesDir(),"projects-v1/"+id+".json");Files.write(f.toPath(),"broken".getBytes(StandardCharsets.UTF_8));
        ProjectStore.BrowserEntry found=null;for(ProjectStore.BrowserEntry e:store.browser())if(e.id.equals(id))found=e;
        assertNotNull(found);assertNull(found.project);assertNotNull(found.error);assertTrue(f.exists());
    }
    @Test public void menuSearchNavigationResizeAndScreenshots()throws Exception{
        fixture("Coastal film");fixture("Winter expedition with a very long project title that should wrap safely");EditProject portrait=fixture("Portrait story");portrait.portrait=true;portrait.frameRate=60;store.save(portrait);
        context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();
        shell("wm size 1080x1920");shell("wm density 420");
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            await(scenario,"Your projects");Thread.sleep(900);scenario.onActivity(a->{TextView label=text(a.getWindow().getDecorView(),"Import project");assertNotNull(label);assertTrue("Import label fits its card",label.getPaint().measureText(label.getText().toString())<=label.getWidth()-label.getPaddingLeft()-label.getPaddingRight()+1);});shell("settings put global sysui_demo_allowed 1");shell("am broadcast -a com.android.systemui.demo -e command enter");shell("am broadcast -a com.android.systemui.demo -e command notifications --ez visible false");capture("home-portrait");
            scenario.onActivity(a->{View search=described(a.getWindow().getDecorView(),"Search projects");assertNotNull(search);search.performClick();});
            scenario.onActivity(a->{EditText search=findEdit(a.getWindow().getDecorView());assertNotNull(search);search.setText("no such project xyz");});
            scenario.onActivity(a->assertNotNull(text(a.getWindow().getDecorView(),"No projects match")));
            scenario.recreate();scenario.onActivity(a->{EditText search=findEdit(a.getWindow().getDecorView());assertEquals("no such project xyz",search.getText().toString());search.setText("Coastal");});
            shell("wm size 1920x1080");shell("wm density 240");Thread.sleep(1200);
            scenario.onActivity(a->{assertNotNull(text(a.getWindow().getDecorView(),"Coastal film"));assertNull(text(a.getWindow().getDecorView(),"Portrait story"));EditText search=findEdit(a.getWindow().getDecorView());search.setText("");});
            scenario.onActivity(a->{described(a.getWindow().getDecorView(),"List view").performClick();assertNotNull(text(a.getWindow().getDecorView(),"Coastal film"));described(a.getWindow().getDecorView(),"Grid view").performClick();});Thread.sleep(500);capture("home-landscape");
            scenario.onActivity(a->described(a.getWindow().getDecorView(),"Projects").performClick());
            scenario.onActivity(a->assertNotNull(text(a.getWindow().getDecorView(),"Your projects")));
        }finally{shell("am broadcast -a com.android.systemui.demo -e command exit");shell("wm size reset");shell("wm density reset");}
    }
    @Test public void creationOpensEditorAndBackSavesBeforeReturningHome()throws Exception{
        context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();
        Instrumentation.ActivityMonitor monitor=inst.addMonitor(EditorActivity.class.getName(),null,false);
        try(ActivityScenario<HomeActivity> scenario=ActivityScenario.launch(HomeActivity.class)){
            await(scenario,"Your projects");
            android.accessibilityservice.AccessibilityServiceInfo service=inst.getUiAutomation().getServiceInfo();service.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;inst.getUiAutomation().setServiceInfo(service);
            scenario.onActivity(a->described(a.getWindow().getDecorView(),"New project").performClick());
            inst.getUiAutomation().waitForIdle(200,4000);
            android.view.accessibility.AccessibilityNodeInfo input=waitAccessible("Project name");assertNotNull("New-project text field is accessible",input);
            android.os.Bundle args=new android.os.Bundle();args.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"Created through menu");
            assertTrue(input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,args));
            android.view.accessibility.AccessibilityNodeInfo create=waitAccessible("Create project");assertNotNull(create);assertTrue(create.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK));
            Activity launched=inst.waitForMonitorWithTimeout(monitor,10000);assertTrue(launched instanceof EditorActivity);EditorActivity editor=(EditorActivity)launched;
            boolean[] loaded={false};for(int i=0;i<100;i++){inst.runOnMainSync(()->loaded[0]=!editor.loading);if(loaded[0])break;Thread.sleep(100);}assertTrue("Editor loaded created project",loaded[0]);
            String[] id={null};inst.runOnMainSync(()->{assertEquals("Created through menu",editor.project.name);assertEquals(30,editor.project.frameRate);assertFalse(editor.project.portrait);assertEquals(540,editor.project.previewHeight);id[0]=editor.project.id;editor.project.name="Saved before home";editor.saveAndProjects();});ids.add(id[0]);
            for(int i=0;i<100;i++){if(store.load(id[0]).name.equals("Saved before home"))break;Thread.sleep(100);}
            assertEquals("Saved before home",store.load(id[0]).name);
            for(int i=0;i<100;i++){boolean[] done={false};inst.runOnMainSync(()->done[0]=editor.isFinishing()||editor.isDestroyed());if(done[0])break;Thread.sleep(100);}
            await(scenario,"Saved before home");
        }finally{inst.removeMonitor(monitor);}
    }
    private android.view.accessibility.AccessibilityNodeInfo waitAccessible(String value)throws Exception{
        for(int i=0;i<50;i++){
            android.view.accessibility.AccessibilityNodeInfo found=accessible(inst.getUiAutomation().getRootInActiveWindow(),value);if(found!=null)return found;
            for(android.view.accessibility.AccessibilityWindowInfo window:inst.getUiAutomation().getWindows()){found=accessible(window.getRoot(),value);if(found!=null)return found;}
            Thread.sleep(100);
        }
        capture("create-dialog-diagnostic");return null;
    }
    private android.view.accessibility.AccessibilityNodeInfo accessible(android.view.accessibility.AccessibilityNodeInfo node,String value){
        if(node==null)return null;if(value.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;if(node.getText()!=null&&value.equalsIgnoreCase(node.getText().toString()))return node;
        for(int i=0;i<node.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo found=accessible(node.getChild(i),value);if(found!=null)return found;}return null;
    }
    private void await(ActivityScenario<HomeActivity> scenario,String value)throws Exception{for(int i=0;i<40;i++){boolean[] ok={false};scenario.onActivity(a->ok[0]=text(a.getWindow().getDecorView(),value)!=null);if(ok[0])return;Thread.sleep(100);}fail("Menu did not load");}
    private void capture(String name)throws Exception{Bitmap b=inst.getUiAutomation().takeScreenshot();File dir=new File(context.getFilesDir(),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void shell(String command)throws Exception{try(android.os.ParcelFileDescriptor p=inst.getUiAutomation().executeShellCommand(command);InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(p)){byte[] buffer=new byte[4096];while(in.read(buffer)!=-1){}}}
    private View described(View v,String target){if(target.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=described(((ViewGroup)v).getChildAt(i),target);if(found!=null)return found;}return null;}
    private TextView text(View v,String target){if(v instanceof TextView&&((TextView)v).getText().toString().contains(target))return (TextView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){TextView found=text(((ViewGroup)v).getChildAt(i),target);if(found!=null)return found;}return null;}
    private EditText findEdit(View v){if(v instanceof EditText)return (EditText)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){EditText found=findEdit(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
}

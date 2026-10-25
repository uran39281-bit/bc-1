package com.blackice.bc1;
import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

@RunWith(AndroidJUnit4.class)
public class EditorWorkspaceTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private TextView text(View view,String value){if(view instanceof TextView&&((TextView)view).getText().toString().equals(value))return (TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView found=text(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private View described(View view,String value){if(value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=described(((ViewGroup)view).getChildAt(i),value);if(found!=null)return found;}return null;}
    private SurfaceView surface(View view){if(view instanceof SurfaceView)return (SurfaceView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){SurfaceView found=surface(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private String shell(String command)throws Exception{try(ParcelFileDescriptor fd=inst.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    private String override(String value){for(String line:value.split("\n"))if(line.startsWith("Override"))return line.substring(line.indexOf(':')+1).trim();return "reset";}
    private void capture(String name)throws Exception{inst.waitForIdleSync();Thread.sleep(1000);Bitmap b=inst.getUiAutomation().takeScreenshot();assertNotNull(b);File dir=new File(inst.getTargetContext().getFilesDir(),"qa");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void assertVisiblePreview(ActivityScenario<EditorActivity> scenario)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+90000;
        while(SystemClock.elapsedRealtime()<deadline){
                // While paused the ICE Lite live preview (a TextureView over the player surface) shows the frame.
                java.util.concurrent.atomic.AtomicInteger live=new java.util.concurrent.atomic.AtomicInteger(-1);
                scenario.onActivity(a->{if(a.liveView!=null&&a.liveView.getVisibility()==View.VISIBLE&&a.liveView.isAvailable()){Bitmap b=a.liveView.getBitmap();if(b!=null){live.set(b.getPixel(b.getWidth()/4,b.getHeight()/2));b.recycle();}}});
                if(live.get()!=-1&&android.graphics.Color.blue(live.get())>30)return;
                java.util.concurrent.atomic.AtomicReference<SurfaceView> preview=new java.util.concurrent.atomic.AtomicReference<>();
                scenario.onActivity(a->preview.set(surface(a.getWindow().getDecorView())));
                Bitmap pixels=Bitmap.createBitmap(preview.get().getWidth(),preview.get().getHeight(),Bitmap.Config.ARGB_8888);
                java.util.concurrent.CountDownLatch copied=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger result=new java.util.concurrent.atomic.AtomicInteger(-1);
                inst.runOnMainSync(()->{try{PixelCopy.request(preview.get(),pixels,status->{result.set(status);copied.countDown();},new Handler(Looper.getMainLooper()));}catch(IllegalArgumentException e){copied.countDown();}});
                assertTrue(copied.await(10,java.util.concurrent.TimeUnit.SECONDS));
                int color=pixels.getPixel(pixels.getWidth()/4,pixels.getHeight()/2);pixels.recycle();
                if(result.get()==PixelCopy.SUCCESS&&android.graphics.Color.blue(color)>30)return;
                Thread.sleep(250);
        }
        fail("Preview must render real image pixels without pressing Play");
    }
    @Test public void rapidSearchDuringDecodeUpdatesCurrentThumbnail()throws Exception{
        assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));
        String size=override(shell("wm size")),density=override(shell("wm density"));
        java.util.concurrent.CountDownLatch decodeAllowed=new java.util.concurrent.CountDownLatch(1),workerBlocked=new java.util.concurrent.CountDownLatch(1);
        EditProject project=new EditProject();project.name="Thumbnail stress";
        File source=new File(inst.getTargetContext().getFilesDir(),"thumbnail-stress.png");
        Bitmap sourcePixels=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);sourcePixels.eraseColor(android.graphics.Color.rgb(24,120,210));
        try(OutputStream out=new FileOutputStream(source)){sourcePixels.compress(Bitmap.CompressFormat.PNG,100,out);}sourcePixels.recycle();
        File second=new File(inst.getTargetContext().getFilesDir(),"thumbnail-stress-b.png");java.nio.file.Files.copy(source.toPath(),second.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        for(String name:new String[]{"ICE / A","ICE / B"}){EditProject.Clip clip=new EditProject.Clip();clip.name=name;clip.uri=android.net.Uri.fromFile(name.endsWith("B")?second:source).toString();clip.image=true;clip.sourceDurationMs=2000;clip.outMs=2000;project.clips.add(clip);}
        new ProjectStore(inst.getTargetContext()).save(project);
        inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",project.id).putString("mode","WIDE").putBoolean("ui-locked",false).commit();
        java.util.concurrent.atomic.AtomicReference<ThumbnailCache> cache=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<View> currentCard=new java.util.concurrent.atomic.AtomicReference<>();
        try{
            shell("wm size 1920x1080");shell("wm density 160");
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                long deadline=SystemClock.elapsedRealtime()+15000;java.util.concurrent.atomic.AtomicBoolean loaded=new java.util.concurrent.atomic.AtomicBoolean();
                while(SystemClock.elapsedRealtime()<deadline&&!loaded.get()){scenario.onActivity(a->loaded.set(described(a.getWindow().getDecorView(),"Media ICE / B")!=null));if(!loaded.get())Thread.sleep(100);}
                assertTrue("Stress fixture loaded",loaded.get());
                scenario.onActivity(a->{try{
                    java.lang.reflect.Field field=EditorActivity.class.getDeclaredField("thumbnails");field.setAccessible(true);((ThumbnailCache)field.get(a)).close();
                    ThumbnailCache fresh=new ThumbnailCache(a);cache.set(fresh);field.set(a,fresh);
                    java.lang.reflect.Field executor=ThumbnailCache.class.getDeclaredField("worker");executor.setAccessible(true);
                    ((java.util.concurrent.ExecutorService)executor.get(fresh)).execute(()->{workerBlocked.countDown();try{decodeAllowed.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}});
                assertTrue("Decoder barrier entered",workerBlocked.await(5,java.util.concurrent.TimeUnit.SECONDS));
                scenario.onActivity(a->{try{
                    java.lang.reflect.Method render=EditorActivity.class.getDeclaredMethod("renderMediaBrowser");render.setAccessible(true);render.invoke(a);
                    android.widget.EditText search=(android.widget.EditText)described(a.getWindow().getDecorView(),"Search project media");
                    for(int i=0;i<32;i++)search.setText(i%2==0?"ICE":"ICE / B");
                }catch(ReflectiveOperationException e){throw new AssertionError(e);}});
                Thread.sleep(100);inst.waitForIdleSync();
                scenario.onActivity(a->{
                    ViewGroup card=(ViewGroup)described(a.getWindow().getDecorView(),"Media ICE / B");
                    currentCard.set(card);
                    assertFalse("Current card starts with a placeholder",((android.widget.ImageView)((ViewGroup)card.getChildAt(0)).getChildAt(0)).getDrawable() instanceof android.graphics.drawable.BitmapDrawable);
                });
                decodeAllowed.countDown();deadline=SystemClock.elapsedRealtime()+15000;
                while(cache.get().peek(project.clips.get(1))==null&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(100);
                assertNotNull("Decode actually completed",cache.get().peek(project.clips.get(1)));inst.waitForIdleSync();
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();ViewGroup card=(ViewGroup)described(root,"Media ICE / B");android.graphics.drawable.Drawable drawable=((android.widget.ImageView)((ViewGroup)card.getChildAt(0)).getChildAt(0)).getDrawable();
                    assertSame("Decode updates the existing card in place",currentCard.get(),card);
                    assertTrue("Latest media card receives decoded pixels after rapid search",drawable instanceof android.graphics.drawable.BitmapDrawable);
                    Bitmap pixels=((android.graphics.drawable.BitmapDrawable)drawable).getBitmap();assertEquals(android.graphics.Color.rgb(24,120,210),pixels.getPixel(pixels.getWidth()/2,pixels.getHeight()/2));assertNotNull(text(root,"1 / 6 items"));
                });capture("v103-thumbnails-loaded");
            }
        }finally{decodeAllowed.countDown();shell("wm size "+size);shell("wm density "+density);inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
    }
    @Test public void editorAdaptsAndTabsKeepVideoSurface()throws Exception{
        assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));String size=override(shell("wm size")),density=override(shell("wm density"));
        EditProject p=new EditProject();p.name="Ice • First Cut";
        File image=new File(inst.getTargetContext().getFilesDir(),"workspace-demo.png");Bitmap b=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(b);android.graphics.Paint paint=new android.graphics.Paint();paint.setShader(new android.graphics.LinearGradient(0,0,640,360,0xFF12364B,0xFF54BDE1,android.graphics.Shader.TileMode.CLAMP));c.drawPaint(paint);try(OutputStream out=new FileOutputStream(image)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();
        File detail=new File(inst.getTargetContext().getFilesDir(),"workspace-demo-detail.png");java.nio.file.Files.copy(image.toPath(),detail.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        for(int i=0;i<2;i++){EditProject.Clip clip=new EditProject.Clip();clip.name=i==0?"ICE / OPENING":"ICE / DETAIL";clip.uri=android.net.Uri.fromFile(i==0?image:detail).toString();clip.image=true;clip.sourceDurationMs=3000;clip.outMs=3000;clip.transition=0;p.clips.add(clip);}
        p.clips.get(1).track=1;p.clips.get(1).startMs=750;p.clips.get(1).scale=.6f;p.clips.get(1).x=.4f;p.clips.get(1).motion.put(3,new Motion.Key(0,0));p.clips.get(1).motion.put(3,new Motion.Key(3000,30));
        EditProject.Overlay title=new EditProject.Overlay();title.text="BLACK ICE";title.y=0;p.overlays.add(title);new ProjectStore(inst.getTargetContext()).save(p);
        inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","AUTO").putBoolean("ui-locked",false).commit();
        try{
            shell("wm size 1080x1920");shell("wm density 320");
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                java.util.concurrent.atomic.AtomicBoolean loaded=new java.util.concurrent.atomic.AtomicBoolean();long loadDeadline=SystemClock.elapsedRealtime()+15000;
                while(!loaded.get()&&SystemClock.elapsedRealtime()<loadDeadline){scenario.onActivity(a->loaded.set(!a.loading));Thread.sleep(50);}assertTrue("Saved project finishes loading",loaded.get());inst.waitForIdleSync();
                scenario.onActivity(a->assertEquals("The saved fixture is active",p.id,a.project.id));
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertNull("Clip tools stay hidden until selection",text(root,"Split"));assertNotNull("Settings is reachable",text(root,"Settings"));text(root,"Settings").performClick();assertNotNull("Unlocked settings are visible",text(root,"Lock editing and layout: OFF"));text(root,"Lock editing and layout: OFF").performClick();a.onKeyDown(KeyEvent.KEYCODE_S,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_S));text(root,"Lock editing and layout: ON").performClick();text(root,"Lock editing and layout: OFF").performClick();openMobileMedia(a);assertNotNull("Loaded project media is available",described(root,"Media ICE / OPENING"));described(root,"Media ICE / OPENING").performClick();
                    // A selected layer replaces Settings / Export in the top bar with its name, Duplicate, Delete and More.
                    assertNotNull("Settings remains accessible beside selected-layer actions",text(root,"Settings"));assertNotNull(described(root,"More actions"));assertNotNull(described(root,"Delete"));
                    a.onKeyDown(KeyEvent.KEYCODE_D,new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_D,0,KeyEvent.META_CTRL_ON));assertNotNull("Settings lock guards edits",text(root,"UI locked • unlock editing in Settings"));
                    a.onKeyDown(KeyEvent.KEYCODE_ESCAPE,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ESCAPE));assertNotNull("Deselecting restores the project bar",text(root,"Settings"));text(root,"Settings").performClick();text(root,"Lock editing and layout: ON").performClick();openMobileMedia(a);});
                assertVisiblePreview(scenario);
                scenario.onActivity(a->assertNotNull("First frame stays paused until Play",described(a.getWindow().getDecorView(),"Play")));
                capture("v10-paused-first-frame");
                scenario.onActivity(a->assertNotNull("Preparation must not advance the playhead",text(a.getWindow().getDecorView(),"00:00.00 / 00:03.75")));
                scenario.onActivity(a->a.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT,new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT,0,KeyEvent.META_SHIFT_ON)));
                Thread.sleep(500);assertVisiblePreview(scenario);
                scenario.onActivity(a->{assertNotNull("Paused seek updates the playhead",text(a.getWindow().getDecorView(),"00:00.33 / 00:03.75"));a.onKeyDown(KeyEvent.KEYCODE_DPAD_LEFT,new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,0,KeyEvent.META_SHIFT_ON));});
                java.util.concurrent.atomic.AtomicReference<EditorActivity> playbackActivity=new java.util.concurrent.atomic.AtomicReference<>();
                scenario.onActivity(playbackActivity::set);
                inst.runOnMainSync(()->playbackActivity.get().onKeyDown(KeyEvent.KEYCODE_SPACE,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_SPACE)));
                // A layered project plays from its rendered proxy, prepared in the background after loading;
                // Play joins that render if it is still running.
                {java.util.concurrent.atomic.AtomicBoolean playing=new java.util.concurrent.atomic.AtomicBoolean();long playDeadline=SystemClock.elapsedRealtime()+60000;
                while(!playing.get()&&SystemClock.elapsedRealtime()<playDeadline){inst.runOnMainSync(()->{EditorActivity a=playbackActivity.get();playing.set(a.player!=null&&a.player.isPlaying());});if(!playing.get())Thread.sleep(100);}
                java.util.concurrent.atomic.AtomicReference<String> state=new java.util.concurrent.atomic.AtomicReference<>();scenario.onActivity(a->state.set(a.previewDebug()));assertTrue("Play starts playback • "+state.get(),playing.get());}
                Thread.sleep(1500);
                scenario.onActivity(a->{
                    assertNotNull(a.player);
                    assertTrue("Playback advances the media clock",a.player.getCurrentPosition()>0);
                    a.player.pause();
                    // Wall-clock scheduling can pause after the first clip ends, where the sampled
                    // quarter-frame legitimately contains black. Seek to a known covered frame.
                    a.seek(1000);
                    // A paused seek hands the frame to the live preview (the player is prepared again on Play).
                    assertTrue("Seek leaves the preview paused",a.player==null||!a.player.getPlayWhenReady());assertEquals(1000,a.project.playheadMs);
                });
                assertVisiblePreview(scenario);
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertTrue("Project title remains accessible",a.projectLabel.getText().toString().startsWith("Ice • First Cut"));SurfaceView video=surface(root);assertNotNull(video);assertTrue(video.getWidth()>0&&video.getHeight()>0);View parent=(View)video.getParent();text(root,"Effects").performClick();assertSame(parent,video.getParent());assertNotNull(text(root,"Styles"));assertNotNull(text(root,"Transitions"));assertNotNull(text(root,"Animation"));});
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();for(int i=0;i<3;i++)a.onKeyDown(KeyEvent.KEYCODE_EQUALS,new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_EQUALS,0,KeyEvent.META_CTRL_ON));});
                Thread.sleep(500);
                scenario.onActivity(a->{android.widget.HorizontalScrollView scroll=(android.widget.HorizontalScrollView)described(a.getWindow().getDecorView(),"Timeline scroll");scroll.scrollTo(140,0);assertTrue("Timeline scroll is available after zoom",scroll.getScrollX()>0);});capture("v10-phone-9x16");
                shell("wm size 1920x1080");shell("wm density 160");Thread.sleep(1800);inst.waitForIdleSync();scenario.onActivity(a->{assertTrue(surface(a.getWindow().getDecorView()).getWidth()>500);});assertVisiblePreview(scenario);capture("v10-wide-16x9");
                java.util.concurrent.atomic.AtomicReference<View> filterSurface=new java.util.concurrent.atomic.AtomicReference<>();
                scenario.onActivity(a->{
                    View root=a.getWindow().getDecorView();View first=described(root,"Media ICE / OPENING"),second=described(root,"Media ICE / DETAIL");
                    assertNotNull(first);assertNotNull(second);int[] p1=new int[2],p2=new int[2];first.getLocationOnScreen(p1);second.getLocationOnScreen(p2);
                    assertEquals("V1 desktop cards share a row",p1[1],p2[1]);assertTrue("Two readable media columns",p2[0]>p1[0]&&first.getWidth()>100);
                    filterSurface.set((View)surface(root).getParent());
                    described(root,"Filter media: Video").performClick();assertNotNull(text(root,"No matching media"));assertNotNull(text(root,"0 / 6 items"));
                    described(root,"Filter media: Audio").performClick();assertNotNull(text(root,"No matching media"));
                    described(root,"Filter media: Image").performClick();assertNotNull(text(root,"2 / 6 items"));
                    ((android.widget.EditText)described(root,"Search project media")).setText(" detail ");
                });Thread.sleep(100);inst.waitForIdleSync();scenario.onActivity(a->{View root=a.getWindow().getDecorView();
                    assertNotNull(text(root,"1 / 6 items"));assertNull(described(root,"Media ICE / OPENING"));
                    described(root,"Media ICE / DETAIL").performClick();
                    assertEquals("Filtered index maps to the original clip","ICE / DETAIL",a.clip().name);assertNotNull(text(a.inspector,"Media Settings"));assertNotNull(text(a.inspector,"ICE / DETAIL"));
                    assertTrue(described(root,"Media ICE / DETAIL").isSelected());
                    assertSame("Filtering never recreates the preview surface",filterSurface.get(),surface(root).getParent());
                });
                scenario.recreate();Thread.sleep(2500);inst.waitForIdleSync();assertVisiblePreview(scenario);
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();assertEquals(" detail ",((android.widget.EditText)described(root,"Search project media")).getText().toString());assertTrue(described(root,"Filter media: Image").isSelected());assertNotNull(text(root,"1 / 6 items"));assertTrue(described(root,"Media ICE / DETAIL").isSelected());});capture("v102-media-filtered");
                scenario.onActivity(a->{View root=a.getWindow().getDecorView();((android.widget.EditText)described(root,"Search project media")).setText("");described(root,"Filter media: All").performClick();assertNotNull(text(root,"6 / 6 items"));described(root,"Media ICE / OPENING").performClick();assertTrue("Selecting media selects its clip and highlights the card",described(root,"Media ICE / OPENING").isSelected());assertFalse(described(root,"Media ICE / DETAIL").isSelected());assertTrue(text(root,"Effects").isEnabled());});
                inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","WIDE").commit();shell("wm size 780x420");scenario.recreate();Thread.sleep(2500);inst.waitForIdleSync();scenario.onActivity(a->{View deselect=described(a.getWindow().getDecorView(),"Deselect");if(deselect!=null)deselect.performClick();text(a.getWindow().getDecorView(),"Settings").performClick();assertNotNull("Wide preference must be restored",text(a.getWindow().getDecorView(),"Workspace layout: Wide tablet / DeX"));SurfaceView video=surface(a.getWindow().getDecorView());assertTrue("Compact wide preview must remain usable: "+video.getWidth()+"x"+video.getHeight(),video.getWidth()>150&&video.getHeight()>60);});assertVisiblePreview(scenario);capture("v10-compact-wide");
            }
        }finally{shell("wm size "+size);shell("wm density "+density);inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").putBoolean("ui-locked",false).commit();}
    }
    private void openMobileMedia(EditorActivity a){
        if(a.mobile.sheet.getVisibility()==View.VISIBLE)a.onKeyDown(KeyEvent.KEYCODE_BACK,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));
        described(a.getWindow().getDecorView(),"Add content").performClick();
        described(a.getWindow().getDecorView(),"Add Media").performClick();
    }
}

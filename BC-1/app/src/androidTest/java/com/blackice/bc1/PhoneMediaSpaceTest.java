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

/** Actual window measurements prevent the phone asset picker regressing to a tiny leftover strip. */
@RunWith(AndroidJUnit4.class)
public class PhoneMediaSpaceTest {
    @Test public void mediaBrowserHasSpaceAndReturnsToEditingWithoutProjectLoss() throws Exception {
        WorkspaceV2Test h=new WorkspaceV2Test();String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{for(boolean small:new boolean[]{false,true}){
            h.shell("wm size "+(small?"1080x1920":"1080x2340"));h.shell("wm density "+(small?"480":"420"));
            EditProject p=new EditProject();p.name="Phone media space";
            File picture=new File(h.inst.getTargetContext().getFilesDir(),"phone-space.png");Bitmap bitmap=Bitmap.createBitmap(160,90,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xFF2879A2);try(OutputStream out=new FileOutputStream(picture)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            EditProject.Clip asset=new EditProject.Clip();asset.name="Footage";asset.uri=android.net.Uri.fromFile(picture).toString();asset.image=true;asset.sourceDurationMs=asset.outMs=3500;p.library.add(asset);
            new ProjectStore(h.inst.getTargetContext()).save(p);
            h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",p.id).putString("mode","PHONE").putBoolean("ui-locked",false).putInt("workspace-focus",0).commit();
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                h.waitLoaded(scenario);scenario.onActivity(a->a.mobile.openAdd(0));Thread.sleep(300);
                scenario.onActivity(a->{assertFalse(a.wideLayout);assertEquals(0,a.tab);
                    assertTrue("Browser has at least 40% of window height",a.mobile.sheet.getHeight()>a.mobile.stage.getHeight()*.4f);
                    assertTrue("Timeline remains flexible below preview",a.phoneTimelineArea.getHeight()>a.dp(100));
                    assertEquals(View.VISIBLE,a.phoneTransport.getVisibility());assertEquals(View.VISIBLE,a.phoneTimelineTools.getVisibility());
                    assertNull(WorkspaceV2Test.text(a.inspector,"Inspector"));assertNotNull(WorkspaceV2Test.described(a.inspector,"Media Footage"));
                    assertEquals(0,a.project.clips.size());
                });
                h.capture(small?"v111-phone-small-picker":"v111-phone-picker");
                scenario.onActivity(a->{View surface=a.surface;a.mediaAction(a.project.library.get(0),"Add to main sequence at playhead");assertEquals(1,a.tab);assertEquals(1,a.project.clips.size());assertEquals(3500,a.project.durationMs());assertSame("Opening/closing drawer retains preview surface",surface,a.surface);assertEquals(View.VISIBLE,a.phoneTransport.getVisibility());
                    int balanced=a.phonePreview.getLayoutParams().height;a.workspaceFocus=2;a.sizePhoneDrawer();assertTrue(a.phonePreview.getLayoutParams().height>balanced);
                    a.workspaceFocus=1;a.sizePhoneDrawer();assertTrue(a.phonePreview.getLayoutParams().height<balanced);a.workspaceFocus=0;a.sizePhoneDrawer();
                    a.tab=0;a.renderInspector();a.onKeyDown(KeyEvent.KEYCODE_BACK,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK));assertEquals(1,a.tab);assertEquals(3500,a.project.durationMs());a.saveNow();});
                Thread.sleep(500);scenario.recreate();h.waitLoaded(scenario);scenario.onActivity(a->{assertEquals(1,a.project.clips.size());assertEquals(3500,a.project.durationMs());assertEquals(1,a.project.library.size());});
            }
        }}finally{h.shell("wm size "+size);h.shell("wm density "+density);h.inst.getTargetContext().getSharedPreferences(EditorActivity.PREFS,0).edit().putString("mode","AUTO").commit();}
    }
}

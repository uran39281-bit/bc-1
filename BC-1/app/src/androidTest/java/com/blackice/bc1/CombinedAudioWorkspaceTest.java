package com.blackice.bc1;

import android.net.Uri;
import android.view.View;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.editor.*;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CombinedAudioWorkspaceTest {
    @Test public void audioAndCaptionsRemainReachableInBothWorkspaces()throws Exception{
        WorkspaceV2Test h=new WorkspaceV2Test();android.content.Context context=h.inst.getTargetContext();
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        File source=new File(context.getFilesDir(),"combined-audio-test.mp4");
        try(InputStream in=h.inst.getContext().getAssets().open("red.mp4");OutputStream out=new FileOutputStream(source)){in.transferTo(out);}
        EditProject project=new EditProject();project.name="Audio and workspace integration";
        EditProject.Clip clip=new MediaImporter(context).importClip(Uri.fromFile(source),false);clip.fadeInMs=200;clip.motion.put(Motion.VOLUME,new Motion.Key(0,.5f));project.clips.add(clip);project.addLandmark(500);
        new ProjectStore(context).save(project);
        context.getSharedPreferences(EditorActivity.PREFS,0).edit().putString("last-project",project.id).putString("mode","AUTO").putBoolean("ui-locked",false).commit();
        try{for(boolean wide:new boolean[]{true,false}){
            h.shell("wm size "+(wide?"1920x1080":"1080x1920"));h.shell("wm density "+(wide?"160":"320"));
            try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(EditorActivity.class)){
                h.waitLoaded(scenario);
                scenario.onActivity(a->{a.selectClip(0,false);View audio=WorkspaceV2Test.described(a.inspector,"Media Settings: Audio");assertNotNull(audio);audio.performClick();
                    for(String name:new String[]{"Noise reduction","Equalizer","Loudness normalization"}){View button=WorkspaceV2Test.text(a.inspector,name);assertNotNull(button);assertTrue(button.isEnabled());}
                    assertNull(WorkspaceV2Test.text(a.inspector,"Fade In"));assertNull(WorkspaceV2Test.text(a.inspector,"Fade Out"));assertNotNull(WorkspaceV2Test.text(a.inspector,"Audio visualizer temporarily disabled"));
                    assertEquals(1,a.project.landmarks.size());assertEquals(1,a.clip().motion.curves.get(Motion.VOLUME).size());assertEquals(200,a.clip().fadeInMs);
                });Thread.sleep(400);h.capture(wide?"combined-landscape-audio":"combined-portrait-audio");
                scenario.onActivity(a->WorkspaceV2Test.text(a.inspector,"Equalizer").performClick());Thread.sleep(400);h.capture(wide?"combined-landscape-eq":"combined-portrait-eq");WorkflowControlsTest.click(h,"Cancel");
                scenario.onActivity(CaptionPanel::show);Thread.sleep(400);h.capture(wide?"combined-landscape-captions":"combined-portrait-captions");WorkflowControlsTest.click(h,"Close");
            }
        }}finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }
}

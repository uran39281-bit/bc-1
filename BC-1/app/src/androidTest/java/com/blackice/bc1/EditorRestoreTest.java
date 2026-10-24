package com.blackice.bc1;

import android.app.Activity;
import android.content.*;
import android.os.Bundle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.*;
import com.blackice.bc1.editor.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EditorRestoreTest {
    @Test public void stateSavedBeforeDiskLoadRetainsRequestedProjectIdentity() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        EditProject requested=new EditProject();requested.name="Pending restore identity";
        new ProjectStore(context).save(requested);
        AtomicReference<Bundle> saved=new AtomicReference<>();
        AtomicReference<Throwable> callbackFailure=new AtomicReference<>();
        ActivityLifecycleCallback callback=(Activity activity,Stage stage)->{
            if(stage!=Stage.CREATED||!(activity instanceof EditorActivity))return;
            EditorActivity editor=(EditorActivity)activity;
            if(!requested.id.equals(editor.getIntent().getStringExtra("project-id")))return;
            try{
                assertTrue("Capture state before the async disk result is delivered",editor.loading);
                assertNotEquals("The placeholder is a different project",requested.id,editor.project.id);
                Bundle state=new Bundle();editor.onSaveInstanceState(state);saved.set(state);
            }catch(Throwable error){callbackFailure.set(error);}
        };
        ActivityLifecycleMonitorRegistry.getInstance().addLifecycleCallback(callback);
        Intent launch=new Intent(context,EditorActivity.class).putExtra("project-id",requested.id);
        try(ActivityScenario<EditorActivity> scenario=ActivityScenario.launch(launch)){
            if(callbackFailure.get()!=null)throw new AssertionError(callbackFailure.get());
            assertNotNull("Captured the real CREATED lifecycle event",saved.get());
            assertEquals("A second configuration change must reopen the requested project",requested.id,saved.get().getString("project-id"));
            new WorkspaceV2Test().waitLoaded(scenario);
            scenario.onActivity(a->assertEquals(requested.id,a.project.id));
        }finally{ActivityLifecycleMonitorRegistry.getInstance().removeLifecycleCallback(callback);}
    }
}

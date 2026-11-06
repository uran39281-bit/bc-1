package com.blackice.bc1;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.ProjectStore;
import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** A browser reader must not discard an editor instance's in-flight atomic save. */
@RunWith(AndroidJUnit4.class)
public class ProjectStoreConcurrencyTest {
    @Test public void separateInstancesPreserveEveryCompletedSaveWhileReading() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        ProjectStore writer=new ProjectStore(context),reader=new ProjectStore(context);
        EditProject project=new EditProject();project.name="Initial revision";
        // A substantial real project makes the read/write overlap observable without test-only hooks.
        for(int i=0;i<80;i++){
            EditProject.Clip c=new EditProject.Clip();c.name="Source "+i;c.image=true;
            c.uri="file:///concurrency-fixture-"+project.id+"-"+i+".png";
            c.sourceDurationMs=c.outMs=1000;project.clips.add(c);
        }
        writer.save(project);
        ExecutorService threads=Executors.newFixedThreadPool(2);
        AtomicBoolean stop=new AtomicBoolean();CountDownLatch readerReady=new CountDownLatch(1);
        Future<?> reads=threads.submit(()->{
            try{reader.load(project.id);readerReady.countDown();while(!stop.get())assertEquals(80,reader.load(project.id).clips.size());}
            catch(Exception e){throw new RuntimeException(e);}
        });
        try{
            assertTrue(readerReady.await(10,TimeUnit.SECONDS));
            Future<?> writes=threads.submit(()->{
                try{
                    for(int revision=1;revision<=40;revision++){
                        project.name="Revision "+revision;writer.save(project);
                        assertEquals("A completed save must survive a concurrent browser read",project.name,reader.load(project.id).name);
                    }
                }catch(Exception e){throw new RuntimeException(e);}finally{stop.set(true);}
            });
            writes.get(90,TimeUnit.SECONDS);reads.get(15,TimeUnit.SECONDS);
            EditProject saved=reader.load(project.id);assertEquals("Revision 40",saved.name);
            assertEquals(80,saved.clips.size());assertEquals(project.clips.get(0).uri,saved.clips.get(0).uri);
        }finally{
            stop.set(true);threads.shutdownNow();assertTrue(threads.awaitTermination(15,TimeUnit.SECONDS));
            for(String suffix:new String[]{".json",".json.new",".json.bak"})new File(context.getFilesDir(),"projects-v1/"+project.id+suffix).delete();
        }
    }
}

package com.blackice.bc1;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** The panel being worked in grows into the black bars beside the preview; the picture itself never shrinks. */
@RunWith(AndroidJUnit4.class)
public class DynamicWindowTest {
    final WorkspaceV2Test h=new WorkspaceV2Test();

    @Test public void workingPanelsGrowIntoTheBlackBarsAndShrinkBack()throws Exception{
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{new Workspace144Test().fixture();
            // A wide, short window leaves plenty of black beside the 16:9 picture.
            h.shell("wm size 1920x800");h.shell("wm density 160");
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);Thread.sleep(700);
                final int[] rest=new int[4];
                scene.onActivity(a->{assertTrue("wide layout",a.wideLayout);a.selectClip(0,false);a.panels.reset();a.renderInspector();});
                Thread.sleep(600);
                scene.onActivity(a->{
                    rest[0]=a.toolsPane.getWidth();rest[1]=a.surface.getWidth();rest[2]=a.libraryPane.getWidth();
                    assertEquals("Idle panels keep their resting widths",a.toolsBasePx,rest[0]);assertEquals(a.libraryBasePx,rest[2]);
                    assertTrue("There is black beside the picture to use",a.monitor.getWidth()>a.surface.getWidth()+a.dp(120));
                    // Preview controls moved out of the picture and into the transport row.
                    assertNotNull(a.compareButton);assertNotNull(a.viewOptionsButton);
                    assertNotSame(a.monitor,a.compareButton.getParent());assertNotSame(a.monitor,a.viewOptionsButton.getParent());
                    assertTrue(a.compareButton.isShown());assertTrue(a.viewOptionsButton.isShown());
                });
                h.capture("v147-wide-dynamic-rest");
                // Working in Move & Transform widens the right panel.
                scene.onActivity(a->a.panels.category("Move & Transform"));
                Thread.sleep(800);
                scene.onActivity(a->{
                    assertTrue("Working panel grows",a.toolsPane.getWidth()>rest[0]+a.dp(40));
                    assertTrue("...but not without limit",a.toolsPane.getWidth()<=a.dp(520)+2);
                    assertEquals("The picture keeps its size",rest[1],a.surface.getWidth(),3);
                    assertTrue("The picture still fits beside the panel",a.monitor.getWidth()>=a.surface.getWidth());
                    assertEquals("The left bin is untouched",rest[2],a.libraryPane.getWidth());
                    assertNotNull("The menu is laid out for the roomier panel",WorkspaceV2Test.text(a.inspector,"Previous Key"));
                });
                h.capture("v147-wide-dynamic-working");
                scene.onActivity(a->a.panels.back());
                Thread.sleep(800);
                scene.onActivity(a->{assertEquals("Leaving the panel restores its width",rest[0],a.toolsPane.getWidth(),2);assertEquals(rest[1],a.surface.getWidth(),3);});
                // The effect catalog widens the left bin instead.
                scene.onActivity(a->a.effectCatalog.open());
                Thread.sleep(800);
                scene.onActivity(a->{
                    assertTrue("Catalog grows the left bin",a.libraryPane.getWidth()>rest[2]+a.dp(40));
                    assertTrue(a.libraryPane.getWidth()<=a.dp(420)+2);
                    assertEquals("Right panel stays at rest",rest[0],a.toolsPane.getWidth(),2);
                    assertEquals("The picture keeps its size",rest[1],a.surface.getWidth(),3);
                });
                h.capture("v147-wide-dynamic-catalog");
                scene.onActivity(a->a.effectCatalog.close());
                Thread.sleep(800);
                scene.onActivity(a->assertEquals("Closing the catalog restores the bin",rest[2],a.libraryPane.getWidth(),2));
            }
        }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }
}

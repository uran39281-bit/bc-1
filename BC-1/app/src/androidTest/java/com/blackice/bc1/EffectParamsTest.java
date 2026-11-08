package com.blackice.bc1;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.blackice.bc1.core.EffectCatalog;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.LayerCapture;
import java.io.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Every effect control changes the rendered picture, and the Effects menu offers them. */
@RunWith(AndroidJUnit4.class)
public class EffectParamsTest {
    final WorkspaceV2Test h=new WorkspaceV2Test();

    /** A busy picture: gradient, checker, a bright block and a coloured block, so every kind of effect has something to act on. */
    private File pattern()throws Exception{
        Bitmap b=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint();
        p.setShader(new LinearGradient(0,0,640,360,0xFF102040,0xFFF0D080,Shader.TileMode.CLAMP));c.drawRect(0,0,640,360,p);p.setShader(null);
        p.setColor(0x66000000);for(int y=0;y<360;y+=40)for(int x=0;x<640;x+=40)if(((x+y)/40)%2==0)c.drawRect(x,y,x+40,y+40,p);
        p.setColor(0xFFFFFFFF);c.drawRect(420,60,560,160,p);p.setColor(0xFFD02040);c.drawRect(80,200,260,320,p);p.setColor(0xFF20C060);c.drawCircle(330,260,40,p);
        File file=new File(h.inst.getTargetContext().getFilesDir(),"effect-pattern.png");try(OutputStream out=new FileOutputStream(file)){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();return file;
    }

    private static int differing(Bitmap a,Bitmap b){
        int count=0;for(int y=0;y<a.getHeight();y+=2)for(int x=0;x<a.getWidth();x+=2){int p=a.getPixel(x,y),q=b.getPixel(x,y);
            if(Math.abs(Color.red(p)-Color.red(q))>=8||Math.abs(Color.green(p)-Color.green(q))>=8||Math.abs(Color.blue(p)-Color.blue(q))>=8||Math.abs(Color.alpha(p)-Color.alpha(q))>=8)count++;}
        return count;
    }

    @Test public void everyControlChangesThePicture()throws Exception{
        android.content.Context context=h.inst.getTargetContext();File image=pattern();
        EditProject project=new EditProject();EditProject.Clip clip=new MediaImporter(context).importClip(Uri.fromFile(image),false);clip.outMs=clip.sourceDurationMs=5000;project.clips.add(clip);project.library.add(clip.copy());
        StringBuilder failures=new StringBuilder();int checked=0;
        for(int id=1;id<EffectCatalog.count();id++){
            EffectCatalog.Param[] params=EffectCatalog.params(id);
            for(int i=0;i<params.length;i++){
                if(id==3||id==65||id==69)continue; // keys: the pattern has no green / blue edge for their controls to show
                clip.effect=id;clip.intensity=.6f;clip.fxEffect=id;
                // 36 degrees, not 45: the kaleidoscope has 8 segments of exactly 45 degrees at this strength, so a 45 degree turn maps it onto itself.
                float low=0f,high=params[i].angle?.1f:1f;
                float[] a=new float[]{params[0].def,params.length>1?params[1].def:0,params.length>2?params[2].def:0,0},b=a.clone();a[i]=low;b[i]=high;
                clip.fx=a;Bitmap first=LayerCapture.render(context,ProjectStore.snapshot(project),clip.id,1500,360);
                clip.fx=b;Bitmap second=LayerCapture.render(context,ProjectStore.snapshot(project),clip.id,1500,360);
                int changed=differing(first,second);checked++;
                if(changed<40)failures.append(EffectCatalog.name(id)).append(" / ").append(params[i].name).append(" changed ").append(changed).append(" px; ");
                first.recycle();second.recycle();
            }
        }
        assertTrue("Checked many controls: "+checked,checked>=40);
        assertEquals("Controls with no visible effect: "+failures,0,failures.length());
    }

    @Test public void defaultControlsLeaveTheOriginalLookUntouched()throws Exception{
        android.content.Context context=h.inst.getTargetContext();File image=pattern();
        EditProject project=new EditProject();EditProject.Clip clip=new MediaImporter(context).importClip(Uri.fromFile(image),false);clip.outMs=clip.sourceDurationMs=5000;project.clips.add(clip);project.library.add(clip.copy());
        for(int id:new int[]{1,8,12,29,30,34,40,43,47}){
            clip.effect=id;clip.intensity=.6f;clip.fx=null;
            Bitmap unset=LayerCapture.render(context,ProjectStore.snapshot(project),clip.id,1500,360);
            EffectCatalog.Param[] params=EffectCatalog.params(id);float[] defaults=new float[params.length];for(int i=0;i<params.length;i++)defaults[i]=params[i].def;
            clip.fx=defaults;clip.fxEffect=id;Bitmap explicit=LayerCapture.render(context,ProjectStore.snapshot(project),clip.id,1500,360);
            assertEquals(EffectCatalog.name(id)+": explicit defaults equal an unset clip",0,differing(unset,explicit));unset.recycle();explicit.recycle();
        }
    }

    @Test public void theEffectsMenuOffersTheControlsOfTheSelectedEffect()throws Exception{
        String size=h.override(h.shell("wm size")),density=h.override(h.shell("wm density"));
        try{EditProject p=new Workspace144Test().fixture();
            EditProject stored=new ProjectStore(h.inst.getTargetContext()).load(p.id);stored.clips.get(0).effect=13;
            new ProjectStore(h.inst.getTargetContext()).save(stored);
            h.shell("wm size 1920x1000");h.shell("wm density 160");
            try(ActivityScenario<EditorActivity> scene=ActivityScenario.launch(EditorActivity.class)){h.waitLoaded(scene);Thread.sleep(700);
                scene.onActivity(a->{a.selectClip(0,false);a.panels.category("Effects");});
                Thread.sleep(500);
                scene.onActivity(a->{
                    assertNotNull("Motion Blur offers an angle",WorkspaceV2Test.described(a.inspector,"Angle"));
                    assertNotNull("...and a length",WorkspaceV2Test.described(a.inspector,"Length"));
                    assertNotNull(WorkspaceV2Test.described(a.inspector,"Angle reset"));
                    android.widget.EditText strength=WorkspaceV2Test.field(a.inspector,"Effect strength exact value in percent");
                    assertNotNull("Strength remains editable",strength);int undo=a.undo.size();strength.setText("65");WorkspaceV2Test.text(a.inspector,"Apply").performClick();assertEquals(.65f,a.clip().intensity,1e-5);assertEquals("Strength edit is undoable",undo+1,a.undo.size());assertEquals("Selected effect retained",13,a.clip().effect);
                });
                h.capture("v151-effect-controls");
            }
        }finally{h.shell("wm size "+size);h.shell("wm density "+density);}
    }

    @Test public void everyNewGradingEffectChangesThePictureAtItsDefaults()throws Exception{
        android.content.Context context=h.inst.getTargetContext();File image=pattern();
        EditProject project=new EditProject();EditProject.Clip clip=new MediaImporter(context).importClip(Uri.fromFile(image),false);clip.outMs=clip.sourceDurationMs=5000;project.clips.add(clip);project.library.add(clip.copy());
        clip.effect=0;clip.fx=null;Bitmap plain=LayerCapture.render(context,ProjectStore.snapshot(project),clip.id,1500,360);
        StringBuilder failures=new StringBuilder();
        for(int id=48;id<EffectCatalog.count();id++){
            if(id==65||id==69)continue; // a blue-screen or (default green) any-colour key leaves this picture untouched
            clip.effect=id;clip.intensity=.6f;clip.fx=null;
            Bitmap shown=LayerCapture.render(context,ProjectStore.snapshot(project),clip.id,1500,360);
            int changed=differing(plain,shown);if(changed<40)failures.append(EffectCatalog.name(id)).append(" changed ").append(changed).append(" px; ");shown.recycle();
        }
        plain.recycle();assertEquals("Effects that do nothing when first added: "+failures,0,failures.length());
    }
}

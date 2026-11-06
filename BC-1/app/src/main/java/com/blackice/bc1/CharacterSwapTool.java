package com.blackice.bc1;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Build;
import android.widget.*;
import com.blackice.bc1.character.CharacterFilter;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.engine.bc.ReverseTranscoder;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Render into a private derivative and commit exactly once on the main thread. */
final class CharacterSwapTool {
    static final int ART=713, PLATE=714;
    private final EditorActivity a;
    private String project,clipId,source;
    private long revision;
    private Uri character;
    private AtomicBoolean cancel;
    private AlertDialog progressDialog;
    CharacterSwapTool(EditorActivity a){this.a=a;}
    String reason(){
        if(!java.util.Arrays.asList(Build.SUPPORTED_ABIS).contains("arm64-v8a"))return "Character tracking requires a 64-bit ARM device.";
        EditProject.Clip c=a.clip();
        if(a.overlay()!=null||c==null||c.image||c.audioOnly||c.reversed||EditProject.audioTrack(c.track))return "Select a forward video clip.";
        if(c.sourceDurationMs>12000)return "Choose a source file up to 12 seconds long.";
        if(a.selectionLocked()||a.uiLocked)return "Unlock the selected layer first.";
        return null;
    }
    void choose(){
        String why=reason();if(why!=null){a.tell(why);return;}
        if(cancel!=null||!a.layerEditable())return;
        project=a.project.id;clipId=a.clip().id;source=a.clip().uri;revision=a.editRevision;character=null;
        a.pick(ART,"image/png");
    }
    private boolean current(){
        EditProject.Clip c=a.clip();
        return !a.isFinishing()&&!a.isDestroyed()&&a.project!=null&&a.project.id.equals(project)&&
            a.overlay()==null&&c!=null&&c.id.equals(clipId)&&c.uri.equals(source)&&revision==a.editRevision&&!a.selectionLocked();
    }
    void picked(int request, Uri uri){
        if(uri==null||cancel!=null)return;
        if(!current()){a.tell("The selection changed. Choose the character again.");return;}
        if(request==ART){
            character=uri;
            new AlertDialog.Builder(a).setTitle("Empty background")
                .setMessage("Use a clear frame from the same stationary camera, with no person in it.")
                .setPositiveButton("Choose image",(d,w)->a.pick(PLATE,"image/*"))
                .setNeutralButton("Use first frame",(d,w)->start(null))
                .setNegativeButton("Cancel",null).show();
        }else if(character!=null)start(uri);
    }
    private void start(Uri plate){
        if(!current()||!a.layerEditable()||character==null||cancel!=null)return;
        final Uri art=character;final String input=source;final long duration=a.clip().sourceDurationMs;
        final AtomicBoolean flag=new AtomicBoolean();cancel=flag;
        final ProgressBar bar=new ProgressBar(a,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);
        progressDialog=new AlertDialog.Builder(a).setTitle("Replacing person on this device").setView(bar)
            .setNegativeButton("Cancel",(d,w)->flag.set(true)).create();
        progressDialog.setOnCancelListener(d->flag.set(true));progressDialog.show();
        a.pausePlayback();
        a.io.execute(()->{
            File result=new File(new File(a.getFilesDir(),"media"),UUID.randomUUID()+"-character.mp4");
            String error=null;
            try {
                File parent=result.getParentFile();
                if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("Cannot create replacement media.");
                if(parent.getUsableSpace()<64L*1024*1024)throw new IOException("Free at least 64 MB to render the replacement.");
                try(CharacterFilter f=new CharacterFilter(a.getApplicationContext(),art,plate)){
                    ReverseTranscoder.replacePerson(a,input,duration,result,f::apply,flag::get,p->a.main.post(()->bar.setProgress(p)));
                    f.requireReplacement();
                }
            }catch(CancellationException e){flag.set(true);}
            catch(Exception | LinkageError | OutOfMemoryError e){
                error=e instanceof LinkageError?"The offline tracker could not start on this device.":
                    e instanceof OutOfMemoryError?"Not enough memory to finish.":e.getMessage()!=null?e.getMessage():"Character replacement failed.";
            }
            final String failure=error;
            a.main.post(()->{
                cancel=null;if(progressDialog!=null&&progressDialog.isShowing())progressDialog.dismiss();progressDialog=null;
                if(flag.get()||failure!=null||!current()||!a.layerEditable()){
                    result.delete();if(!a.isDestroyed()&&!a.isFinishing())a.tell(flag.get()?"Replacement cancelled.":failure!=null?failure:"The clip changed; result discarded.");
                    return;
                }
                String output=Uri.fromFile(result).toString();
                a.edit("Person replaced",()->{EditProject.Clip c=a.clip();c.uri=output;c.name="Character • "+(c.name==null?"Video":c.name);});
                if(a.project.libraryIndex(output)<0 && (a.clip()==null||!output.equals(a.clip().uri)))result.delete();
                a.renderInspector();
            });
        });
    }
    void destroy(){if(cancel!=null)cancel.set(true);if(progressDialog!=null)progressDialog.dismiss();progressDialog=null;}
}

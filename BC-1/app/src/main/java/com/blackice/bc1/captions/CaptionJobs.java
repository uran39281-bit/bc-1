package com.blackice.bc1.captions;

import android.content.Context;
import com.blackice.bc1.core.SpeechCheckpoint;
import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.ProjectStore;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;
import org.json.JSONObject;

/** Persistent original input + options. Resuming never silently switches to a newly edited timeline. */
public final class CaptionJobs {
    private CaptionJobs() {}
    static File directory(Context c,String projectId){
        if(projectId==null||!projectId.matches("[A-Za-z0-9-]{1,80}"))throw new IllegalArgumentException("Invalid project ID");
        return new File(new File(c.getFilesDir(),"caption-jobs"),projectId);
    }
    public static boolean exists(Context c,String id){return new File(directory(c,id),"job.json").isFile();}
    public static final class Job {
        public final String id;public final EditProject project;public final Transcriber.Options options;public final File checkpoint;
        Job(String id,EditProject project,Transcriber.Options options,File checkpoint){this.id=id;this.project=project;this.options=options;this.checkpoint=checkpoint;}
    }
    static Job create(Context c,EditProject project,Transcriber.Options options)throws Exception{
        if(exists(c,project.id))throw new IOException("A saved speech job exists. Resume or discard it in Speech & captions first.");
        String id=UUID.randomUUID().toString();
        JSONObject j=new JSONObject().put("format",1).put("id",id).put("project",new JSONObject(ProjectStore.encode(project)))
            .put("model",options.model.name()).put("modelHash",options.model.sha256).put("language",options.language).put("translate",options.translate).put("clip",options.clipId);
        // A completed old job can leave an orphan checkpoint after deletion was interrupted.
        File dir=directory(c,project.id);File checkpoint=new File(dir,"checkpoint.json");
        if(checkpoint.exists()&&!checkpoint.delete())throw new IOException("Cannot clear the old checkpoint");
        SpeechCheckpoint.atomicWrite(new File(dir,"job.json"),j.toString());
        return load(c,project.id);
    }
    static Job load(Context c,String projectId)throws Exception{
        File dir=directory(c,projectId);JSONObject j=new JSONObject(new String(Files.readAllBytes(new File(dir,"job.json").toPath()),StandardCharsets.UTF_8));
        if(j.getInt("format")!=1)throw new IOException("Unsupported saved speech job");
        EditProject p=ProjectStore.decode(j.getJSONObject("project").toString());
        if(!projectId.equals(p.id))throw new IOException("Saved speech job belongs to another project");
        Transcriber.Options o=new Transcriber.Options();o.model=SpeechModels.Model.valueOf(j.getString("model"));
        if(!o.model.sha256.equals(j.getString("modelHash")))throw new IOException("Speech model changed; discard this job and start again");
        o.language=j.getString("language");o.translate=j.getBoolean("translate");o.clipId=j.optString("clip",null);
        return new Job(j.getString("id"),p,o,new File(dir,"checkpoint.json"));
    }
    static void discard(Context c,String projectId)throws IOException{
        File dir=directory(c,projectId),job=new File(dir,"job.json");
        if(job.exists()&&!job.delete())throw new IOException("Cannot discard the saved speech job");
        File[] files=dir.listFiles();if(files!=null)for(File f:files)f.delete();dir.delete();
    }
    static void complete(Context c,String projectId,String jobId){
        try{if(exists(c,projectId)&&load(c,projectId).id.equals(jobId))discard(c,projectId);}catch(Exception ignored){}
    }
}

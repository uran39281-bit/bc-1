package com.blackice.bc1.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** A committed prefix of recognition output. Its sample cursor and segments move together. */
public final class SpeechCheckpoint {
    public static final int FORMAT = 1;
    public final String jobId;
    public long nextFrame, elapsedMs;
    public String language = "auto";
    public final List<long[]> times = new ArrayList<>();
    public final List<String> texts = new ArrayList<>();
    public SpeechCheckpoint(String jobId) { this.jobId=jobId; }

    public static SpeechCheckpoint load(File file, String jobId, long totalFrames) throws Exception {
        if (!file.isFile()) return new SpeechCheckpoint(jobId);
        JSONObject j=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        if(j.getInt("format")!=FORMAT || !jobId.equals(j.getString("jobId"))) throw new IOException("Checkpoint does not match this speech job. Discard it and start again.");
        SpeechCheckpoint c=new SpeechCheckpoint(jobId);c.nextFrame=j.getLong("nextFrame");c.elapsedMs=j.getLong("elapsedMs");c.language=j.getString("language");
        if(c.nextFrame<0 || c.nextFrame>totalFrames || c.elapsedMs<0)throw new IOException("Invalid speech checkpoint cursor");
        JSONArray a=j.getJSONArray("segments");long last=-1;
        for(int i=0;i<a.length();i++){
            JSONArray v=a.getJSONArray(i);long start=v.getLong(0),end=v.getLong(1);String text=v.getString(2);
            if(start<0||start<last||end<start||end>c.nextFrame*1000/16000)throw new IOException("Invalid speech checkpoint timing");
            c.times.add(new long[]{start,end});c.texts.add(text);last=start;
        }
        return c;
    }
    public void save(File file) throws Exception {
        if(times.size()!=texts.size())throw new IOException("Incomplete speech checkpoint");
        JSONArray a=new JSONArray();for(int i=0;i<times.size();i++)a.put(new JSONArray().put(times.get(i)[0]).put(times.get(i)[1]).put(texts.get(i)));
        atomicWrite(file,new JSONObject().put("format",FORMAT).put("jobId",jobId).put("nextFrame",nextFrame)
                .put("elapsedMs",elapsedMs).put("language",language).put("segments",a).toString());
    }
    public static void atomicWrite(File file,String text) throws IOException {
        File parent=file.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create speech storage");
        File tmp=new File(file.getPath()+".tmp");
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(text.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
        Files.move(tmp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}

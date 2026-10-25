package com.blackice.bc1.core;

import com.blackice.bc1.editor.EditProject;
import com.blackice.bc1.editor.ProjectStore;
import java.util.List;

/** Prepare privately; persist before acknowledgement. A saved job ID makes crash recovery idempotent. */
public final class CaptionApplication {
    private CaptionApplication() {}
    public interface Save { void save(EditProject project) throws Exception; }
    public interface Acknowledge { void acknowledge() throws Exception; }
    public static EditProject commit(EditProject current,List<CaptionBuilder.Cue> cues,long from,long to,String jobId,Save save,Acknowledge ack) throws Exception {
        if(jobId==null||jobId.isEmpty())throw new IllegalArgumentException("Caption result has no job identity");
        EditProject next=ProjectStore.snapshot(current);
        if(!jobId.equals(next.appliedCaptionJobId)){
            CaptionBuilder.apply(next,cues,from,to);next.appliedCaptionJobId=jobId;next.validate();
        }
        save.save(next);
        // A failed acknowledgement leaves a durable, already-applied result for safe cleanup on reopen.
        try { ack.acknowledge(); } catch(Exception ignored) { }
        return next;
    }
}

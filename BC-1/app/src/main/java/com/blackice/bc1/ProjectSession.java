package com.blackice.bc1;

import android.content.Context;
import android.os.*;
import com.blackice.bc1.editor.*;
import java.util.*;
import java.util.concurrent.*;

/** Process-retained editing session. Model capture occurs on main; hashing and commits run on IO. */
final class ProjectSession {
    static final Map<String,ProjectSession> ACTIVE=new HashMap<>();
    static final ExecutorService WRITER=Executors.newSingleThreadExecutor();
    final String id,session=UUID.randomUUID().toString();final ProjectStore store;final RecoveryStore recovery;
    final Handler main=new Handler(Looper.getMainLooper());
    final LatestQueue<Snapshot> saves,checkpoints,hashes;
    EditProject model;long revision,saveRequest,lastCheckpointAttempt,firstPendingEdit;String savedHash,currentHash,saveFailure,recoveryWarning;
    String recoveryIdentity;long recoveryCaptured;boolean saving;volatile boolean closed;volatile long committedRevision=-1;volatile String committedHash;Runnable listener,afterSave;
    private static final class Snapshot {final EditProject project;final long revision,request;final String base;Snapshot(ProjectSession s,long request){project=s.model.copy();revision=s.revision;this.request=request;base=s.savedHash;}}
    ProjectSession(Context context,EditProject model,String ordinaryHash){this.id=model.id;this.model=model;savedHash=ordinaryHash;currentHash=ordinaryHash;store=new ProjectStore(context.getApplicationContext());recovery=new RecoveryStore(context.getApplicationContext());
        saves=new LatestQueue<>(WRITER,this::writeSave);checkpoints=new LatestQueue<>(WRITER,this::writeCheckpoint);
        hashes=new LatestQueue<>(WRITER,s->{try{String hash=DocumentIdentity.fingerprint(s.project);main.post(()->{if(s.revision==revision){currentHash=hash;notifyChange();}});}catch(Exception e){main.post(()->{saveFailure="Project could not be validated: "+e.getMessage();notifyChange();});}});
    }
    static ProjectSession attach(Context context,EditProject project,String ordinaryHash){ProjectSession retained=ACTIVE.get(project.id);if(retained!=null&&!retained.closed)return retained;ProjectSession s=new ProjectSession(context,project,ordinaryHash);ACTIVE.put(project.id,s);return s;}
    String status(){return saveFailure!=null?"Save failed":saving?"Saving…":dirty()?"Unsaved changes":"Saved";}
    boolean dirty(){return currentHash==null||savedHash==null||savedHash.isEmpty()||!currentHash.equals(savedHash);}
    void notifyChange(){if(listener!=null)listener.run();}
    void edited(EditProject project,boolean gesture){model=project;revision++;currentHash=null;notifyChange();long now=SystemClock.uptimeMillis();if(firstPendingEdit==0)firstPendingEdit=now;
        main.removeCallbacks(checkpointDue);main.postDelayed(checkpointDue,Math.max(0,Math.min(1000,firstPendingEdit+5000-now)));
        if(!gesture){hashes.submit(new Snapshot(this,0));}else if(now-lastCheckpointAttempt>=5000)checkpoint();
    }
    final Runnable checkpointDue=this::checkpoint;
    void checkpoint(){main.removeCallbacks(checkpointDue);firstPendingEdit=0;if(closed||!dirty())return;lastCheckpointAttempt=SystemClock.uptimeMillis();checkpoints.submit(new Snapshot(this,0));}
    void save(Runnable onSuccess){if(closed)return;afterSave=onSuccess==null?afterSave:onSuccess;saveFailure=null;saving=true;notifyChange();saves.submit(new Snapshot(this,++saveRequest));}
    private void writeSave(Snapshot s){try{
        store.save(s.project);String hash=DocumentIdentity.fingerprint(s.project);committedRevision=s.revision;committedHash=hash;
        recovery.saved(id,session,s.revision,hash);
        main.post(()->{savedHash=hash;if(revision==s.revision){currentHash=hash;model.name=s.project.name;}if(s.request==saveRequest){saving=false;saveFailure=null;}notifyChange();if(!saving&&afterSave!=null){if(!dirty()){Runnable exit=afterSave;afterSave=null;exit.run();}else save(null);}});
    }catch(Exception error){main.post(()->{if(s.request!=saveRequest)return;saving=false;saveFailure=error.getMessage()==null?"Storage is unavailable.":error.getMessage();afterSave=null;notifyChange();});}}
    private void writeCheckpoint(Snapshot s){try{
        if(closed||s.revision<=committedRevision)return;String hash=DocumentIdentity.fingerprint(s.project);
        // Reload the ordinary identity at the serial writer boundary to avoid redundant checkpoints.
        String ordinary;try{ordinary=DocumentIdentity.fingerprint(store.load(id));}catch(Exception unreadable){ordinary="";}
        if(hash.equals(ordinary)){recovery.saved(id,session,s.revision,hash);main.post(()->{if(s.revision==revision){currentHash=hash;notifyChange();}});return;}
        RecoveryStore.Candidate c=recovery.write(s.project,session,committedHash!=null?committedHash:s.base==null?"":s.base,s.revision);
        main.post(()->{recoveryIdentity=c.identity;recoveryCaptured=c.captured;recoveryWarning=null;if(s.revision==revision)currentHash=hash;notifyChange();});
    }catch(Exception error){main.post(()->{String warning="Recovery checkpoint could not be saved. Use Save to protect your changes.";if(!warning.equals(recoveryWarning)){recoveryWarning=warning;notifyChange();}});}}
    void finish(){closed=true;main.removeCallbacks(checkpointDue);ACTIVE.remove(id);listener=null;}
}

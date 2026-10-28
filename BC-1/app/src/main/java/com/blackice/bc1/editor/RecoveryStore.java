package com.blackice.bc1.editor;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Two atomic, validated generations in private persistent storage. Never contains media bytes. */
public final class RecoveryStore {
    private final File directory,projects;
    public RecoveryStore(Context c){directory=new File(c.getFilesDir(),"project-recovery-v1");directory.mkdirs();projects=new File(c.getFilesDir(),"projects-v1");}
    public static final class Candidate {
        public final EditProject project;public final String session,base,fingerprint,identity;public final long revision,captured;
        Candidate(EditProject p,String session,String base,String fingerprint,String identity,long revision,long captured){project=p;this.session=session;this.base=base;this.fingerprint=fingerprint;this.identity=identity;this.revision=revision;this.captured=captured;}
    }
    private File file(String id,boolean previous)throws IOException{if(id==null||!id.matches("[a-zA-Z0-9-]{1,80}"))throw new IOException("Invalid recovery identity");return new File(directory,id+(previous?".previous":".current"));}
    private boolean exists(String id){return new File(projects,id+".json").isFile()&&!new File(projects,id+".deleted").exists();}
    public Candidate read(String id)throws IOException{ synchronized(ProjectStore.FILE_LOCK){return readLocked(id);} }
    private Candidate readLocked(String id)throws IOException{
        if(!exists(id))return null;IOException failure=null;
        for(boolean previous:new boolean[]{false,true}){File f=file(id,previous);if(!f.exists()&&!new File(f+".bak").exists())continue;try{return parse(new AtomicFile(f).readFully(),id);}catch(Exception error){failure=new IOException("Recovery checkpoint could not be read. Original files were retained.",error);}}
        if(failure!=null)throw failure;return null;
    }
    public Candidate write(EditProject p,String session,String base,long revision)throws Exception{ synchronized(ProjectStore.FILE_LOCK){return writeLocked(p,session,base,revision);} }
    private Candidate writeLocked(EditProject p,String session,String base,long revision)throws Exception{
        if(!exists(p.id))throw new IOException("Project no longer exists; recovery was not recreated.");
        byte[] payload=ProjectStore.encode(p).getBytes(StandardCharsets.UTF_8);String hash=DocumentIdentity.hash(payload),fingerprint=DocumentIdentity.fingerprint(p);
        JSONObject envelope=new JSONObject().put("format",1).put("schema",EditProject.SCHEMA).put("id",p.id).put("session",session).put("base",base).put("revision",revision).put("captured",System.currentTimeMillis()).put("length",payload.length).put("checksum",hash).put("fingerprint",fingerprint).put("document",new String(payload,StandardCharsets.UTF_8));
        byte[] bytes=envelope.toString().getBytes(StandardCharsets.UTF_8);Candidate result=parse(bytes,p.id);
        // Only replace the fallback with a validated current generation.
        File current=file(p.id,false);byte[] old=null;try{byte[] candidate=new AtomicFile(current).readFully();parse(candidate,p.id);old=candidate;}catch(Exception ignored){}if(old!=null)atomic(file(p.id,true),old);
        atomic(current,bytes);return result;
    }
    private static Candidate parse(byte[] bytes,String id)throws Exception{
        JSONObject e=new JSONObject(new String(bytes,StandardCharsets.UTF_8));byte[] payload=e.getString("document").getBytes(StandardCharsets.UTF_8);
        if(e.getInt("format")!=1||!id.equals(e.getString("id"))||payload.length!=e.getLong("length")||!DocumentIdentity.hash(payload).equals(e.getString("checksum")))throw new IOException("Recovery integrity check failed");
        EditProject p=ProjectStore.decode(new String(payload,StandardCharsets.UTF_8));String fingerprint=DocumentIdentity.fingerprint(p);
        if(!id.equals(p.id)||e.getInt("schema")<1||e.getInt("schema")>EditProject.SCHEMA||!fingerprint.equals(e.getString("fingerprint")))throw new IOException("Recovery identity check failed");
        return new Candidate(p,e.getString("session"),e.getString("base"),fingerprint,e.getString("checksum"),e.getLong("revision"),e.getLong("captured"));
    }
    public void saved(String id,String session,long revision,String fingerprint)throws IOException{ synchronized(ProjectStore.FILE_LOCK){savedLocked(id,session,revision,fingerprint);} }
    private void savedLocked(String id,String session,long revision,String fingerprint)throws IOException{
        for(boolean previous:new boolean[]{false,true}){File f=file(id,previous);try{Candidate c=parse(new AtomicFile(f).readFully(),id);if(c.fingerprint.equals(fingerprint)||c.session.equals(session)&&c.revision<=revision)new AtomicFile(f).delete();}catch(Exception ignored){/* Invalid generations remain available for diagnostics. */}}
    }
    public void discard(String id,String identity)throws IOException{ synchronized(ProjectStore.FILE_LOCK){discardLocked(id,identity);} }
    private void discardLocked(String id,String identity)throws IOException{
        Candidate current=read(id);if(current==null)return;if(!current.identity.equals(identity))throw new IOException("Recovery changed. Reopen the recovery decision.");for(boolean previous:new boolean[]{false,true})new AtomicFile(file(id,previous)).delete();
    }
    private static void atomic(File target,byte[] bytes)throws IOException{AtomicFile file=new AtomicFile(target);FileOutputStream out=null;try{out=file.startWrite();out.write(bytes);out.getFD().sync();file.finishWrite(out);}catch(IOException e){if(out!=null)file.failWrite(out);throw e;}}
}

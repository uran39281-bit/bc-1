package com.blackice.bc1.editor;

import android.content.Context;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned JSON, atomic writes and one file per project; v0.3 sessions stay untouched. */
public final class ProjectStore {
    private final File directory, cacheDirectory;
    public ProjectStore(Context context) { cacheDirectory=context.getCacheDir(); directory=new File(context.getFilesDir(),"projects-v1"); directory.mkdirs(); }
    public synchronized void save(EditProject p) throws IOException, JSONException {
        String json=encode(p);File old=new File(directory,p.id+".json"),backup=new File(directory,p.id+".v1-backup");
        if(old.isFile()){byte[] bytes=new AtomicFile(old).readFully();try{int schema=new JSONObject(new String(bytes,StandardCharsets.UTF_8)).optInt("schema");backup=new File(directory,p.id+".v"+schema+"-backup");if(schema<EditProject.SCHEMA&&!backup.exists())java.nio.file.Files.write(backup.toPath(),bytes);}catch(JSONException ignored){}}
        AtomicFile file=new AtomicFile(new File(directory,p.id+".json"));
        FileOutputStream stream=null;
        try { stream=file.startWrite(); stream.write(json.getBytes(StandardCharsets.UTF_8)); file.finishWrite(stream); }
        catch(IOException e) { if(stream!=null) file.failWrite(stream); throw e; }
    }
    public synchronized EditProject load(String id) throws IOException,JSONException {
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,80}")) throw new IOException("Invalid project ID");
        return decode(new String(new AtomicFile(new File(directory,id+".json")).readFully(),StandardCharsets.UTF_8));
    }
    public static final class Entry {
        public final String id,name;
        Entry(String id,String name) { this.id=id; this.name=name; }
    }
    public synchronized List<Entry> recent() {
        List<Entry> result=new ArrayList<>(); File[] files=directory.listFiles((d,n)->n.endsWith(".json"));
        if(files==null) return result;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        for(File f:files) try { EditProject p=load(f.getName().replace(".json","")); result.add(new Entry(p.id,p.name)); }
        catch(Exception ignored) { /* A damaged file is retained for recovery, never overwritten. */ }
        return result;
    }
    /** Browser rows retain damaged files as visible, recoverable entries. Call off the UI thread. */
    public static final class BrowserEntry {
        public final String id, name, error;
        public final long edited;
        public final EditProject project;
        BrowserEntry(String id, String name, long edited, EditProject project, String error) {
            this.id=id; this.name=name; this.edited=edited; this.project=project; this.error=error;
        }
    }
    public synchronized List<BrowserEntry> browser() throws IOException {
        File[] files=directory.listFiles((d,n)->n.endsWith(".json"));
        if(files==null) throw new IOException("Project storage is unavailable. Try again.");
        List<BrowserEntry> result=new ArrayList<>();
        for(File f:files) {
            String id=f.getName().substring(0,f.getName().length()-5);
            try { EditProject p=load(id); result.add(new BrowserEntry(id,p.name,f.lastModified(),p,null)); }
            catch(Exception e) { result.add(new BrowserEntry(id,"Unavailable project",f.lastModified(),null,"Project file is damaged or unsupported. Original retained.")); }
        }
        result.sort((a,b)->Long.compare(b.edited,a.edited)); return result;
    }
    public synchronized void rename(String id,String name) throws Exception {
        if(name==null || name.isBlank() || name.length()>120) throw new IOException("Use a name from 1 to 120 characters.");
        EditProject p=load(id);p.name=name.trim();save(p);
    }
    public synchronized EditProject duplicate(String id) throws Exception {
        EditProject p=load(id);p.id=UUID.randomUUID().toString();
        p.name=p.name.substring(0,Math.min(113,p.name.length()))+" (copy)";save(p);return p;
    }
    /** Delete only project-owned records. Shared media copies and original sources remain intact. */
    public synchronized void delete(String id) throws IOException {
        if(id==null || !id.matches("[a-zA-Z0-9-]{1,80}")) throw new IOException("Invalid project ID");
        File record=new File(directory,id+".json");
        if(!record.isFile()) throw new IOException("Project no longer exists.");
        // Proxy keys include project identity; duplicates have different keys even when media is shared.
        String previewKey=null;
        try { EditProject p=load(id);p.playheadMs=0;
            byte[] hash=java.security.MessageDigest.getInstance("SHA-256").digest(encode(p).getBytes(StandardCharsets.UTF_8));
            StringBuilder key=new StringBuilder();for(byte b:hash)key.append(String.format(Locale.ROOT,"%02x",b&255));previewKey=key.toString();
        } catch(Exception ignored) { /* A damaged project can still be deleted. */ }
        if(!record.delete()) throw new IOException("Could not delete project.");
        if(previewKey!=null)for(String suffix:new String[]{".mp4",".part.mp4"}){
            File proxy=new File(new File(cacheDirectory,"bc1-preview-v11"),previewKey+suffix);
            if(proxy.exists()&&!proxy.delete())throw new IOException("Project deleted, but its temporary preview could not be removed.");
        }
        File[] companions=directory.listFiles((d,n)->n.equals(id+".json.bak") || n.equals(id+".json.new") || n.matches(java.util.regex.Pattern.quote(id)+"\\.v[0-9]+-backup"));
        if(companions!=null)for(File f:companions)if(!f.delete())throw new IOException("Project deleted, but a recovery file could not be removed.");
    }
    public static String encode(EditProject p) throws JSONException {
        // Anything on the timeline is also listed in the media bin.
        p.ensureLibrary(); p.validate(); JSONObject j=new JSONObject(); j.put("schema",EditProject.SCHEMA).put("id",p.id).put("name",p.name)
                .put("portrait",p.portrait).put("previewHeight",p.previewHeight).put("playheadMs",p.playheadMs)
                .put("frameRate",p.frameRate).put("loopInMs",p.loopInMs).put("loopOutMs",p.loopOutMs);
        JSONArray tracks=new JSONArray();
        for(EditProject.Track t:p.tracks) tracks.put(new JSONObject().put("name",t.name).put("color",t.color).put("hidden",t.hidden).put("solo",t.solo)
                .put("locked",t.locked).put("muted",t.muted).put("collapsed",t.collapsed));
        j.put("tracks",tracks); JSONArray markers=new JSONArray(); for(long m:p.markers) markers.put(m); j.put("markers",markers);
        JSONArray clips=new JSONArray();
        for(EditProject.Clip c:p.clips) clips.put(new JSONObject().put("id",c.id).put("uri",c.uri).put("name",c.name)
                .put("audioOnly",c.audioOnly).put("image",c.image).put("sourceDurationMs",c.sourceDurationMs).put("inMs",c.inMs).put("outMs",c.outMs)
                .put("effect",c.effect).put("intensity",c.intensity).put("opacity",c.opacity).put("volume",c.volume).put("unmutedVolume",c.unmutedVolume).put("speed",c.speed)
                .put("track",c.track).put("startMs",c.startMs).put("gapMs",c.gapMs).put("fadeInMs",c.fadeInMs).put("fadeOutMs",c.fadeOutMs)
                .put("x",c.x).put("y",c.y).put("scale",c.scale).put("rotation",c.rotation).put("motion",motion(c.motion))
                .put("chroma",c.chroma).put("keyColor",c.keyColor).put("keyTolerance",c.keyTolerance).put("keySoftness",c.keySoftness).put("keySpill",c.keySpill)
                .put("transition",c.transition).put("transitionMs",c.transitionMs).put("easing",c.easing).put("blend",c.blend)
                .put("heightScale",c.heightScale).put("aspect",c.aspect).put("skewX",c.skewX).put("skewY",c.skewY).put("anchorX",c.anchorX).put("anchorY",c.anchorY)
                .put("flipH",c.flipH).put("flipV",c.flipV).put("width",c.width).put("height",c.height).put("group",c.group));
        j.put("clips",clips); JSONArray library=new JSONArray();
        for(EditProject.Clip a:p.library) library.put(new JSONObject().put("id",a.id).put("uri",a.uri).put("name",a.name).put("image",a.image).put("audioOnly",a.audioOnly)
                .put("sourceDurationMs",a.sourceDurationMs).put("inMs",a.inMs).put("outMs",a.outMs).put("width",a.width).put("height",a.height));
        j.put("library",library); JSONArray overlays=new JSONArray();
        for(EditProject.Overlay o:p.overlays) overlays.put(new JSONObject().put("id",o.id).put("text",o.text)
                .put("motion",motion(o.motion)).put("imageUri",o.imageUri).put("startMs",o.startMs).put("endMs",o.endMs)
                .put("x",o.x).put("y",o.y).put("scale",o.scale).put("opacity",o.opacity).put("font",o.font).put("color",o.color).put("alignment",o.alignment)
                .put("shape",o.shape).put("name",o.name).put("group",o.group).put("hidden",o.hidden).put("solo",o.solo).put("locked",o.locked)
                .put("flipH",o.flipH).put("flipV",o.flipV).put("rotation",o.rotation).put("heightScale",o.heightScale).put("aspect",o.aspect).put("skewX",o.skewX).put("skewY",o.skewY)
                .put("anchorX",o.anchorX).put("anchorY",o.anchorY).put("borderWidth",o.borderWidth).put("shadow",o.shadow).put("borderColor",o.borderColor).put("blend",o.blend));
        j.put("overlays",overlays);
        if(p.music!=null) { EditProject.Music m=p.music;
            j.put("music",new JSONObject().put("uri",m.uri).put("name",m.name).put("sourceDurationMs",m.sourceDurationMs)
                    .put("inMs",m.inMs).put("outMs",m.outMs).put("startMs",m.startMs).put("volume",m.volume).put("loop",m.loop)); }
        return j.toString(2);
    }
    public static EditProject decode(String json) throws JSONException {
        JSONObject j=new JSONObject(json);
        if(j.getInt("schema")<1||j.getInt("schema")>EditProject.SCHEMA) throw new JSONException("Project version not supported; original file is preserved.");
        EditProject p=new EditProject(); p.id=j.getString("id"); p.name=j.getString("name"); p.portrait=j.getBoolean("portrait");
        p.previewHeight=j.getInt("previewHeight"); p.playheadMs=j.getLong("playheadMs");
        // Schema 4 additions; earlier files receive defaults that render identically.
        p.frameRate=j.optInt("frameRate",30); p.loopInMs=j.optLong("loopInMs",-1); p.loopOutMs=j.optLong("loopOutMs",-1);
        // Schema 5 moved audio from tracks 4–7 to 8–11 to make room for visual layers 4–7.
        boolean legacyTracks=j.getInt("schema")<5;
        JSONArray tracks=j.optJSONArray("tracks");
        if(tracks!=null) for(int t=0;t<tracks.length();t++) { int target=legacyTracks&&t>=4?t+4:t; if(target>=p.tracks.length)continue; JSONObject v=tracks.getJSONObject(t); EditProject.Track track=p.tracks[target];
            track.name=v.optString("name",track.name); track.color=v.optInt("color",track.color); track.hidden=v.optBoolean("hidden"); track.solo=v.optBoolean("solo");
            track.locked=v.optBoolean("locked"); track.muted=v.optBoolean("muted"); track.collapsed=v.optBoolean("collapsed"); }
        JSONArray markers=j.optJSONArray("markers"); if(markers!=null) for(int i=0;i<markers.length();i++) p.markers.add(markers.getLong(i));
        JSONArray clips=j.getJSONArray("clips");
        for(int i=0;i<clips.length();i++) { JSONObject v=clips.getJSONObject(i); EditProject.Clip c=new EditProject.Clip();
            c.track=v.optInt("track",0);if(legacyTracks&&c.track>=4)c.track+=4;c.startMs=v.optLong("startMs",0);c.gapMs=v.optLong("gapMs",0);c.fadeInMs=v.optLong("fadeInMs",0);c.fadeOutMs=v.optLong("fadeOutMs",0);
            c.x=(float)v.optDouble("x",0);c.y=(float)v.optDouble("y",0);c.scale=(float)v.optDouble("scale",1);c.rotation=(float)v.optDouble("rotation",0);c.motion=motion(v.optJSONArray("motion"));
            c.chroma=v.optBoolean("chroma",false);c.keyColor=v.optInt("keyColor",0xFF00FF00);c.keyTolerance=(float)v.optDouble("keyTolerance",.25);c.keySoftness=(float)v.optDouble("keySoftness",.08);c.keySpill=(float)v.optDouble("keySpill",.5);
            c.id=v.getString("id"); c.uri=v.getString("uri"); c.name=v.getString("name"); c.image=v.getBoolean("image");c.audioOnly=v.optBoolean("audioOnly",false);
            c.sourceDurationMs=v.getLong("sourceDurationMs"); c.inMs=v.getLong("inMs"); c.outMs=v.getLong("outMs");
            c.effect=v.getInt("effect"); c.intensity=(float)v.getDouble("intensity"); c.opacity=(float)v.getDouble("opacity");
            c.volume=(float)v.getDouble("volume");c.unmutedVolume=(float)v.optDouble("unmutedVolume",-1);c.speed=(float)v.optDouble("speed",1); c.transition=v.getInt("transition"); c.transitionMs=v.getLong("transitionMs"); c.easing=v.getInt("easing");c.blend=v.optInt("blend",0);
            c.heightScale=(float)v.optDouble("heightScale",-1); c.aspect=(float)v.optDouble("aspect",1); c.skewX=(float)v.optDouble("skewX",0); c.skewY=(float)v.optDouble("skewY",0); c.anchorX=(float)v.optDouble("anchorX",0); c.anchorY=(float)v.optDouble("anchorY",0);
            c.flipH=v.optBoolean("flipH"); c.flipV=v.optBoolean("flipV"); c.width=v.optInt("width",0); c.height=v.optInt("height",0); c.group=v.isNull("group")?null:v.optString("group",null); p.clips.add(c); }
        // The media bin is optional: older files rebuild it from their timeline sources.
        JSONArray library=j.optJSONArray("library");
        if(library!=null) for(int i=0;i<library.length();i++) { JSONObject v=library.getJSONObject(i); EditProject.Clip a=new EditProject.Clip();
            a.id=v.optString("id",a.id); a.uri=v.getString("uri"); a.name=v.getString("name"); a.image=v.optBoolean("image"); a.audioOnly=v.optBoolean("audioOnly");
            a.sourceDurationMs=v.getLong("sourceDurationMs"); a.inMs=v.optLong("inMs",0); a.outMs=v.optLong("outMs",a.sourceDurationMs); a.width=v.optInt("width",0); a.height=v.optInt("height",0);
            if(p.libraryIndex(a.uri)<0&&p.library.size()<EditProject.LIBRARY_LIMIT) p.library.add(a); }
        p.ensureLibrary();
        JSONArray overlays=j.getJSONArray("overlays");
        for(int i=0;i<overlays.length();i++) { JSONObject v=overlays.getJSONObject(i); EditProject.Overlay o=new EditProject.Overlay();
            o.motion=motion(v.optJSONArray("motion"));o.id=v.getString("id"); o.text=v.getString("text"); o.imageUri=v.optString("imageUri",null);
            o.startMs=v.getLong("startMs"); o.endMs=v.getLong("endMs"); o.x=(float)v.getDouble("x"); o.y=(float)v.getDouble("y");
            o.scale=(float)v.getDouble("scale"); o.opacity=(float)v.getDouble("opacity");o.font=v.optString("font","sans-serif-medium");o.color=v.optInt("color",0xFFFFFFFF);o.alignment=v.optInt("alignment",1);
            o.shape=v.optInt("shape",0); o.name=v.isNull("name")?null:v.optString("name",null); o.group=v.isNull("group")?null:v.optString("group",null);
            o.hidden=v.optBoolean("hidden"); o.solo=v.optBoolean("solo"); o.locked=v.optBoolean("locked"); o.flipH=v.optBoolean("flipH"); o.flipV=v.optBoolean("flipV");
            o.rotation=(float)v.optDouble("rotation",0); o.heightScale=(float)v.optDouble("heightScale",-1); o.aspect=(float)v.optDouble("aspect",1); o.skewX=(float)v.optDouble("skewX",0); o.skewY=(float)v.optDouble("skewY",0);
            o.anchorX=(float)v.optDouble("anchorX",0); o.anchorY=(float)v.optDouble("anchorY",0); o.borderWidth=(float)v.optDouble("borderWidth",0); o.shadow=(float)v.optDouble("shadow",0);
            o.borderColor=v.optInt("borderColor",0xFF000000); o.blend=v.optInt("blend",0); p.overlays.add(o); }
        if(j.has("music")) { JSONObject v=j.getJSONObject("music"); EditProject.Music m=new EditProject.Music();
            m.uri=v.getString("uri"); m.name=v.getString("name"); m.sourceDurationMs=v.getLong("sourceDurationMs");
            m.inMs=v.getLong("inMs"); m.outMs=v.getLong("outMs"); m.startMs=v.getLong("startMs");
            m.volume=(float)v.getDouble("volume"); m.loop=v.getBoolean("loop"); p.music=m; }
        p.validate(); return p;
    }
    private static JSONArray motion(Motion m)throws JSONException{JSONArray all=new JSONArray();for(java.util.List<Motion.Key> curve:m.curves){JSONArray keys=new JSONArray();for(Motion.Key k:curve)keys.put(new JSONArray().put(k.timeMs).put(k.value).put(k.x1).put(k.y1).put(k.x2).put(k.y2));all.put(keys);}return all;}
    private static Motion motion(JSONArray all)throws JSONException{Motion m=new Motion();if(all==null)return m;if(all.length()!=6&&all.length()!=10&&all.length()!=Motion.COUNT)throw new JSONException("Invalid motion channels");for(int p=0;p<all.length();p++){JSONArray keys=all.getJSONArray(p);for(int i=0;i<keys.length();i++){JSONArray a=keys.getJSONArray(i);Motion.Key k=new Motion.Key(a.getLong(0),(float)a.getDouble(1));k.x1=(float)a.getDouble(2);k.y1=(float)a.getDouble(3);k.x2=(float)a.getDouble(4);k.y2=(float)a.getDouble(5);m.curves.get(p).add(k);}}return m;}
    public static EditProject snapshot(EditProject p) {
        try { return decode(encode(p)); } catch(JSONException e) { throw new IllegalStateException(e); }
    }
}

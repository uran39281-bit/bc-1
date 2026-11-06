package com.blackice.bc1.editor;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Stable content identity independent of JSON key iteration order and volatile view position. */
public final class DocumentIdentity {
    private DocumentIdentity(){}
    public static String fingerprint(EditProject project)throws JSONException {
        JSONObject json=new JSONObject(ProjectStore.encode(project));json.remove("playheadMs");
        JSONArray tracks=json.optJSONArray("tracks");if(tracks!=null)for(int i=0;i<tracks.length();i++)tracks.getJSONObject(i).remove("collapsed");
        return hash(canonical(json).getBytes(StandardCharsets.UTF_8));
    }
    private static String canonical(Object value)throws JSONException{
        if(value instanceof JSONObject){JSONObject object=(JSONObject)value;List<String> names=new ArrayList<>();object.keys().forEachRemaining(names::add);Collections.sort(names);StringBuilder out=new StringBuilder("{");for(String name:names){if(out.length()>1)out.append(',');out.append(JSONObject.quote(name)).append(':').append(canonical(object.get(name)));}return out.append('}').toString();}
        if(value instanceof JSONArray){JSONArray array=(JSONArray)value;StringBuilder out=new StringBuilder("[");for(int i=0;i<array.length();i++){if(i>0)out.append(',');out.append(canonical(array.get(i)));}return out.append(']').toString();}
        return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);
    }
    public static String hash(byte[] bytes){try{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
}

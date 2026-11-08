package com.blackice.bc1.editor;

import com.blackice.bc1.core.EffectCatalog;
import java.util.UUID;
import org.json.*;

/** A supported effect and its own source-time intensity track. Other named controls remain static. */
public final class EffectInstance {
    public String id;
    public int type;
    public float intensity=.6f;
    public float[] params;
    public Motion motion;
    public boolean enabled=true;
    public EffectInstance(int type){this.type=type;intensity=EffectCatalog.defaultStrength(type);id=UUID.randomUUID().toString();motion=new Motion();}
    EffectInstance(String id,int type,float intensity,float[] params,Motion motion,boolean enabled){this.id=id;this.type=type;this.intensity=intensity;this.params=params;this.motion=motion;this.enabled=enabled;}
    public float[] resolved(){return EffectCatalog.resolve(type,params);}
    public float strength(long sourceMs){return motion.value(Motion.INTENSITY,sourceMs,intensity);}
    public EffectInstance copy(boolean newIdentity){EffectInstance e=new EffectInstance(type);e.id=newIdentity?UUID.randomUUID().toString():id;e.intensity=intensity;e.params=params==null?null:params.clone();e.motion=motion.copy();e.enabled=enabled;return e;}
    public void validate(){
        if(id==null||id.isEmpty()||id.length()>80||type==0||!EffectCatalog.valid(type)||!Motion.allowed(Motion.INTENSITY,intensity))throw new IllegalArgumentException("Invalid effect instance; original project is preserved.");
        if(params!=null){if(params.length>EffectCatalog.MAX_PARAMS)throw new IllegalArgumentException("Invalid effect parameters");for(float n:params)if(!Float.isFinite(n)||n<0||n>1)throw new IllegalArgumentException("Invalid effect parameter");}
        motion.validate();
    }
    JSONObject json()throws JSONException{
        JSONArray values=new JSONArray();if(params!=null)for(float v:params)values.put(v);
        return new JSONObject().put("id",id).put("type",type).put("intensity",intensity).put("enabled",enabled).put("params",params==null?null:values).put("motion",ProjectStore.motion(motion));
    }
    static EffectInstance read(JSONObject j)throws JSONException{
        EffectInstance e=new EffectInstance(j.getInt("type"));e.id=j.getString("id");e.intensity=(float)j.getDouble("intensity");e.enabled=j.optBoolean("enabled",true);e.motion=ProjectStore.motion(j.optJSONArray("motion"));
        JSONArray p=j.optJSONArray("params");if(p!=null){if(p.length()>EffectCatalog.MAX_PARAMS)throw new JSONException("Too many effect parameters");e.params=new float[p.length()];for(int i=0;i<p.length();i++)e.params[i]=(float)p.getDouble(i);}e.validate();return e;
    }
}

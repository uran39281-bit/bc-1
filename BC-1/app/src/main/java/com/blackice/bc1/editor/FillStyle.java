package com.blackice.bc1.editor;

import java.util.*;
import org.json.*;

/** Non-destructive fill animation in overlay-local milliseconds. Stop membership uses hold keys;
 * scalar/color tracks interpolate only within the same stable stop identity. */
public final class FillStyle {
 public static final int MAX_STOPS=16,MAX_KEYS=200;
 public boolean gradient;
 public final Track solid=new Track(0xFFFFFFFF,true),angle=new Track(0,false);
 public final LinkedHashMap<String,Stop> stops=new LinkedHashMap<>();
 public final List<String> active=new ArrayList<>();
 public final TreeMap<Long,List<String>> structures=new TreeMap<>();
 public static final class Track {
  public double base;public final boolean color;public final TreeMap<Long,Double> keys=new TreeMap<>();
  public Track(double value,boolean color){base=value;this.color=color;}
  public double at(long time){if(keys.isEmpty())return base;Map.Entry<Long,Double> lo=keys.floorEntry(time),hi=keys.ceilingEntry(time);if(lo==null)return keys.firstEntry().getValue();if(hi==null||hi.getKey().equals(lo.getKey()))return lo.getValue();double t=(time-lo.getKey())/(double)(hi.getKey()-lo.getKey());return color?mix((int)(double)lo.getValue(),(int)(double)hi.getValue(),t):lo.getValue()+(hi.getValue()-lo.getValue())*t;}
  public void set(long time,double value){if(!Double.isFinite(value))throw new IllegalArgumentException("Use a finite fill value");if(keys.isEmpty())base=value;else{if(!keys.containsKey(time)&&keys.size()>=MAX_KEYS)throw new IllegalArgumentException("At most 200 fill keys per property");keys.put(time,value);}}
  public void toggle(long time){double value=at(time);if(keys.containsKey(time)){keys.remove(time);if(keys.isEmpty())base=value;}else{if(keys.size()>=MAX_KEYS)throw new IllegalArgumentException("At most 200 fill keys per property");keys.put(time,value);}}
  public void split(long time){double start=at(time);TreeMap<Long,Double> shifted=new TreeMap<>();if(!keys.isEmpty()){shifted.put(0L,start);for(Map.Entry<Long,Double> k:keys.tailMap(time,false).entrySet())shifted.put(k.getKey()-time,k.getValue());}keys.clear();keys.putAll(shifted);}
  JSONObject json()throws JSONException{JSONArray list=new JSONArray();for(Map.Entry<Long,Double> k:keys.entrySet())list.put(new JSONArray().put(k.getKey()).put(k.getValue()));return new JSONObject().put("base",base).put("keys",list);}
  void read(JSONObject j)throws JSONException{if(j==null)return;base=j.getDouble("base");JSONArray array=j.optJSONArray("keys");if(array!=null)for(int i=0;i<array.length();i++){JSONArray k=array.getJSONArray(i);long time=k.getLong(0);if(keys.containsKey(time))throw new JSONException("Duplicate fill key");keys.put(time,k.getDouble(1));}}
  void validate(double min,double max){if(!valid(base,min,max)||keys.size()>MAX_KEYS)throw new IllegalArgumentException("Invalid fill track");for(Map.Entry<Long,Double> k:keys.entrySet())if(k.getKey()<0||!valid(k.getValue(),min,max))throw new IllegalArgumentException("Invalid fill key");}
  boolean valid(double n,double min,double max){return Double.isFinite(n)&&n>=min&&n<=max&&(!color||n==Math.rint(n));}
 }
 public static final class Stop {
  public final String id;public final Track position,color;
  public Stop(String id,double position,int color){this.id=id;this.position=new Track(position,false);this.color=new Track(color,true);}
 }
 public FillStyle(int color){solid.base=color;Stop a=new Stop(UUID.randomUUID().toString(),0,color),b=new Stop(UUID.randomUUID().toString(),1,0xFF40BFFF);stops.put(a.id,a);stops.put(b.id,b);active.add(a.id);active.add(b.id);}
 private FillStyle(){}
 public List<String> ids(long time){if(structures.isEmpty())return new ArrayList<>(active);Map.Entry<Long,List<String>> e=structures.floorEntry(time);return new ArrayList<>((e==null?structures.firstEntry():e).getValue());}
 public List<Stop> ordered(long time){List<Stop> result=new ArrayList<>();for(String id:ids(time))result.add(stops.get(id));result.sort(Comparator.comparingDouble((Stop s)->s.position.at(time)).thenComparing(s->s.id));return result;}
 public boolean animated(){if(!solid.keys.isEmpty()||!angle.keys.isEmpty()||!structures.isEmpty())return true;for(Stop s:stops.values())if(!s.position.keys.isEmpty()||!s.color.keys.isEmpty())return true;return false;}
 private void membership(long time,List<String> value){if(animated()){if(structures.isEmpty())structures.put(0L,new ArrayList<>(active));if(!structures.containsKey(time)&&structures.size()>=MAX_KEYS)throw new IllegalArgumentException("At most 200 stop-structure keys");structures.put(time,new ArrayList<>(value));}else{active.clear();active.addAll(value);}}
 public String add(long time,double position,int color){List<String> ids=ids(time);if(ids.size()>=MAX_STOPS||stops.size()>=128)throw new IllegalArgumentException("At most 16 visible stops and 128 retained stop identities");Stop s=new Stop(UUID.randomUUID().toString(),position,color);stops.put(s.id,s);ids.add(s.id);membership(time,ids);return s.id;}
 public void remove(long time,String id){List<String> ids=ids(time);if(ids.size()<=2)throw new IllegalArgumentException("Keep at least two gradient stops");if(!ids.remove(id))return;membership(time,ids);}
 public List<Track> tracks(){List<Track> all=new ArrayList<>();all.add(solid);all.add(angle);for(Stop s:stops.values()){all.add(s.color);all.add(s.position);}return all;}
 public long neighbor(long time,boolean next,long tolerance){long best=-1;TreeSet<Long> times=new TreeSet<>(structures.keySet());for(Track t:tracks())times.addAll(t.keys.keySet());for(long k:times)if(Math.abs(k-time)>tolerance&&(next?k>time:k<time)&&(best<0||(next?k<best:k>best)))best=k;return best;}
 public void split(long time){List<String> ids=ids(time);TreeMap<Long,List<String>> shifted=new TreeMap<>();if(!structures.isEmpty()){shifted.put(0L,ids);for(Map.Entry<Long,List<String>> k:structures.tailMap(time,false).entrySet())shifted.put(k.getKey()-time,new ArrayList<>(k.getValue()));}structures.clear();structures.putAll(shifted);for(Track t:tracks())t.split(time);}
 public void validate(){solid.validate(Integer.MIN_VALUE,Integer.MAX_VALUE);angle.validate(-360000,360000);if(stops.size()<2||stops.size()>128||structures.size()>MAX_KEYS)throw new IllegalArgumentException("Invalid gradient");checkIds(active);for(Map.Entry<Long,List<String>> e:structures.entrySet()){if(e.getKey()<0)throw new IllegalArgumentException("Invalid stop timing");checkIds(e.getValue());}for(Stop s:stops.values()){if(s.id.isEmpty()||s.id.length()>80)throw new IllegalArgumentException("Invalid stop identity");s.color.validate(Integer.MIN_VALUE,Integer.MAX_VALUE);s.position.validate(0,1);}}
 private void checkIds(List<String> ids){if(ids.size()<2||ids.size()>MAX_STOPS||new HashSet<>(ids).size()!=ids.size()||!stops.keySet().containsAll(ids))throw new IllegalArgumentException("Invalid gradient stop membership");}
 public JSONObject json()throws JSONException{JSONArray array=new JSONArray(),structuresJson=new JSONArray();for(Stop s:stops.values())array.put(new JSONObject().put("id",s.id).put("position",s.position.json()).put("color",s.color.json()));for(Map.Entry<Long,List<String>> e:structures.entrySet())structuresJson.put(new JSONObject().put("time",e.getKey()).put("ids",new JSONArray(e.getValue())));return new JSONObject().put("gradient",gradient).put("solid",solid.json()).put("angle",angle.json()).put("stops",array).put("active",new JSONArray(active)).put("structures",structuresJson);}
 public static FillStyle read(JSONObject j)throws JSONException{if(j==null)return null;FillStyle f=new FillStyle();f.gradient=j.optBoolean("gradient");f.solid.read(j.getJSONObject("solid"));f.angle.read(j.getJSONObject("angle"));JSONArray stops=j.getJSONArray("stops");if(stops.length()>128)throw new JSONException("Too many fill stops");for(int i=0;i<stops.length();i++){JSONObject v=stops.getJSONObject(i);Stop s=new Stop(v.getString("id"),0,0);s.position.read(v.getJSONObject("position"));s.color.read(v.getJSONObject("color"));if(f.stops.put(s.id,s)!=null)throw new JSONException("Duplicate stop identity");}JSONArray active=j.getJSONArray("active");for(int i=0;i<active.length();i++)f.active.add(active.getString(i));JSONArray structures=j.optJSONArray("structures");if(structures!=null)for(int i=0;i<structures.length();i++){JSONObject v=structures.getJSONObject(i);List<String> ids=new ArrayList<>();JSONArray a=v.getJSONArray("ids");for(int n=0;n<a.length();n++)ids.add(a.getString(n));if(f.structures.put(v.getLong("time"),ids)!=null)throw new JSONException("Duplicate structure key");}f.validate();return f;}
 public FillStyle copy(){FillStyle f=new FillStyle();f.gradient=gradient;f.solid.base=solid.base;f.solid.keys.putAll(solid.keys);f.angle.base=angle.base;f.angle.keys.putAll(angle.keys);for(Stop s:stops.values()){Stop n=new Stop(s.id,s.position.base,(int)s.color.base);n.position.keys.putAll(s.position.keys);n.color.keys.putAll(s.color.keys);f.stops.put(n.id,n);}f.active.addAll(active);for(Map.Entry<Long,List<String>> e:structures.entrySet())f.structures.put(e.getKey(),new ArrayList<>(e.getValue()));return f;}
 public static int mix(int a,int b,double t){int result=0;for(int shift:new int[]{0,8,16,24})result|=((int)Math.round(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;return result;}
}

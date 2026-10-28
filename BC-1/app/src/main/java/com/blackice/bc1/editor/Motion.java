package com.blackice.bc1.editor;
import java.util.*;
/** Source-time keyframes: trimming and splitting do not move animation. */
public final class Motion {
 public static final float MAX_SCALE=100f, MAX_HEIGHT=2000f;
 public static final String[] PROPERTIES={"X","Y","Scale","Rotation","Opacity","Intensity","Skew X","Skew Y","Anchor X","Anchor Y","Height","Volume"};
 public static final int X=0,Y=1,SCALE=2,ROTATION=3,OPACITY=4,INTENSITY=5,SKEW_X=6,SKEW_Y=7,ANCHOR_X=8,ANCHOR_Y=9,HEIGHT=10,VOLUME=11,COUNT=12;
 public static final class Key {
  public String id=UUID.randomUUID().toString();
  /** -1 preserves a legacy/custom cubic; 0..3 are exact quadratic presets. */
  public int easingToNext=0;
  public long timeMs; public float value,x1=0,y1=0,x2=1,y2=1;
  public Key(long time,float v){timeMs=time;value=v;}
  public Key copy(){Key k=new Key(timeMs,value);k.id=id;k.easingToNext=easingToNext;k.x1=x1;k.y1=y1;k.x2=x2;k.y2=y2;return k;}
 }
 public final List<List<Key>> curves=new ArrayList<>();
 public Motion(){for(int i=0;i<COUNT;i++)curves.add(new ArrayList<>());}
 public Motion copy(){Motion m=new Motion();for(int i=0;i<COUNT;i++)for(Key k:curves.get(i))m.curves.get(i).add(k.copy());return m;}
 public void put(int property,Key key){List<Key> keys=curves.get(property);keys.removeIf(k->k.timeMs==key.timeMs);keys.add(key);keys.sort(Comparator.comparingLong(k->k.timeMs));}
 public boolean animated(){for(List<Key> keys:curves)if(!keys.isEmpty())return true;return false;}
 public float value(int property,long time,float fallback){List<Key> keys=curves.get(property);if(keys.isEmpty())return fallback;Key a=keys.get(0);if(time<=a.timeMs)return a.value;
  for(int i=1;i<keys.size();i++){Key b=keys.get(i);if(time==b.timeMs)return b.value;if(time<b.timeMs&&b.timeMs>a.timeMs){float t=(float)(time-a.timeMs)/(b.timeMs-a.timeMs);return (float)((double)a.value+((double)b.value-a.value)*ease(t,a));}a=b;}return a.value;
 }
 private static float bez(float t,float a,float b){float q=1-t;return 3*q*q*t*a+3*q*t*t*b+t*t*t;}
 public static float ease(float x,Key k){x=Math.max(0,Math.min(1,x));if(x==0||x==1)return x;int preset=Keyframes.easingOf(k);if(preset==0)return x;if(preset==1)return x*x;if(preset==2)return 1-(1-x)*(1-x);if(preset==3)return x<.5f?2*x*x:1-2*(1-x)*(1-x);float low=0,high=1;for(int i=0;i<20;i++){float t=(low+high)*.5f;if(bez(t,k.x1,k.x2)<x)low=t;else high=t;}return bez((low+high)*.5f,k.y1,k.y2);}
 public static boolean allowed(int p,float v){return Float.isFinite(v)&&(p==VOLUME?v>=0&&v<=1:p==HEIGHT?v>=.0025f&&v<=MAX_HEIGHT:p<2?Math.abs(v)<=100000:p>=8?Math.abs(v)<=2:p==2?v>=.05f&&v<=MAX_SCALE:p==3?true:p>=6?Math.abs(v)<=80:v>=0&&v<=1);}
 public void validate(){for(int p=0;p<COUNT;p++){List<Key> keys=curves.get(p);if(keys.size()>200)throw new IllegalArgumentException("Use at most 200 keys per property.");Set<String> identities=new HashSet<>();long previous=Long.MIN_VALUE;for(Key k:keys){if(!identities.add(k.id)||k.timeMs<=previous||k.id==null||k.id.isEmpty()||k.easingToNext < -1||k.easingToNext>3||!allowed(p,k.value)||!unit(k.x1)||!unit(k.x2)||!unit(k.y1)||!unit(k.y2))throw new IllegalArgumentException("Invalid motion curve.");previous=k.timeMs;}}}
 private static boolean unit(float f){return Float.isFinite(f)&&f>=0&&f<=1;}
}

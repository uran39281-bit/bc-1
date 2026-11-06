package com.blackice.bc1.editor;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class EffectStackTest {
    private EditProject project(){EditProject p=new EditProject();EditProject.Clip c=new EditProject.Clip();c.uri="content://original";c.name="Stack";c.image=true;c.sourceDurationMs=c.outMs=4000;p.clips.add(c);return p;}
    @Test public void limitIsPerLayerAndFourthAddIsAnAtomicNoop()throws Exception{
        EditProject p=project();EditProject.Clip c=p.clips.get(0);for(int type:new int[]{4,9,26})c.addEffect(type);
        String before=ProjectStore.encode(p);try{c.addEffect(25);fail("Fourth effect accepted");}catch(IllegalArgumentException expected){}assertEquals(before,ProjectStore.encode(p));
        EditProject.Clip second=c.copy();p.clips.add(second);second.track=1;p.validate();assertEquals(3,second.effects().size());
        assertNotEquals(c.effects().get(0).id,second.effects().get(0).id);assertNotEquals(c.effects().get(1).id,second.effects().get(1).id);
    }
    @Test public void legacyMigrationKeepsOneInstanceAndPreservesCurvesAcrossTwoReloads()throws Exception{
        EditProject p=project();EditProject.Clip c=p.clips.get(0);c.effect=13;c.intensity=.4f;c.fxEffect=13;c.fx=new float[]{.2f,.8f};Motion.Key key=new Motion.Key(0,.1f);key.easingToNext=-1;key.x1=.2f;key.y1=.4f;c.motion.put(Motion.INTENSITY,key);c.motion.put(Motion.INTENSITY,new Motion.Key(3000,.9f));
        JSONObject legacy=new JSONObject(ProjectStore.encode(p));legacy.put("schema",11);legacy.getJSONArray("clips").getJSONObject(0).remove("effects");
        EditProject migrated=ProjectStore.decode(legacy.toString());String id=migrated.clips.get(0).effects().get(0).id;
        for(int n=0;n<2;n++){migrated=ProjectStore.decode(ProjectStore.encode(migrated));EditProject.Clip after=migrated.clips.get(0);assertEquals(1,after.effects().size());EffectInstance fx=after.effects().get(0);assertEquals(id,fx.id);assertEquals(13,fx.type);assertEquals(.4f,fx.intensity,0);assertEquals(key.id,fx.motion.curves.get(Motion.INTENSITY).get(0).id);for(long t=0;t<4000;t+=71)assertEquals(c.motion.value(Motion.INTENSITY,t,c.intensity),fx.strength(t),0);assertEquals(.2f,fx.resolved()[0],0);}
    }
    @Test public void middleRemovalSnapshotsAndCopiesKeepIndependentIdentityAndTracks()throws Exception{
        EditProject p=project();EditProject.Clip c=p.clips.get(0);String first=c.addEffect(4),middle=c.addEffect(9),last=c.addEffect(26);c.effect(last).motion.put(Motion.INTENSITY,new Motion.Key(100,.75f));
        String before=ProjectStore.encode(p);c.removeEffect(middle);assertEquals(Arrays.asList(first,last),Arrays.asList(c.effects().get(0).id,c.effects().get(1).id));assertEquals(.75f,c.effect(last).strength(100),0);
        p=ProjectStore.decode(before);c=p.clips.get(0);assertEquals(middle,c.effects().get(1).id);EditProject.Clip copy=c.copy();copy.effects().get(2).motion.curves.get(Motion.INTENSITY).get(0).value=.2f;assertEquals(.75f,c.effect(last).strength(100),0);c.removeEffect(first);assertEquals(middle,c.effects().get(0).id);assertEquals(last,c.effects().get(1).id);assertEquals(.75f,c.effect(last).strength(100),0);c.addEffect(25);assertEquals(3,c.effects().size());
    }
    @Test public void oversizedOrUnknownImportedStackIsRejectedWithoutTruncation()throws Exception{
        EditProject p=project();for(int type:new int[]{4,9,26})p.clips.get(0).addEffect(type);JSONObject json=new JSONObject(ProjectStore.encode(p));JSONArray stack=json.getJSONArray("clips").getJSONObject(0).getJSONArray("effects");stack.put(new JSONObject(stack.getJSONObject(0).toString()).put("id","fourth"));try{ProjectStore.decode(json.toString());fail();}catch(JSONException|IllegalArgumentException expected){}stack.remove(3);stack.getJSONObject(2).put("type",999);try{ProjectStore.decode(json.toString());fail();}catch(JSONException|IllegalArgumentException expected){}
    }
    @Test public void attributesCopyAllKeepsAllInstancesAndDoesNotShareAnimation()throws Exception{
        EditProject p=project();EditProject.Clip source=p.clips.get(0);source.inMs=1000;source.sourceDurationMs=source.outMs=5000;for(int type:new int[]{4,9,26})source.addEffect(type);for(EffectInstance e:source.effects()){e.motion.put(Motion.INTENSITY,new Motion.Key(1000,.2f));e.motion.put(Motion.INTENSITY,new Motion.Key(3000,.8f));}
        EditProject.Clip target=project().clips.get(0);target.inMs=2000;target.outMs=target.sourceDurationMs=6000;Attributes attrs=Attributes.decode(Attributes.of(source,Attributes.EFFECTS|Attributes.ANIMATION).encode());attrs.applyTo(target,Attributes.EFFECTS|Attributes.ANIMATION);assertEquals(3,target.effects().size());for(int i=0;i<3;i++){EffectInstance a=source.effects().get(i),b=target.effects().get(i);assertNotEquals(a.id,b.id);assertEquals(.5f,b.strength(3000),1e-6);b.motion.curves.get(Motion.INTENSITY).get(0).value=.1f;assertEquals(.2f,a.strength(1000),0);}
    }
    @Test public void sessionSnapshotsKeepLegacyIdentityAndDocumentFingerprint()throws Exception{
        EditProject p=project();EditProject.Clip c=p.clips.get(0);c.id="upgrade-image";c.effect=12;c.intensity=.5f;c.motion.put(Motion.INTENSITY,new Motion.Key(100,.2f));
        JSONObject legacy=new JSONObject(ProjectStore.encode(p));legacy.put("schema",11);legacy.getJSONArray("clips").getJSONObject(0).remove("effects");
        p=ProjectStore.decode(legacy.toString());String identity="legacy-upgrade-image",fingerprint=DocumentIdentity.fingerprint(p);
        for(int n=0;n<3;n++){
            EditProject snapshot=ProjectStore.snapshot(p);assertEquals(p.id,snapshot.id);assertEquals(p.clips.get(0).id,snapshot.clips.get(0).id);
            assertEquals(identity,snapshot.clips.get(0).effects().get(0).id);assertEquals(fingerprint,DocumentIdentity.fingerprint(snapshot));
            p=ProjectStore.decode(ProjectStore.encode(snapshot));assertEquals(identity,p.clips.get(0).effects().get(0).id);assertEquals(fingerprint,DocumentIdentity.fingerprint(p));
        }
    }
    @Test public void stackedSessionSnapshotsRetainIdsAndDetachEveryMutableTrack()throws Exception{
        EditProject p=project();EditProject.Clip c=p.clips.get(0);for(int type:new int[]{4,9,26})c.addEffect(type);
        for(EffectInstance effect:c.effects()){effect.params=new float[]{.25f};c.updateEffect(effect);effect=c.effect(effect.id);effect.motion.put(Motion.INTENSITY,new Motion.Key(100,.75f));}
        String before=ProjectStore.encode(p),fingerprint=DocumentIdentity.fingerprint(p);
        EditProject snapshot=p.copy();assertEquals(before,ProjectStore.encode(snapshot));assertEquals(fingerprint,DocumentIdentity.fingerprint(snapshot));
        EditProject.Clip detached=snapshot.clips.get(0);
        for(int i=0;i<3;i++){
            EffectInstance original=c.effects().get(i),copy=detached.effects().get(i);assertEquals(original.id,copy.id);
            assertNotSame(original.motion,copy.motion);assertNotSame(original.params,copy.params);assertEquals(original.motion.curves.get(Motion.INTENSITY).get(0).id,copy.motion.curves.get(Motion.INTENSITY).get(0).id);
            copy.params[0]=.9f;copy.motion.curves.get(Motion.INTENSITY).get(0).value=.1f;assertEquals(.25f,original.params[0],0);assertEquals(.75f,original.motion.curves.get(Motion.INTENSITY).get(0).value,0);
        }
        assertEquals(before,ProjectStore.encode(p));EditProject.Clip duplicate=c.copy();assertNotEquals(c.id,duplicate.id);
        for(int i=0;i<3;i++)assertNotEquals(c.effects().get(i).id,duplicate.effects().get(i).id);
    }
    @Test public void fillCustomCurveUsesTheSharedTimingAndRoundTrips()throws Exception{
        FillStyle fill=new FillStyle(0xFFFF0000);fill.solid.toggle(0);fill.solid.set(4000,0xFF0000FF);fill.solid.curve(0,.25f,.1f,.75f,.9f);String id=fill.solid.interval(0).id;FillStyle restored=FillStyle.read(fill.json());Motion.Key key=new Motion.Key(0,0);key.easingToNext=-1;key.x1=.25f;key.y1=.1f;key.x2=.75f;key.y2=.9f;
        for(long t=0;t<=4000;t+=53){double progress=Motion.ease(t/4000f,key);assertEquals(FillStyle.mix(0xFFFF0000,0xFF0000FF,progress),(int)restored.solid.at(t));}assertEquals(id,restored.solid.interval(0).id);assertEquals(-1,restored.solid.interval(0).easing);
    }
}

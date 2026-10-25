package com.blackice.bc1.editor;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class ShapeCatalogTest {
 @Test public void catalogHasUniqueStableIdsAndRequestedShapes(){
  Set<Integer> ids=new HashSet<>();Set<String> names=new HashSet<>();for(int id:ShapeCatalog.ids()){assertTrue(ids.add(id));names.add(EditProject.SHAPES[id]);}
  assertEquals(EditProject.SHAPES.length-1,ids.size());assertEquals(27,ids.size());
  for(String name:new String[]{"Square","Rectangle","Triangle","Circle","Oval","Hexagon","Diamond","Octagon","Rhombus","Star","Heart","Half moon","Cross","Line","Half circle","Arrow","Three-leaf clover","Monkey head"})assertTrue(name,names.contains(name));
  assertEquals("Square",EditProject.SHAPES[1]);assertEquals("Circle",EditProject.SHAPES[2]);assertEquals("Rectangle",EditProject.SHAPES[14]);
 }
 @Test public void placementAndTransformsRoundTripForEveryShape()throws Exception{
  for(int id:ShapeCatalog.ids()){
   EditProject p=new EditProject();EditProject.Overlay o=ShapeCatalog.create(id,2500);p.overlays.add(o);
   assertEquals(5500,o.endMs);assertEquals(1,o.aspect,0);assertEquals(-1,o.heightScale,0);assertEquals(0,o.y,0);
   o.x=.1f;o.rotation=30;o.color=0xFFFF8844;o.motion.put(Motion.OPACITY,new Motion.Key(500,.5f));
   EditProject back=ProjectStore.decode(ProjectStore.encode(p));EditProject.Overlay restored=back.overlays.get(0);
   assertEquals(id,restored.shape);assertEquals(2500,restored.startMs);assertEquals(.1f,restored.x,0);assertEquals(30,restored.rotation,0);assertEquals(o.color,restored.color);assertEquals(1,restored.motion.curves.get(Motion.OPACITY).size());
  }
 }
 @Test public void schemaEightKeepsExistingGeometryAndMedia()throws Exception{
  EditProject p=new EditProject();p.overlays.add(ShapeCatalog.create(14,0));EditProject.Clip c=new EditProject.Clip();c.name="Original image";c.uri="content://source/original";c.image=true;c.outMs=c.sourceDurationMs=3000;p.library.add(c);
  org.json.JSONObject old=new org.json.JSONObject(ProjectStore.encode(p));old.put("schema",8);EditProject back=ProjectStore.decode(old.toString());assertEquals(14,back.overlays.get(0).shape);assertEquals(c.uri,back.library.get(0).uri);
 }
 @Test public void invalidPlacementDoesNotCreateLayer(){for(int id:new int[]{0,-1,28})try{ShapeCatalog.create(id,0);fail();}catch(IllegalArgumentException expected){}try{ShapeCatalog.create(1,-1);fail();}catch(IllegalArgumentException expected){}}
}

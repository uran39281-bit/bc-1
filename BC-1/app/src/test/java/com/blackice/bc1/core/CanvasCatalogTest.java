package com.blackice.bc1.core;
import com.blackice.bc1.editor.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class CanvasCatalogTest {
 @Test public void fiveRatiosRoundTripWithoutChangingCreationDefaults()throws Exception{int[][] expected={{1920,1080},{1080,1920},{1080,1350},{1080,1080},{1440,1080}};for(int i=0;i<CanvasFormat.RATIOS.length;i++){EditProject p=new EditProject();p.setCanvasRatio(CanvasFormat.RATIOS[i]);EditProject copy=ProjectStore.decode(ProjectStore.encode(p));assertEquals(p.ratioName(),copy.ratioName());assertArrayEquals(expected[i],copy.canvasSize(1080));assertEquals(30,copy.frameRate);assertEquals(540,copy.previewHeight);OutputSpec output=OutputSpec.of(copy,1080);assertEquals(expected[i][0],output.width);assertEquals(expected[i][1],output.height);}}
 @Test public void oldBooleanProjectsKeepTheirGeometry()throws Exception{for(boolean portrait:new boolean[]{true,false}){EditProject p=new EditProject();p.portrait=portrait;org.json.JSONObject json=new org.json.JSONObject(ProjectStore.encode(p));json.remove("canvasRatio");EditProject copy=ProjectStore.decode(json.toString());assertArrayEquals(ExportPlanner.size(1080,portrait),copy.canvasSize(1080));assertEquals(portrait,copy.portrait);}}
 @Test public void encoderCapabilityChecksActualRatio(){for(String ratio:CanvasFormat.RATIOS){int[] size=CanvasFormat.size(1080,ratio);ExportPlanner.Plan p=ExportPlanner.choose(1080,30,ratio,VideoCodec.Choice.AUTO,(codec,w,h,fps)->w==size[0]&&h==size[1]&&fps==30);assertEquals(size[0],p.width);assertEquals(size[1],p.height);assertFalse(p.reduced);}}
 @Test public void displayGroupsCoverExistingEffectsExactlyOnce(){Set<Integer> all=new HashSet<>();assertEquals(6,EffectCatalogBrowser.CATEGORIES.length);for(int i=0;i<6;i++)for(int id:EffectCatalogBrowser.entries(i,"")){assertTrue(EffectCatalog.valid(id));assertTrue("Duplicate effect ID",all.add(id));}assertEquals(EffectCatalog.count()-1,all.size());assertEquals(Arrays.asList(1),EffectCatalogBrowser.entries(-1,"gaussian"));assertTrue(EffectCatalogBrowser.entries(0,"gaussian").isEmpty());}
}

package com.blackice.bc1.character;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CharacterMeshTest {
    @Test public void translationAndScalePreserveEveryVertexIncludingTransparentBorder(){
        CharacterMesh m=new CharacterMesh(240,320);float[] joints=CharacterMesh.REST.clone();
        for(int i=0;i<joints.length;i++)joints[i]=(i%2==0?240:320)*joints[i]*.7f+(i%2==0?40:20);
        m.pose(joints);int g=CharacterMesh.GRID;
        for(int y=0;y<=g;y++)for(int x=0;x<=g;x++){
            int i=(y*(g+1)+x)*2;assertEquals(40+x*240f/g*.7f,m.vertices[i],.001);
            assertEquals(20+y*320f/g*.7f,m.vertices[i+1],.001);
        }
    }
    @Test public void raisingOneWristMovesThatSleeveAndLeavesTheOtherInPlace(){
        CharacterMesh m=new CharacterMesh(320,320);float[] joints=CharacterMesh.REST.clone();
        for(int i=0;i<joints.length;i++)joints[i]*=320;
        joints[11]-=60;m.pose(joints);
        int left=(8*33+1)*2,right=(8*33+31)*2;
        assertTrue(m.vertices[left+1]<35);assertEquals(80,m.vertices[right+1],.1);
    }
}

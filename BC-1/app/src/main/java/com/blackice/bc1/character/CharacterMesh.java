package com.blackice.bc1.character;

/** Linear blend skinning of a front-facing, tightly cropped T-pose sprite. Positions are pixel units. */
public final class CharacterMesh {
    public static final int GRID = 32;
    // nose, neck, pelvis, screen-left shoulder/elbow/wrist, right shoulder/elbow/wrist,
    // left hip/knee/ankle, right hip/knee/ankle. Left in the sprite = performer's right.
    static final float[] REST = {.50f,.115f, .50f,.255f, .50f,.55f,
        .36f,.265f, .195f,.265f, .025f,.265f, .64f,.265f,.805f,.265f,.975f,.265f,
        .425f,.55f,.415f,.755f,.405f,.965f,.575f,.55f,.585f,.755f,.595f,.965f};
    private static final int[][] BONES={{1,0},{1,2},{1,3},{3,4},{4,5},{1,6},{6,7},{7,8},{2,9},{9,10},{10,11},{2,12},{12,13},{13,14}};
    private final float[] rest = REST.clone(), weights;
    private final int w,h;
    public final float[] vertices=new float[2*(GRID+1)*(GRID+1)];

    public CharacterMesh(int width,int height) {
        w=width;h=height;
        for(int i=0;i<rest.length;i++) rest[i]*=i%2==0?w:h;
        weights=new float[vertices.length/2*BONES.length];
        for(int v=0;v<vertices.length/2;v++){
            float x=(v%(GRID+1))*w/(float)GRID,y=(v/(GRID+1))*h/(float)GRID,sum=0;
            for(int j=0;j<BONES.length;j++){
                int a=BONES[j][0]*2,b=BONES[j][1]*2;float u=rest[b]-rest[a],s=rest[b+1]-rest[a+1];
                float t=Math.max(0,Math.min(1,((x-rest[a])*u+(y-rest[a+1])*s)/(u*u+s*s)));
                float dx=x-rest[a]-u*t,dy=y-rest[a+1]-s*t;
                float d=(dx*dx+dy*dy)/(h*(float)h)+.001f;
                float weight=1/(d*d*d);weights[v*BONES.length+j]=weight;sum+=weight;
            }
            for(int j=0;j<BONES.length;j++)weights[v*BONES.length+j]/=sum;
        }
    }

    public void pose(float[] joints) {
        for(int v=0;v<vertices.length/2;v++){
            float x=(v%(GRID+1))*w/(float)GRID,y=(v/(GRID+1))*h/(float)GRID,px=0,py=0;
            for(int j=0;j<BONES.length;j++){
                int a=BONES[j][0]*2,b=BONES[j][1]*2;
                float u=rest[b]-rest[a],s=rest[b+1]-rest[a+1],d=u*u+s*s;
                float along=((x-rest[a])*u+(y-rest[a+1])*s)/d,across=((y-rest[a+1])*u-(x-rest[a])*s)/d;
                float dx=joints[b]-joints[a],dy=joints[b+1]-joints[a+1],weight=weights[v*BONES.length+j];
                px+=weight*(joints[a]+along*dx-across*dy);py+=weight*(joints[a+1]+along*dy+across*dx);
            }
            vertices[v*2]=px;vertices[v*2+1]=py;
        }
    }
}

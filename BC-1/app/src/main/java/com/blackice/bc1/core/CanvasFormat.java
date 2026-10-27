package com.blackice.bc1.core;

/** Project geometry, independent of workspace orientation and preview quality. */
public final class CanvasFormat {
    public static final String[] RATIOS={"16:9","9:16","4:5","1:1","4:3"};
    private CanvasFormat(){}
    public static boolean valid(String ratio){for(String r:RATIOS)if(r.equals(ratio))return true;return false;}
    public static float aspect(String ratio){if(!valid(ratio))throw new IllegalArgumentException("Unsupported canvas ratio");String[] n=ratio.split(":");return Float.parseFloat(n[0])/Float.parseFloat(n[1]);}
    public static int[] size(int shortEdge,String ratio){
        if(shortEdge<2||shortEdge%2!=0||shortEdge>4320)throw new IllegalArgumentException("Invalid canvas short edge");
        String[] n=ratio.split(":");if(!valid(ratio))throw new IllegalArgumentException("Unsupported canvas ratio");
        int x=Integer.parseInt(n[0]),y=Integer.parseInt(n[1]);
        // Retain the shipped even-dimension rule, including 16:9 at 540p.
        int longEdge=(shortEdge*Math.max(x,y)/Math.min(x,y)+1)/2*2;
        return x<y?new int[]{shortEdge,longEdge}:new int[]{longEdge,shortEdge};
    }
}

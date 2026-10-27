package com.blackice.bc1.core;
import java.util.*;
/** Display groups only; serialized IDs and the existing processing taxonomy remain unchanged. */
public final class EffectCatalogBrowser {
    public static final String[] CATEGORIES={"Color & Light","Blur & Stylize","Distort & Glitch","Motion & 3D","Patterns & Texture","Mask & Reveal"};
    private static final int[][] IDS={{2,4,6,9,10,11,17,18,19,20,21,22,23,24,25,26,27,28},{1,7,12,16,39,40},{29,30,31,32,33,34,35,36,44,45,46},{13,14,15,37},{8,42,43,47},{3,5,38,41}};
    private EffectCatalogBrowser(){}
    public static List<Integer> entries(int category,String query){String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);List<Integer> out=new ArrayList<>();for(int c=0;c<IDS.length;c++)if(category<0||category==c)for(int id:IDS[c])if(EffectCatalog.name(id).toLowerCase(Locale.ROOT).contains(q))out.add(id);return out;}
}

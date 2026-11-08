package com.blackice.bc1.core;
import java.util.*;
/** Display groups only; serialized IDs and the existing processing taxonomy remain unchanged. See {@link EffectGroups}. */
public final class EffectCatalogBrowser {
    public static final String[] CATEGORIES=EffectGroups.CATEGORIES;
    private EffectCatalogBrowser(){}
    public static List<EffectGroups.Entry> entries(int category,String query){return EffectGroups.entries(category,query);}
}

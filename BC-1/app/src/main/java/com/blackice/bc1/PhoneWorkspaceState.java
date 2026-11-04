package com.blackice.bc1;

import android.os.Bundle;

/** Presentation state, deliberately separate from the project and undo history. */
final class PhoneWorkspaceState {
    enum Drawer { BASE, ADD_MEDIA, SELECTED_LAYER }
    Drawer drawer=Drawer.BASE;
    boolean dismissed;
    String selection="",drawerOwner="";
    int firstMedia,mediaOffset,drawerScroll,timelineScroll;
    android.os.Parcelable mediaViewState;
    boolean restoreDrawerScroll;
    void save(Bundle out){out.putParcelable("phone-media-view-state",mediaViewState);out.putString("phone-drawer-owner",drawerOwner);out.putString("phone-drawer",drawer.name());out.putBoolean("phone-dismissed",dismissed);out.putString("phone-selection",selection);out.putInt("phone-media-first",firstMedia);out.putInt("phone-media-offset",mediaOffset);out.putInt("phone-drawer-scroll",drawerScroll);out.putInt("phone-timeline-scroll",timelineScroll);}
    void restore(Bundle in){if(in==null)return;mediaViewState=in.getParcelable("phone-media-view-state");restoreDrawerScroll=true;drawerOwner=in.getString("phone-drawer-owner","");try{drawer=Drawer.valueOf(in.getString("phone-drawer","BASE"));}catch(IllegalArgumentException ignored){}dismissed=in.getBoolean("phone-dismissed");selection=in.getString("phone-selection","");firstMedia=in.getInt("phone-media-first");mediaOffset=in.getInt("phone-media-offset");drawerScroll=in.getInt("phone-drawer-scroll");timelineScroll=in.getInt("phone-timeline-scroll");}
}

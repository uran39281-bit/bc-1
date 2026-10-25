package com.blackice.bc1;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.util.*;

/** One device browser, shared by all import entry points. Project Media remains the imported bin. */
final class MediaImportWindow {
    final EditorActivity a;
    final DeviceMedia gallery;
    PopupWindow window;
    LinearLayout content,grid,albums,filters;
    ScrollView scroll;
    TextView status;
    String query="",album="All albums",loadError;
    int filter,scrollY;
    boolean loading,restoreOpen;
    List<DeviceMedia.Item> items;
    volatile int generation;
    String accessState="";
    void resume(){String now=gallery.access(false)+":"+gallery.access(true);if(!now.equals(accessState)){accessState=now;refresh();}}
    MediaImportWindow(EditorActivity a){this.a=a;gallery=new DeviceMedia(a);}
    boolean showing(){return window!=null&&window.isShowing();}
    void save(Bundle b){b.putBoolean("browse-open",showing());b.putString("browse-query",query);b.putString("browse-album",album);b.putInt("browse-filter",filter);b.putInt("browse-scroll",scroll==null?scrollY:scroll.getScrollY());}
    void restore(Bundle b){if(b==null)return;restoreOpen=b.getBoolean("browse-open");query=b.getString("browse-query","");album=b.getString("browse-album","All albums");filter=b.getInt("browse-filter");scrollY=b.getInt("browse-scroll");}
    void close(){if(scroll!=null)scrollY=scroll.getScrollY();if(window!=null)window.dismiss();generation++;}
    void reposition(){if(showing()){close();a.getWindow().getDecorView().post(this::show);}}
    void show(){
        if(a.isFinishing()||a.isDestroyed()||showing())return;
        content=a.column();content.setPadding(a.dp(14),a.dp(10),a.dp(14),a.dp(10));content.setBackground(a.panelBackground());content.setContentDescription("Browse files window");
        LinearLayout heading=a.row();heading.setGravity(Gravity.CENTER_VERTICAL);heading.addView(a.label("Browse files",18,EditorActivity.TEXT,true),new LinearLayout.LayoutParams(0,a.dp(48),1));
        heading.addView(a.compactButton("×","Close Browse files",v->close()),new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));content.addView(heading);
        content.addView(a.note("Choose files to add to Project Media, then drag them onto the timeline."));
        TextView files=a.button("Browse files…",v->a.pick(EditorActivity.IMPORT,"*/*"));files.setContentDescription("Open Android system file picker");content.addView(files,new LinearLayout.LayoutParams(-1,a.dp(48)));
        EditText search=new EditText(a);search.setSingleLine(true);search.setTextColor(EditorActivity.TEXT);search.setHintTextColor(EditorActivity.MUTED);search.setHint("Search device media…");search.setContentDescription("Search device media");search.setText(query);content.addView(search,new LinearLayout.LayoutParams(-1,a.dp(48)));
        filters=a.row();content.addView(filters);String[] names={"All","Video","Audio","Image"};
        for(int i=0;i<names.length;i++){final int n=i;TextView tab=a.button(names[i],v->{filter=n;album="All albums";scrollY=0;render();});tab.setTextSize(12);tab.setContentDescription("Browse filter: "+names[i]);filters.addView(tab,new LinearLayout.LayoutParams(0,a.dp(48),1));}
        HorizontalScrollView folders=new HorizontalScrollView(a);folders.setHorizontalScrollBarEnabled(false);albums=a.row();folders.addView(albums);content.addView(folders);
        scroll=new ScrollView(a);grid=a.column();grid.setContentDescription("Device media results");scroll.addView(grid);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=a.note("");status.setContentDescription("Media import status");content.addView(status);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();scrollY=0;renderGrid();}public void afterTextChanged(Editable e){}});
        View decor=a.getWindow().getDecorView();Rect frame=new Rect();decor.getWindowVisibleDisplayFrame(frame);int[] origin=new int[2];decor.getLocationOnScreen(origin);
        int left=frame.left-origin[0],top=frame.top-origin[1],available=frame.width(),height=frame.height(),gap=a.dp(12),width=Math.min(a.dp(440),available-gap*2),x=left+available-width-gap;
        if(a.mediaBrowser!=null&&a.mediaBrowser.isShown()){int[] anchor=new int[2];a.mediaBrowser.getLocationOnScreen(anchor);int beside=anchor[0]-origin[0]+a.mediaBrowser.getWidth()+gap;
            if(left+available-beside>=a.dp(300)){x=beside;width=Math.min(a.dp(440),left+available-x-gap);}}
        int popupHeight=Math.min(a.dp(700),height-gap*2);int y=top+gap;
        if(!a.wideLayout){width=available-gap*2;x=left+gap;popupHeight=Math.min(popupHeight,Math.round(height*.82f));y=top+height-popupHeight-gap;}
        window=new PopupWindow(content,width,popupHeight,true);window.setBackgroundDrawable(a.panelBackground());window.setElevation(a.dp(18));window.setOutsideTouchable(true);window.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        window.setOnDismissListener(()->{if(scroll!=null)scrollY=scroll.getScrollY();generation++;});window.showAtLocation(decor,Gravity.TOP|Gravity.LEFT,x,y);
        content.setFocusableInTouchMode(true);content.requestFocus();render();if(items==null)load();
    }
    void refresh(){items=null;if(showing()){render();load();}}
    void load(){if(loading)return;loading=true;loadError=null;renderGrid();a.io.execute(()->{List<DeviceMedia.Item> found=new ArrayList<>();String error=null;
        try{if(!gallery.access(false).equals("none"))found.addAll(gallery.query(false,300));if(!gallery.access(true).equals("none"))found.addAll(gallery.query(true,300));}catch(RuntimeException e){error="Device media could not be loaded. Use Browse files to choose a file.";}
        String problem=error;a.main.post(()->{if(a.isDestroyed())return;loading=false;items=found;loadError=problem;if(showing())render();});});}
    boolean matches(DeviceMedia.Item i){return (filter==0||filter==1&&i.video||filter==2&&i.audio||filter==3&&!i.video&&!i.audio)&&i.name.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));}
    void render(){if(!showing())return;for(int i=0;i<filters.getChildCount();i++){TextView tab=(TextView)filters.getChildAt(i);tab.setSelected(filter==i);tab.setBackground(a.buttonBackground(filter==i));tab.setTextColor(filter==i?EditorActivity.CYAN:EditorActivity.TEXT);}
        albums.removeAllViews();List<DeviceMedia.Item> matching=new ArrayList<>();if(items!=null)for(DeviceMedia.Item i:items)if(matches(i))matching.add(i);List<String> names=DeviceMedia.albums(matching);names.add(0,"All albums");
        if(!names.contains(album))album="All albums";
        for(String folder:names){TextView chip=a.button(folder,v->{album=folder;scrollY=0;render();});chip.setBackground(a.buttonBackground(folder.equals(album)));chip.setSelected(folder.equals(album));chip.setTextSize(11);chip.setContentDescription("Browse album: "+folder);albums.addView(chip,new LinearLayout.LayoutParams(-2,a.dp(44)));}renderGrid();}
    void renderGrid(){if(grid==null)return;int token=++generation;grid.removeAllViews();
        if(loading){grid.addView(a.note("Loading device media…"));return;}
        if(gallery.access(false).equals("none")||gallery.access(true).equals("none")){
            grid.addView(a.note("Browse files works without gallery access. You can also allow access to browse thumbnails here."));
            if(gallery.access(false).equals("none"))grid.addView(a.button("Allow photos and videos",v->a.requestPermissions(DeviceMedia.visualPermissions(),EditorActivity.MEDIA_ACCESS)));
            if(gallery.access(true).equals("none"))grid.addView(a.button("Allow audio",v->a.requestPermissions(DeviceMedia.audioPermissions(),EditorActivity.MEDIA_ACCESS)));
        }else grid.addView(a.button("Manage media access",v->a.openAppSettings()));
        List<DeviceMedia.Item> shown=new ArrayList<>();if(items!=null)for(DeviceMedia.Item i:items)if(matches(i)&&(album.equals("All albums")||album.equals(i.album)))shown.add(i);
        if(shown.isEmpty())grid.addView(a.note(query.isBlank()?"No device media to show. Use Browse files to choose local or cloud files.":"No matching files. Clear the search to see all media."));
        if(!query.isBlank())grid.addView(a.button("Clear search",v->{query="";close();show();}));
        int count=Math.min(shown.size(),90),cols=filter==2?1:3;
        for(int start=0;start<count;start+=cols){LinearLayout row=a.row();for(int j=0;j<cols;j++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(cols==1?58:112),1);lp.setMargins(a.dp(3),a.dp(3),a.dp(3),a.dp(3));row.addView(start+j<count?card(shown.get(start+j),token):new View(a),lp);}grid.addView(row);}
        if(shown.size()>90)grid.addView(a.note("Showing the first 90 matches. Narrow the search or choose an album."));
        scroll.post(()->{if(showing())scroll.scrollTo(0,scrollY);});updateStatus();
    }
    View card(DeviceMedia.Item item,int token){LinearLayout card=a.column();card.setBackground(a.buttonBackground(false));card.setPadding(a.dp(4),a.dp(4),a.dp(4),a.dp(4));card.setFocusable(true);card.setContentDescription("Import "+item.name+" to Project Media");
        if(!item.audio){ImageView image=new ImageView(a);image.setScaleType(ImageView.ScaleType.CENTER_CROP);Bitmap cached=gallery.cached(item.uri);if(cached!=null)image.setImageBitmap(cached);else{image.setImageDrawable(a.icon("Media",EditorActivity.MUTED));a.io.execute(()->{if(generation!=token)return;Bitmap thumb=gallery.thumbnail(item,256);if(thumb!=null)a.main.post(()->{if(showing()&&generation==token)image.setImageBitmap(thumb);});});}FrameLayout thumb=new FrameLayout(a);thumb.addView(image,new FrameLayout.LayoutParams(-1,-1));if(item.video){TextView badge=a.label("▶ "+EditorActivity.clock(item.durationMs),9,EditorActivity.TEXT,true);badge.setBackgroundColor(0xAA08131F);badge.setPadding(a.dp(4),a.dp(2),a.dp(4),a.dp(2));thumb.addView(badge,new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START));}card.addView(thumb,new LinearLayout.LayoutParams(-1,0,1));}
        TextView name=a.label((item.audio?"♫ ":"")+item.name,11,EditorActivity.TEXT,false);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name);
        card.setOnClickListener(v->{scrollY=scroll.getScrollY();a.importUris(List.of(item.uri),item.audio?EditorActivity.AUDIO_LAYER:EditorActivity.IMPORT);updateStatus();});return card;
    }
    void updateStatus(){if(!showing()||status==null)return;String text=a.importing?"Importing media…":a.message.startsWith("Import issue:")?a.message:loadError!=null?loadError:a.project.library.size()+" items in Project Media";if(!text.contentEquals(status.getText()))status.setText(text);}
}

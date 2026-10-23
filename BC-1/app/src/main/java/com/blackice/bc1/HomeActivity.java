package com.blackice.bc1;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.blackice.bc1.editor.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Native, window-responsive project browser. The editor remains the sole editing workspace. */
public final class HomeActivity extends Activity {
    private static final int BG=0xff050f1b, PANEL=0xff0b1b2c, BORDER=0xff203e5b,
            CYAN=0xff19baff, TEXT=0xffedf7ff, MUTED=0xff91afd2, IMPORT=701;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private ProjectStore store;
    private ThumbnailCache thumbnails;
    private java.util.List<ProjectStore.BrowserEntry> entries=new ArrayList<>();
    private String query="", destination="Home", failure;
    private boolean byName, loading=true, busy, searchVisible, wide;
    private int generation, scrollY, anchorOffset;
    private String anchorId;
    private final Map<String,Integer> scrolls=new HashMap<>();
    private final Map<String,String> anchors=new HashMap<>();
    private final Map<String,Integer> offsets=new HashMap<>();
    private ScrollView scroll;
    private LinearLayout content, list;
    private TextView state;
    private EditText search;
    private ProgressBar progress;
    private int renderedWidth;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        store=new ProjectStore(this); thumbnails=new ThumbnailCache(this);
        if(saved!=null){query=saved.getString("query","");destination=saved.getString("destination","Home");
            byName=saved.getBoolean("byName");searchVisible=saved.getBoolean("searchVisible");
            scrollY=saved.getInt("scroll");anchorId=saved.getString("anchor");anchorOffset=saved.getInt("offset");}
        build();
    }
    @Override protected void onResume(){super.onResume();refresh();}
    @Override protected void onDestroy(){io.shutdown();thumbnails.close();super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle out){captureScroll();out.putString("query",query);out.putString("destination",destination);
        out.putBoolean("byName",byName);out.putBoolean("searchVisible",searchVisible);out.putInt("scroll",scrollY);
        out.putString("anchor",anchorId);out.putInt("offset",anchorOffset);super.onSaveInstanceState(out);}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);captureScroll();build();}
    private void captureScroll(){
        if(loading||scroll==null||list==null)return;scrollY=scroll.getScrollY();anchorId=null;
        for(int i=0;i<list.getChildCount();i++){View row=list.getChildAt(i);int top=list.getTop()+row.getTop();
            if(top+row.getHeight()>scrollY){Object tag=row.getTag();if(tag instanceof String){anchorId=(String)tag;anchorOffset=top-scrollY;}break;}}
    }
    private boolean useWide(int width,int height){
        String mode=getSharedPreferences(EditorActivity.PREFS,MODE_PRIVATE).getString("mode","AUTO");
        if("PHONE".equals(mode))return false;
        return width>=680 && ("WIDE".equals(mode)||width>height||width>=900);
    }
    private void build(){
        int width=getResources().getConfiguration().screenWidthDp,height=getResources().getConfiguration().screenHeightDp;
        wide=useWide(width,height);renderedWidth=width;list=null;
        LinearLayout root=row();root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff071b31,BG,0xff040c15}));
        root.setFitsSystemWindows(true);
        if(wide){LinearLayout rail=column();rail.setPadding(dp(12),dp(20),dp(12),dp(12));rail.setBackgroundColor(0xaa071321);
            rail.addView(branding(true),new LinearLayout.LayoutParams(-1,dp(130)));
            for(String item:new String[]{"Home","Projects","Settings"})rail.addView(nav(item,true),new LinearLayout.LayoutParams(-1,dp(56)));
            root.addView(rail,new LinearLayout.LayoutParams(dp(width<900?164:200),-1));}
        LinearLayout page=column();root.addView(page,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(16),dp(8),dp(12),dp(8));
        header.addView(wide?text(destination,28,TEXT,true):branding(false),new LinearLayout.LayoutParams(0,dp(64),1));
        header.addView(icon("Help","Help",v->help()),new LinearLayout.LayoutParams(dp(48),dp(48)));
        header.addView(icon("Search","Search projects",v->{searchVisible=!searchVisible;if(!searchVisible)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(),0);captureScroll();build();if(searchVisible){search.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,InputMethodManager.SHOW_IMPLICIT);}}),new LinearLayout.LayoutParams(dp(48),dp(48)));
        page.addView(header);
        if(searchVisible){LinearLayout searchRow=row();searchRow.setPadding(dp(16),0,dp(16),dp(8));search=new EditText(this);search.setSingleLine();search.setTextColor(TEXT);search.setTextSize(16);search.setHintTextColor(MUTED);search.setHint("Search projects by name");search.setContentDescription("Search projects by name");search.setText(query);
            searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
            searchRow.addView(action("Clear",v->{query="";search.setText("");}),new LinearLayout.LayoutParams(dp(64),dp(48)));page.addView(searchRow);
            search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){query=s.toString();scrollY=0;anchorId=null;renderList();}public void afterTextChanged(Editable e){}});}
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(busy?View.VISIBLE:View.GONE);page.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        scroll=new ScrollView(this);scroll.setFillViewport(true);content=column();content.setPadding(dp(16),dp(8),dp(16),dp(16));scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(destination.equals("Settings"))buildSettings();else{
            if(destination.equals("Home")){
                LinearLayout actions=row();int cardHeight=wide?Math.min(190,Math.max(128,height/3)):142;
                LinearLayout.LayoutParams newLp=new LinearLayout.LayoutParams(0,dp(cardHeight),2);newLp.rightMargin=dp(10);
                actions.addView(createCard(false),newLp);actions.addView(createCard(true),new LinearLayout.LayoutParams(0,dp(cardHeight),1));content.addView(actions);}
            LinearLayout heading=row();heading.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams hLp=new LinearLayout.LayoutParams(-1,dp(48));hLp.topMargin=dp(8);content.addView(heading,hLp);
            heading.addView(text(destination.equals("Home")?"Recent projects":"All projects",wide?25:22,TEXT,true),new LinearLayout.LayoutParams(0,-1,1));
            if(destination.equals("Home"))heading.addView(action("View all  ›",v->navigate("Projects")),new LinearLayout.LayoutParams(-2,dp(48)));
            TextView sort=action(byName?"Project name  ▾":"Last edited  ▾",v->{PopupMenu menu=new PopupMenu(this,v);menu.getMenu().add("Last edited");menu.getMenu().add("Project name");menu.setOnMenuItemClickListener(item->{byName=item.getTitle().equals("Project name");scrollY=0;anchorId=null;build();return true;});menu.show();});sort.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);sort.setTextColor(MUTED);content.addView(sort,new LinearLayout.LayoutParams(-2,dp(36)));
            list=column();content.addView(list);renderList();}
        if(!wide){LinearLayout bottom=row();bottom.setPadding(dp(8),dp(4),dp(8),dp(4));bottom.setBackgroundColor(0xff071522);
            for(String item:new String[]{"Home","Projects","Settings"})bottom.addView(nav(item,false),new LinearLayout.LayoutParams(0,dp(64),1));page.addView(bottom);}
        setContentView(root);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int now=Math.round((r-l)/getResources().getDisplayMetrics().density);if(now>0&&Math.abs(now-renderedWidth)>4){renderedWidth=now;main.post(()->{if(!isDestroyed()){captureScroll();build();}});}});
    }
    private void navigate(String next){captureScroll();scrolls.put(destination,scrollY);anchors.put(destination,anchorId);offsets.put(destination,anchorOffset);
        destination=next;scrollY=scrolls.getOrDefault(next,0);anchorId=anchors.get(next);anchorOffset=offsets.getOrDefault(next,0);build();}
    private View branding(boolean stacked){LinearLayout box=stacked?column():row();box.setGravity(Gravity.CENTER_VERTICAL|(stacked?Gravity.CENTER_HORIZONTAL:Gravity.START));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.ic_black_ice);logo.setContentDescription("Black Ice logo");box.addView(logo,new LinearLayout.LayoutParams(dp(stacked?64:44),dp(stacked?64:52)));
        LinearLayout words=column();words.setGravity(Gravity.CENTER_VERTICAL);TextView name=text("BLACK ICE",stacked?22:24,TEXT,true);name.setSingleLine(true);words.addView(name);TextView sub=text("V I D E O  E D I T O R",stacked?8:9,MUTED,false);sub.setSingleLine(true);words.addView(sub);box.addView(words,new LinearLayout.LayoutParams(-2,-2));return box;}
    private View nav(String name,boolean sidebar){TextView t=action(name,v->navigate(name));boolean selected=name.equals(destination);t.setTextColor(selected?CYAN:MUTED);t.setTextSize(16);t.setGravity(Gravity.CENTER);
        Drawable glyph=new V1Icon(name.equals("Projects")?"Folder":name,selected?CYAN:MUTED);glyph.setBounds(0,0,dp(26),dp(26));if(sidebar)t.setCompoundDrawables(glyph,null,null,null);else t.setCompoundDrawables(null,glyph,null,null);t.setCompoundDrawablePadding(dp(5));t.setBackground(interactive(selected?0xff0d2a48:0x00000000));t.setSelected(selected);return t;}
    private View createCard(boolean importing){LinearLayout card=column();card.setGravity(Gravity.CENTER);card.setPadding(dp(8),dp(12),dp(8),dp(12));card.setBackground(new WaveDrawable(!importing));
        ImageView icon=new ImageView(this);icon.setImageDrawable(new V1Icon(importing?"ImportProject":"NewProject",TEXT));card.addView(icon,new LinearLayout.LayoutParams(dp(wide?68:54),dp(wide?68:54)));
        TextView label=text(importing?"Import project":"New project",wide?23:18,TEXT,true);label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setHorizontallyScrolling(false);label.setAutoSizeTextTypeUniformWithConfiguration(12,wide?23:18,1,android.util.TypedValue.COMPLEX_UNIT_SP);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);card.addView(label,lp);
        clickable(card,importing?"Import project":"New project",v->{if(busy)return;if(importing)pickImport();else newProject();});return card;}
    private void refresh(){if(busy)return;captureScroll();final int token=++generation;loading=true;failure=null;renderList();io.execute(()->{
        try{java.util.List<ProjectStore.BrowserEntry> loaded=store.browser();main.post(()->{if(isDestroyed()||token!=generation)return;entries=loaded;loading=false;renderList();});}
        catch(Exception e){main.post(()->{if(isDestroyed()||token!=generation)return;loading=false;failure=e.getMessage();renderList();});}});}
    private void renderList(){if(list==null||destination.equals("Settings"))return;list.removeAllViews();
        if(loading){message("Loading projects…",null,null);return;}if(failure!=null){message("Could not load projects. "+failure,"Try again",v->refresh());return;}
        java.util.List<ProjectStore.BrowserEntry> matches=new ArrayList<>();for(ProjectStore.BrowserEntry e:entries)if(e.name.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))matches.add(e);
        if(byName)matches.sort((a,b)->a.name.compareToIgnoreCase(b.name));else matches.sort((a,b)->Long.compare(b.edited,a.edited));
        if(matches.isEmpty()){if(!query.isEmpty())message("No projects match “"+query+"”.","Clear search",v->{query="";if(search!=null)search.setText("");else renderList();});else message("Your next story starts here.\nCreate your first Black Ice project.","New project",v->newProject());return;}
        int available=getResources().getConfiguration().screenWidthDp-(wide?(getResources().getConfiguration().screenWidthDp<900?164:200):0)-32;
        int columns=wide?Math.max(1,Math.min(3,(available+12)/252)):1;
        int count=destination.equals("Home")&&query.isEmpty()?Math.min(matches.size(),12):matches.size();
        for(int i=0;i<count;i+=columns){LinearLayout row=row();row.setTag(matches.get(i).id);LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.bottomMargin=dp(12);list.addView(row,rowLp);
            for(int c=0;c<columns;c++){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(c>0)lp.leftMargin=dp(12);if(i+c<count)row.addView(projectCard(matches.get(i+c),wide),lp);else row.addView(new View(this),lp);}}
        restoreScroll();
    }
    private void restoreScroll(){scroll.post(()->{if(isDestroyed())return;int target=scrollY;if(anchorId!=null){for(int i=0;i<list.getChildCount();i++){View row=list.getChildAt(i);boolean matches=anchorId.equals(row.getTag());if(row instanceof android.view.ViewGroup)for(int j=0;j<((android.view.ViewGroup)row).getChildCount();j++)matches|=("project:"+anchorId).equals(((android.view.ViewGroup)row).getChildAt(j).getTag());if(matches){target=list.getTop()+row.getTop()-anchorOffset;break;}}}scroll.scrollTo(0,target);});}
    private void message(String value,String button,View.OnClickListener listener){LinearLayout box=column();box.setPadding(dp(16),dp(32),dp(16),dp(32));TextView label=text(value,17,MUTED,false);label.setGravity(Gravity.CENTER);box.addView(label);if(button!=null)box.addView(action(button,listener),new LinearLayout.LayoutParams(-1,dp(56)));list.addView(box);}
    private View projectCard(ProjectStore.BrowserEntry entry,boolean grid){
        EditProject p=entry.project;LinearLayout card=grid?column():row();card.setPadding(dp(8),dp(8),dp(8),dp(8));card.setGravity(Gravity.CENTER_VERTICAL);card.setBackground(interactive(PANEL));
        clickable(card,"Open "+entry.name,v->open(entry));card.setTag("project:"+entry.id);
        FrameLayout thumb=new FrameLayout(this);thumb.setBackground(round(0xff071321,12));thumb.setClipToOutline(true);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageDrawable(new V1Icon("Media",BORDER));thumb.addView(image,new FrameLayout.LayoutParams(-1,-1));
        if(p!=null){EditProject.Clip first=null;for(EditProject.Clip c:p.clips)if(!c.audioOnly){first=c;break;}if(first==null)for(EditProject.Clip c:p.library)if(!c.audioOnly){first=c;break;}
            if(first!=null){EditProject.Clip source=first;Bitmap cached=thumbnails.get(source,()->{Bitmap ready=thumbnails.peek(source);if(ready!=null)image.setImageBitmap(ready);});if(cached!=null)image.setImageBitmap(cached);}}
        TextView badge=text(p==null?"!":clock(p.durationMs()),12,TEXT,true);badge.setPadding(dp(5),dp(2),dp(5),dp(2));badge.setBackground(round(0xdd020810,6));FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);badgeLp.setMargins(0,0,dp(5),dp(5));thumb.addView(badge,badgeLp);
        int available=getResources().getConfiguration().screenWidthDp-(wide?(getResources().getConfiguration().screenWidthDp<900?164:200):0)-32;
        int cols=wide?Math.max(1,Math.min(3,(available+12)/252)):1;
        card.addView(thumb,new LinearLayout.LayoutParams(grid?-1:dp(126),dp(grid?Math.min(160,(available/cols-16)*9/16):88)));
        LinearLayout details=column();details.setPadding(dp(grid?4:12),dp(grid?8:0),0,0);card.addView(details,new LinearLayout.LayoutParams(grid?-1:0,-2,grid?0:1));
        LinearLayout title=row();title.setGravity(Gravity.CENTER_VERTICAL);TextView name=text(entry.name,18,TEXT,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);title.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        title.addView(icon("More","Actions for "+entry.name,v->projectActions(entry,v)),new LinearLayout.LayoutParams(dp(48),dp(48)));details.addView(title);
        if(p!=null){TextView metadata=text((p.portrait?"9:16":"16:9")+"  •  1080p  •  "+p.frameRate+" fps",12,MUTED,false);details.addView(metadata);
            details.addView(text("Edited "+android.text.format.DateUtils.getRelativeTimeSpanString(entry.edited,System.currentTimeMillis(),60000),12,MUTED,false));}
        else details.addView(text(entry.error,12,0xffffbd8a,false));return card;
    }
    private void projectActions(ProjectStore.BrowserEntry e,View anchor){PopupMenu menu=new PopupMenu(this,anchor);for(String a:new String[]{"Open","Rename","Duplicate","Delete"}){android.view.MenuItem item=menu.getMenu().add(a);if(e.project==null&&!a.equals("Delete"))item.setEnabled(false);}
        menu.setOnMenuItemClickListener(item->{if(busy)return true;switch(item.getTitle().toString()){
            case "Open":open(e);break;case "Rename":rename(e);break;case "Duplicate":work("Duplicating project…",()->{store.duplicate(e.id);return null;},false);break;
            case "Delete":new AlertDialog.Builder(this).setTitle("Delete “"+e.name+"”?").setMessage("This deletes the saved project and its recovery records. Your original videos, photos and audio are kept.").setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->work("Deleting project…",()->{store.delete(e.id);if(e.id.equals(getSharedPreferences(EditorActivity.PREFS,MODE_PRIVATE).getString("last-project",null)))getSharedPreferences(EditorActivity.PREFS,MODE_PRIVATE).edit().remove("last-project").apply();return null;},false)).show();break;}return true;});menu.show();}
    private void rename(ProjectStore.BrowserEntry e){EditText input=field(e.name);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Rename project").setView(input).setNegativeButton("Cancel",null).setPositiveButton("Rename",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String name=input.getText().toString().trim();if(!validName(input,name))return;dialog.dismiss();work("Renaming project…",()->{store.rename(e.id,name);return null;},false);}));dialog.show();}
    private void newProject(){if(busy)return;EditProject defaults=new EditProject();LinearLayout form=column();form.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText name=field(defaults.name);form.addView(text("Project name",14,MUTED,false));form.addView(name);
        Spinner ratio=spinner(form,"Aspect ratio",new String[]{"16:9 • Landscape","9:16 • Portrait"},defaults.portrait?1:0);
        spinner(form,"Project resolution",new String[]{"1080p canvas • 1920×1080 or 1080×1920"},0);
        int[] rates=com.blackice.bc1.core.ExportPlanner.FRAME_RATES;String[] labels=new String[rates.length];int selected=0;for(int i=0;i<rates.length;i++){labels[i]=rates[i]+" fps";if(rates[i]==defaults.frameRate)selected=i;}
        Spinner fps=spinner(form,"Frame rate",labels,selected);form.addView(text("Preview quality and export resolution are configured separately in the editor.",12,MUTED,false));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("New project").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Create",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{String title=name.getText().toString().trim();if(!validName(name,title))return;
            defaults.name=title;defaults.portrait=ratio.getSelectedItemPosition()==1;defaults.frameRate=rates[fps.getSelectedItemPosition()];dialog.dismiss();work("Creating project…",()->{store.save(defaults);return defaults;},true);}));dialog.show();}
    private boolean validName(EditText input,String name){if(name.isBlank()||name.length()>120){input.setError("Use 1–120 characters");return false;}return true;}
    private void pickImport(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(intent,IMPORT);}catch(ActivityNotFoundException e){error("No system document picker is installed.");}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=IMPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();work("Validating and importing project bundle…",()->{try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("The selected file cannot be read.");return ProjectBundle.read(this,in);}},true);}
    private interface Operation{EditProject run()throws Exception;}
    private void work(String label,Operation operation,boolean open){if(busy)return;busy=true;++generation;progress.setVisibility(View.VISIBLE);Toast.makeText(this,label,Toast.LENGTH_SHORT).show();io.execute(()->{try{EditProject result=operation.run();main.post(()->{if(isDestroyed())return;busy=false;progress.setVisibility(View.GONE);refresh();if(open&&result!=null)launch(result.id,false);});}catch(Exception e){main.post(()->{if(isDestroyed())return;busy=false;progress.setVisibility(View.GONE);error(e.getMessage()==null?"The project operation could not be completed.":e.getMessage());refresh();});}});}
    private void open(ProjectStore.BrowserEntry entry){if(busy)return;if(entry.project==null){error(entry.error);return;}
        work("Checking project media…",()->{EditProject p=store.load(entry.id);java.util.Set<String> sources=new HashSet<>();for(EditProject.Clip c:p.library)sources.add(c.uri);for(EditProject.Clip c:p.clips)sources.add(c.uri);for(EditProject.Overlay o:p.overlays)if(o.imageUri!=null)sources.add(o.imageUri);if(p.music!=null)sources.add(p.music.uri);
            int missing=0;for(String source:sources)try(android.content.res.AssetFileDescriptor descriptor=getContentResolver().openAssetFileDescriptor(Uri.parse(source),"r")){if(descriptor==null)missing++;}catch(Exception ex){missing++;}
            final int count=missing;main.post(()->{if(isDestroyed())return;if(count>0)new AlertDialog.Builder(this).setTitle("Media unavailable").setMessage(count+" source file(s) could not be accessed. Your edits are preserved. You can open the project and replace missing media in the editor.").setNegativeButton("Cancel",null).setPositiveButton("Open anyway",(d,w)->launch(p.id,false)).show();else launch(p.id,false);});return null;},false);}
    private void launch(String id,boolean settings){Intent intent=new Intent(this,EditorActivity.class);if(id!=null)intent.putExtra("project-id",id);intent.putExtra("show-settings",settings);startActivity(intent);}
    private void buildSettings(){list=null;content.addView(text("Application settings",24,TEXT,true));content.addView(text("Application preferences are shared with the existing editor. Project-specific preview and export controls remain in editor settings.",16,MUTED,false));
        preferenceToggle("Reduced motion","reduced-motion",false);
        preferenceToggle("Timeline snapping","snapping",true);
        preferenceToggle("Lock editing and layout","ui-locked",false);
        content.addView(action("Diagnostics and crash logs",v->CrashLogs.show(this)),new LinearLayout.LayoutParams(-1,dp(56)));
        content.addView(action("Open existing settings",v->{String id=getSharedPreferences(EditorActivity.PREFS,MODE_PRIVATE).getString("last-project",null);if(id==null&&!entries.isEmpty())for(ProjectStore.BrowserEntry e:entries)if(e.project!=null){id=e.id;break;}
            if(id==null){error("Create or open a project to access the editor’s project-dependent settings.");return;}launch(id,true);}),new LinearLayout.LayoutParams(-1,dp(56)));
        content.addView(action("Workspace layout",v->{String mode=getSharedPreferences(EditorActivity.PREFS,MODE_PRIVATE).getString("mode","AUTO");int index=mode.equals("PHONE")?1:mode.equals("WIDE")?2:0;
            new AlertDialog.Builder(this).setTitle("Workspace layout").setSingleChoiceItems(new String[]{"Automatic","Portrait interface","Wide interface"},index,(d,i)->{getSharedPreferences(EditorActivity.PREFS,MODE_PRIVATE).edit().putString("mode",new String[]{"AUTO","PHONE","WIDE"}[i]).apply();d.dismiss();build();}).setNegativeButton("Cancel",null).show();}),new LinearLayout.LayoutParams(-1,dp(56)));}
    private void preferenceToggle(String label,String key,boolean fallback){
        android.content.SharedPreferences prefs=getSharedPreferences(EditorActivity.PREFS,MODE_PRIVATE);
        Switch toggle=new Switch(this);toggle.setText(label);toggle.setTextSize(16);toggle.setTextColor(TEXT);
        toggle.setChecked(prefs.getBoolean(key,fallback));toggle.setPadding(dp(8),dp(8),dp(8),dp(8));
        toggle.setOnCheckedChangeListener((v,checked)->prefs.edit().putBoolean(key,checked).apply());
        content.addView(toggle,new LinearLayout.LayoutParams(-1,dp(56)));
    }
    private void help(){new AlertDialog.Builder(this).setTitle("Black Ice Editor").setMessage("New project: choose a name, canvas and frame rate.\n\nImport project: restore a .bc1zip project + media bundle created by Black Ice. ZIP contents are validated before a new copy is saved.\n\nOpen a project to continue editing. Use its three-dot menu to rename, duplicate or delete it. Copies keep the same media references.\n\nProjects shows all saved work. Search filters names; Last edited changes sorting. Settings opens the existing application controls.\n\nThe interface layout and project aspect ratio are independent.").setPositiveButton("Got it",null).show();}
    private void error(String value){new AlertDialog.Builder(this).setTitle("Black Ice Editor").setMessage(value).setPositiveButton("OK",null).show();}
    private Spinner spinner(LinearLayout form,String label,String[] values,int selected){form.addView(text(label,14,MUTED,false));Spinner s=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values);s.setAdapter(adapter);s.setSelection(selected);form.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));return s;}
    private EditText field(String value){EditText t=new EditText(this);t.setText(value);t.setTextColor(TEXT);t.setSingleLine();t.setSelectAllOnFocus(true);return t;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String value,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    private TextView action(String label,View.OnClickListener listener){TextView t=text(label,15,CYAN,false);t.setGravity(Gravity.CENTER);t.setPadding(dp(8),0,dp(8),0);t.setBackground(interactive(0x00000000));clickable(t,label,listener);return t;}
    private View icon(String glyph,String label,View.OnClickListener listener){ImageView v=new ImageView(this);v.setImageDrawable(new V1Icon(glyph,MUTED));v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setBackground(interactive(0x00000000));clickable(v,label,listener);return v;}
    private void clickable(View v,String label,View.OnClickListener listener){v.setContentDescription(label);v.setFocusable(true);v.setOnClickListener(listener);v.setForeground(new RippleDrawable(ColorStateList.valueOf(0x4040bfff),null,round(0xffffffff,14)));}
    private GradientDrawable round(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(Color.alpha(color)>0)d.setStroke(dp(1),BORDER);return d;}
    private Drawable interactive(int color){android.graphics.drawable.StateListDrawable states=new android.graphics.drawable.StateListDrawable();states.addState(new int[]{android.R.attr.state_focused},round(0xff164169,14));states.addState(new int[]{android.R.attr.state_pressed},round(0xff164169,14));states.addState(new int[]{},round(color,14));return states;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static String clock(long ms){long s=ms/1000;return String.format(Locale.ROOT,"%02d:%02d",s/60,s%60);}
    /** Vector waves scale with the card, with native ripple and focus overlays above them. */
    private final class WaveDrawable extends Drawable {
        private final boolean bright;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        WaveDrawable(boolean bright){this.bright=bright;}
        @Override public void draw(Canvas c){Rect b=getBounds();float w=b.width(),h=b.height();c.save();c.translate(b.left,b.top);Path clip=new Path();clip.addRoundRect(new RectF(0,0,w,h),dp(14),dp(14),Path.Direction.CW);c.clipPath(clip);
            p.setShader(new LinearGradient(0,0,w,h,bright?new int[]{0xff007fd4,0xff32caff,0xff00368b}:new int[]{0xff0a2b55,0xff071725},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
            for(int i=0;i<3;i++){Path wave=new Path();float y=h*(.52f+i*.12f);wave.moveTo(0,y);wave.cubicTo(w*.28f,y-h*.5f,w*.48f,y+h*.65f,w,y-h*.43f);wave.lineTo(w,h);wave.lineTo(0,h);wave.close();p.setColor(bright?new int[]{0x5521cfff,0x773c9fff,0x66304dc5}[i]:0x100b6fc1);c.drawPath(wave,p);}c.restore();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(bright?CYAN:BORDER);c.drawRoundRect(new RectF(b.left+1,b.top+1,b.right-1,b.bottom-1),dp(14),dp(14),p);p.setStyle(Paint.Style.FILL);}
        @Override public void setAlpha(int a){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}

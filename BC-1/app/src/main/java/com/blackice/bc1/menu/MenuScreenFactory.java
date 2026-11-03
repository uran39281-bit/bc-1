package com.blackice.bc1.menu;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/**
 * Native Android Views reference. Copy/adapt to existing ToolPanels/TransformPanel;
 * do not mount a second document model or replace the app with this inspector.
 */
public final class MenuScreenFactory {
 public enum Menu {POSITION,ROTATION,SCALE,BLENDING,EFFECTS}
 public enum Property {X,Y,ROTATION,WIDTH,HEIGHT,OPACITY,EFFECT_STRENGTH}
 public enum KeyVisual {STATIC,ANIMATED,CURRENT_KEY}
 public enum Blend {NORMAL,MULTIPLY,SCREEN,OVERLAY,ADD,DARKEN,LIGHTEN}
 public interface Host {
  double value(Property property);double minimum(Property property);double maximum(Property property);double step(Property property);
  boolean editable(Property property);KeyVisual keyVisual(Property property);
  boolean canPrevious(Property property);boolean canNext(Property property);
  void previousKey(Property property);void nextKey(Property property);void toggleKey(Property property);
  /** begin captures a complete pre-edit snapshot; preview never adds undo entries. */
  void beginEdit(String label);void previewValue(Property property,double value);void commitEdit();void cancelEdit();
  void reset(Property property);void resetPosition();boolean linked();void setLinked(boolean linked);void fitCanvas(boolean fill);
  CubicBezier curve(Property property);boolean hasInterval(Property property);String intervalLabel(Property property);
  /** Override for legacy interpolation: display its real evaluator, preserve until explicit edit. */
  default double curveProgress(Property property,double progress){CubicBezier c=curve(property);return (c==null?CubicBezier.LINEAR:c).valueAt(progress);}
  void previewCurve(Property property,CubicBezier curve);
  Blend blend();void selectBlend(Blend blend);
  boolean hasSelectedEffect();String effectTitle();void addEffects();void removeSelectedEffect();
  default boolean canFrame(boolean fill){return editable(Property.X)&&editable(Property.Y)&&editable(Property.WIDTH)&&editable(Property.HEIGHT);}
  default boolean canSelectBlend(Blend mode){return editable(Property.OPACITY);}
  default boolean canAddEffects(){return editable(Property.OPACITY);}
  /** Effect controls beyond strength: how many, their label, 0..1 value and default, and whether they read as an angle. */
  default int effectParamCount(){return 0;}
  default String effectParamName(int index){return "";}
  default boolean effectParamIsAngle(int index){return false;}
  default double effectParam(int index){return 0;}
  default double effectParamDefault(int index){return 0;}
  default boolean effectParamEditable(){return false;}
  default void previewEffectParam(int index,double value){}
  void back();void settings();void export();void reportInputError(String message);
  /** Called when the visible menu or its tab changes so the host can keep its own tab state. */
  default void menuChanged(Menu menu){}
  /** Begins a graph edit; the host captures the interval's stable identity here. */
  default void beginCurveEdit(Property property,String label){beginEdit(label);}
  /** Lets the person choose which key's outgoing interval the graph edits. */
  default void selectInterval(Property property){}
  /** Extra compositing modes beyond the seven in the picture (clips keep every existing mode). */
  default boolean hasMoreBlends(){return false;}
  default String[] moreBlendLabels(){return new String[0];}
  default int moreBlendSelected(){return -1;}
  default void selectMoreBlend(int index){}
  /** Approximate width of the panel in dp, so narrow inspectors can use a compact layout. */
  default int panelWidthDp(){return 450;}
  /** Density follows the actual workspace, including its manual layout preference. */
  default boolean compactControls(){return false;}
  default double positionSliderSpan(Property property){return 2000;}

 }
 /** Narrow panels use smaller type, with flexible rows instead of fixed-width fields. */
 public static final int COMPACT_BELOW_DP=420;
 private final Context context;private final Host host;private int widthDp=450,widthOverride;private boolean compact;private float buttonSp=15;private final ArrayList<Runnable> readers=new ArrayList<>();
 private final ArrayList<Runnable> dialogReaders=new ArrayList<>();
 private PositionTrackpadView positionPad;
 private LinearLayout body;private ScrollView scroll;private boolean includeChrome=true;private boolean editActive;private Menu current=Menu.POSITION;private Property curveProperty=Property.X;
 public MenuScreenFactory(Context context,Host host){this.context=context;this.host=host;}
 private int dp(float n){return MenuPalette.dp(context,n);}
 private int controlDp(){return InspectorStyle.controlDp(context,host.compactControls());}
 private int controlHeight(){return dp(controlDp());}
 /** Call after external playhead/selection/model changes. Do not reconstruct during an active drag. */
 public void refresh(){for(Runnable r:new ArrayList<>(readers))r.run();for(Runnable r:new ArrayList<>(dialogReaders))r.run();}
 public View create(Menu menu){return create(menu,true);}
 /** Embedded inspectors should pass false to reuse the app toolbar rather than duplicating it. */
 public View create(Menu menu,boolean includeChrome){this.includeChrome=includeChrome;scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setBackgroundColor(MenuPalette.BACKGROUND);body=column();body.setPadding(dp(12),dp(8),dp(12),dp(16));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));show(menu);return scroll;}
 /** Embedded in an existing scrolling inspector: returns the content column without its own ScrollView or app chrome. */
 public LinearLayout createEmbedded(Menu menu){includeChrome=false;scroll=null;body=column();body.setBackgroundColor(MenuPalette.BACKGROUND);body.setPadding(dp(6),dp(6),dp(6),dp(10));readers.clear();show(menu);return body;}
 /** Graph selection survives inspector rebuilds that follow every committed edit. */
 /** Test and preview hook: lay the menus out as if the panel were this many dp wide. */
 public void overrideWidthDp(int dp){widthOverride=dp;}
 /** Re-lays the current menu out when the panel width crossed the compact threshold; a no-op otherwise. */
 public void relayoutIfNeeded(){if(body==null||editActive)return;int w=widthOverride>0?widthOverride:host.panelWidthDp();if((w<COMPACT_BELOW_DP)!=compact)show(current);}
 public int graphSelection(){return curveProperty.ordinal();}
 public void restoreGraphSelection(int ordinal){Property[] all=Property.values();if(ordinal>=0&&ordinal<all.length)curveProperty=all[ordinal];}
 public void show(Menu menu){if(body==null)throw new IllegalStateException("Call create before show");current=menu;host.menuChanged(menu);widthDp=widthOverride>0?widthOverride:host.panelWidthDp();compact=widthDp<COMPACT_BELOW_DP;buttonSp=widthDp<480?12:14;readers.clear();body.removeAllViews();
  if(includeChrome){LinearLayout top=row();Button settings=button("Settings",false,host::settings),export=button("Export",false,host::export);export.setBackground(MenuPalette.export(context));export.setTextColor(MenuPalette.BACKGROUND);leftIcon(settings,MenuIconDrawable.Kind.SETTINGS,MenuPalette.TEXT);weighted(top,settings);weighted(top,export);add(body,top,0);}
  LinearLayout title=row();Button back=button("Back",false,host::back);leftIcon(back,MenuIconDrawable.Kind.BACK,MenuPalette.TEXT);title.addView(back,new LinearLayout.LayoutParams(dp(compact?64:78),controlHeight()));TextView heading=text(menu==Menu.BLENDING?"Blending & Opacity":menu==Menu.EFFECTS?"Effects":"Move & Transform",compact?16:18,MenuPalette.ACCENT,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(compact?8:12);title.addView(heading,hp);add(body,title,8);
  if(menu==Menu.POSITION||menu==Menu.ROTATION||menu==Menu.SCALE){LinearLayout tabs=row();for(Menu m:new Menu[]{Menu.POSITION,Menu.ROTATION,Menu.SCALE}){Button b=button(title(m),false,()->{curveProperty=m==Menu.ROTATION?Property.ROTATION:m==Menu.SCALE?Property.WIDTH:Property.X;show(m);});LinearLayout column=column();if(m==menu)b.setTextColor(MenuPalette.ACCENT);column.addView(b,new LinearLayout.LayoutParams(-1,controlHeight()));View underline=new View(context);underline.setBackgroundColor(m==menu?MenuPalette.ACCENT:0);column.addView(underline,new LinearLayout.LayoutParams(-1,dp(4)));LinearLayout.LayoutParams tab=new LinearLayout.LayoutParams(0,-2,1);if(tabs.getChildCount()>0)tab.leftMargin=dp(8);tabs.addView(column,tab);}add(body,tabs,8);}
  switch(menu){
   case POSITION:curveProperty=curveProperty==Property.Y?Property.Y:Property.X;keyNav(body,curveProperty,true);positionControls();curveCard(body,curveProperty,false);break;
   case ROTATION:curveProperty=Property.ROTATION;keyNav(body,curveProperty,false);dial();numericCard(Property.ROTATION,"Rotation","°",false,false);presets();curveCard(body,curveProperty,false);break;
   case SCALE:curveProperty=curveProperty==Property.HEIGHT?Property.HEIGHT:Property.WIDTH;keyNav(body,curveProperty,false);LinearLayout fits=row();Button fit=button("Fit Canvas",false,()->commandSafe(()->host.fitCanvas(false))),fill=button("Fill Canvas",false,()->commandSafe(()->host.fitCanvas(true)));weighted(fits,fit);weighted(fits,fill);readers.add(()->{fit.setEnabled(host.canFrame(false));fill.setEnabled(host.canFrame(true));});add(body,fits,8);numericCard(Property.WIDTH,"Width","%",true,false);Button link=button("Lock W and H",host.linked(),()->{commandSafe(()->host.setLinked(!host.linked()));show(current);});leftIcon(link,MenuIconDrawable.Kind.TOGGLE,host.linked()?MenuPalette.ACCENT:MenuPalette.MUTED);add(body,link,10);numericCard(Property.HEIGHT,"Height","%",true,false);curveCard(body,curveProperty,true);break;
   case BLENDING:curveProperty=Property.OPACITY;keyNav(body,curveProperty,false);numericCard(Property.OPACITY,"Opacity","%",true,false,true);blendModes();curveCard(body,curveProperty,false);break;
   case EFFECTS:curveProperty=Property.EFFECT_STRENGTH;effects();break;
  }refresh();
 }
 public enum PositionControl {RULER,SLIDER,TRACKPAD}
 public static final String CONTROL_PREFS="black-ice-menu-controls",POSITION_CONTROL="position-control";
 private PositionControl positionControl(){try{return PositionControl.valueOf(context.getSharedPreferences(CONTROL_PREFS,Context.MODE_PRIVATE).getString(POSITION_CONTROL,"RULER"));}catch(IllegalArgumentException ex){return PositionControl.RULER;}}
 private void positionControls(){
  PositionControl selected=positionControl();LinearLayout choices=row();
  for(PositionControl choice:PositionControl.values()){
   String label=choice==PositionControl.RULER?"Ruler":choice==PositionControl.SLIDER?"Slider":"Trackpad";
   Button b=button(label,choice==selected,()->{body.clearFocus();context.getSharedPreferences(CONTROL_PREFS,Context.MODE_PRIVATE).edit().putString(POSITION_CONTROL,choice.name()).apply();show(current);});
   b.setSelected(choice==selected);b.setContentDescription("Position control: "+label);weighted(choices,b);
  }
  add(body,choices,8);
  numericCard(Property.X,"X"," px",selected!=PositionControl.TRACKPAD,true);
  numericCard(Property.Y,"Y"," px",selected!=PositionControl.TRACKPAD,true);
  if(selected==PositionControl.TRACKPAD){
   PositionTrackpadView pad=new PositionTrackpadView(context,new PositionTrackpadView.Binding(){
    public double x(){return host.value(Property.X);}public double y(){return host.value(Property.Y);}
    public void begin(){body.clearFocus();beginEditSafe("Position updated");}
    public void preview(double x,double y){previewValueSafe(Property.X,Math.max(host.minimum(Property.X),Math.min(host.maximum(Property.X),x)));previewValueSafe(Property.Y,Math.max(host.minimum(Property.Y),Math.min(host.maximum(Property.Y),y)));refresh();}
    public void commit(){finishEditSafe(true);restoreTrackpadFocus();refresh();}public void cancel(){finishEditSafe(false);restoreTrackpadFocus();refresh();}
   });
   positionPad=pad;add(body,pad,8);readers.add(()->{pad.setEnabled(host.editable(Property.X)&&host.editable(Property.Y));pad.invalidate();});
   add(body,text("Drag to move · Arrow keys: 1 px · Shift: 10 px",12,MenuPalette.MUTED,false),6);
  }
 }
 private void restoreTrackpadFocus(){if(current==Menu.POSITION&&positionControl()==PositionControl.TRACKPAD&&positionPad!=null&&positionPad.isAttachedToWindow())positionPad.requestFocus();}
 private static String unitWord(String unit){String u=unit.trim();return u.equals("px")?"pixels":u.equals("°")?"degrees":u.equals("%")?"percent":u;}
 private String title(Menu m){return m==Menu.POSITION?"Position":m==Menu.ROTATION?"Rotation":"Scale";}
 private LinearLayout column(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private LinearLayout row(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 private TextView text(String label,int sp,int color,boolean bold){TextView t=new TextView(context);t.setText(label);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
 private Button button(String label,boolean selected,Runnable action){Button b=new Button(context);b.setAllCaps(false);b.setBackgroundTintList(null);b.setStateListAnimator(null);b.setElevation(0);b.setText(label);b.setTextSize(buttonSp);b.setMaxLines(2);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(selected?MenuPalette.ACCENT:MenuPalette.TEXT);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(controlHeight());b.setMinimumHeight(controlHeight());b.setPadding(dp(compact?4:8),dp(3),dp(compact?4:8),dp(3));b.setBackground(MenuPalette.surface(context,selected));b.setContentDescription(label);b.setOnClickListener(v->action.run());return b;}
 /** Protect owned live transactions against host validation/storage/model exceptions. */
 private void beginEditSafe(String label){if(editActive)finishEditSafe(false);editActive=true;try{host.beginEdit(label);}catch(RuntimeException ex){abortEdit("Edit could not be started.");}}
 private void beginCurveEditSafe(Property p,String label){if(editActive)finishEditSafe(false);editActive=true;try{host.beginCurveEdit(p,label);}catch(RuntimeException ex){abortEdit("Edit could not be started.");}}
 private void previewValueSafe(Property property,double value){if(!editActive)return;try{host.previewValue(property,value);}catch(RuntimeException ex){abortEdit("Edit could not be applied.");}}
 private void previewCurveSafe(Property property,CubicBezier curve){if(!editActive)return;try{host.previewCurve(property,curve);}catch(RuntimeException ex){abortEdit("Curve edit could not be applied.");}}
 private void finishEditSafe(boolean commit){if(!editActive)return;editActive=false;try{if(commit)host.commitEdit();else host.cancelEdit();}catch(RuntimeException ex){boolean restored=rollbackHost();host.reportInputError(restored?"Edit failed; the previous state was restored.":"Edit failed and rollback failed. Keep the project open and inspect its state.");}}
 private boolean rollbackHost(){try{host.cancelEdit();return true;}catch(RuntimeException ex){return false;}}
 private void abortEdit(String message){editActive=false;boolean restored=rollbackHost();host.reportInputError(restored?message:message+" Rollback failed; keep the project open.");refresh();}
 /** Existing atomic host commands own their snapshots/undo and must roll back if they throw. */
 private void commandSafe(Runnable action){try{action.run();}catch(RuntimeException ex){host.reportInputError("Action could not be completed.");}refresh();}
 private void leftIcon(Button button,MenuIconDrawable.Kind kind,int color){button.setCompoundDrawablesRelativeWithIntrinsicBounds(new MenuIconDrawable(context,kind,color),null,null,null);button.setCompoundDrawablePadding(dp(3));}
 private void rightIcon(Button button,MenuIconDrawable.Kind kind,int color){button.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,new MenuIconDrawable(context,kind,color),null);button.setCompoundDrawablePadding(dp(3));}
 private void weighted(LinearLayout row,View child){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,controlHeight(),1);if(row.getChildCount()>0)lp.leftMargin=dp(8);row.addView(child,lp);}
 private void add(LinearLayout parent,View view,int gap){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(gap);parent.addView(view,lp);}
 private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(8),dp(8),dp(8),dp(8));c.setBackground(MenuPalette.surface(context,false));return c;}
 private void keyNav(LinearLayout parent,Property p,boolean resetPosition){keyNav(parent,p,resetPosition,false);}
 private void keyNav(LinearLayout parent,Property p,boolean resetPosition,boolean doubleChevrons){LinearLayout nav=row();Button previous=button("Previous Key",false,()->{host.previousKey(p);refresh();}),next=button("Next Key",false,()->{host.nextKey(p);refresh();});leftIcon(previous,doubleChevrons?MenuIconDrawable.Kind.DOUBLE_BACK:MenuIconDrawable.Kind.BACK,MenuPalette.TEXT);rightIcon(next,doubleChevrons?MenuIconDrawable.Kind.DOUBLE_NEXT:MenuIconDrawable.Kind.NEXT,MenuPalette.TEXT);if(doubleChevrons){previous.setBackground(null);next.setBackground(null);LinearLayout joined=row();joined.setBackground(MenuPalette.surface(context,false));LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,controlHeight(),1),d=new LinearLayout.LayoutParams(dp(1),dp(28)),b2=new LinearLayout.LayoutParams(0,controlHeight(),1);joined.addView(previous,a);View divider=new View(context);divider.setBackgroundColor(MenuPalette.BORDER);joined.addView(divider,d);joined.addView(next,b2);readers.add(()->{previous.setEnabled(host.canPrevious(p));next.setEnabled(host.canNext(p));});add(parent,joined,8);return;}weighted(nav,previous);Button reset=null;if(resetPosition){reset=button("Reset all",false,()->{body.clearFocus();commandSafe(host::resetPosition);});reset.setTextColor(MenuPalette.MUTED);LinearLayout.LayoutParams resetLp=new LinearLayout.LayoutParams(0,controlHeight(),.72f);resetLp.leftMargin=dp(6);nav.addView(reset,resetLp);}weighted(nav,next);readers.add(()->{previous.setEnabled(host.canPrevious(p));next.setEnabled(host.canNext(p));});add(parent,nav,8);}
 private LinearLayout numericCard(Property property,String label,String unit,boolean ruler,boolean selectable){return numericCard(property,label,unit,ruler,selectable,false);}
 private LinearLayout numericCard(Property property,String label,String unit,boolean ruler,boolean selectable,boolean applyRow){
  LinearLayout c=card(),line=row();Button diamond=button("",false,()->{commandSafe(()->host.toggleKey(property));});MenuIconDrawable keyIcon=new MenuIconDrawable(context,MenuIconDrawable.Kind.DIAMOND,MenuPalette.MUTED);diamond.setCompoundDrawablesRelativeWithIntrinsicBounds(keyIcon,null,null,null);diamond.setContentDescription(label+" keyframe");line.addView(diamond,new LinearLayout.LayoutParams(controlHeight(),controlHeight()));
  TextView name=text(label,compact?14:16,MenuPalette.TEXT,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=dp(8);line.addView(name,np);if(selectable){name.setMinHeight(controlHeight());name.setGravity(Gravity.CENTER_VERTICAL);name.setFocusable(true);name.setContentDescription("Select "+label+" easing interval");name.setOnClickListener(v->{curveProperty=property;show(current);});}
  EditText input=new EditText(context);input.setSingleLine(true);input.setTextSize(compact?15:18);input.setPadding(dp(4),0,dp(4),0);input.setTypeface(Typeface.DEFAULT_BOLD);input.setTextColor(MenuPalette.TEXT);input.setSelectAllOnFocus(true);input.setGravity(Gravity.CENTER);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setImeOptions(EditorInfo.IME_ACTION_DONE);input.setBackgroundTintList(null);input.setBackground(MenuPalette.surface(context,false));input.setContentDescription(label+" exact value in "+unitWord(unit));line.addView(input,new LinearLayout.LayoutParams(dp(90),controlHeight()));
  TextView units=text(unit,12,MenuPalette.MUTED,false);units.setPadding(dp(3),0,dp(3),0);line.addView(units);
  Runnable submit=()->{try{double n=Double.parseDouble(input.getText().toString().trim());if(!Double.isFinite(n)||n<host.minimum(property)||n>host.maximum(property))throw new NumberFormatException();if(n!=host.value(property)){beginEditSafe(label+" updated");previewValueSafe(property,n);finishEditSafe(true);}input.clearFocus();refresh();}catch(NumberFormatException ex){input.setError("Enter "+host.minimum(property)+" to "+host.maximum(property));host.reportInputError("Invalid "+label+" value");}};
  input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN)){submit.run();return true;}return false;});
  Button resetButton=null;if(!applyRow){Button reset=button("Reset",false,()->{input.clearFocus();commandSafe(()->host.reset(property));});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(56),controlHeight());rp.leftMargin=dp(8);line.addView(reset,rp);readers.add(()->reset.setEnabled(host.editable(property)));resetButton=reset;}
  line.removeAllViews();add(c,new AdaptiveValueRow(context,diamond,name,input,units,resetButton,controlDp()),0);
  if(ruler&&(property==Property.EFFECT_STRENGTH||(current==Menu.POSITION&&positionControl()==PositionControl.SLIDER))){effectSlider(c,property,label,input);}
  else if(ruler){RulerView v=new RulerView(context,label,host.minimum(property),host.maximum(property),host.step(property),new RulerView.Binding(){public double value(){return host.value(property);}public void begin(){input.clearFocus();beginEditSafe(label+" updated");}public void preview(double n){previewValueSafe(property,n);refresh();}public void commit(){finishEditSafe(true);refresh();}public void cancel(){finishEditSafe(false);refresh();}});add(c,v,6);readers.add(()->{v.setEnabled(host.editable(property));v.invalidate();});}
  if(applyRow){LinearLayout actions=row();Button apply=button("Apply",false,submit),reset=button("Reset",false,()->{input.clearFocus();commandSafe(()->host.reset(property));});weighted(actions,apply);weighted(actions,reset);readers.add(()->{apply.setEnabled(host.editable(property));reset.setEnabled(host.editable(property));});add(c,actions,8);if(property==Property.OPACITY){TextView note=text("0% transparent · 100% visible",12,MenuPalette.MUTED,false);note.setGravity(Gravity.CENTER);add(c,note,8);}}
  readers.add(()->{boolean enabled=host.editable(property);input.setEnabled(enabled);diamond.setEnabled(enabled);KeyVisual k=host.keyVisual(property);keyIcon.mark(k==KeyVisual.STATIC?MenuPalette.MUTED:MenuPalette.ACCENT,k==KeyVisual.CURRENT_KEY,k==KeyVisual.ANIMATED);diamond.setContentDescription(label+" keyframe: "+(k==KeyVisual.CURRENT_KEY?"keyframe at playhead • tap to remove":k==KeyVisual.ANIMATED?"animated • tap to add a keyframe here":"not animated • tap to animate"));if(!input.hasFocus())input.setText(String.format(Locale.US,"%.2f",host.value(property)));});add(body,c,10);return c;
 }

 /** One named effect control: label, value, 0..1 slider and a reset to the effect's default. */
 private void effectParamRow(LinearLayout parent,int index){
  String name=host.effectParamName(index);boolean angle=host.effectParamIsAngle(index);
  LinearLayout row=row();TextView label=text(name,14,MenuPalette.TEXT,true);row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
  TextView value=text("",13,MenuPalette.MUTED,false);value.setGravity(Gravity.END);row.addView(value,new LinearLayout.LayoutParams(dp(58),-2));
  Button reset=button("Reset",false,()->{if(!host.effectParamEditable())return;beginEditSafe(name+" reset");previewEffectParamSafe(index,host.effectParamDefault(index));finishEditSafe(true);refresh();});
  reset.setTextSize(12);reset.setMinHeight(dp(36));reset.setMinimumHeight(dp(36));reset.setContentDescription(name+" reset");row.addView(reset,new LinearLayout.LayoutParams(dp(compact?64:72),dp(36)));
  add(parent,row,8);
  final boolean[] active={false};
  SeekBar slider=new SeekBar(context){@Override protected void onDetachedFromWindow(){if(active[0]){active[0]=false;finishEditSafe(false);}super.onDetachedFromWindow();}};
  slider.setMax(1000);slider.setContentDescription(name);slider.setProgressTintList(android.content.res.ColorStateList.valueOf(MenuPalette.ACCENT));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(MenuPalette.ACCENT));slider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(MenuPalette.BORDER));
  Runnable show=()->{double v=host.effectParam(index);value.setText(angle?String.format(Locale.US,"%.0f°",v*360):String.format(Locale.US,"%.0f%%",v*100));value.setContentDescription(name+" "+value.getText());};
  slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onStartTrackingTouch(SeekBar b){active[0]=true;beginEditSafe(name+" updated");}
   public void onStopTrackingTouch(SeekBar b){if(!active[0])return;active[0]=false;finishEditSafe(true);refresh();}
   public void onProgressChanged(SeekBar b,int progress,boolean fromUser){if(!fromUser)return;double v=progress/1000d;if(active[0]){previewEffectParamSafe(index,v);show.run();}else{beginEditSafe(name+" updated");previewEffectParamSafe(index,v);finishEditSafe(true);refresh();}}});
  add(parent,slider,2);
  readers.add(()->{boolean enabled=host.effectParamEditable();slider.setEnabled(enabled);reset.setEnabled(enabled);if(!active[0])slider.setProgress((int)Math.round(host.effectParam(index)*1000));show.run();});
 }
 private void previewEffectParamSafe(int index,double value){if(!editActive)return;try{host.previewEffectParam(index,value);}catch(RuntimeException ex){abortEdit("Effect control could not be applied.");}}
 private void effectSlider(LinearLayout container,Property property,String label,EditText input){
  final boolean[] active={false},cancelling={false};
  boolean position=property==Property.X||property==Property.Y;
  double span=host.positionSliderSpan(property),center=host.value(property);
  final double low=position?Math.max(host.minimum(property),center-span):host.minimum(property);
  final double high=position?Math.min(host.maximum(property),center+span):host.maximum(property);
  SeekBar slider=new SeekBar(context){@Override public void setEnabled(boolean enabled){if(!enabled&&active!=null&&active[0]){active[0]=false;finishEditSafe(false);}super.setEnabled(enabled);}@Override public boolean onTouchEvent(MotionEvent e){boolean cancel=e.getActionMasked()==MotionEvent.ACTION_CANCEL;if(cancel)cancelling[0]=true;boolean result=super.onTouchEvent(e);if(cancel&&active[0]){active[0]=false;finishEditSafe(false);refresh();}cancelling[0]=false;return result;}
   @Override protected void onDetachedFromWindow(){if(active[0]){active[0]=false;finishEditSafe(false);}super.onDetachedFromWindow();}};
  slider.setMax(10000);slider.setContentDescription(label+" slider");slider.setProgressTintList(android.content.res.ColorStateList.valueOf(MenuPalette.CYAN));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(MenuPalette.CYAN));slider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(MenuPalette.BORDER));
  slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){input.clearFocus();active[0]=true;beginEditSafe(label+" updated");}public void onStopTrackingTouch(SeekBar b){if(!active[0])return;active[0]=false;if(cancelling[0])finishEditSafe(false);else finishEditSafe(true);refresh();}public void onProgressChanged(SeekBar b,int value,boolean fromUser){if(!fromUser)return;double n=low+(high-low)*value/10000d;if(active[0]){previewValueSafe(property,n);refresh();}else{beginEditSafe(label+" updated");previewValueSafe(property,n);finishEditSafe(true);refresh();}}});
  add(container,slider,8);readers.add(()->{slider.setEnabled(host.editable(property));if(!active[0]){double range=high-low;slider.setProgress(range<=0?0:(int)Math.round((host.value(property)-low)/range*10000));}});
 }
 private void dial(){RotationDialView v=new RotationDialView(context,new RotationDialView.Binding(){public double value(){return host.value(Property.ROTATION);}public void begin(){body.clearFocus();beginEditSafe("Rotation updated");}public void preview(double n){previewValueSafe(Property.ROTATION,n);refresh();}public void commit(){finishEditSafe(true);refresh();}public void cancel(){finishEditSafe(false);refresh();}});add(body,v,4);readers.add(()->{v.setEnabled(host.editable(Property.ROTATION));v.invalidate();});}
 private void presets(){LinearLayout c=card();add(c,text("Angle presets",17,MenuPalette.TEXT,true),0);LinearLayout columns=row();for(int angle:new int[]{45,90,180,360}){LinearLayout col=column();Button plus=button("+",false,()->nudgeAngle(angle)),minus=button("−",false,()->nudgeAngle(-angle));plus.setTextSize(26);minus.setTextSize(26);plus.setContentDescription("Add "+angle+" degrees");minus.setContentDescription("Subtract "+angle+" degrees");add(col,plus,0);TextView value=text(angle+"°",16,MenuPalette.TEXT,true);value.setGravity(Gravity.CENTER);add(col,value,6);add(col,minus,6);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(columns.getChildCount()>0)lp.leftMargin=dp(8);columns.addView(col,lp);readers.add(()->{plus.setEnabled(host.editable(Property.ROTATION));minus.setEnabled(host.editable(Property.ROTATION));});}add(c,columns,8);add(body,c,10);}
 private void nudgeAngle(double delta){double n=host.value(Property.ROTATION)+delta;if(n<host.minimum(Property.ROTATION)||n>host.maximum(Property.ROTATION)){host.reportInputError("Rotation exceeds the engine's supported range");return;}beginEditSafe("Rotation preset");previewValueSafe(Property.ROTATION,n);finishEditSafe(true);refresh();}
 private void blendModes(){LinearLayout c=card();add(c,text("Blend Mode",17,MenuPalette.TEXT,true),0);Blend[] modes=Blend.values();for(int i=0;i<modes.length;i+=3){LinearLayout r=row();for(int j=i;j<Math.min(i+3,modes.length);j++){Blend m=modes[j];String label=m==Blend.NORMAL?"Normal":m.name().substring(0,1)+m.name().substring(1).toLowerCase(Locale.US);boolean chosen=host.blend()==m;Button b=button(label,chosen,()->{commandSafe(()->host.selectBlend(m));show(current);});if(chosen){b.setText("●  "+label);b.setContentDescription(label+", selected");b.setBackground(MenuPalette.surfaceGreen(context));b.setTextColor(MenuPalette.GREEN);}weighted(r,b);readers.add(()->b.setEnabled(host.canSelectBlend(m)));}if(i+3>=modes.length&&host.hasMoreBlends()&&r.getChildCount()<3){boolean other=host.moreBlendSelected()>=0;Button more=button(other?"Other ✓":"More…",other,()->moreBlends());weighted(r,more);readers.add(()->more.setEnabled(host.editable(Property.OPACITY)));}for(int j=r.getChildCount();j<3;j++)weighted(r,new Space(context));add(c,r,8);}add(body,c,10);}
 private void moreBlends(){String[] labels=host.moreBlendLabels();if(labels.length==0)return;new android.app.AlertDialog.Builder(context).setTitle("More blend modes").setSingleChoiceItems(labels,host.moreBlendSelected(),(d,which)->{d.dismiss();commandSafe(()->host.selectMoreBlend(which));if(body!=null)show(current);}).setNegativeButton("Cancel",null).show();}
 private void effects(){Button add=button("Add Effects",false,()->commandSafe(host::addEffects));readers.add(()->add.setEnabled(host.canAddEffects()));leftIcon(add,MenuIconDrawable.Kind.ADD,MenuPalette.CYAN);add.setTextColor(MenuPalette.CYAN);add(body,add,8);if(!host.hasSelectedEffect())return;LinearLayout heading=card();LinearLayout name=row();ImageView sparkle=new ImageView(context);sparkle.setImageDrawable(new MenuIconDrawable(context,MenuIconDrawable.Kind.SPARKLE,MenuPalette.CYAN));sparkle.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);name.addView(sparkle,new LinearLayout.LayoutParams(dp(28),dp(28)));TextView effectName=text(host.effectTitle(),compact?18:21,MenuPalette.TEXT,true);LinearLayout.LayoutParams namePart=new LinearLayout.LayoutParams(0,-2,1);namePart.leftMargin=dp(10);name.addView(effectName,namePart);add(heading,name,0);View rule=new View(context);rule.setBackgroundColor(MenuPalette.BORDER);LinearLayout.LayoutParams ruleLp=new LinearLayout.LayoutParams(-1,dp(1));ruleLp.topMargin=dp(10);heading.addView(rule,ruleLp);LinearLayout controls=numericCard(Property.EFFECT_STRENGTH,"Effect strength","%",true,false,true);body.removeView(controls);controls.setBackground(null);controls.setPadding(0,dp(10),0,0);add(heading,controls,8);for(int i=0;i<host.effectParamCount();i++)effectParamRow(heading,i);keyNav(heading,Property.EFFECT_STRENGTH,false,true);add(body,heading,10);curveCard(body,Property.EFFECT_STRENGTH,false);Button remove=button("Remove Effect",false,()->{commandSafe(host::removeSelectedEffect);show(current);});remove.setTextColor(MenuPalette.DANGER);leftIcon(remove,MenuIconDrawable.Kind.TRASH,MenuPalette.DANGER);android.graphics.drawable.GradientDrawable danger=MenuPalette.surface(context,false);danger.setStroke(dp(1),MenuPalette.DANGER);remove.setBackground(danger);add(body,remove,10);}
 private void curveCard(LinearLayout parent,Property property,boolean scaleChannels){LinearLayout c=card();
  TextView interval=text(host.intervalLabel(property),compact?13:14,MenuPalette.MUTED,false);interval.setMinHeight(controlHeight());interval.setGravity(Gravity.CENTER_VERTICAL);interval.setFocusable(true);interval.setClickable(true);interval.setOnClickListener(v->commandSafe(()->host.selectInterval(property)));
  LinearLayout heading=row();ImageView curveIcon=new ImageView(context);curveIcon.setImageDrawable(new MenuIconDrawable(context,MenuIconDrawable.Kind.CURVE,MenuPalette.ACCENT));curveIcon.setPadding(dp(6),dp(6),dp(6),dp(6));curveIcon.setBackground(MenuPalette.surface(context,false));curveIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);heading.addView(curveIcon,new LinearLayout.LayoutParams(controlHeight(),controlHeight()));
  LinearLayout names=column();names.addView(text("Easing Curve Editor",compact?15:18,MenuPalette.TEXT,true),new LinearLayout.LayoutParams(-2,-2));LinearLayout.LayoutParams namePart=new LinearLayout.LayoutParams(0,-2,1);namePart.leftMargin=dp(10);heading.addView(names,namePart);
  Button expand=button("",false,()->expandCurve(property));leftIcon(expand,MenuIconDrawable.Kind.EXPAND,MenuPalette.MUTED);expand.setContentDescription("Expand easing curve editor");heading.addView(expand,new LinearLayout.LayoutParams(controlHeight(),controlHeight()));add(c,heading,0);if(!scaleChannels)add(c,interval,2);
  if(scaleChannels){LinearLayout channels=row();weighted(channels,button("Width",property==Property.WIDTH,()->{curveProperty=Property.WIDTH;show(current);}));weighted(channels,button("Height",property==Property.HEIGHT,()->{curveProperty=Property.HEIGHT;show(current);}));LinearLayout.LayoutParams intervalLp=new LinearLayout.LayoutParams(-1,-2);add(c,channels,6);c.addView(interval,intervalLp);}
  CurveEditorView graph=graph(property);add(c,graph,2);readers.add(()->{String label=host.intervalLabel(property);interval.setText(label);interval.setContentDescription("Easing keyframe: "+label+". Tap to choose a keyframe.");graph.setEnabled(host.hasInterval(property)&&host.editable(property));graph.invalidate();});
  LinearLayout footer=row();ImageView info=new ImageView(context);info.setImageDrawable(new MenuIconDrawable(context,MenuIconDrawable.Kind.INFO,MenuPalette.MUTED));info.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);footer.addView(info,new LinearLayout.LayoutParams(dp(20),dp(20)));TextView help=text("Drag handles to adjust easing",12,MenuPalette.MUTED,false);LinearLayout.LayoutParams helpLp=new LinearLayout.LayoutParams(0,-2,1);helpLp.leftMargin=dp(8);footer.addView(help,helpLp);Button reset=button("Reset curve",false,()->resetCurve(property));footer.addView(reset,new LinearLayout.LayoutParams(dp(compact?90:104),controlHeight()));readers.add(()->reset.setEnabled(host.hasInterval(property)&&host.editable(property)));add(c,footer,8);add(parent,c,10);
 }
 private CurveEditorView graph(Property property){return new CurveEditorView(context,new CurveEditorView.Model(){public CubicBezier curve(){return host.curve(property);}public boolean editable(){return host.hasInterval(property)&&host.editable(property);}public String intervalLabel(){return host.intervalLabel(property);}public double progressAt(double u){return host.curveProgress(property,u);}},new CurveEditorView.Edits(){public void begin(){beginCurveEditSafe(property,"Easing curve updated");}public void preview(CubicBezier c){previewCurveSafe(property,c);refresh();}public void commit(){finishEditSafe(true);refresh();}public void cancel(){finishEditSafe(false);refresh();}});}
 private void resetCurve(Property p){if(!host.hasInterval(p)||!host.editable(p))return;beginCurveEditSafe(p,"Easing curve reset");previewCurveSafe(p,CubicBezier.LINEAR);finishEditSafe(true);refresh();}
 private void expandCurve(Property property){Dialog dialog=new Dialog(context);LinearLayout expanded=column();expanded.setPadding(dp(16),dp(16),dp(16),dp(16));expanded.setBackgroundColor(MenuPalette.BACKGROUND);Button close=button("Back",false,dialog::dismiss);leftIcon(close,MenuIconDrawable.Kind.BACK,MenuPalette.TEXT);add(expanded,close,0);TextView title=text("Easing Curve Editor",22,MenuPalette.TEXT,true),interval=text(host.intervalLabel(property),14,MenuPalette.MUTED,false);add(expanded,title,10);add(expanded,interval,6);CurveEditorView graph=graph(property);expanded.addView(graph,new LinearLayout.LayoutParams(-1,0,1));Button reset=button("Reset curve",false,()->resetCurve(property));add(expanded,reset,8);
  Runnable reader=()->{interval.setText(host.intervalLabel(property));graph.setEnabled(host.hasInterval(property)&&host.editable(property));graph.invalidate();reset.setEnabled(host.hasInterval(property)&&host.editable(property));};dialogReaders.add(reader);dialog.setContentView(expanded);dialog.setOnDismissListener(d->{dialogReaders.remove(reader);refresh();});dialog.show();Window window=dialog.getWindow();if(window!=null){window.setLayout(-1,-1);window.setBackgroundDrawableResource(android.R.color.transparent);}reader.run();
 }
}

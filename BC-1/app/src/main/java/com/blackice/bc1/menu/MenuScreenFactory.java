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
 }
 private final Context context;private final Host host;private final ArrayList<Runnable> readers=new ArrayList<>();
 private final ArrayList<Runnable> dialogReaders=new ArrayList<>();
 private LinearLayout body;private ScrollView scroll;private boolean includeChrome=true;private boolean editActive;private Menu current=Menu.POSITION;private Property curveProperty=Property.X;
 public MenuScreenFactory(Context context,Host host){this.context=context;this.host=host;}
 private int dp(float n){return MenuPalette.dp(context,n);}
 private int controlHeight(){return dp(Math.max(48,48*context.getResources().getConfiguration().fontScale));}
 /** Call after external playhead/selection/model changes. Do not reconstruct during an active drag. */
 public void refresh(){for(Runnable r:new ArrayList<>(readers))r.run();for(Runnable r:new ArrayList<>(dialogReaders))r.run();}
 public View create(Menu menu){return create(menu,true);}
 /** Embedded inspectors should pass false to reuse the app toolbar rather than duplicating it. */
 public View create(Menu menu,boolean includeChrome){this.includeChrome=includeChrome;scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setBackgroundColor(MenuPalette.BACKGROUND);body=column();body.setPadding(dp(12),dp(8),dp(12),dp(16));scroll.addView(body,new ScrollView.LayoutParams(-1,-2));show(menu);return scroll;}
 /** Embedded in an existing scrolling inspector: returns the content column without its own ScrollView or app chrome. */
 public LinearLayout createEmbedded(Menu menu){includeChrome=false;scroll=null;body=column();body.setBackgroundColor(MenuPalette.BACKGROUND);body.setPadding(dp(6),dp(6),dp(6),dp(10));readers.clear();show(menu);return body;}
 /** Graph selection survives inspector rebuilds that follow every committed edit. */
 public int graphSelection(){return curveProperty.ordinal();}
 public void restoreGraphSelection(int ordinal){Property[] all=Property.values();if(ordinal>=0&&ordinal<all.length)curveProperty=all[ordinal];}
 public void show(Menu menu){if(body==null)throw new IllegalStateException("Call create before show");current=menu;host.menuChanged(menu);readers.clear();body.removeAllViews();
  if(includeChrome){LinearLayout top=row();Button settings=button("Settings",false,host::settings),export=button("Export",false,host::export);export.setBackground(MenuPalette.export(context));export.setTextColor(MenuPalette.BACKGROUND);leftIcon(settings,MenuIconDrawable.Kind.SETTINGS,MenuPalette.TEXT);weighted(top,settings);weighted(top,export);add(body,top,0);}
  LinearLayout title=row();Button back=button("Back",false,host::back);leftIcon(back,MenuIconDrawable.Kind.BACK,MenuPalette.TEXT);title.addView(back,new LinearLayout.LayoutParams(dp(90),dp(48)));TextView heading=text(menu==Menu.BLENDING?"Blending & Opacity":menu==Menu.EFFECTS?"Effects":"Move & Transform",20,MenuPalette.GREEN,true);LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-2,1);hp.leftMargin=dp(12);title.addView(heading,hp);add(body,title,8);
  if(menu==Menu.POSITION||menu==Menu.ROTATION||menu==Menu.SCALE){LinearLayout tabs=row();for(Menu m:new Menu[]{Menu.POSITION,Menu.ROTATION,Menu.SCALE}){Button b=button(title(m),m==menu,()->{curveProperty=m==Menu.ROTATION?Property.ROTATION:m==Menu.SCALE?Property.WIDTH:Property.X;show(m);});weighted(tabs,b);}add(body,tabs,8);}
  switch(menu){
   case POSITION:curveProperty=curveProperty==Property.Y?Property.Y:Property.X;keyNav(body,curveProperty,true);numericCard(Property.X,"X"," px",true,true);numericCard(Property.Y,"Y"," px",true,true);curveCard(body,curveProperty,false);break;
   case ROTATION:curveProperty=Property.ROTATION;keyNav(body,curveProperty,false);dial();numericCard(Property.ROTATION,"Rotation","°",false,false);presets();curveCard(body,curveProperty,false);break;
   case SCALE:curveProperty=curveProperty==Property.HEIGHT?Property.HEIGHT:Property.WIDTH;keyNav(body,curveProperty,false);LinearLayout fits=row();Button fit=button("Fit Canvas",false,()->commandSafe(()->host.fitCanvas(false))),fill=button("Fill Canvas",false,()->commandSafe(()->host.fitCanvas(true)));weighted(fits,fit);weighted(fits,fill);readers.add(()->{fit.setEnabled(host.canFrame(false));fill.setEnabled(host.canFrame(true));});add(body,fits,8);numericCard(Property.WIDTH,"Width","%",true,false);Button link=button("Lock W and H",host.linked(),()->{commandSafe(()->host.setLinked(!host.linked()));show(current);});leftIcon(link,MenuIconDrawable.Kind.LINK,host.linked()?MenuPalette.GREEN:MenuPalette.TEXT);add(body,link,10);numericCard(Property.HEIGHT,"Height","%",true,false);curveCard(body,curveProperty,true);break;
   case BLENDING:curveProperty=Property.OPACITY;keyNav(body,curveProperty,false);numericCard(Property.OPACITY,"Opacity","%",true,false,true);blendModes();curveCard(body,curveProperty,false);break;
   case EFFECTS:curveProperty=Property.EFFECT_STRENGTH;effects();break;
  }refresh();
 }
 private static String unitWord(String unit){String u=unit.trim();return u.equals("px")?"pixels":u.equals("°")?"degrees":u.equals("%")?"percent":u;}
 private String title(Menu m){return m==Menu.POSITION?"Position":m==Menu.ROTATION?"Rotation":"Scale";}
 private LinearLayout column(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.VERTICAL);return v;}
 private LinearLayout row(){LinearLayout v=new LinearLayout(context);v.setOrientation(LinearLayout.HORIZONTAL);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
 private TextView text(String label,int sp,int color,boolean bold){TextView t=new TextView(context);t.setText(label);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
 private Button button(String label,boolean selected,Runnable action){Button b=new Button(context);b.setAllCaps(false);b.setText(label);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT_BOLD);b.setTextColor(selected?MenuPalette.GREEN:MenuPalette.TEXT);b.setMinHeight(controlHeight());b.setMinimumHeight(controlHeight());b.setPadding(dp(8),dp(5),dp(8),dp(5));b.setBackground(MenuPalette.surface(context,selected));b.setContentDescription(label);b.setOnClickListener(v->action.run());return b;}
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
 private void leftIcon(Button button,MenuIconDrawable.Kind kind,int color){button.setCompoundDrawablesRelativeWithIntrinsicBounds(new MenuIconDrawable(context,kind,color),null,null,null);button.setCompoundDrawablePadding(dp(6));}
 private void rightIcon(Button button,MenuIconDrawable.Kind kind,int color){button.setCompoundDrawablesRelativeWithIntrinsicBounds(null,null,new MenuIconDrawable(context,kind,color),null);button.setCompoundDrawablePadding(dp(6));}
 private void weighted(LinearLayout row,View child){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,controlHeight(),1);if(row.getChildCount()>0)lp.leftMargin=dp(8);row.addView(child,lp);}
 private void add(LinearLayout parent,View view,int gap){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(gap);parent.addView(view,lp);}
 private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(10),dp(10),dp(10),dp(10));c.setBackground(MenuPalette.surface(context,false));return c;}
 private void keyNav(LinearLayout parent,Property p,boolean resetPosition){LinearLayout nav=row();Button previous=button("Previous Key",false,()->{host.previousKey(p);refresh();}),next=button("Next Key",false,()->{host.nextKey(p);refresh();});leftIcon(previous,MenuIconDrawable.Kind.BACK,MenuPalette.TEXT);rightIcon(next,MenuIconDrawable.Kind.NEXT,MenuPalette.TEXT);weighted(nav,previous);if(resetPosition){Button reset=button("Reset all",false,()->{body.clearFocus();commandSafe(host::resetPosition);});weighted(nav,reset);}weighted(nav,next);readers.add(()->{previous.setEnabled(host.canPrevious(p));next.setEnabled(host.canNext(p));});add(parent,nav,8);}
 private LinearLayout numericCard(Property property,String label,String unit,boolean ruler,boolean selectable){return numericCard(property,label,unit,ruler,selectable,false);}
 private LinearLayout numericCard(Property property,String label,String unit,boolean ruler,boolean selectable,boolean applyRow){
  LinearLayout c=card(),line=row();Button diamond=button("",false,()->{commandSafe(()->host.toggleKey(property));});MenuIconDrawable keyIcon=new MenuIconDrawable(context,MenuIconDrawable.Kind.DIAMOND,MenuPalette.MUTED);diamond.setCompoundDrawablesRelativeWithIntrinsicBounds(keyIcon,null,null,null);diamond.setContentDescription(label+" keyframe");line.addView(diamond,new LinearLayout.LayoutParams(dp(48),dp(48)));
  TextView name=text(label,16,MenuPalette.TEXT,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=dp(8);line.addView(name,np);if(selectable){name.setMinHeight(dp(48));name.setGravity(Gravity.CENTER_VERTICAL);name.setFocusable(true);name.setContentDescription("Select "+label+" easing interval");name.setOnClickListener(v->{curveProperty=property;show(current);});}
  EditText input=new EditText(context);input.setSingleLine(true);input.setTextSize(17);input.setTextColor(MenuPalette.TEXT);input.setSelectAllOnFocus(true);input.setGravity(Gravity.CENTER);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED|InputType.TYPE_NUMBER_FLAG_DECIMAL);input.setImeOptions(EditorInfo.IME_ACTION_DONE);input.setBackground(MenuPalette.surface(context,false));input.setContentDescription(label+" exact value in "+unitWord(unit));line.addView(input,new LinearLayout.LayoutParams(dp(112),dp(48)));
  TextView units=text(unit,12,MenuPalette.MUTED,false);units.setPadding(dp(3),0,dp(3),0);line.addView(units);
  Runnable submit=()->{try{double n=Double.parseDouble(input.getText().toString().trim());if(!Double.isFinite(n)||n<host.minimum(property)||n>host.maximum(property))throw new NumberFormatException();if(n!=host.value(property)){beginEditSafe(label+" updated");previewValueSafe(property,n);finishEditSafe(true);}input.clearFocus();refresh();}catch(NumberFormatException ex){input.setError("Enter "+host.minimum(property)+" to "+host.maximum(property));host.reportInputError("Invalid "+label+" value");}};
  input.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN)){submit.run();return true;}return false;});
  Button resetButton=null;if(!applyRow){Button reset=button("Reset",false,()->{input.clearFocus();commandSafe(()->host.reset(property));});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(dp(68),dp(48));rp.leftMargin=dp(8);line.addView(reset,rp);readers.add(()->reset.setEnabled(host.editable(property)));resetButton=reset;}
  line.removeAllViews();add(c,new AdaptiveValueRow(context,diamond,name,input,units,resetButton),0);
  if(ruler&&property==Property.EFFECT_STRENGTH){effectSlider(c,property,label,input);}
  else if(ruler){RulerView v=new RulerView(context,label,host.minimum(property),host.maximum(property),host.step(property),new RulerView.Binding(){public double value(){return host.value(property);}public void begin(){input.clearFocus();beginEditSafe(label+" updated");}public void preview(double n){previewValueSafe(property,n);refresh();}public void commit(){finishEditSafe(true);refresh();}public void cancel(){finishEditSafe(false);refresh();}});add(c,v,6);readers.add(()->{v.setEnabled(host.editable(property));v.invalidate();});}
  if(applyRow){LinearLayout actions=row();Button apply=button("Apply",false,submit),reset=button("Reset",false,()->{input.clearFocus();commandSafe(()->host.reset(property));});weighted(actions,apply);weighted(actions,reset);readers.add(()->{apply.setEnabled(host.editable(property));reset.setEnabled(host.editable(property));});add(c,actions,8);if(property==Property.OPACITY){TextView note=text("0% transparent · 100% visible",12,MenuPalette.MUTED,false);note.setGravity(Gravity.CENTER);add(c,note,8);}}
  readers.add(()->{boolean enabled=host.editable(property);input.setEnabled(enabled);diamond.setEnabled(enabled);KeyVisual k=host.keyVisual(property);keyIcon.mark(k==KeyVisual.STATIC?MenuPalette.MUTED:MenuPalette.GREEN,k==KeyVisual.CURRENT_KEY,k==KeyVisual.ANIMATED);diamond.setContentDescription(label+" keyframe: "+(k==KeyVisual.CURRENT_KEY?"keyframe at playhead • tap to remove":k==KeyVisual.ANIMATED?"animated • tap to add a keyframe here":"not animated • tap to animate"));if(!input.hasFocus())input.setText(String.format(Locale.US,"%.2f",host.value(property)));});add(body,c,10);return c;
 }

 private void effectSlider(LinearLayout container,Property property,String label,EditText input){
  final boolean[] active={false},cancelling={false};
  SeekBar slider=new SeekBar(context){@Override public void setEnabled(boolean enabled){if(!enabled&&active!=null&&active[0]){active[0]=false;finishEditSafe(false);}super.setEnabled(enabled);}@Override public boolean onTouchEvent(MotionEvent e){boolean cancel=e.getActionMasked()==MotionEvent.ACTION_CANCEL;if(cancel)cancelling[0]=true;boolean result=super.onTouchEvent(e);if(cancel&&active[0]){active[0]=false;finishEditSafe(false);refresh();}cancelling[0]=false;return result;}
   @Override protected void onDetachedFromWindow(){if(active[0]){active[0]=false;finishEditSafe(false);}super.onDetachedFromWindow();}};
  slider.setMax(10000);slider.setContentDescription(label);slider.setProgressTintList(android.content.res.ColorStateList.valueOf(MenuPalette.CYAN));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(MenuPalette.CYAN));slider.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(MenuPalette.BORDER));
  slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){input.clearFocus();active[0]=true;beginEditSafe(label+" updated");}public void onStopTrackingTouch(SeekBar b){if(!active[0])return;active[0]=false;if(cancelling[0])finishEditSafe(false);else finishEditSafe(true);refresh();}public void onProgressChanged(SeekBar b,int value,boolean fromUser){if(!fromUser)return;double n=host.minimum(property)+(host.maximum(property)-host.minimum(property))*value/10000d;if(active[0]){previewValueSafe(property,n);refresh();}else{beginEditSafe(label+" updated");previewValueSafe(property,n);finishEditSafe(true);refresh();}}});
  add(container,slider,8);readers.add(()->{slider.setEnabled(host.editable(property));if(!active[0]){double range=host.maximum(property)-host.minimum(property);slider.setProgress(range<=0?0:(int)Math.round((host.value(property)-host.minimum(property))/range*10000));}});
 }
 private void dial(){RotationDialView v=new RotationDialView(context,new RotationDialView.Binding(){public double value(){return host.value(Property.ROTATION);}public void begin(){body.clearFocus();beginEditSafe("Rotation updated");}public void preview(double n){previewValueSafe(Property.ROTATION,n);refresh();}public void commit(){finishEditSafe(true);refresh();}public void cancel(){finishEditSafe(false);refresh();}});add(body,v,4);readers.add(()->{v.setEnabled(host.editable(Property.ROTATION));v.invalidate();});}
 private void presets(){LinearLayout c=card();add(c,text("Angle presets",17,MenuPalette.TEXT,true),0);LinearLayout columns=row();for(int angle:new int[]{45,90,180,360}){LinearLayout col=column();Button plus=button("+",false,()->nudgeAngle(angle)),minus=button("−",false,()->nudgeAngle(-angle));plus.setContentDescription("Add "+angle+" degrees");minus.setContentDescription("Subtract "+angle+" degrees");add(col,plus,0);TextView value=text(angle+"°",16,MenuPalette.TEXT,true);value.setGravity(Gravity.CENTER);add(col,value,6);add(col,minus,6);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(columns.getChildCount()>0)lp.leftMargin=dp(8);columns.addView(col,lp);readers.add(()->{plus.setEnabled(host.editable(Property.ROTATION));minus.setEnabled(host.editable(Property.ROTATION));});}add(c,columns,8);add(body,c,10);}
 private void nudgeAngle(double delta){double n=host.value(Property.ROTATION)+delta;if(n<host.minimum(Property.ROTATION)||n>host.maximum(Property.ROTATION)){host.reportInputError("Rotation exceeds the engine's supported range");return;}beginEditSafe("Rotation preset");previewValueSafe(Property.ROTATION,n);finishEditSafe(true);refresh();}
 private void blendModes(){LinearLayout c=card();add(c,text("Blend Mode",17,MenuPalette.TEXT,true),0);Blend[] modes=Blend.values();for(int i=0;i<modes.length;i+=3){LinearLayout r=row();for(int j=i;j<Math.min(i+3,modes.length);j++){Blend m=modes[j];String label=m==Blend.NORMAL?"Normal":m.name().substring(0,1)+m.name().substring(1).toLowerCase(Locale.US);boolean chosen=host.blend()==m;Button b=button(label,chosen,()->{commandSafe(()->host.selectBlend(m));show(current);});if(chosen){b.setText("●  "+label);b.setContentDescription(label+", selected");}weighted(r,b);readers.add(()->b.setEnabled(host.canSelectBlend(m)));}if(i+3>=modes.length&&host.hasMoreBlends()&&r.getChildCount()<3){boolean other=host.moreBlendSelected()>=0;Button more=button(other?"Other ✓":"More…",other,()->moreBlends());weighted(r,more);readers.add(()->more.setEnabled(host.editable(Property.OPACITY)));}for(int j=r.getChildCount();j<3;j++)weighted(r,new Space(context));add(c,r,8);}add(body,c,10);}
 private void moreBlends(){String[] labels=host.moreBlendLabels();if(labels.length==0)return;new android.app.AlertDialog.Builder(context).setTitle("More blend modes").setSingleChoiceItems(labels,host.moreBlendSelected(),(d,which)->{d.dismiss();commandSafe(()->host.selectMoreBlend(which));if(body!=null)show(current);}).setNegativeButton("Cancel",null).show();}
 private void effects(){Button add=button("Add Effects",false,()->commandSafe(host::addEffects));readers.add(()->add.setEnabled(host.canAddEffects()));leftIcon(add,MenuIconDrawable.Kind.ADD,MenuPalette.CYAN);add.setTextColor(MenuPalette.CYAN);add(body,add,8);if(!host.hasSelectedEffect())return;LinearLayout heading=card();add(heading,text(host.effectTitle(),21,MenuPalette.TEXT,true),0);LinearLayout controls=numericCard(Property.EFFECT_STRENGTH,"Effect strength","%",true,false,true);body.removeView(controls);controls.setBackground(null);controls.setPadding(0,dp(10),0,0);add(heading,controls,8);keyNav(heading,Property.EFFECT_STRENGTH,false);add(body,heading,10);curveCard(body,Property.EFFECT_STRENGTH,false);Button remove=button("Remove Effect",false,()->{commandSafe(host::removeSelectedEffect);show(current);});remove.setTextColor(MenuPalette.DANGER);leftIcon(remove,MenuIconDrawable.Kind.TRASH,MenuPalette.DANGER);android.graphics.drawable.GradientDrawable danger=MenuPalette.surface(context,false);danger.setStroke(dp(1),MenuPalette.DANGER);remove.setBackground(danger);add(body,remove,10);}
 private void curveCard(LinearLayout parent,Property property,boolean scaleChannels){LinearLayout c=card(),heading=row();TextView title=text("Easing Curve Editor",19,MenuPalette.TEXT,true);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button expand=button("",false,()->expandCurve(property));leftIcon(expand,MenuIconDrawable.Kind.EXPAND,MenuPalette.MUTED);expand.setContentDescription("Expand easing curve editor");heading.addView(expand,new LinearLayout.LayoutParams(dp(48),dp(48)));add(c,heading,0);
  if(scaleChannels){LinearLayout channels=row();weighted(channels,button("Width",property==Property.WIDTH,()->{curveProperty=Property.WIDTH;show(current);}));weighted(channels,button("Height",property==Property.HEIGHT,()->{curveProperty=Property.HEIGHT;show(current);}));add(c,channels,6);}
  TextView interval=text(host.intervalLabel(property),14,MenuPalette.MUTED,false);interval.setMinHeight(dp(48));interval.setGravity(Gravity.CENTER_VERTICAL);interval.setFocusable(true);interval.setClickable(true);interval.setOnClickListener(v->commandSafe(()->host.selectInterval(property)));add(c,interval,0);CurveEditorView graph=graph(property);add(c,graph,2);readers.add(()->{String label=host.intervalLabel(property);interval.setText(label);interval.setContentDescription("Easing keyframe: "+label+". Tap to choose a keyframe.");graph.setEnabled(host.hasInterval(property)&&host.editable(property));graph.invalidate();});
  LinearLayout footer=row();TextView help=text("Drag handles to adjust easing",12,MenuPalette.MUTED,false);footer.addView(help,new LinearLayout.LayoutParams(0,-2,1));Button reset=button("Reset curve",false,()->resetCurve(property));footer.addView(reset,new LinearLayout.LayoutParams(dp(112),dp(48)));readers.add(()->reset.setEnabled(host.hasInterval(property)&&host.editable(property)));add(c,footer,8);add(parent,c,10);
 }
 private CurveEditorView graph(Property property){return new CurveEditorView(context,new CurveEditorView.Model(){public CubicBezier curve(){return host.curve(property);}public boolean editable(){return host.hasInterval(property)&&host.editable(property);}public String intervalLabel(){return host.intervalLabel(property);}public double progressAt(double u){return host.curveProgress(property,u);}},new CurveEditorView.Edits(){public void begin(){beginCurveEditSafe(property,"Easing curve updated");}public void preview(CubicBezier c){previewCurveSafe(property,c);refresh();}public void commit(){finishEditSafe(true);refresh();}public void cancel(){finishEditSafe(false);refresh();}});}
 private void resetCurve(Property p){if(!host.hasInterval(p)||!host.editable(p))return;beginCurveEditSafe(p,"Easing curve reset");previewCurveSafe(p,CubicBezier.LINEAR);finishEditSafe(true);refresh();}
 private void expandCurve(Property property){Dialog dialog=new Dialog(context);LinearLayout expanded=column();expanded.setPadding(dp(16),dp(16),dp(16),dp(16));expanded.setBackgroundColor(MenuPalette.BACKGROUND);Button close=button("Back",false,dialog::dismiss);leftIcon(close,MenuIconDrawable.Kind.BACK,MenuPalette.TEXT);add(expanded,close,0);TextView title=text("Easing Curve Editor",22,MenuPalette.TEXT,true),interval=text(host.intervalLabel(property),14,MenuPalette.MUTED,false);add(expanded,title,10);add(expanded,interval,6);CurveEditorView graph=graph(property);expanded.addView(graph,new LinearLayout.LayoutParams(-1,0,1));Button reset=button("Reset curve",false,()->resetCurve(property));add(expanded,reset,8);
  Runnable reader=()->{interval.setText(host.intervalLabel(property));graph.setEnabled(host.hasInterval(property)&&host.editable(property));graph.invalidate();reset.setEnabled(host.hasInterval(property)&&host.editable(property));};dialogReaders.add(reader);dialog.setContentView(expanded);dialog.setOnDismissListener(d->{dialogReaders.remove(reader);refresh();});dialog.show();Window window=dialog.getWindow();if(window!=null){window.setLayout(-1,-1);window.setBackgroundDrawableResource(android.R.color.transparent);}reader.run();
 }
}

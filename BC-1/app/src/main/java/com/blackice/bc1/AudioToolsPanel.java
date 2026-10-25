package com.blackice.bc1;
import android.app.AlertDialog;
import android.widget.*;
import com.blackice.bc1.audio.*;
import com.blackice.bc1.editor.*;
import com.blackice.bc1.engine.bc.AudioAnalysis;
import java.util.concurrent.atomic.AtomicBoolean;
/** Non-destructive local audio controls, using the editor's undo and save path. */
final class AudioToolsPanel {
 static boolean ready(EditorActivity a){return a.layerEditable()&&a.clip()!=null&&!a.clip().image;}
 static void noise(EditorActivity a){
  if(!ready(a))return;EditProject.Clip c=a.clip();String id=a.project.id;
  new AlertDialog.Builder(a).setTitle("Noise reduction · local soft gate").setSingleChoiceItems(new String[]{"Off","Light","Medium","Strong"},c.audio.noise,(d,i)->{if(ready(a)&&id.equals(a.project.id)&&a.project.clips.contains(c))a.edit("Noise reduction updated",()->{c.audio.noise=i;c.audio.normalizationDb=0;});d.dismiss();}).setNegativeButton("Cancel",null).show();
 }
 static void eq(EditorActivity a){
  if(!ready(a))return;EditProject.Clip c=a.clip();String id=a.project.id;AudioSettings draft=c.audio.copy();LinearLayout box=a.form();String[] labels={"Preamp","80 Hz","250 Hz","1 kHz","4 kHz","10 kHz"};
  for(int i=0;i<labels.length;i++){
   final int band=i;TextView title=new TextView(a);box.addView(title);SeekBar slider=new SeekBar(a);slider.setMax(48);slider.setContentDescription(labels[i]);float value=i==0?draft.preampDb:draft.eqDb[i-1];slider.setProgress(Math.round((value+12)*2));title.setText(labels[i]+": "+value+" dB");box.addView(slider);
   slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int p,boolean u){float v=p/2f-12;if(band==0)draft.preampDb=v;else draft.eqDb[band-1]=v;title.setText(labels[band]+": "+v+" dB");}});
  }
  ScrollView scroll=new ScrollView(a);scroll.addView(box);
  new AlertDialog.Builder(a).setTitle("Equalizer").setView(scroll).setPositiveButton("Apply",(d,w)->{if(ready(a)&&id.equals(a.project.id)&&a.project.clips.contains(c))a.edit("EQ updated",()->{draft.normalizationDb=0;c.audio=draft.copy();});}).setNeutralButton("Flat",(d,w)->{if(ready(a)&&id.equals(a.project.id)&&a.project.clips.contains(c))a.edit("EQ reset",()->{java.util.Arrays.fill(c.audio.eqDb,0);c.audio.preampDb=0;c.audio.normalizationDb=0;});}).setNegativeButton("Cancel",null).show();
 }
 static void normalize(EditorActivity a){
  if(!ready(a))return;new AlertDialog.Builder(a).setTitle("Loudness normalization").setItems(new String[]{"−16 LUFS","−23 LUFS","−14 LUFS","Remove normalization"},(d,i)->{if(!ready(a))return;EditProject.Clip c=a.clip();if(i==3){a.edit("Normalization removed",()->c.audio.normalizationDb=0);return;}measure(a,c,new double[]{-16,-23,-14}[i]);}).setNegativeButton("Cancel",null).show();
 }
 static void measure(EditorActivity a,EditProject.Clip c,double target){
  a.pausePlayback();if(a.player!=null)a.player.pause();String id=a.project.id;long revision=a.editRevision;EditProject.Clip copy=c.copy();AtomicBoolean stop=new AtomicBoolean();
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Measuring loudness locally").setMessage("Preparing…").setNegativeButton("Cancel",(d,w)->stop.set(true)).setCancelable(false).show();
  new Thread(()->{try{
   LoudnessMeter meter=AudioAnalysis.measure(a.getApplicationContext(),copy,stop,p->a.main.post(()->{if(dialog.isShowing())dialog.setMessage(p+"%");}));float gain=(float)meter.gainFor(target);
   a.main.post(()->{dialog.dismiss();if(stop.get()||a.isDestroyed())return;if(!id.equals(a.project.id)||revision!=a.editRevision||!a.project.clips.contains(c)){a.tell("Project changed. Measure again.");return;}
    new AlertDialog.Builder(a).setTitle("Apply normalization?").setMessage(String.format(java.util.Locale.ROOT,"Measured %.1f LUFS. Apply %+.1f dB. Limited to ±24 dB and −1 dBFS sample peaks; the target may not be reachable. This is not a true-peak limiter.",meter.integrated(),gain)).setPositiveButton("Apply",(d,w)->{if(ready(a)&&id.equals(a.project.id)&&revision==a.editRevision&&a.project.clips.contains(c))a.edit("Loudness normalized",()->c.audio.normalizationDb=gain);else a.tell("Project changed. Measure again.");}).setNegativeButton("Cancel",null).show();
   });
  }catch(Exception e){a.main.post(()->{dialog.dismiss();if(!a.isDestroyed()&&!stop.get())a.tell("Measurement failed: "+e.getMessage());});}},"ice-audio-analysis").start();
 }
}

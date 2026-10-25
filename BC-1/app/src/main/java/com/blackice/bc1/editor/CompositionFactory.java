package com.blackice.bc1.editor;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import androidx.media3.common.*;
import androidx.media3.common.audio.GainProcessor;
import androidx.media3.common.util.Size;
import androidx.media3.effect.*;
import androidx.media3.transformer.*;
import com.google.common.collect.ImmutableList;
import java.util.*;

/** The Android backend for the portable EditProject. Preview and export use the same graph. */
@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class CompositionFactory {
    private CompositionFactory() {}
    public static DefaultVideoFrameProcessor.Factory frameProcessor() {
        return new DefaultVideoFrameProcessor.Factory.Builder()
                .setGlObjectsProvider(new Gles3Provider())
                .setSdrWorkingColorSpace(DefaultVideoFrameProcessor.WORKING_COLOR_SPACE_ORIGINAL).build();
    }
    /** Goldfish's host decoder lost buffer-pool handles during repeated CI exports.
     * Prefer Android software decoders on emulators only; physical devices keep hardware ordering. */
    public static AssetLoader.Factory assetLoader(Context context) {
        DefaultDecoderFactory.Builder decoder=new DefaultDecoderFactory.Builder(context).setEnableDecoderFallback(true);
        if(android.os.Build.HARDWARE.contains("ranchu")||android.os.Build.HARDWARE.contains("goldfish")) {
            decoder.setMediaCodecSelector((mime,secure,tunneling)->{
                List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> codecs=
                        androidx.media3.exoplayer.mediacodec.MediaCodecSelector.DEFAULT.getDecoderInfos(mime,secure,tunneling);
                List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> compatible=new ArrayList<>();
                for(androidx.media3.exoplayer.mediacodec.MediaCodecInfo codec:codecs)if(!codec.name.contains("goldfish"))compatible.add(codec);
                return compatible.isEmpty()?codecs:compatible;
            });
        }
        return new DefaultAssetLoaderFactory(context,decoder.build(),androidx.media3.common.util.Clock.DEFAULT,null);
    }
    public static Composition build(Context context,EditProject project,boolean export) {
        return buildAtHeight(context,project,export?1080:project.previewHeight,!export);
    }
    /** Export semantics: hidden layers and muted audio are omitted; solo is ignored. */
    public static Composition buildAtHeight(Context context,EditProject project,int shortEdge) {
        return buildAtHeight(context,project,shortEdge,false);
    }
    /** The live (seekable) preview player's graph. In Media3 1.11 DefaultFrameDroppingShaderProgram.flush()
     * releases its previous-frame texture without clearing it, so the first frame after a seek draws into a
     * deleted framebuffer (GL error 0x506, "Error processing video frames"). The live preview therefore plays
     * source frames without frame-rate conversion; export and the transcoded preview, which never seek, keep it. */
    public static Composition buildLive(Context context,EditProject project) {
        return buildAtHeight(context,project,project.previewHeight,true,false);
    }
    /** Preview additionally isolates soloed layers. */
    public static Composition buildAtHeight(Context context,EditProject project,int shortEdge,boolean preview) {
        return buildAtHeight(context,project,shortEdge,preview,true);
    }
    private static Composition buildAtHeight(Context context,EditProject project,int shortEdge,boolean preview,boolean frameDrop) {
        project.validate(); if(project.clips.isEmpty()) throw new IllegalArgumentException("Import a video or image first.");
        int longEdge=(shortEdge*16/9+1)/2*2;
        int w=project.portrait?shortEdge:longEdge, h=project.portrait?longEdge:shortEdge;
        List<EditProject.Placement> placements=project.placements(); long duration=project.durationMs();
        List<EditedMediaItemSequence> sequences=new ArrayList<>();
        List<List<EditProject.Placement>> videoInputs=new ArrayList<>();
        // Input zero is topmost. Explicit layers sit above the two alternating base streams.
        List<List<EditProject.Placement>> groups=new ArrayList<>();
        for(int layer=EditProject.LAST_LAYER;layer>=1;layer--){List<EditProject.Placement> list=new ArrayList<>();for(EditProject.Placement p:placements)if(p.clip.track==layer)list.add(p);if(!list.isEmpty())groups.add(list);}
        List<EditProject.Placement> even=new ArrayList<>(),odd=new ArrayList<>();int baseIndex=0;
        for(EditProject.Placement p:placements)if(p.clip.track==0){(baseIndex++%2==0?even:odd).add(p);}
        if(!even.isEmpty())groups.add(even);if(!odd.isEmpty())groups.add(odd);
        for(int layer=EditProject.AUDIO_FIRST;layer<=EditProject.AUDIO_LAST;layer++){List<EditProject.Placement> list=new ArrayList<>();for(EditProject.Placement p:placements)if(p.clip.track==layer)list.add(p);if(!list.isEmpty())groups.add(list);}
        // Media3 1.11 creates the shared audio output from the first sequence that carries audio. A later
        // sequence that also declares audio but starts with a picture asks for synthetic silence and races
        // that creation (NullPointerException in SequenceAssetLoader). So only the first video sequence
        // carries audio; the sound of other visual layers moves to parallel audio-only sequences.
        List<EditedMediaItemSequence> separateAudio=new ArrayList<>();
        for(List<EditProject.Placement> group:groups){
            group.sort(Comparator.comparingLong(p->p.startMs));boolean audioOnly=EditProject.audioTrack(group.get(0).clip.track),primary=sequences.isEmpty()&&!audioOnly;
            if(!audioOnly)videoInputs.add(group);
            boolean videoOnly=!audioOnly&&!primary;
            EditedMediaItemSequence.Builder sequence=new EditedMediaItemSequence.Builder(audioOnly?Set.of(C.TRACK_TYPE_AUDIO):videoOnly?Set.of(C.TRACK_TYPE_VIDEO):Set.of(C.TRACK_TYPE_VIDEO,C.TRACK_TYPE_AUDIO));
            EditedMediaItemSequence.Builder sound=videoOnly?new EditedMediaItemSequence.Builder(Set.of(C.TRACK_TYPE_AUDIO)):null;boolean hasSound=false;long cursor=0,soundCursor=0;
            for(EditProject.Placement p:group){
                if(p.startMs>cursor)sequence.addGap((p.startMs-cursor)*1000);
                EditProject.Clip c=p.clip;
                EditProject.Clip previous=null;int ordinal=0;
                if(c.track==0)for(EditProject.Placement candidate:placements){if(candidate.index==p.index)break;if(candidate.clip.track==0){previous=candidate.clip;ordinal++;}}
                boolean renders=LayerOps.renders(project,c,preview);
                List<Effect> effects=new ArrayList<>();if(!audioOnly){effects.add(Presentation.createForWidthAndHeight(w,h,Presentation.LAYOUT_SCALE_TO_FIT));effects.add(new ClipEffect(p,previous,ordinal%2==0,renders));if(frameDrop)effects.add(FrameDropEffect.createDefaultFrameDropEffect(project.frameRate));}
                // Audio tracks honour mute; a hidden picture keeps its own sound (mute it with volume).
                float volume=audioOnly&&!renders?-1:c.volume;
                // Pictures carry no sound, so their (silent) audio path is left untouched.
                if(!audioOnly&&!c.image&&preview&&LayerOps.anySolo(project)&&!renders)volume=-1;
                sequence.addItem(item(c,audioOnly,videoOnly,volume,effects,project.frameRate));cursor=p.endMs;
                if(sound!=null){if(!c.image&&hasAudio(context,c.uri)){if(p.startMs>soundCursor)sound.addGap((p.startMs-soundCursor)*1000);sound.addItem(item(c,true,false,volume,List.of(),project.frameRate));soundCursor=p.endMs;hasSound=true;}}
            }
            if(cursor<duration)sequence.addGap((duration-cursor)*1000);sequences.add(sequence.build());
            if(hasSound){if(soundCursor<duration)sound.addGap((duration-soundCursor)*1000);separateAudio.add(sound.build());}
        }
        // Video inputs keep the lowest sequence indices (the compositor addresses them by index).
        int firstAudio=0;while(firstAudio<sequences.size()&&firstAudio<videoInputs.size())firstAudio++;
        sequences.addAll(firstAudio,separateAudio);
        if(videoInputs.isEmpty())throw new IllegalArgumentException("Keep at least one video or image layer in the project.");
        if(project.music!=null && project.music.startMs<duration) {
            EditProject.Music m=project.music;
            EditedMediaItemSequence.Builder audio=new EditedMediaItemSequence.Builder(Set.of(C.TRACK_TYPE_AUDIO));
            if(m.startMs>0) audio.addGap(m.startMs*1000);
            long cursor=m.startMs; int count=0;
            do {
                long length=Math.min(m.outMs-m.inMs,duration-cursor);
                MediaItem item=new MediaItem.Builder().setUri(m.uri).setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(m.inMs).setEndPositionMs(m.inMs+length).build()).build();
                audio.addItem(new EditedMediaItem.Builder(item).setRemoveVideo(true).setDurationUs(m.sourceDurationMs*1000)
                        .setEffects(new Effects(List.of(gain(m.volume)),List.of())).build());
                cursor+=length;
                if(++count>2000) throw new IllegalArgumentException("Music segment is too short to loop across this project. Use a longer trim.");
            } while(m.loop && cursor<duration);
            if(cursor<duration) audio.addGap((duration-cursor)*1000);
            sequences.add(audio.build());
        }
        List<Effect> finalEffects=new ArrayList<>();
        List<EditProject.Overlay> overlays=new ArrayList<>();for(EditProject.Overlay o:project.overlays)if(LayerOps.renders(project,o,preview))overlays.add(o);
        if(!overlays.isEmpty()) finalEffects.add(new OverlayEffect(ImmutableList.of(new ProjectOverlay(context,overlays))));
        finalEffects.add(Presentation.createForWidthAndHeight(w,h,Presentation.LAYOUT_SCALE_TO_FIT));
        if(frameDrop)finalEffects.add(FrameDropEffect.createDefaultFrameDropEffect(project.frameRate));
        Composition.Builder composition=new Composition.Builder(sequences)
                .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL);
        // Audio-only sequences do not make a multi-input video graph. Media3's
        // single-input graph rejects custom compositor settings altogether.
        if(videoInputs.size()>1) composition.setVideoCompositorSettings(new VideoCompositorSettings() {
                    @Override public Size getOutputSize(List<Size> sizes) { return new Size(w,h); }
                    @Override public OverlaySettings getOverlaySettings(int inputId,long timeUs) {
                        long ms=timeUs/1000; boolean active=false;
                        for(EditProject.Placement p:inputId<videoInputs.size()?videoInputs.get(inputId):List.<EditProject.Placement>of()) if(ms>=p.startMs && ms<p.endMs) {active=true;break;}
                        return new StaticOverlaySettings.Builder().setAlphaScale(active?1:0).build();
                    }
                });
        return composition.setEffects(new Effects(List.of(),finalEffects)).build();
    }
    private static EditedMediaItem item(EditProject.Clip c,boolean removeVideo,boolean removeAudio,float volume,List<Effect> effects,int frameRate){
        MediaItem.Builder source=new MediaItem.Builder().setUri(c.uri);
        if(c.image)source.setMimeType(MimeTypes.IMAGE_PNG).setImageDurationMs(c.durationMs());else source.setClippingConfiguration(new MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.inMs).setEndPositionMs(c.outMs).build());
        return new EditedMediaItem.Builder(source.build()).setFrameRate(frameRate).setRemoveVideo(removeVideo).setRemoveAudio(removeAudio&&!removeVideo)
            .setSpeed(new androidx.media3.common.audio.SpeedProvider(){public float getSpeed(long timeUs){return c.speed;}public long getNextSpeedChangeTimeUs(long timeUs){return C.TIME_UNSET;}})
            .setDurationUs((c.image?c.durationMs():c.sourceDurationMs)*1000)
            .setEffects(new Effects(removeAudio?List.of():List.of(new com.blackice.bc1.audio.ClipAudioProcessor(c.audio),clipGain(c,volume<0)),effects)).build();
    }
    private static final Map<String,Boolean> AUDIO_TRACKS=new java.util.concurrent.ConcurrentHashMap<>();
    /** Whether a source has an audio track (cached; local files only, so this is quick). */
    static boolean hasAudio(Context context,String uri){
        Boolean known=AUDIO_TRACKS.get(uri);if(known!=null)return known;
        boolean result;try{result=AudioWaveform.hasAudio(context,uri);}catch(Exception e){result=false;}
        AUDIO_TRACKS.put(uri,result);return result;
    }
    private static GainProcessor clipGain(EditProject.Clip clip,boolean muted){
        EditProject.Clip snapshot=clip.copy();
        return new GainProcessor(new GainProcessor.GainProvider(){
            public float getGainFactorAtSamplePosition(long sample,int rate){return ClipAudio.gain(snapshot,sample*1000.0/rate,muted);}
            public long isUnityUntil(long sample,int rate){if(!muted&&snapshot.unmutedVolume<0&&snapshot.volume==1&&snapshot.audio.normalizationDb==0&&snapshot.fadeInMs==0&&snapshot.fadeOutMs==0&&snapshot.motion.curves.get(Motion.VOLUME).isEmpty())return C.TIME_END_OF_SOURCE;return getGainFactorAtSamplePosition(sample,rate)==1?sample+1:C.TIME_UNSET;}
        });
    }
    private static GainProcessor gain(float volume){return gain(volume,0,0,0);}
    private static GainProcessor gain(float volume,long duration,long fadeIn,long fadeOut) {
        return new GainProcessor(new GainProcessor.GainProvider() {
            @Override public float getGainFactorAtSamplePosition(long sample,int rate) { long ms=sample*1000/rate;float envelope=1;if(fadeIn>0)envelope=Math.min(envelope,(float)ms/fadeIn);if(fadeOut>0)envelope=Math.min(envelope,(float)(duration-ms)/fadeOut);return volume*Math.max(0,envelope); }
            @Override public long isUnityUntil(long sample,int rate) {
                if(volume==1&&fadeIn==0&&fadeOut==0)return C.TIME_END_OF_SOURCE;
                // GainProcessor calls this whenever the envelope reaches exactly 1,
                // including its plateau and a single sample crossing at volume > 1.
                return getGainFactorAtSamplePosition(sample,rate)==1?sample+1:C.TIME_UNSET;
            }
        });
    }
    /** Upload only when timed overlay visibility changes, not once per decoded frame. */
    private static final class ProjectOverlay extends BitmapOverlay {
        private final OverlayPainter painter;
        private final List<EditProject.Overlay> overlays;
        private Bitmap bitmap;
        private int width,height,activeMask=-1;
        ProjectOverlay(Context context,List<EditProject.Overlay> overlays) { painter=new OverlayPainter(context);this.overlays=overlays; }
        @Override public void configure(Size size) {super.configure(size);width=size.getWidth();height=size.getHeight();}
        @Override public Bitmap getBitmap(long timeUs) throws VideoFrameProcessingException {
            int mask=0; for(int i=0;i<overlays.size();i++) {EditProject.Overlay o=overlays.get(i); if(timeUs/1000>=o.startMs && timeUs/1000<o.endMs) mask|=1<<i;}
            if(bitmap==null || bitmap.getWidth()!=width || bitmap.getHeight()!=height) {
                if(bitmap!=null) bitmap.recycle(); bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);activeMask=-1;
            }
            boolean animated=false;for(EditProject.Overlay o:overlays)animated|=o.motion.animated();
            if(activeMask==mask&&!animated) return bitmap;
            bitmap.eraseColor(Color.TRANSPARENT); Canvas canvas=new Canvas(bitmap);
            try {
                for(int i=0;i<overlays.size();i++) if((mask&(1<<i))!=0) {
                    EditProject.Overlay o=overlays.get(i);long local=Math.max(0,timeUs/1000-o.startMs);
                    painter.draw(canvas,width,height,o,local);
                }
            } catch(java.io.IOException e) {throw new VideoFrameProcessingException(e);}
            activeMask=mask; return bitmap;
        }
        @Override public void release() throws VideoFrameProcessingException {super.release();if(bitmap!=null)bitmap.recycle();painter.release();}
    }
}

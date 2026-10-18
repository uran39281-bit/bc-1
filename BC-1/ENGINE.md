# ICE Lite — the Black Ice custom engine

(Developed under the working name "BC Core"; code packages `core/` and `engine/bc/` keep their names.)

ICE Lite is BC-1's own render and export engine. It runs beside the shipping Media3 pipeline and is
selected in **Setup → Export engine → ICE Lite (experimental)**. Media3 stays the default until ICE Lite
has passed device QA.

## Layout

```
core/            pure Java, no Android imports — shared by every backend
  RenderPlan     what to draw at a timeline instant: ordered layers, source times, transforms,
                 effects, opacity, transition role/progress (ClipEffect uses the same transition code)
  AudioPlan      every sound source as a timed segment with volume and fade envelope; music looping
  AudioMixer     mixes an AudioPlan block by block into 48 kHz stereo float PCM
  Resampler      streaming linear resampler; playback speed folded into the ratio
  FrameClock     exact frame timestamps (no cumulative rounding)
  OutputSpec     frame size, frame rate, bitrates
  ExportEngine   engine choice persisted in the export preferences
  ExportPlanner  resolution / frame rate / codec plan with device fallback; bitrate; size estimate
  VideoCodec     H.264 or HEVC (H.265); Auto / H.264 / HEVC preference
  Staging        splits layers into render passes when decoders run short

engine/bc/       Android backend (MediaCodec + GLES 3)
  BcExporter     orchestrates one export on a worker thread
  VideoSource    frame-accurate decoder in MediaCodec async mode (each layer decodes in parallel)
                 → SurfaceTexture → fitted frame texture
  DecoderBudget  decoders an export may open at once (device instance limit, memory estimate)
  EncoderCaps    which codec / size / frame rate the device can encode; DeviceVideoReport
  ImageSource    still picture decoded once, fitted once
  Compositor     fit passes + the shipping layer shader (assets/shaders/video.frag) + overlays
  AudioDecoderSource / AudioEncoder   source PCM in, AAC out (staged in a private temp file)
  EglCore, GlKit encoder-surface EGL context and GL helpers
editor/OverlayPainter   text/shape/image overlay drawing shared by both engines
```

## Export pipeline

1. **Audio pass.** `AudioPlan` → `AudioMixer` (decoders + resampling + gains) → AAC encoder. Encoded
   access units are written to a temp file with their timestamps.
2. **Video pass.** For every frame `n` at `n / fps`: `RenderPlan.layersAt(t)` → each layer's source is
   advanced to its exact source time → layers composited bottom-to-top over black → overlays → the
   encoder input surface receives the frame with its presentation time.
3. **Muxing.** The muxer starts once the H.264 format is known; both track formats are known at that
   point, and buffered audio is interleaved up to each video timestamp. There is no race between two
   encoders feeding one muxer.

Each timeline frame produces exactly one encoded picture. Audio is AAC 128 kb/s, 48 kHz stereo.

## Resolution, frame rate and bitrate

- **Setup → Export resolution:** 1080p, 1440p or 2160p (4K). **Setup → Frame rate:** 24, 25, 30, 50, 60
  or 120 fps (also the timeline frame grid). Both engines honour these.
- **Bitrate** scales with the frame: 8 Mb/s at 1080p30, ~25 Mb/s at 1440p60, ~56 Mb/s at 4K60,
  ~97 Mb/s at 4K120 (`ExportPlanner.videoBitrate`).
- **Device check.** Before exporting, `EncoderCaps` asks the phone's H.264 encoders what they can do and
  `ExportPlanner` picks the first supported preset: the requested one, then the same size down to half
  the frame rate (never below 30), then smaller sizes at the full rate, then the rest.
  4K120 → 4K60 → 1440p120 → 1080p120 → … The export status says when it stepped down.
- **High-fps sources.** The decoder looks one frame ahead and never sends superseded frames to the GPU,
  so a 120 fps clip in a 30 fps export renders one source frame in four.
- Exports need not run in real time: 4K120 may take several times the clip length on a phone.

## Codecs (H.264 and HEVC)

**Setup → Export codec:** Auto (default) keeps H.264 whenever the device can encode the preset and
switches to HEVC only when it must (an encoder that stops at 4K60 in H.264, say). Measured on a Galaxy
S24+ (Snapdragon 8 Gen 3): the hardware H.264 and HEVC encoders both report 4K120, so Auto keeps H.264
there; choose HEVC to roughly halve the file. HEVC uses ~60% of the H.264 bitrate: 4K120 ≈ 58 Mb/s
(≈1.7 GB for four minutes) against ≈97 Mb/s (≈2.9 GB) in H.264. Both
engines honour the codec. The export dialog shows the exact plan and estimated size before starting,
and export refuses to start without room for the file twice (private copy + saved copy).

## Layers and decoders

Projects have **eight visual tracks**: the main sequence plus layers 1–7. Audio moved from tracks 4–7 to
8–11 (project schema 5); older projects and saved elements are remapped when opened.

Every video layer needs a hardware decoder while it is on screen. ICE Lite estimates a **decoder budget**
(the device's reported decoder instances, bounded by memory for the largest source). When a project
needs more decoders at once than the budget, export renders in **passes**: the first pass renders the
lowest layers to a private intermediate video (same codec, twice the bitrate); each next pass decodes
that video as its background and adds the next layers; the last pass adds titles and audio. Alpha-over
compositing makes this identical to one pass, apart from one extra encode generation per pass. If a
decoder still cannot be created mid-export, ICE Lite lowers the budget and retries automatically.
**Setup → Device video capabilities** lists what this phone's codecs report, with a copyable report.

## Blend modes

Every visual clip has a **blend mode** (Effects → Blend mode): Normal, Darken, Multiply, Color Burn, Linear
Burn, Darker Color, Lighten, Screen, Color Dodge, Linear Dodge (Add), Lighter Color, Overlay, Soft Light,
Hard Light, Vivid Light, Linear Light, Pin Light, Hard Mix, Difference, Exclusion, Subtract, Divide, Hue,
Saturation, Color and Luminosity. They are BC-1's own implementation of the public W3C *Compositing and
Blending* formulas plus the usual photo-editing extensions (`core/BlendMode`: a Java reference and the
matching GLSL). Frames that contain a blended layer are composed in two off-screen buffers so the layer can
read what lies beneath; frames without one take the direct path. Media3 cannot render blend modes, so
projects that use them export with ICE Lite and get an ICE Lite prepared preview.

## Engine routing

Each export picks its engine from the setting plus the project: more than four simultaneous video layers
or any blend mode → ICE Lite; HDR (PQ/HLG) clips → Media3, which tone-maps them, unless the project needs
ICE Lite anyway. The saved-export message says when the engine differed from the setting and why.

## Known differences from the Media3 path

- Letterbox bars of upper layers are transparent (lower layers show through).
- Audio speed changes pitch (linear resampling). A pitch-preserving time stretch is a later stage.
- Live preview uses Media3/ExoPlayer; prepared previews of heavy or blended projects are rendered by ICE Lite.
- Galaxy S24+ (SM8650) benchmark: 8 layers × 1080p60 exported at 72.6 frames/s (1.21× real time),
  one pass, direct layer drawing, decoder budget 16 (16 hardware decoder instances, 11.6 GB).
- 4K120 itself is verified only on real hardware; the CI emulator verifies 60 fps output from a 120 fps
  source, the planner on its own encoder list, and the planner rules in unit tests.

## Verification

- JVM unit tests: `core/RenderPlanTest`, `core/AudioEngineTest`, `core/ExportPlannerTest`, `core/StagingTest`, `core/BlendModeTest`,
  `editor/ProjectMigrationTest` (schema 4 → 5 track remap).
- Device tests: `engine/BcCoreExportTest` (pixel checks across a dissolve, exact frame count, AAC
  present, portrait/layers/pictures/speed, cancellation, HEVC, 60 fps from a 120 fps source, three-pass
  staged render matching the single-pass render on layers 1/4/7, device report and budget, all 26 blend
  modes rendered on the GPU and compared with the Java reference). CI decodes `qa/bc-core-1080p.mp4`
  independently with ffmpeg/ffprobe and checks 105 frames, 48 kHz stereo AAC and A/V sync.

## Roadmap

- Next: desktop backend (FFmpeg via JavaCV for decode/encode, LWJGL OpenGL running the same shaders)
  on top of the unchanged `core` package.
- Phase 3: ICE Lite preview player (same RenderPlan, display surface instead of encoder surface).
- Pitch-preserving speed, hardware decoder pooling for many simultaneous layers.

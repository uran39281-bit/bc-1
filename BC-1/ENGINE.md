# BC Core — the Black Ice custom engine

BC Core is BC-1's own render and export engine. It runs beside the shipping Media3 pipeline and is
selected in **Setup → Export engine → BC Core (experimental)**. Media3 stays the default until BC Core
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

engine/bc/       Android backend (MediaCodec + GLES 3)
  BcExporter     orchestrates one export on a worker thread
  VideoSource    frame-accurate forward decoder → SurfaceTexture → fitted frame texture
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

## Known differences from the Media3 path

- Letterbox bars of upper layers are transparent (lower layers show through).
- Audio speed changes pitch (linear resampling). A pitch-preserving time stretch is a later stage.
- Preview still uses Media3/ExoPlayer at up to 1080p; BC Core is export-only in this phase.
- 4K120 itself is verified only on real hardware; the CI emulator verifies 60 fps output from a 120 fps
  source, the planner on its own encoder list, and the planner rules in unit tests.

## Verification

- JVM unit tests: `core/RenderPlanTest`, `core/AudioEngineTest`.
- Device tests: `engine/BcCoreExportTest` (pixel checks across a dissolve, exact frame count, AAC
  present, portrait/layers/pictures/speed, cancellation). CI decodes `qa/bc-core-1080p.mp4`
  independently with ffmpeg/ffprobe and checks 105 frames, 48 kHz stereo AAC and A/V sync.

## Roadmap

- Phase 2: desktop backend (FFmpeg via JavaCV for decode/encode, LWJGL OpenGL running the same shaders)
  on top of the unchanged `core` package.
- Phase 3: BC Core preview player (same RenderPlan, display surface instead of encoder surface).
- Pitch-preserving speed, hardware decoder pooling for many simultaneous layers.

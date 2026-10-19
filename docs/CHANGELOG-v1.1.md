# Black Ice Editor 1.1.0 — cinematic redesign preview

## 1.2.1 — live edits

- Edits show at once while paused: the ICE Lite live preview draws the frame at the playhead, so moving, trimming, effects, sliders, layers and paused scrubbing no longer show "Preparing preview" or need Play to appear.
- Projects with layers or several clips still play from a rendered preview; it is now prepared in the background after edits settle, and Play joins a render already running.
- Switching a clip's effect compiles only that effect (a small pass of its own) in the live preview. Export is unchanged.

## 1.2.0 — ICE Lite engine, timeline and media update

- ICE Lite render/export engine beside Media3 (Setup → Export engine); projects Media3 cannot render are routed to ICE Lite automatically.
- Export up to 4K and 120 fps (device permitting), H.264 or HEVC, with a size estimate before export.
- Eight visual layers with staged decoding when decoders run short; 26 blend modes and 36 Black Ice effects.
- Project schema 5 (audio on tracks 8–11); older projects are migrated when opened.
- Includes the timeline and media update below. The preview is unchanged from 1.1.2 (Media3); the ICE Lite paused live preview is not included.

## 1.1.2 — preview scrubbing recovery

- Fixes "Preview error: Error processing video frames" after scrubbing the timeline quickly on single-clip projects (the live preview).
- Cause, confirmed against Media3 1.11.1 source and CI logs: (1) `DefaultFrameDroppingShaderProgram.reset()` releases its previous-frame texture without clearing it, so the first frame after a seek draws into a deleted framebuffer (GL 0x506); (2) `MultipleInputVideoGraph.flush()` flushes only its input processors, not the compositor, so the first post-seek frame finds no free output texture.
- The live preview now uses `SingleInputVideoGraph` (it only ever holds one clip) without the frame-drop effect. Export and the transcoded preview proxy keep frame-rate conversion, so exported cadence is unchanged.
- Seeks are sent one at a time; newer scrub positions update the target and are sent when the player settles. After a player error the preview rebuilds itself at the same position (up to three times in 30 s).
- Silent buffering stalls are also watched: after three seconds, rebuild at the latest requested position using the same bounded recovery budget. The final seek is watched even after touch input stops; lifecycle cleanup cancels pending checks. This mitigates the observed stall without claiming its Media3 root cause is fixed.
- Export diagnostics regression uses the installed version instead of a hardcoded old version.
- Diagnostics: `BC1Preview` logcat tag, preview state in `PreviewScrubTest` failure messages, preview log kept in CI.

## Timeline and media update (1.2.0)

- The timeline starts at 0 s beside the layer column with a moving playhead (no more half-screen lead-in). Tap or drag the ruler to move the playhead; drag empty space to scroll; zoom keeps the time under the playhead or pinch in place.
- Layers can be dragged straight away, without selecting first. Dragging up or down moves a clip to another row; an empty visual and an empty audio row are always offered for creating a new layer.
- The main sequence can hold gaps: a clip dragged later keeps the empty space before it, and moving a clip away leaves the others where they are (later clips only shift when a clip is dropped on top of them). Close gaps on the main track removes them. A gap suppresses the transition into that clip. Stored as an optional `gapMs` per clip; older projects open unchanged.
- Imports go to Project Media, a per-project media bin (optional `library` in the project file, rebuilt from timeline sources for older projects; bundles carry it). Long-press a card and drag it onto a timeline row; tap for Add at playhead, favorites and removal.
- One set of navigation per layout: wide windows use the top-bar tabs (Media, Edit, Effects, Audio, Text, Layers) without the side rail, the Add panel's own category row or a separate + Add button; timeline zoom lives only in the timeline toolbar. Undo/Redo appear in the top bar on wide windows and in the transport otherwise.
- Closer to the V1.1 reference: icon transport with a round Play button, Save/Settings with icons and a filled Export, media cards with duration badges, a timeline toolbar with zoom slider and snapping magnet, reference clip colours and the "Nothing selected" inspector.

## Interface

- Centralized BlackIceTheme palette and reusable gradient surfaces; native monoline cube/icon styling and subtle static orbital decoration.
- Persistent desktop branding, project name, Media/Edit/Effects/Audio/Export navigation, Save and undo/redo actions. Autosave-aware unsaved indicator.
- Three-column wide workspace with a full-width bottom timeline and draggable timeline height. Short landscape windows retain reachable horizontal navigation and omit a redundant toolbar to preserve preview space.
- Shared phone workspace with touch controls and contextual Inspector/Project sections; no second editing engine or HTML replacement.
- Slim selected-only timeline actions, media-specific clip colors and preserved timeline gestures, source bounds, selection and history.
- Project-media favorites, sorting and grid/list mode; real project clips and asynchronous thumbnails.
- View-only Fit/Fill/100% scaling, preview volume, fullscreen exit and previous/next frame controls. Preview metadata refreshes after project load.
- Reduced-motion setting; inspector animation also respects disabled system animators. No moving-content blur.

## Compatibility

Package `com.blackice.bc1.preview`, versionCode18, versionName1.1.0. Project schema4 and legacy readers unchanged. Existing save/reopen, export, GLES3 effects, keyframes, audio, snapping, transforms and undo/redo are retained. No 3D Page Curl; no new rendering backend or heavy dependency.

## Scope still open

This is a working redesign preview, not full completion of the supplied specification. Exact mockup parity, a draggable phone inspector drawer, reverse playback, independent effect tracks and pen/draw remain open. Preview changes can require proxy preparation. Physical Snapdragon/DeX performance and exhaustive interaction coverage of every new control remain unmeasured.

See `qa/BC-1-v1.1-REDESIGN.md` for qualification findings, build instructions and the unresolved intermittent export watchdog failure. A passing qualification run does not prove that intermittent failure is fixed.

# Black Ice Editor 1.1.0 — cinematic redesign preview

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

This is a working redesign preview, not full completion of the supplied specification. Exact mockup parity, a separate pre-insertion asset catalog, desktop media drag/drop, a draggable phone inspector drawer, reverse playback, independent effect tracks and pen/draw remain open. The media browser lists imported clips already in the project. Preview changes can require proxy preparation. Physical Snapdragon/DeX performance and exhaustive interaction coverage of every new control remain unmeasured.

See `qa/BC-1-v1.1-REDESIGN.md` for qualification findings, build instructions and the unresolved intermittent export watchdog failure. A passing qualification run does not prove that intermittent failure is fixed.

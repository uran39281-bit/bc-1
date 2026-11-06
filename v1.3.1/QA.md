# BC-1 v1.3.1 — tested development preview

## Implementation map
- Native Java Views: `MobileChrome`, `ToolPanels`, `TimelineView`, `V1Icon`, shared `BlackIceTheme`.
- Existing authoritative `EditProject`, `TimelineEdits`, `LayerOps`, `ProjectStore`, history and autosave.
- Existing MediaImporter/SAF and Project Media; ICE Lite/Media3 preview and export unchanged.
- Preview quality remains `previewHeight`; it does not change project canvas or export preferences.

## Changes
Mint actions, restrained navy backdrop without particles, existing cube resource, six-category mobile navigation. The plus button opens Add, while selecting video/image opens Footage. A selected Footage panel reserves layout space below the timeline; short landscape windows place it at the right. Focused controls use existing commands. Back closes nested tools, then selection; project exit saves first.

All timeline headers are 56dp, without visible track names. The entire 52dp-high row is the visibility/mute target. Long press opens lock/select/rename/layer management. Layer management retains reorder controls. Accessibility virtual buttons announce internal names, type/ordinal, state and actions. This replaces tiny adjacent header targets and direct handle reordering.

Footage home: Speed or image Duration, Trim Start, Split, Trim End, Mute; four property tiles. Edit Shape is explicitly unavailable for raster footage. Additional existing slip/chroma/extract/animation/compare tools remain under More footage tools. Color & Fill, Border & Shadow and Presets category entry points are removed; serialized properties and engines are retained.

Playhead trim preserves the opposite edge and unrelated main-sequence clip positions. Edge dragging retains the existing main-sequence ripple policy, which is explained in the panel. Selected-clip mute checks source audio asynchronously; it stores the prior gain in an optional backward-compatible `unmutedVolume` field while existing preview/export consume effective volume. Old projects default to -1 (not muted).

## Verification
Qualified development preview, 2026-10-20. Source `0e90227ddf82ec927450428920935c6a98468076`; [CI run 39472715226](https://github.com/uran39281-bit/bc-1/actions/runs/39472715226).

- Build and 137 JVM tests passed, zero failures.
- Lint: zero errors, 49 warnings; not warning-free.
- API 35 x86_64 emulator with SwiftShader: all 43 Android tests passed, including actual rendered image pixels after rotation/reopening, font scaling, phone/DeX gestures, selection, history, media playback and persistence.
- Ten independent ffmpeg/ffprobe export checks passed: three repeated 1080p H.264/AAC exports, recovery, speed/A-V sync, ICE Lite 1080p, 60fps, HEVC, staged rendering and vector-only export.
- Actual screenshots reviewed: empty phone, selected image Footage, 1.3× font scale, short landscape and desktop. At large font sizes the Footage actions reflow and properties require scrolling. The system clipboard overlay in some captures is emulator OS UI, not application artwork.
- Earlier qualification attempts exposed and corrected preview space allocation, font wrapping, a surface request-order race and a stale save bookmark callback. Failed runs were not published.

Signed APK: `com.blackice.bc1.preview`, versionName `1.3.1`, versionCode `24`. Retained certificate SHA-256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`; v2/v3 verification passed. All 43 non-META-INF ZIP entries match CI APK artifact `12288150996`. APK SHA-256 `e40a6da69cca85504f30b1afb035e35bd9e214c665cebdda56650308dda8f3d2`. No signing material published or regenerated.

## Known limitations
- No physical Samsung DeX/Snapdragon performance or assistive-technology session yet.
- Clip effects use the existing single effect plus chroma/animation pipeline; arbitrary effect stack reordering is not introduced.
- No vector-node/crop editor for raster footage, automatic captions, freehand drawing or AI enhancement is claimed.
- Existing project canvas choices remain portrait/landscape. This change does not add arbitrary aspect ratios.
- Default duplication uses free space after the source, or a free unlocked layer at the same time; unrelated clip positions are preserved. If neither fits, it reports the limit.
- Source imports enter Project Media and are inserted using its real Add at playhead/new-layer command. Overlay navigation chooses new-layer insertion.

## QA corrections made before release
- Fixed a real preview-expansion regression caused by the selected-panel height cap. Selected footage now reserves room for the complete first timeline row; preview/timeline focus still adjusts space.
- Retained horizontal timeline scroll when rebuilding responsive chrome; selection and project state survive phone landscape and window resizing.
- Export measures its label at the actual font size rather than wrapping at 1.3× font scale. The regression checks both line count and available text width.
- Screenshots wait for the imported PNG's actual green/blue pixels and matching live-render time. A one-second delay captured black before the first portrait frame (roughly 3–5 seconds in the recorded run). The stronger check then exposed a real surface-replacement race on rotation: a queued drain could consume the coalesced request before bind. Redraw is now requested on the GL thread after binding. This preserves the existing renderer and only fixes request ordering.
- Default duplicate avoids occupied or locked destination tracks and preserves unrelated main-sequence positions. Mutable animation data is copied independently.

## Coverage and boundaries
The instrumentation scenario imports a generated PNG through the real media insertion command, selects without seeking, opens/dismisses tools with system Back, trims with undo/redo, saves/reopens, rotates, changes font scale, checks timeline scroll retention, and inserts around a locked track. Existing tests cover real video/audio playback, timeline gestures and independently decoded export outputs. The system file picker itself was not manually exercised on a physical phone. QA screenshots contain real imported test content, not the reference mountain image.

Phone checks include approximately 393dp width, a short landscape window, and 1.3× font scaling; existing checks include narrower phone media space and 1920×1080 desktop layout. These are emulator checks, not physical Samsung DeX certification. TalkBack and keyboard-only traversal have not received a manual audit.

Remaining inherited limits: eight visual tracks, four audio tracks and twenty overlay objects. Captions are manually created text, not transcription. Legacy image-overlay objects retain their transforms/duration but do not gain the media-clip effect pipeline; the Effects tile explains its unavailable state. Add-sheet close remains immediate; full reference transition polish is not claimed. The existing Media3 intermittent stall is not declared eliminated by this UI update.

A save-completion callback from a destroyed Activity or a different project is now ignored for the last-project bookmark and dirty indicator. The save itself still completes; an obsolete callback cannot redirect a newly opened editor back to the previous project. The workspace test explicitly checks the requested project identity before frame navigation.

## Screenshots

[Main workspace](main-workspace.png) · [Selected Footage](footage-panel.png) · [Large font](large-font.png) · [Phone landscape](phone-landscape.png) · [Desktop/DeX layout](dex-workspace.png)

These are screenshots from the running Android application, not mockups. [Build instructions and changelog](../docs/CHANGELOG-v1.3.1.md).

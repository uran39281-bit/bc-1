# BC-1 v1.3.1 — two-state native workspace

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
Pending CI qualification. Do not label this build stable or publish a release until compilation, JVM, emulator and independent export checks are inspected. Added regression tests cover main/Footage state, no-seek selection, narrow strip, removed tiles, nested Back, trim undo/redo, persistence and mute gain round-trip. Existing phone/DeX gestures, media, playback and export suite retained.

## Known limitations
- No physical Samsung DeX/Snapdragon performance or assistive-technology session yet.
- Clip effects use the existing single effect plus chroma/animation pipeline; arbitrary effect stack reordering is not introduced.
- No vector-node/crop editor for raster footage, automatic captions, freehand drawing or AI enhancement is claimed.
- Existing project canvas choices remain portrait/landscape. This change does not add arbitrary aspect ratios.
- Default duplication uses free space after the source, or a free unlocked layer at the same time; unrelated clip positions are preserved. If neither fits, it reports the limit.
- Source imports enter Project Media and are inserted using its real Add at playhead/new-layer command. Overlay navigation chooses new-layer insertion.

# v1.5.0 (build 44) — Layer Tools and a compact timeline toolbar

No project-schema change (still 11). New fields are optional and written only when used, so existing projects are byte-identical and open in older builds (which ignore them).

## Layer Tools (Media Settings → Layer Tools)
Five separate tabs: **Speed, Reverse, Screenshot, Joint Movement, Copy All**. Back returns to Media Settings with the same layer and playhead; changing tabs is not an edit.

- **Speed:** the existing 0.25–4× rate control with a readout, reset to 1.00× and the trimmed/timeline durations. Pictures, text, shapes and image layers show why it is unavailable.
- **Reverse:** writes a reversed copy of the trimmed range (picture and sound) in app storage, with progress and Cancel; **Restore Forward** returns to the same original range without re-encoding. Timeline duration, rate and authored animation timing are unchanged. Copies are cached; failures and cancels change nothing. Limit: 3,600 frames (about 2 minutes at 30 fps) per reversal.
- **Screenshot:** renders only the selected layer at the playhead onto a transparent background at canvas resolution (transform, parent movement, effects and opacity included; other layers and editor handles excluded) and inserts it as a new full-canvas image layer in one undo step. The source blend mode is kept as metadata on the new layer.
- **Joint Movement (Parent & Child):** choose a parent, Set Parent, Remove Parent. The child follows the parent's position, rotation and scale (not opacity, effects, speed or visibility); attaching, detaching and deleting a parent keep the child where it is. Loops and self-parenting are refused. Handles and dragging work in the drawn pose.
- **Copy All:** snapshots the layer's transformations, effects and keyframes into the existing attribute clipboard (no undo entry); apply with **Paste attributes…**. Source, trim, speed, placement, reverse and parenting are excluded.

## Timeline toolbar
- Mouse/DeX: 32 dp buttons, 18 dp icons, 8 dp between buttons, 16 dp between groups (Trash/Undo/Redo, Trim Start/Split/Trim End, Snap/Fit/Markers). Touch layouts keep 48 dp non-overlapping targets.
- The timeline zoom ruler, readout and +/- buttons are removed and the remaining controls close up. Pinch and Ctrl+wheel zoom on the timeline itself, Fit timeline, and the preview's Fit/fullscreen controls are unchanged.

## Known limits
- Splitting a clip leaves existing children following the left half only; duplicating a parent does not carry its children.
- A reversed clip's own keyframes that fall before its trimmed start are dropped when reversing.

## Publishing 1.5.0
`.github/workflows/bc1-v150-publish.yml` mirrors the 1.4.9 gate: it runs when `v1.5.0/release.json` lands on `main`, restores the retained-key signed APK from the qualified CI APK, installs published Preview 1.4.9 build 43, upgrades in place, verifies the project and publishes to `test-apk/preview/v1.5.0`. The signed APK, `release.json`, checksums and QA do not exist yet and are not committed as placeholders.

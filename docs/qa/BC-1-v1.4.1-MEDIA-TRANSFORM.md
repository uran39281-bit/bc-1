# v1.4.1-dev — Media Settings and Move & Transform

The selected media's phone and DeX panels share four square blue tiles with decorative waves. Effects remains explicitly unavailable under the existing engine policy. Audio and Blending retain their existing destinations; the Add button retains its import behavior. The phone's existing footage operations remain available.

Move & Transform has Position, Rotation and Scale tabs. Numeric entry validates finite values; rulers update the live renderer without flinging; the dial accumulates wrapped pointer deltas through multiple revolutions. Each gesture is a single undo transaction and cancellation restores the original project. Reset operations affect only their named properties at the current animation scope. Each tab navigates only its own keys and disables unavailable directions.

Width and Height are independently animatable. Linked edits preserve the proportion evaluated when the edit begins and create paired keys when either dimension is animated. Legacy uniform-scale curves are copied into Height only on first dimension edit, with all times and easing preserved. Linking itself does not change the project.

## Compatibility

- Project schema 6 adds `heightScale` and appends the Height motion channel. Schemas 1–5, including six- and ten-channel curves, remain readable. The existing store retains a backup before saving an older schema. Old application versions cannot open schema 6 projects.
- Stored coordinates remain centered, positive Y upward. UI values are project pixels. Rotation storage remains cumulative counterclockwise degrees; the new clockwise-positive dial and fields convert the sign at the UI boundary. Old projects render in their original direction.
- Preview, Media3 effects and ICE Lite exports use the shared evaluated transform. Legacy skew, anchors and flips remain stored and rendered.
- Normal media scale 100% is the fitted import size. Legacy overlays use their original default scale as 100%.
- The existing positive-scale limits remain: Width 5–400% for clips (legacy overlays retain their internal 0.1–2 bounds); Height internal scale 0.0025–80. Zero is rejected because the existing inverse shader requires a positive width. Cumulative rotation accepts finite signed values; only the rendered pointer/matrix uses modulo 360 after animation evaluation. These are development-build limits, not hidden flips or modulo rotation.

## Verification

Pending final branch qualification. Added JVM regressions cover linked proportions, independent dimensions, legacy curve preservation at many times, ten-channel decoding, independent Height persistence, cumulative rotation interpolation, pointer wrapping, invalid scale and property-specific navigation. Added Android UI coverage exercises tile geometry, full-turn dial gestures, live cancellation, playhead stability, undo/redo, reset scope and paired keys. A new encoded export test checks two full revolutions in each direction after save/reopen by sampling an asymmetric four-color image, with independent ffmpeg decoding.

Physical Samsung/DeX gesture feel, accessibility with assistive technology, and hardware performance remain unverified. Existing older releases remain available.

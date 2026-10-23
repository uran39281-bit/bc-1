# BC-1 v1.4.1-dev — Media Settings and transforms

Work in progress on `ui/media-transform`; do not treat the branch as a qualified release until its final CI run passes.

## Implemented

- Shared blue, equal-sized 2×2 Media Settings tiles for selected footage on phones and wide windows; existing text/shape tools remain accessible. Effects stays disabled.
- Position, Rotation, Scale tabs only. Project-pixel positions retain the engine's center-relative, positive-Y-up coordinate convention.
- Live rulers/dial and direct canvas manipulation; cancel restores the preceding project, completed gestures create one undo entry.
- Scoped resets and keyframe editing; cumulative clockwise UI rotation translates to the renderer's existing counterclockwise coordinate convention. Stored engine angles stay unwrapped; legacy projects retain their visual rotation.
- Independent width/height static values and animation channels. Legacy scale/aspect animation remains intact and is materialized lazily when editing a dimension. Linked edits preserve the current evaluated proportion and insert paired keys when animated.
- Schema 6 reads schemas 1–5, retains legacy channels, and creates the existing version backup before upgrading an on-disk project. Do not reopen a schema-6 project in an older APK.

## Deliberate renderer limits

Zero/negative scale is rejected: this renderer requires an invertible transform. Width range is 5–400%; height range is 0.25–8000%, retaining the legacy scale×aspect envelope. Rotation retains the validated ±3600° engine limit (ten turns). Position uses ±one full canvas dimension from center. Preview size does not change project coordinates.

## Qualification

Initial source compiled and passed JVM tests. CI 39557339598: 45/46 Android tests passed; lint rejected two literal orientation constants, and the selected-media home was missing the old Duplicate/Delete action accessibility entry points. Both were fixed. The subsequent build/lint job in run 39897896989 passed; Android tests were still running when this record was written.

Added regression coverage: cumulative dial turns and relative presets, live mutation, cancel/undo/redo, scoped animated reset, linked width/height keys, legacy channel migration, save/reopen, and decoded two-turn exports in both directions. Final results and reviewed screenshots are pending.

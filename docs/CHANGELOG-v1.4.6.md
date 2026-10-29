# v1.4.6 (build 40) — Effects enabled and cleanup

## Effects
- The 48 built-in effects are no longer bypassed. Live preview, the playback proxy and both export engines (ICE Lite and Media3) render the clip's saved effect with the shared layer shader.
- Effect Catalog entries are selectable for a selected video or picture: tap to apply, tap the applied entry to remove it. Each change is one undo step.
- The Effects panel shows the applied effect, an animatable **Effect strength** control and **Remove effect**.
- Projects saved by earlier versions keep their stored effect values; those effects now render again.

## Fixes and cleanup
- Exceeding the 2,000-caption limit reports a caption-specific message instead of the clip/overlay limit.
- Export uses the saved export options unless the caller explicitly requests a resolution (no double planning).
- Project JSON is written compactly, reducing file and undo-snapshot size for caption-heavy projects. Older pretty-printed files still load.
- Removed the unused `MainActivity` (not declared in the manifest) and the "Coming Later" effects stub.
- Corrected stale schema/shape comments in `EditProject` and refreshed `BC-1/README.md`.

## Not changed
- `WorkbenchActivity` is kept: CI capture scripts and `WorkspaceTest` still launch it.
- `BC-1/.github/workflows/build-android.yml` is inert (GitHub only runs root workflows) and is left for the owner to delete.
- No signed APK is published by this change; publication still goes through the retained-key publish workflow.

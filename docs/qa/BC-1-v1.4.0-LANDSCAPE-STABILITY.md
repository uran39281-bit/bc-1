# v1.4.0-dev — landscape stability checkpoint

Based on main 821c363. Not a qualified release until CI and device evidence below are complete.

## Changes
- Fix transform gesture release after the inspector rebuild detaches its view (rotation dial, position pad, value ruler).
- Decoder orientation is explicitly zeroed after reading source metadata. The ICE Lite compositor alone applies source rotation; user rotation remains a separate project transform. Invalid dimensions fail before decoder allocation.
- Reset Rotation clears only rotation and its animation. Existing undo/project serialization remains in use.
- Persistent bounded private crash/error reports, operation context, OS/device/build/memory and previous process crash/native/ANR exit metadata. Fatal errors are forwarded to Android's original handler. Exception messages are omitted to prevent media/path leakage. Settings → Diagnostics → Crash Logs offers view/copy/share/clear/export. Native tombstone bodies are not collected.
- Wide header removes six tabs; retains save, undo/redo/settings/export. Add content and Layers remain in the media browser. Media Settings replaces Inspector/Project tabs, with Transform, Blending, Effects Coming Later, Audio. Text/shape-specific editing remains in overflow.
- Wide thumbnail horizontal touch drag or mouse drag starts after movement slop, no hold. Vertical touch motion remains browser scrolling. Click retains media actions.
- Wide Split/Trim Start/Trim End beside zoom; enabled state follows playhead. Playback Speed is in overflow. Centered playhead is unchanged.
- Existing built-in effects are bypassed on private render snapshots for preview/export; their saved values remain intact. Engine implementations/tests retained for future recovery. Transitions, transforms, chroma, opacity remain active.
- Native export form: real filename, project details, estimated output size and staging free bytes; quality presets map to actual video bitrates; independent export FPS/resolution/codec and custom bitrate. Existing encoder routing/cancellation/output publication remains.

## Explicit gaps before final acceptance
- Physical Samsung crash reproduction and real Android camera/screen-recording/third-party orientation fixtures remain unverified.
- Destination-provider free capacity is unavailable before selecting a URI; shown as unknown, not fabricated.
- Audio encoder bitrate/sample-rate controls are still fixed, not exposed as fake adjustable options.
- Full progress screen, reliable ETA and Open Location remain to implement; existing foreground export status/cancel/play/share retained.
- Arbitrary canvas/background/save-location choices and new perspective editing are not implemented.
- No retained signing key has been recovered in this run; CI test APK installs separately.

## Validation
Pending CI. Added transform-release/undo/save regression, diagnostics privacy check, and saved-effect preservation test. Existing engine/export suites must pass before publishing a test APK.

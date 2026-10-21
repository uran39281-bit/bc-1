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
- Export progress now shows stage, percentage, elapsed time, output bytes and approximate ETA after sufficient progress, plus cancel/play/share. Provider-specific Open Location remains unavailable.
- Arbitrary canvas/background/save-location choices and new perspective editing are not implemented.
- Retained Preview signing backup recovered privately; certificate matches the existing app. Normal upgrade signing is available; no signing material is published.

## Validation
Pending CI. Added transform-release/undo/save regression, diagnostics privacy check, saved-effect preservation and bitrate tests. Added 0/90/180/270° metadata fixtures with live preview + export marker-pixel comparisons against Android decoding, and four independent export decode checks. Existing engine/export suites must pass before publishing a test APK.

### Initial candidate evidence (not qualified)

- Initial candidate source: `5b309d4d1c2a23333b8efe7da86ef4d5f1f58417`.
- GitHub Actions run: https://github.com/uran39281-bit/bc-1/actions/runs/39539371367
- Build, lint and **139 JVM tests** passed (0 failures / 0 ignored).
- Test APK manifest independently read: `com.blackice.bc1.icelite`, `1.4.0-dev-icelite`, versionCode **27**.
- APK signature verified with Android apksig. It uses the CI test certificate, not the retained Preview certificate; not an upgrade for `com.blackice.bc1.preview` and may not upgrade older CI test installs. Preserve/backup existing projects before changing installations.
- APK SHA-256: `4290618c673229058e11c353d38bbe6a8e5ac744fa3f5afa3d995195484482d1`.
- Immutable test artifact commit: `fde3041952267e9bf5d039a6bd4e97e2b1cdbbc3`.

Initial Android run: 43/46 passed. Rotation gesture/undo/reopen and all four source orientation preview/export checks passed. Two old UI tests expected removed controls; updated to the new interface. Paused scrub texture pixels passed but the immediate screen capture failed: screen presentation now has its own bounded pixel wait and failure screenshot. Independent host export checks were gated by the test failures and did not run. Requalification required.

Second run (`39541348259`, `2764c282`): 44/46 Android tests passed. Paused scrubbing now passed both texture and real-screen pixel checks. Remaining failures: obsolete Effects selection prompt and ambiguous audio test target (clip name versus category). Effects now consistently explains unavailability; category accessibility labels distinguish buttons from clip names. Requalification pending.

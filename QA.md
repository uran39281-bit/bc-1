# Black Ice Editor 1.4.5 — October 27 update

Version **1.4.5**, build **39**, package **com.blackice.bc1.preview**. Android 8.0/API 26 or newer; ARM64 and x86-64 included.

[Preview APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/Black-Ice-Editor-v1.4.5-Preview-build39.apk) · [SHA-256](SHA256SUMS.txt) · [Source PR #15](https://github.com/uran39281-bit/bc-1/pull/15)

Install over **Black Ice Editor Preview**. The original Preview signing certificate is retained. **Integration remains a separate app**; keep it and its projects. Moving projects between packages uses the established project export/import flow. No uninstall or data clearing is required for this Preview upgrade.

## Implemented

- **Home:** native navy/cyan hero, existing Black Ice branding, real project count, search/reset, sorting, grid/list selection, empty-state creation, Help and Settings. Landscape uses a sidebar with Settings at the bottom; portrait uses fixed bottom navigation. Existing project creation/import/open/rename/duplicate/delete paths remain connected to real storage. Content scrolls on compact windows.
- **Project names and limits:** blank names allocate the lowest unused ICE_01–ICE_99 at the actual save transaction. Creation, import and duplication share the 99-project limit. Existing projects can still save at the limit; recovery retains the same identity and consumes no slot. Cancelled creation allocates nothing.
- **Timeline:** only actual motion/fill keys draw diamonds at their true times and row centers. Newly inserted static shapes have no decorative keyframe diamond. Live edits invalidate markers immediately. Trash, Undo and Redo are adjacent. Native Trim Start, Split and Trim End icons call the established commands. The timeline zoom ruler maps levels 0–10 monotonically to the existing zoom range and retains the playhead anchor.
- **Inspector and header:** opacity has a live 0–100 ruler and exact numeric entry. Effects contains its header/Back and Add Effects; the latter opens the existing catalog without applying anything. The unboxed, fixed-width work timer is directly before Save. Back to Projects waits for a successful save; failure keeps the editor open with Retry/Keep editing. The keyboard guide reads the actual customizable binding registry and shows context/category information.
- **Fit Canvas / Fill Canvas:** Scale-panel commands use canonical source/shape bounds and evaluated transforms. Fit contains the transformed object rectangle; Fill contains all canvas corners in the inverse object basis. Rotation, cumulative turns, flips, noncenter pivots, skew and unequal axes are preserved. Position and both scale dimensions update together as one undoable command; each property's existing animation state is respected. Invalid/unavailable geometry and unsupported limits leave the original transform intact. Repeated commands are no-ops within tolerance.
- **Easing:** exactly Linear, Ease In, Ease Out and Ease In/Out, with the requested quadratic definitions. Stable key identities own outgoing intervals independently for motion, opacity, other existing Motion properties, fill color/alpha, gradient angle and stop position. Explicit key selection controls the target interval; terminal keys are disabled. Existing cubic motion remains unchanged until explicitly replaced. Legacy fill remains linear; existing color mixing and discrete gradient-stop membership are preserved. Trim/split retain original sampling ranges instead of restarting curves. Preview and both export engines share evaluation.
- **Save and recovery:** content fingerprints distinguish Saved, Unsaved changes, Saving and Save failed; playhead, collapsed rows and timer ticks do not dirty content. Snapshot hashing and disk writes run off the UI thread, with bounded queues. Recovery uses validated atomic files in private persistent storage, with current/previous generations, project/session/revision/base identities and integrity checks. Checkpoints debounce after edits and continue during long gestures. Restore, confirmed Keep saved version and Later retain the appropriate versions. Saving restored edits retires superseded recovery history while retaining any newer checkpoint. Missing media does not invalidate an otherwise readable project. Shared storage locking and deletion tombstones prevent read/write races and delayed resurrection of deleted projects.
- **Responsive state:** selection, playhead, zoom and browser state survive resizing; focused property panels and explicit easing selection survive Activity recreation. Density/font changes rebuild the existing native layout. Canonical shape insets keep logical bounds independent of preview resolution.

## Qualification

Qualified source: `d08b2edc82136a2e16d6de1887e7e6e4cb837328`.

[Full CI run 40645411166](https://github.com/uran39281-bit/bc-1/actions/runs/40645411166) passed:

| Check | Result |
| --- | --- |
| JVM unit tests | 225 passed; no failures or skips |
| Android instrumentation, API 35 emulator | 90 passed |
| Independent FFmpeg export checks | 30 passed |
| Android lint | 0 errors, 105 warnings |
| App and instrumentation APK compilation | Passed |
| Signed APK payload compared with qualified CI candidate | Identical non-signature entries |
| Retained Preview certificate and 16 KiB ZIP alignment | Passed |

New tests cover exact easing samples, legacy cubic/color compatibility, independent interval ownership, copied identities, retained trim/split samples, geometry containment with rotations/flips/skew/pivots, static and animated framing, one-step undo and idempotence, opacity endpoints, zoom endpoints, toolbar ordering, density/orientation changes, empty/populated Home, search/view switching and creation/navigation.

Recovery tests inject unavailable project storage, block an older save while editing continues, verify retry and undo-to-saved status, corrupt the newest checkpoint, simulate an interrupted atomic promotion, enforce the 99-project limit, restore missing-media structure, prevent deleted-project resurrection and retire restored checkpoint ancestry correctly. The lifecycle test exercises Restore, Activity recreation, subsequent checkpoints, a cold-session reopen, Later and explicit discard. It checks that restoration leaves the ordinary saved version untouched until Save.

Actual exports through **ICE Lite and Media3** verify recovered Fit/Fill geometry and quadratic motion/fill pixels. Existing playback, audio, orientation, five canvas ratios, shape rendering, animated fills and cumulative rotation regressions remain in the suite. Independent decoding checks the produced files outside Android's media stack.

The publication gate installs signed Preview 1.4.4, opens and saves a real fixture, installs this APK with `adb install -r`, and verifies reopening plus source-image bytes, trim, transforms, motion keys, shape properties and legacy gradient/color keys. Publication only proceeds if that upgrade passes. Its [verification record](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/upgrade-verification.json) and [screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/upgrade-after.png) accompany the immutable APK.

## Screenshots

Actual emulator captures use deterministic test projects, not user media. A fixture named “1.4.4 · Workspace” is a project name, not the installed application version.

| View | Landscape | Portrait |
| --- | --- | --- |
| Home with projects | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/screenshots/v145-home-landscape.png) | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/screenshots/v145-home-portrait.png) |
| Empty Home | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/screenshots/v145-home-empty-wide.png) | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/screenshots/v145-home-empty-phone.png) |
| Transform and timeline controls | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/screenshots/v145-wide-transform.png) | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/screenshots/v145-phone-transform.png) |

[Recovery prompt](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/screenshots/v145-recovery-prompt.png)

## Remaining limits

- Physical Samsung/DeX hardware, all external document providers, all input/accessibility combinations and actual full-device storage conditions were not tested. Failure injection and resized emulator windows are covered. The local software-only emulator was unstable; qualification uses the hosted accelerated emulator.
- The established text renderer caps fitted text at 90% of canvas width. Text framing that cannot converge or would exceed supported transform limits reports an error and makes no partial edit. The text rendering engine was not replaced.
- The existing effect engine remains bypassed, as in 1.4.4. Catalog availability labels remain truthful; this update does not enable unimplemented effect processing.
- Recovery protects the last committed checkpoint, not every last input. The prompt shows its timestamp. Sudden power loss, physical storage failure and every possible process-termination point are not claimed exhaustively tested.
- Compact windows require scrolling through long inspector forms and Home content. Small-window layout is responsive rather than a fixed-resolution copy of the mockup.

## Release integrity

Certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.

APK SHA-256: `5e859c3b73ca3e662341f44f75c04c3ace9cd1cb85b8ce47d96308721c9a839b`.

Only public ZIP/signature metadata is committed. The private signing key and credentials are never included in the repository or APK delivery.

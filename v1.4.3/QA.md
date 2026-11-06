# Black Ice Editor 1.4.3 — insertion, timeline and Color & Fill

Version **1.4.3**, build **37**, package **com.blackice.bc1.preview**. Android 8.0/API 26 or newer; ARM64 and x86-64 included.

[Download the retained-key Preview APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.3/Black-Ice-Editor-v1.4.3-Preview-build37.apk) · [SHA-256](SHA256SUMS.txt) · [Source PR #13](https://github.com/uran39281-bit/bc-1/pull/13)

## Installation

Install over **Black Ice Editor Preview**. The APK uses the retained Preview signing certificate, including the certificate used by the recovered 1.4.2 Preview build 36. Android should offer an update for Preview; an in-place upgrade on the user's phone has not been exercised here.

**Black Ice Editor Integration is a separate app.** This APK does not replace it. Keep Integration installed and keep its projects. Do not uninstall either app or clear its data to work around a signing error. To move projects between installations, use the existing project export/import workflow and retain the originals. External media access remains subject to Android document-provider permissions.

Signing certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.

## Changes

- The existing per-project work clock sits immediately before Undo and Redo in the top toolbar. Accumulated time and pause/resume behavior are retained; Export stays reachable on narrow screens.
- Shape activation inserts one independent, spatially centered layer without a menu. Favorites have a separate button. Ordinary media selects on a single click and inserts once on a double-click/tap. Insertion starts at `max(0, playhead − duration/2)`, retains the full default/source duration and leaves the playhead unchanged. Drag/drop remains available.
- Project Media selection is independent of timeline selection and survives recreation. The explicit “Select timeline instance” command remains available.
- Audio exposes the existing offline Speech to Text command. It explains when no suitable source is selected and uses the existing setup/model/progress/cancellation/caption service.
- Selected-layer Effects displays real saved state or “No effects applied.” The outlined icy-blue **+ Add Effects** button is disabled and labeled **Coming Later**. Existing global Styles, Transitions and Animation controls remain available; no future effects engine was added.
- Normal 16:9 landscape shows five complete track rows. Drawing, hit testing, accessibility bounds and scrolling share the compact metrics; additional tracks still scroll.
- Shape scale supports at least 10,000%, including linked dimensions, exact inputs, gestures, keyframes, save/reopen and rendering. Output-sized rendering avoids allocating a bitmap the size of an enlarged shape.
- Marker tap creates immediately. Dragging from the toolbar to the ruler creates a marker; dragging a ruler flag moves it. Same-frame additions select the existing marker. Cancellation/outside drops create nothing. The five-marker cap, distinct colors, deletion and undo remain.
- The clover path is contiguous through the center, including rotation and partial transparency. Existing instances use the corrected geometry.
- Native Color & Fill provides a hue/saturation wheel, brightness, fill alpha, swatch, HEX/RGBA, solid color and editable linear gradients. Stable stops support addition/removal, dragging/exact positions, direction, per-property keyframes and Previous/Next Key. Continuous changes form one undo action.
- Resizing the preview updates its surface buffer, keeping the rendered image aligned with transform handles. Media3 receives straight-alpha overlay pixels so its compositor does not apply fill transparency twice; ICE Lite retains its premultiplied upload path.

## Qualification

**Passed:** 208 JVM tests, Android lint/build, 74 Android instrumentation tests on API 35, and 18 independent MP4/decode checks. The focused nine-test regression suite also passed.

Qualified source: `c2bd72bd0cf38a89df7857f024d233caf97ab828`. [Full qualification run](https://github.com/uran39281-bit/bc-1/actions/runs/40284602706) · [Focused run](https://github.com/uran39281-bit/bc-1/actions/runs/40284602670). The final documentation/publication commit does not change app or test sources.

The new checks cover centered insertion, one insertion per activation, unchanged playhead, independent media instances, shape undo, restored clock totals, Effects non-mutation, actual toolbar drag creation/cancellation, marker identity/cap/deletion, fifth-row selection/trim/accessibility, resize preview pixel alignment, saved fill state and gesture undo. JVM checks cover static/animated property semantics, stable gradient membership, split/reopen, key navigation, large scales and schema-9 compatibility.

Decoded frames from **both Media3 and ICE Lite** check animated gradient color changes, spatial ramp variation, large rotated shapes and translucent clover centers. Existing device coverage also exercises projects, media references, timeline editing, playback/export, import, offline speech and audio tools. This is automated qualification, not a claim that every device/gesture combination was manually tested.

## Actual screenshots

Screenshots use deterministic test projects rather than user media. The full-resolution PNGs are included with the delivered screenshot archive. Repository previews (the inspector scrolls to reveal controls below the captured area):

| Panel | Landscape | Portrait |
| --- | --- | --- |
| Effects | [Landscape Effects](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/v1.4.3-validation/v143-wide-effects.png) | [Portrait Effects](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/v1.4.3-validation/v143-phone-effects.png) |
| Gradient ramp | [Landscape ramp](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/v1.4.3-validation/v143-wide-fill-ramp.png) | [Portrait ramp](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/v1.4.3-validation/v143-phone-fill-ramp.png) |
| Color wheel | [Landscape wheel](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/v1.4.3-validation/v143-wide-color-wheel.png) | [Portrait wheel](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/v1.4.3-validation/v143-phone-color-wheel.png) |

## Compatibility and limits

- This is a development Preview. Physical Samsung/DeX hardware, the user's installation and complete assistive-technology operation have not been qualified here. Emulator results do not establish physical-device performance.
- Fill data uses project schema 10. Schemas 1–9 remain readable; the existing store backs up older data during migration. Older app versions cannot open schema-10 projects. Preserve project backups before downgrading.
- Linear gradients apply to native text/vector fills, not image/video recoloring. Supported limits are 2–16 visible gradient stops, 128 retained stop identities and 200 keys per property. Existing general project limits remain.
- Speech to Text uses the existing supported offline model and source restrictions. No new language model or transcription implementation is included.
- **Add Effects remains Coming Later.** Existing saved effect data is preserved; the placeholder cannot create or modify effects.
- Existing attribute-preset formats have not been extended to carry the new fill tracks; project persistence and ordinary layer duplication do carry them.

## Release integrity

The APK is built by the qualification workflow and signed offline with the retained Preview key. Every ZIP entry's uncompressed content is compared against the CI candidate, the package/version and certificate are verified, and 16 KiB ZIP alignment is checked. The download publication workflow requires that exact source run to pass and verifies the complete signed APK hash. Only public APK metadata/signatures are stored in `release.json`; the private keystore and passwords are not uploaded.

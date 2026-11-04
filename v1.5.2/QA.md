# Black Ice Editor 1.5.2 Preview — build 46

Status: signed candidate verified; in-place upgrade gate pending. This branch is not merged and no official Preview branch or download table has been published.

## Change

The phone portrait editor follows the three supplied target states: compact header and preview, six real timeline lanes, plus-open Add Media overlay, and compact selected-layer drawer. Base/Add Media use three playback controls; selected-layer tools use five. New presentation applies only when the existing layout policy selects phone mode and the available window is portrait. Project canvas aspect ratio is independent. The wide/DeX presentation is retained.

The library uses real project data, native recycled rows, the existing asynchronous thumbnail cache and existing insert/import commands. Native list position, drawer state and selected layer survive recreation. Short windows use a smaller real timeline scroll viewport and a scrolling drawer beneath playback; large text scrolls rather than shrinking touch targets. Undo, markers, settings, search and sort remain reachable through the project/context menus.

## Exact qualified build

| Item | Value |
|---|---|
| Source | `6edda59993dc0c4e51b44523f3106492daf83d7c` |
| Full CI | [41788812660](https://github.com/uran39281-bit/bc-1/actions/runs/41788812660), attempt 1, success |
| Candidate artifact | `13302063999` — Black-Ice-Editor-v1.5.2-ci-candidate |
| Passing GPU reports | `13303189437` — BC-1-GPU-test-reports |
| Build reports | `13302811202` |
| Focused native run | [41788812742](https://github.com/uran39281-bit/bc-1/actions/runs/41788812742), 15/15 |
| Native captures | `13302787101` — Phone-update-native-captures |
| Package | `com.blackice.bc1.preview` |
| Version | `1.5.2`, versionCode `46` |

The candidate and report artifact ZIP digests were checked against GitHub's artifact metadata before extraction.

## Checks performed

- 275 unit tests passed; zero failures and zero ignored tests.
- Lint: zero errors, 114 warnings.
- All 108 Android tests passed, with zero failures/ignored tests; four additional inspector preflight tests also passed.
- Fifteen focused native tests passed, including real duplication/undo, library scroll across reopening/recreation, shape insertion/deduplication, fill, save/reopen, playback/trim, transform gestures and native marker drag/drop/cancellation.
- Thirty independent export checks passed: H.264/AAC acceptance and recovery, synchronized speed changes, ICE Lite H.264/HEVC/staged exports, 60 fps/frame counts, vector-only rendering, orientation, repeated rotations, animated fills, five canvas ratios and framing/easing through both export backends.
- The reverse test passed its actual last-to-first frame-order assertions and exact Restore Forward in-point `0`. Independent audio measurement: leading 0.3 s peak −91.0 dB, trailing 0.3 s peak −0.2 dB, confirming the opening tone moved to the end.
- Eight wide native view images and eight visible view trees match the unchanged baseline exactly: Media Settings, Position, Rotation, Scale, Blending, Effects, Layer Tools and Add. Native view pixel comparisons exclude separate video surfaces; full device screenshots are also included.
- Portrait captures cover 360/412 dp widths, 16:9 and 9:16 project canvases, 1.5 font scale, and a 360×480 dp short window. Preview/timeline/transport rectangles remain exact on Add Media open. The protected upper region is pixel-identical at 412 dp; 360 dp differs in 2,170/643,680 pixels by at most 3/255 per channel.

## Signature and payload verification

- Retained Preview certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.
- CI APK SHA-256: `9c4a1bacf088dee4dc757e2590540d00c3c229d3d4a36edf0c21063f54115086`.
- Signed APK SHA-256: `f9c67fdd44ec8153cc88a4d42e4cedaac32e6c6b64e659045e26132347537842`.
- ZIP alignment and 16 KiB native-library alignment passed.
- All 57 non-signature ZIP entries are identical to the exact CI APK.
- `bc1-make-release-json.py` generated the real public signature manifest; `bc1-restore-signed-apk.py` reconstructed the signed APK byte for byte.
- Private signing material remains outside the repository and CI.

## Upgrade gate

Pending: install signed 1.5.2 (46) over signed 1.5.1 (45) using `adb install -r`, without uninstalling or clearing data. The fixture checks original source bytes/URI, trims, canvas/frame rate, effects, transforms, shape/gradient keys, custom/legacy easing intervals and a paused work clock. It also opens the real portrait Add Media controls after the upgrade. This report will record the actual result after that job finishes.

## Test history and limits

An earlier source, `5c9c360`, ran 107/108 Android tests successfully; a standalone CompositionPlayer test failed in the emulator's native `c2.goldfish.h264.decoder`. Its unchanged retry was superseded by the short-window UI correction. No decoder/engine change is claimed. The final exact source above passed all 108 tests, including that preview-seek case. Earlier phone test assertions were migrated to the compact native controls and recycled list; all insertion, rendering, undo and persistence assertions remain.

Physical Samsung/DeX testing is unavailable. Existing engine limits remain: one effect per clip, unsupported generated-overlay effects remain disabled, and bounded cubic handles do not overshoot. This update changes no project schema, source-media ownership, renderer, shader or export implementation.

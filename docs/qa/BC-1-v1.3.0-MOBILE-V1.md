# BC-1 1.3.0 mobile V1 — tested development preview

Status: qualified development preview on 2026-10-19; not a claim that every requested feature is implemented or that physical-device performance is certified.

Source: `f3e1e02fc4a37f5ace49ebd2ee776ef8437d7e16` on `ui/v1-mobile-sheet`.
CI: https://github.com/uran39281-bit/bc-1/actions/runs/39299364162

- Build and 133 JVM tests: passed, zero failures.
- Lint: zero errors, 50 warnings (including touch accessibility and localization warnings); not warning-free.
- Android API 35 emulator: 42 tests passed. Covers phone and DeX layouts, timeline gestures, selection/tools, history, persistence, playback, live preview and export.
- Ten independent ffmpeg/ffprobe checks passed: three repeated 1080p exports, failure recovery, synchronized speed change, ICE Lite 1080p, 60 fps, HEVC, staged export, and vector-only export.
- New shape restore check waits for real blue pixels from the live texture at the restored time; the earlier black screenshot was taken before frame presentation. No renderer recovery hack was required.
- Screenshots reviewed: main phone workspace with a rendered circle, Add Shapes, small-phone media picker, and 1920×1080 editor. Narrow phones show a scrollable grid rather than squeezing all 18 shapes into three rows. Media filenames and remaining shapes can require scrolling or expanding the sheet.
- Earlier iteration: arc API compilation error corrected; three legacy UI tests updated for the new navigation, time display and palette without removing behavioral assertions.

Physical Samsung/Snapdragon, large-font accessibility and system-picker device workflows were not manually verified. The existing intermittent Media3 stall is not claimed eliminated by a UI change.
Base: main 119e203 (1.2.1), preserving the ICE Lite live preview and existing editor operations.

## Implementation

- Native Android Views; `MobileChrome` owns phone presentation while `EditorActivity` retains editing state, persistence, importing, playback and export.
- Compact header with save-before-project-navigation, editable project title, real preview-resolution badge, Settings and Export. No fabricated AI UHD option.
- Undo/redo, boundary navigation, playback, duplicate and preview framing controls directly below preview.
- Flexible timeline with existing selection, trimming, moving, pinch zoom and snapping; floating Add button and scrollable navigation with 48dp targets.
- Add Content is an overlay sheet in the same Activity, with resize handle, Shape/Media/Audio/Elements content, Text and close rail. Opening does not rebuild the preview or seek.
- Eighteen vector shape types share geometry between thumbnails, preview and export. Existing shape IDs and project serialization remain intact. Shape-only/text-only projects use ICE Lite instead of requiring dummy video.
- Existing tablet/DeX workspace retained, with shared palette and editor state.

## Staged limitations

- Freehand drawing, editable vector nodes, templates and automatic captions are not implemented; no fake controls added. Captions creates manual editable text.
- Shape geometry is preset; transform, fill, outline, opacity, duration and existing keyframes are editable. Independent X/Y scale and arbitrary node editing are not newly implemented.
- Existing engine supports landscape 16:9 and portrait 9:16 project canvases; arbitrary project aspect ratios remain outside this stage.
- Existing limits remain: eight visual tracks, four audio tracks, twenty overlay objects.
- Audio import and waveform display are retained; library audio audition and multi-selection staging need a later pass.
- Sheet opens with reduced-motion-aware animation; close currently dismisses immediately. No fake pagination or unsupported assets.
- Physical Snapdragon/Samsung DeX testing remains necessary. Emulator rendering cannot establish device performance.

## Validation and build

New MobileSheetTest covers sheet state, playhead/preview preservation, shape insertion, undo/redo, persistence and vector-only export. PhoneMediaSpaceTest measures usable media sheet height on two phone sizes. Existing WorkspaceV2Test exercises phone and DeX editing. Existing media export checks are retained, with one additional independently decoded vector-only MP4 check.

Build using JDK 17, Android SDK 36, Gradle 8.11.1:

```
cd BC-1
gradle --no-daemon -Pbc1SideBySide testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

GitHub Actions additionally runs the Android instrumentation suite and independent ffmpeg/ffprobe export verification. The downloadable APK is separately signed with the retained preview key. Certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`. APK verification passed; every non-META-INF ZIP entry matches CI artifact 12211665696. No signing material is committed.

Package: `com.blackice.bc1.preview`; versionName `1.3.0`; versionCode `23`.
APK SHA-256: `b5187845e9eeaae2a4f61ad224a2ff5f35dca70ccf5c9c6d102adbedfb84f8b9`.

Screenshots and APK: [v1.3.0 release folder](../../v1.3.0/).

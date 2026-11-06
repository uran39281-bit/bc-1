# Black Ice Editor 1.4.4 — workspace, catalog and shape geometry

Version **1.4.4**, build **38**, package **com.blackice.bc1.preview**. Android 8.0/API 26 or newer; ARM64 and x86-64 included.

[Download the retained-key Preview APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/Black-Ice-Editor-v1.4.4-Preview-build38.apk) · [SHA-256](SHA256SUMS.txt) · [Source PR #14](https://github.com/uran39281-bit/bc-1/pull/14)

Scope: the replacement October 26 combined prompt's eight areas. Older accumulated feature requests are not an additional checklist for this update.

## Installation

The APK uses the original retained Preview signing certificate. Install over **Black Ice Editor Preview** to retain its project storage. **Integration is a separate app**: keep it and its projects. Do not uninstall either installation or clear its data to resolve an update error. Moving between those packages requires the existing project export/import flow and retaining source media and document-provider permissions.

Certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.

**Upgrade passed:** [publication run](https://github.com/uran39281-bit/bc-1/actions/runs/40462292400) verified installing this APK over Preview 1.4.3 without clearing app data.

The publication gate installs the original signed 1.4.3 build on a disposable API 35 emulator, opens and saves a project, then uses `adb install -r` for this APK. It reopens the project and compares its trims, transform values, motion keys, shape state, canvas/frame rate and source-image bytes. Neither installation is uninstalled and application data is never cleared. The resulting [upgrade verification record](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/upgrade-verification.json) and [post-upgrade screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/upgrade-after.png) accompany the published download. This is an emulator upgrade check; external document-provider access and a physical-phone update are not claimed verified.

## Implemented changes

- **Effect Catalog:** selected-layer **+ Add Effects** opens the catalog in the existing landscape Project Media panel. The exact header, Back, Close, search and six illustrated category cards are native controls. Entries come from the real existing catalog; browsing leaves the selected layer, playhead and saved project unchanged. Back/Close restores media category, query, sorting, view and scroll state. Repeated opening reuses the same panel. On phones, a scrollable catalog band shares the lower tool area with the selected-layer inspector and leaves the player visible.
- **Workspace:** reduced outside padding and panel gutters give the actual canvas more space. Fit uses both available width and height; the canvas keeps its project ratio. The timeline, inspector, transport, Fit, Compare and fullscreen controls remain accessible. The transport row scrolls in narrower desktop windows. One layout calculation positions both preview surfaces and editing controls. ICE Lite renders into a buffer at the selected preview quality, then presents the completed frame to the actual EGL window dimensions; this prevents stale native-buffer sizes from shifting or clipping the image after resizing. Export rendering is unchanged.
- **Create new project:** navy native dialog with name validation, Close, Cancel and primary Create project action. Five proportional outlines appear in order: **16:9, 9:16, 4:5, 1:1, 4:3**. Entire tiles are selectable, with centered labels and accessible selected state. Tiles wrap on narrow screens; the form scrolls with the keyboard. Frame-rate and preview defaults remain 30 fps and 540p. The chosen ratio is saved and used by both export engines; legacy projects retain their existing portrait/landscape interpretation.
- **Shape favorites:** shape tile favorite icons, click targets and accessibility actions are removed. The Star shape, normal media Favorites and stored preference data remain. Shape insertion and drag/drop use the established paths.
- **Selection geometry:** shapes, image overlays and footage share logical bounds and evaluated transforms with rendering. Image metadata resolves off the UI thread and refreshes the selected frame without reselection. View origins, fitting, zoom and pan are applied once. Decoder crop and orientation use displayed dimensions; rotated nonuniform resize gestures use local layer axes.
- **Filled shapes:** Speech Bubble body/tail and Flower components form unified paths. Their interiors have uniform opacity without overlapping paint passes. Deliberate cutouts remain.
- **Shape edges:** the existing vector paths render at output size with 2× supersampling in reusable tiles. A scratch tile is approximately 4.1 MiB, independent of an enlarged shape's dimensions. Logical size, anchors and keyframes remain separate from raster detail. Both render engines share the painter and paths.

## Qualification

**Passed:** 212 unit tests, 81 Android instrumentation tests and 28 independent export checks on the source below. The separate focused suite passed 18 tests and 10 export decodes.

The selection regression compares actual cyan shape bounds from the composed display with shared logical selection bounds in wide, resized and portrait layouts. The media-browser regressions also verify readable card widths, persistent compact-wide access, drag/drop, search and recreation.

The final source is `498fb5a042437e60139604cb5634083bb2bfc988`. The [focused qualification](https://github.com/uran39281-bit/bc-1/actions/runs/40458232203) passed **18 Android tests and 10 independent export decodes**. The build passed **212 unit tests**. Android lint completed with **0 errors and 102 warnings**. The [full qualification](https://github.com/uran39281-bit/bc-1/actions/runs/40458232163) passed on attempt 2: **81 Android tests and 28 independent export checks**, with no skipped tests. Attempt 1 lost its emulator after 30 passing tests; the complete fresh-emulator retry passed.

New checks cover all five ratio choices and persistence, keyboard-visible dialog actions, legacy project compatibility, catalog navigation/search/restoration/non-mutation, resize to portrait, asynchronous image bounds, shape selection under animated rotation and nonuniform scale, decoder crop/orientation dimensions, and all 27 shapes' antialiased edges and uniform translucent alpha.

The new export check renders all five ratios through both **ICE Lite and Media3**, including 1080p landscape output. It checks displayed dimensions (including container rotation metadata), translucent Flower and Speech Bubble interiors, and the bubble body/tail joint. Independent FFmpeg decoding complements Android metadata and pixel checks. Existing tests continue covering projects, media references, timeline editing, playback, audio and export.

## Screenshots

Actual emulator captures use deterministic test projects, not user media. The delivery includes landscape/portrait catalog views, the native creation dialog in both orientations, the aligned selection example, unobstructed shape tiles, an all-shape quality sheet, and decoded 1080p Flower frames from both engines.

| View | Landscape | Portrait |
| --- | --- | --- |
| Effect Catalog | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/screenshots/v144-wide-effect-catalog.png) | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/screenshots/v144-phone-effect-catalog.png) |
| Create new project | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/screenshots/v144-wide-create-project.png) | [Screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/screenshots/v144-phone-create-project.png) |

[Selection alignment](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/screenshots/v144-wide-selection.png) · [Shape tiles](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/screenshots/v144-phone-shapes.png) · [All-shape rendering sheet](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/screenshots/v144-shape-quality.png)

## Remaining limits

- The app's existing effect processing remains bypassed. The catalog exposes real entries and labels them **Currently unavailable** or **Saved · currently bypassed**. Opening it does not fabricate applied processing or enable a new effects engine.
- Physical Samsung/DeX performance, all hardware codecs and every accessibility/input-device combination have not been verified. Resized emulator windows are covered; no physical-device performance claim is made.
- Preview quality still follows the user's preview-resolution setting. A low-resolution preview is not the export resolution. Most new ratio export cases use 360p for qualification; landscape is also checked at 1080p. 4K performance is not benchmarked.
- Destination-dependent shape blend modes retain the existing direct antialiased Canvas path; tiled supersampling applies to ordinary compositing. Text retains its established typographic editing frame; this request fixes media and shape geometry.

## Release integrity

The qualified CI APK is signed offline with the retained Preview key. Uncompressed APK payload entries must match the CI candidate, the package/version and certificate are verified, and 16 KiB ZIP alignment is checked. Publication requires a successful qualification run and a matching complete signed APK hash. Only public ZIP/signature metadata is committed; the private keystore and passwords are never uploaded.

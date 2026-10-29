# BC-1 — Black Ice editor

**Current update: v1.4.6 (build 40) — effects enabled.** Saved clip effects render in preview and export; the Effect Catalog applies/removes them and the Effects panel adds animatable strength. Includes caption-limit messages, export-option handling and compact project storage. [Download the retained-key Preview APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.6/Black-Ice-Editor-v1.4.6-Preview-build40.apk) · [QA, screenshots, upgrade verification and limitations](v1.4.6/QA.md) · [Changelog](docs/CHANGELOG-v1.4.6.md). Install over **Black Ice Editor Preview** 1.4.5 without uninstalling. One effect per clip; effect stacking is not implemented. Physical Samsung/DeX performance testing remains pending.

**Previous update: v1.4.5 (build 39) — refreshed Home, timeline controls, Fit/Fill Canvas, interval easing and recovery.** Includes the reference trim icons, opacity/zoom rulers, adjacent Trash/Undo/Redo, project naming/limits, save-state feedback and recoverable private checkpoints. [Download the retained-key Preview APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/Black-Ice-Editor-v1.4.5-Preview-build39.apk) · [Validation, screenshots and limitations](v1.4.5/QA.md) · [PR #15](https://github.com/uran39281-bit/bc-1/pull/15). Install over **Black Ice Editor Preview**; the signed 1.4.4 upgrade preserves the tested project and media. Keep the separate Integration app and its projects. Effect processing was unavailable in 1.4.5 and is enabled in 1.4.6.

[**All versions and APK downloads ↓**](#all-versions--downloads)

Home of the BC-1 editor (Black Ice Custom Engine): app source in [`BC-1/`](BC-1), released APKs in the version folders below.

## Source

- [`BC-1/`](BC-1) — Android app (Gradle; `gradle -Pbc1SideBySide assembleDebug` builds the `com.blackice.bc1.preview` package).
- [`.github/workflows/bc-1-android.yml`](.github/workflows/bc-1-android.yml) — unit tests, lint, APK, and emulator tests with independent MP4 checks; runs on pushes that change the app, or manually.
- [`docs/qa/`](docs/qa) — QA records per version; [`docs/HANDOFF.md`](docs/HANDOFF.md) — development notes.
- Previous development: v1.0.5 (formerly labelled v2.0.0; selection-based workspace, layered timeline, transforms and keyframe animation) — published below with the recovered retained preview signing key. See [docs/qa/BC-1-v1.0.5-QA.md](docs/qa/BC-1-v1.0.5-QA.md).
- Moved from [Black-Ice-Emulator](https://github.com/uran39281-bit/Black-Ice-Emulator/tree/claude/app-update-4vo05i) at commit `14b8308`; earlier history and version branches remain there.

## Previous separate test build: v1.3.3 — Centered playhead

[Download BC-1 v1.3.3 test APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.3.3-test/BC-1-v1.3.3-test.apk)

Swipe the timeline beneath the centered green playhead to preview frames while paused. Hold then drag to move a clip. Build/unit/lint, 44 Android tests and 10 independent export checks passed.

**Separate test app:** package `com.blackice.bc1.icelite`, version `1.3.3-icelite`, versionCode 26. Keep your existing Preview app and projects. This CI-signed build is not an upgrade for `com.blackice.bc1.preview`; retained-key access was blocked when this older test build was published and has now been restored for v1.4.0-dev. Physical Samsung verification remains pending.

[QA and screenshot](docs/qa/BC-1-v1.3.3-CENTERED-PLAYHEAD.md) · [Checksum](v1.3.3-test/SHA256SUMS.txt)

## Previous Preview upgrade: v1.3.1 — Main workspace and Footage tools

[Download BC-1 v1.3.1 APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.3.1/BC-1-v1.3.1.apk)

Mint/navy native mobile redesign with a 56dp icon-only track strip. Selecting footage opens its tools; + opens Add. Includes playhead trim, safe duplication, selected-clip mute, nested Back navigation, large-font layout and retained timeline position across resizing. Existing project, media, preview and export services are preserved.

137 JVM tests, 43 Android tests and ten independent export checks passed. Package `com.blackice.bc1.preview`, versionCode 24, signed with the retained preview certificate. Install over the existing Preview without uninstalling. See [QA, actual screenshots and limitations](v1.3.1/QA.md), [checksum](v1.3.1/SHA256SUMS.txt), and [changelog/build instructions](docs/CHANGELOG-v1.3.1.md). Physical Samsung performance, full accessibility validation and unsupported shape/effect tools remain outside this qualification.

## Previous: v1.3.0 — Mobile V1 workspace

[Download BC-1 v1.3.0 APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.3.0/BC-1-v1.3.0.apk)

Native mobile workspace, floating Add Content sheet, eighteen editable vector shapes, real media/audio/text insertion and retained timeline editing. Uses the latest ICE Lite live preview; phone and DeX share project state. Signed with the retained preview key: package `com.blackice.bc1.preview`, versionCode 23. Install over the existing Preview without uninstalling.

133 JVM tests, 42 Android tests and ten independent export checks passed. Development preview: freehand, editable path nodes, templates, automatic captions and arbitrary project aspect ratios remain outside this stage. Physical-device performance is unverified. [QA, screenshots and limitations](v1.3.0/QA.md) · [Checksum](v1.3.0/SHA256SUMS.txt) · [Changelog](docs/CHANGELOG-v1.3.md) · [Build instructions](docs/qa/BC-1-v1.3.0-MOBILE-V1.md).

## Previous: v1.2.0 — ICE Lite engine

[Download BC-1 v1.2.0 APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.2.0/BC-1-v1.2.0.apk)

ICE Lite render/export engine plus timeline and media updates. Signed with the retained preview key: package `com.blackice.bc1.preview`, versionCode 21. Install over the existing Preview without uninstalling. 132 JVM tests, 39 Android tests and nine independent export checks passed. [QA and limitations](v1.2.0/QA.md) · [Checksum](v1.2.0/SHA256SUMS.txt) · [Changelog](docs/CHANGELOG-v1.1.md).

## Previous: v1.1.2 preview scrubbing recovery

[Download BC-1 v1.1.2 APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.1.2/BC-1-v1.1.2.apk)

Live-preview graph fixes and bounded recovery for silent seek stalls; version-aware export diagnostics test. 88 unit tests, 28 Android tests and five independent export checks passed. Signed with the retained preview key; install over the current Preview without uninstalling. Development preview: the underlying intermittent Media3 stall is not claimed eliminated. [QA, screenshots and limitations](v1.1.2/QA.md) · [Checksum](v1.1.2/SHA256SUMS.txt).

## Previous: v1.1.1 phone media space

[Download BC-1 v1.1.1 APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.1.1/BC-1-v1.1.1.apk)

On phones, Add opens a large scrollable browser with a small preview and timeline drop target. Done returns to editing. Redundant Inspector/Project tabs and inactive clip tools no longer crowd media selection. Existing media-bin and free-placement changes are retained. 88 unit tests,27 Android tests and five export checks passed. [QA and screenshots](v1.1.1/QA.md).

## Previous: v1.1.0 cinematic redesign preview

[Download BC-1 v1.1.0 APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.1.0/BC-1-v1.1.0.apk)

Native dark-navy/icy-blue theme, desktop navigation, contextual inspector, full-width resizable timeline, media favorites/sort/list controls and preview view/frame controls. Existing editor, projects and package identity preserved; signed with the retained preview key. Install over the existing Preview without uninstalling.

78 unit + 25 Android tests and five independent export checks passed. This is a **development preview**, with incomplete specification items and an unresolved intermittent export stall; physical Snapdragon/DeX performance is unmeasured. [QA, screenshots and limitations](v1.1.0/QA.md) · [Changelog](docs/CHANGELOG-v1.1.md) · [Build instructions](docs/qa/BC-1-v1.1-REDESIGN.md) · [Checksum](v1.1.0/SHA256SUMS.txt).

## Previous: v1.0.5

[Download BC-1 v1.0.5 APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.5/BC-1-v1.0.5.apk)

Selection-based tools, duration handles, timeline zoom, undo/redo, layered editing and transforms. 78 unit + 25 Android tests and five independent export checks passed. Retained signing certificate verified. [QA and limitations](v1.0.5/QA.md).

## Previous: v1.0.4

[Download BC-1 v1.0.4 APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.4/BC-1-v1.0.4.apk)

New in v1.0.4: **Setup → Export diagnostics → Copy report** provides a redacted failure summary for support. Added missing-media failure cleanup/recovery coverage and three required repeated exports. No automatic sending or retries; successful exports retain the last failure report.

From v1.0.3: fixes stale media thumbnails after rapid searches; updates current cards in place without rebuilding the browser. A deterministic regression fails on the old implementation and passes with this fix.

Known limitation: during v1.0.3 testing, one export test failed on the first attempt with a muxer error; the unchanged rerun passed all tests and independent MP4 decoding. The underlying intermittent failure is still under investigation. v1.0.4 passed its full suite and all four independent export checks on its first qualification run; this does not establish the earlier failure's cause. This is a development build, not production-stable certification.

V1 two-column desktop Project Media grid, working media-type filters and filename search, preserved browser state and synchronized clip selection.

Closer to the official V1 phone/desktop references: monoline icons, editing shortcut strip, preview scrubber and pinned track labels.

V1 dark/cyan interface, phone and tablet/DeX workspaces, UI Lock, thumbnail timeline, desktop media browser, GPU effects and transitions, keyframes, chroma controls, audio layers and project bundles. Image/multilayer previews are prepared and cached for reliable paused frames and seeking; edits require preparation again. No 3D Page Curl.

## All versions — downloads

Published APKs and development builds, newest update first. Source-only checkpoints are not listed as downloadable builds. Test counts describe the recorded automated checks, not production-stability certification.

| Version | Download | Verification |
| --- | --- | --- |
| v1.4.6 (build 40) — Effects enabled + cleanup (current Preview upgrade) | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.6/Black-Ice-Editor-v1.4.6-Preview-build40.apk) | 227 unit + 90 Android tests; 30 export checks; 1.4.5 upgrade verified; retained Preview key; [QA and screenshots](v1.4.6/QA.md) |
| v1.4.5 (build 39) — Home, framing, easing + recovery (previous Preview upgrade) | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.5/Black-Ice-Editor-v1.4.5-Preview-build39.apk) | 225 unit + 90 Android tests; 30 export checks; 1.4.4 upgrade verified; retained Preview key; [QA and screenshots](v1.4.5/QA.md) |
| v1.4.4 (build 38) — Catalog, workspace + shape geometry (previous Preview upgrade) | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.4/Black-Ice-Editor-v1.4.4-Preview-build38.apk) | 212 unit + 81 Android tests; 28 export checks; 1.4.3 upgrade verified; retained Preview key; [QA and screenshots](v1.4.4/QA.md) |
| v1.4.3 (build 37) — Instant insertion, timeline + Color & Fill (previous Preview upgrade) | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.3/Black-Ice-Editor-v1.4.3-Preview-build37.apk) | 208 unit + 74 Android tests; 18 independent export checks; retained Preview key; [QA and screenshots](v1.4.3/QA.md) |
| v1.4.2 (build 36) — Browse Files + editable shapes (previous integration app) | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/integration/feature/media-browser-shapes/Black-Ice-Editor-v1.4.2-integration.apk) | 197 unit + 69 Android tests; 16 independent export checks; [QA, screenshots and installation limits](MEDIA-SHAPES-UPDATE.md) |
| v1.4.2 (build 35, older) — Workspace + offline speech/audio (separate integration app) | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/integration/feature/workspace-stability/Black-Ice-Editor-v1.4.2-integration.apk) | 193 unit + 65 Android tests on the integrated application; version-only rebuild; [QA and limitations](INTEGRATION-UPDATE.md) |
| v1.4.1-dev — Media Settings and transforms (older signed Preview upgrade) | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.4.1-dev/BC-1-v1.4.1-dev.apk) | 150 unit + 48 Android tests; 16 export checks; retained-key signed; [QA](docs/qa/BC-1-v1.4.1-MEDIA-TRANSFORM.md) |
| v1.4.0-dev — Landscape stability | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.4.0-dev/BC-1-v1.4.0-dev.apk) | 139 unit + 46 Android tests; 14 export checks; retained-key signed; [QA](docs/qa/BC-1-v1.4.0-LANDSCAPE-STABILITY.md) |
| v1.3.3 — Centered playhead (separate test app) | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.3.3-test/BC-1-v1.3.3-test.apk) | 44 Android tests; 10 export checks; **not a Preview upgrade**; [QA](docs/qa/BC-1-v1.3.3-CENTERED-PLAYHEAD.md) |
| v1.3.1 — Main workspace & Footage tools | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.3.1/BC-1-v1.3.1.apk) | 137 unit + 43 Android tests; 10 export checks; [QA](v1.3.1/QA.md) |
| v1.3.0 — Mobile V1 workspace | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.3.0/BC-1-v1.3.0.apk) | 133 unit + 42 Android tests; 10 export checks; [QA](v1.3.0/QA.md) |
| v1.2.0 — ICE Lite engine | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.2.0/BC-1-v1.2.0.apk) | 132 unit + 39 Android tests; 9 export checks; [QA](v1.2.0/QA.md) |
| v1.1.2 — Preview scrubbing recovery | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.1.2/BC-1-v1.1.2.apk) | 88 unit + 28 Android tests; 5 export checks; [QA](v1.1.2/QA.md) |
| v1.1.1 — Phone media space | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.1.1/BC-1-v1.1.1.apk) | 88 unit + 27 Android tests; 5 export checks; [QA](v1.1.1/QA.md) |
| v1.1.0 — Cinematic redesign | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.1.0/BC-1-v1.1.0.apk) | 78 unit + 25 Android tests; 5 export checks; export-stall caveat in [QA](v1.1.0/QA.md) |
| v1.0.5 — Editing essentials | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.5/BC-1-v1.0.5.apk) | 78 unit + 25 Android tests; 5 export checks; [QA](v1.0.5/QA.md) |
| v1.0.4 Export diagnostics | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.4/BC-1-v1.0.4.apk) | 36 unit +20 Android tests; four independent MP4 checks |
| v1.0.3 Thumbnail reliability | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.3/BC-1-v1.0.3.apk) | 33 unit +18 Android tests; MP4 decode passed on rerun; see export caveat above |
| v1.0.2 Media browser | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.2/BC-1-v1.0.2.apk) | 33 unit + 17 Android tests; independent MP4 decode passed |
| v1.0.1 UI polish | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.1/BC-1-v1.0.1.apk) | 30 unit + 17 Android tests; independent MP4 decode passed |
| v1.0 | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0/BC-1-v1.0.apk) | 30 unit + 17 Android tests; independent MP4 decode passed |
| v0.5 Motion & Layers | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.5/BC-1-v0.5-Preview.apk) | 29 unit + 16 Android tests passed |
| v0.4 Timeline & Export | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.4/BC-1-v0.4-Preview.apk) | Previous preview preserved |

**Current retained-key Preview upgrade:** `com.blackice.bc1.preview`, v1.4.4, versionCode 38. Install over Preview to retain its projects; do not uninstall it first. Integration (`com.blackice.bc1.integration`) and the older v1.3.3 test app (`com.blackice.bc1.icelite`) remain separate installations.

[Current v1.4.4 validation and limitations](v1.4.4/QA.md) · [Current APK checksum](v1.4.4/SHA256SUMS.txt)

Checks use an Android API35 emulator. Physical Snapdragon 8 Gen 3 and Samsung DeX performance remains unmeasured. RTX integration is a future backend. Versioned APKs are retained and never replaced with different builds.

# BC-1 — Black Ice editor

**v1.3.2 source update:** paused playhead-scrubbing fix passed 137 JVM tests, 44 Android tests and 10 export checks. Normal upgrade APK awaits retained signing-key access. [Separate test APK and QA](docs/qa/BC-1-v1.3.2-PLAYHEAD.md); it installs beside Preview and does not share existing project storage. The signed release below remains v1.3.1.

Home of the BC-1 editor (Black Ice Custom Engine): app source in [`BC-1/`](BC-1), released APKs in the version folders below.

## Source

- [`BC-1/`](BC-1) — Android app (Gradle; `gradle -Pbc1SideBySide assembleDebug` builds the `com.blackice.bc1.preview` package).
- [`.github/workflows/bc-1-android.yml`](.github/workflows/bc-1-android.yml) — unit tests, lint, APK, and emulator tests with independent MP4 checks; runs on pushes that change the app, or manually.
- [`docs/qa/`](docs/qa) — QA records per version; [`docs/HANDOFF.md`](docs/HANDOFF.md) — development notes.
- Previous development: v1.0.5 (formerly labelled v2.0.0; selection-based workspace, layered timeline, transforms and keyframe animation) — published below with the recovered retained preview signing key. See [docs/qa/BC-1-v1.0.5-QA.md](docs/qa/BC-1-v1.0.5-QA.md).
- Moved from [Black-Ice-Emulator](https://github.com/uran39281-bit/Black-Ice-Emulator/tree/claude/app-update-4vo05i) at commit `14b8308`; earlier history and version branches remain there.

## Latest: v1.3.1 — Main workspace and Footage tools

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

| Version | Download | Verification |
| --- | --- | --- |
| v1.0.4 Export diagnostics | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.4/BC-1-v1.0.4.apk) | 36 unit +20 Android tests; four independent MP4 checks |
| v1.0.3 Thumbnail reliability | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.3/BC-1-v1.0.3.apk) | 33 unit +18 Android tests; MP4 decode passed on rerun; see export caveat above |
| v1.0.2 Media browser | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.2/BC-1-v1.0.2.apk) | 33 unit + 17 Android tests; independent MP4 decode passed |
| v1.0.1 UI polish | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.1/BC-1-v1.0.1.apk) | 30 unit + 17 Android tests; independent MP4 decode passed |
| v1.0 | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0/BC-1-v1.0.apk) | 30 unit + 17 Android tests; independent MP4 decode passed |
| v0.5 Motion & Layers | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.5/BC-1-v0.5-Preview.apk) | 29 unit + 16 Android tests passed |
| v0.4 Timeline & Export | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.4/BC-1-v0.4-Preview.apk) | Previous preview preserved |

Package `com.blackice.bc1.preview`, retained BC-1 preview signing certificate. Install over your existing Preview to retain projects; do not uninstall it first. v1.0.4 is versionCode16, versionName1.0.4.

[QA evidence and limitations](v1.0.4/QA.md) · [SHA256 checksum](v1.0.4/SHA256SUMS.txt)

Checks use an Android API35 emulator. Physical Snapdragon 8 Gen 3 and Samsung DeX performance remains unmeasured. RTX integration is a future backend. Versioned APKs are retained and never replaced with different builds.

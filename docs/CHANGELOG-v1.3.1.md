# 1.3.1 (version code 24)

- Native mint main/Footage workspace, narrow icon-only timeline headers, contextual actions and nested Back navigation.
- Existing editing, rendering and export services retained; no HTML or demonstration media.
- Non-ripple playhead trim and persisted selected-clip mute gain restoration.
- Responsive phone landscape and 1.3× font layout, timeline scroll retention, safe duplication around occupied/locked tracks.
- Fixed a live-preview surface-attachment race that could leave a resized/recreated preview black.
- Release verification is recorded in `docs/qa/BC-1-v1.3.1-TWO-STATE.md`.

Build: JDK 17, Android SDK 36, Gradle 8.11.1. From `BC-1`, run `gradle -Pbc1SideBySide testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`. GitHub Actions runs the API 35 emulator and independent ffmpeg export checks. Do not publish the CI debug signing identity; distributed preview APKs must use the retained certificate.

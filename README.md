# BC-1 Android APKs

Separate download repository for Black Ice Custom Engine. [Source code](https://github.com/uran39281-bit/Black-Ice-Emulator/tree/bc-1-v1.0.4-diagnostics).

## Latest: v1.0.4

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

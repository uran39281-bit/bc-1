# BC-1 Android APKs

Separate download repository for Black Ice Custom Engine. [Source code](https://github.com/uran39281-bit/Black-Ice-Emulator/tree/bc-1-v1.0.1-ui).

## Latest: v1.0.1

[Download BC-1 v1.0.1 APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.1/BC-1-v1.0.1.apk)

Closer to the official V1 phone/desktop references: monoline icons, editing shortcut strip, preview scrubber and pinned track labels.

V1 dark/cyan interface, phone and tablet/DeX workspaces, UI Lock, thumbnail timeline, desktop media browser, GPU effects and transitions, keyframes, chroma controls, audio layers and project bundles. Image/multilayer previews are prepared and cached for reliable paused frames and seeking; edits require preparation again. No 3D Page Curl.

| Version | Download | Verification |
| --- | --- | --- |
| v1.0.1 UI polish | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.1/BC-1-v1.0.1.apk) | 30 unit + 17 Android tests; independent MP4 decode passed |
| v1.0 | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0/BC-1-v1.0.apk) | 30 unit + 17 Android tests; independent MP4 decode passed |
| v0.5 Motion & Layers | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.5/BC-1-v0.5-Preview.apk) | 29 unit + 16 Android tests passed |
| v0.4 Timeline & Export | [APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.4/BC-1-v0.4-Preview.apk) | Previous preview preserved |

Package `com.blackice.bc1.preview`, retained BC-1 preview signing certificate. Install over your existing Preview to retain projects; do not uninstall it first. v1.0.1 is versionCode 13, versionName 1.0.1.

[QA evidence and limitations](v1.0.1/QA.md) · [SHA256 checksum](v1.0.1/SHA256SUMS.txt)

Checks use an Android API35 emulator. Physical Snapdragon 8 Gen 3 and Samsung DeX performance remains unmeasured. RTX integration is a future backend. Versioned APKs are retained and never replaced with different builds.

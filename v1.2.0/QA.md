# BC-1 v1.2.0 — signed publication

Published from main `886b6f1837c5201f45975fe18a92df4f268bc5ac`, whose tree `892657c9f2572fb1e76ce97f582fcf4f1e9e48e8` is identical to qualified commit `20a59518a44587bb0cd2223ec96a72e80ecb3c58`.

[CI run 39186264263](https://github.com/uran39281-bit/bc-1/actions/runs/39186264263): both build and GPU jobs passed. APK artifact 12158081821, build reports 12157219716, GPU reports 12159070500.

## Verification

- 132 JVM tests, zero failures/skips; lint 44 warnings, no errors.
- 39 Android tests passed.
- Nine independent ffmpeg/ffprobe export checks passed: three repeated 1080p H.264/AAC exports, missing-media recovery, synchronized speed export, ICE Lite 1080p H.264/AAC, ICE Lite 60 fps (120 frames), HEVC/AAC, and staged H.264/AAC export.
- Signed APK manifest verified: `com.blackice.bc1.preview`, versionName `1.2.0`, versionCode `21`.
- Retained signing certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`. Signature verified. Non-signature APK ZIP contents match the qualified CI artifact byte-for-byte. No signing material regenerated or published.
- No application source changes during signing/publication. Previous releases preserved.

## Scope and limits

ICE Lite render/export engine, timeline and media updates as described in [the changelog](../docs/CHANGELOG-v1.1.md). Project schema 5 migrates older projects on opening; preview remains Media3, and the ICE Lite paused live preview is not included. Export settings up to 4K/120 fps remain device-dependent and are not qualified by these 1080p/60 fps checks. Physical Snapdragon/Samsung DeX performance has not been measured here. Historical intermittent Media3 stalls are not claimed eliminated. This is a development preview, not production-stable certification.

Install over the existing BC-1 Preview; do not uninstall first. Back up important projects before upgrading across the schema migration. APK checksum is in SHA256SUMS.txt.

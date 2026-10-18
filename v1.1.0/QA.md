# BC-1 1.1.0 cinematic redesign preview

Qualification source: `6ff1b306876a3af2ec06d1bccf0504e43b57223c`.
[GitHub Actions 39105942908](https://github.com/uran39281-bit/bc-1/actions/runs/39105942908).

Build: 78 JVM tests passed (0 failures, 0 ignored); Android lint 0 errors / 44 warnings. 25 Android tests passed; five independent ffmpeg/ffprobe checks passed (three 1080p H.264/AAC exports, missing-media recovery export, and synchronized five-second speed-change export). Both workflow jobs succeeded.

Package `com.blackice.bc1.preview`, versionName1.1.0, versionCode18. APK signing verified with the retained preview certificate, SHA256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`. All 41 non-signature APK entries are byte-identical to the CI artifact. Install over the existing BC-1 Preview to retain its app data; do not uninstall first. Older APKs are preserved.

The requested native theme, responsive workspace, selected-only toolbar, media sorting/favorites/list mode, preview view controls and frame navigation are implemented. Existing editor and project schema are retained. See [changelog](../docs/CHANGELOG-v1.1.md) and [implementation/build instructions](../docs/qa/BC-1-v1.1-REDESIGN.md).

## Important limits

This is a development preview, not a production-stable claim or completion of every requested feature. Exact mockup parity, independent pre-insertion asset catalog and desktop asset drag/drop, draggable phone inspector drawer, reverse playback, effect tracks and pen/draw remain incomplete. Some new controls lack dedicated end-to-end interaction tests. UI polish, localization and accessibility lint warnings remain. Physical Snapdragon 8 Gen 3 / Samsung DeX performance, thermal behavior and battery use are not measured by the API35 x86_64/SwiftShader emulator.

An intermittent Media3 export stall recurred during this update (run39104959581): the watchdog aborted after 25 seconds without an output sample. Its initiating cause is not fixed. No timeout or export gate was weakened. Even a final passing run does not establish that this issue is resolved. Earlier failed qualification attempts and the test/layout corrections are recorded in the implementation report.

No Page Curl, project schema changes or package identity changes. No signing key is published.

## Visual review

Reviewed final-run screenshots at 1080×1920 phone, 1920×1080 wide and 780×420 compact-wide sizes. Real paused preview pixels, correct loaded-project caption and preserved aspect ratio were visible. The compact window keeps playback accessible and scrolls its remaining navigation. The phone has no squeezed desktop sidebars. Reference parity remains incomplete; duplicate zoom controls and small desktop text are still polish opportunities.

Screenshots: [phone](screenshots/phone.png), [desktop](screenshots/desktop.png), [compact landscape](screenshots/compact.png). Fixtures are generated only by tests, not bundled demonstration media.

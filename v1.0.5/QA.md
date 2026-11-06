# BC-1 v1.0.5 — qualified development build

Source: `7c68ae953e9a01e21ffc0993250929029d063bc3`.
[Qualification run](https://github.com/uran39281-bit/bc-1/actions/runs/39098242781), October 18, 2026 UTC: build and GPU jobs passed on the first attempt.

78 unit tests, 25 Android API35 instrumentation tests, lint 0 errors / 41 warnings. Four independent H.264 1920×1080/AAC export decode checks and the synchronized five-second speed export passed. Phone and wide selection screenshots reviewed; wide transform screenshot captures preview preparation, not a completed frame. Tests cover clip handles, zoom, undo/redo, contextual tools, preview movement, schema migration, save/reopen, rendering and export.

Includes the selection-based workspace, layered timeline, focused controls, direct transforms and animation previously labelled v2.0.0; retains the chosen v1.0.5 numbering. This follow-up fixes exact duration/source-offset precision and rebinds controls after rejected edits. Saved-project schema remains 4 with compatibility for schemas 1–3. No Page Curl.

Package `com.blackice.bc1.preview`; versionName `1.0.5`, versionCode `17`. Recovered retained preview key used privately; certificate SHA256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d` matches the v1.0.4 APK. APK signature verified and every non-META-INF payload entry compared identical to the tested CI APK. No key is stored in this repository.

Install over the original Preview to retain projects; do not uninstall first. An independently signed candidate cannot be updated with this key. Earlier APKs are preserved.

Limits: emulator qualification, not physical Snapdragon/DeX performance certification. Preview gestures show guides and re-prepare the composition on release. Historical intermittent muxer failure remains unexplained despite this passing run. Development-signed debug APK, not production-stable certification.

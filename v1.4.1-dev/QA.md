# v1.4.1-dev — Media Settings and Move & Transform

The selected media's phone and DeX panels share four square blue tiles with decorative waves. Effects remains explicitly unavailable under the existing engine policy. Audio and Blending retain their existing destinations; the Add button retains its import behavior. The phone's existing footage operations remain available.

Move & Transform has Position, Rotation and Scale tabs. Numeric entry validates finite values; rulers update the live renderer without flinging; the dial accumulates wrapped pointer deltas through multiple revolutions. Each gesture is a single undo transaction and cancellation restores the original project. Reset operations affect only their named properties at the current animation scope. Each tab navigates only its own keys and disables unavailable directions.

Width and Height are independently animatable. Linked edits preserve the proportion evaluated when the edit begins and create paired keys when either dimension is animated. Legacy uniform-scale curves are copied into Height only on first dimension edit, with all times and easing preserved. Linking itself does not change the project.

## Compatibility

- Project schema 6 adds `heightScale` and appends the Height motion channel. Schemas 1–5, including six- and ten-channel curves, remain readable. The existing store retains a backup before saving an older schema. Old application versions cannot open schema 6 projects.
- Stored coordinates remain centered, positive Y upward. UI values are project pixels. Rotation storage remains cumulative counterclockwise degrees; the new clockwise-positive dial and fields convert the sign at the UI boundary. Old projects render in their original direction.
- Preview, Media3 effects and ICE Lite exports use the shared evaluated transform. Legacy skew, anchors and flips remain stored and rendered.
- Normal media scale 100% is the fitted import size. Legacy overlays use their original default scale as 100%.
- The existing positive-scale limits remain: Width 5–400% for clips (legacy overlays retain their internal 0.1–2 bounds); Height internal scale 0.0025–80. Zero is rejected because the existing inverse shader requires a positive width. Cumulative rotation accepts finite signed values; only the rendered pointer/matrix uses modulo 360 after animation evaluation. These are development-build limits, not hidden flips or modulo rotation.

## Verification

The complete build and GPU run [39709627434](https://github.com/uran39281-bit/bc-1/actions/runs/39709627434) passed at `ae843e11369a349c64deb3fe7273c9cd1efacc22`: JVM tests, Android lint, APK assembly, all 48 Android instrumentation tests, and 16 independent encoded-video checks. Phone Media Settings/Scale/Rotation and wide Media Settings/Position screenshots were reviewed from QA commit `1f97c0f90029862321f83a0ece2cd8193c8e6bb0`; small-screen panels scroll to retain usable touch targets.

The follow-up `2ab6fa383fc975992cffe8d726a0f22d6a205253` fixes Pop In to animate both dimensions proportionally, with regression coverage for independent dimensions, scale boundaries and old six-channel callers. Its complete build, 150 JVM tests, lint, APK assembly, 48 Android tests and 16 independent export checks passed in [39718133591](https://github.com/uran39281-bit/bc-1/actions/runs/39718133591). The final run completed successfully. The subsequent source-branch head changes only this QA record; app source is identical to the qualified commit.

The [test APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/eaa36bc0e9aaf8108c41ed6bf3e6f76137b53680/BC-1-ICE-Lite-test.apk) is pinned to that source commit. It installs as `com.blackice.bc1.icelite` beside BC-1; it is not an in-place update of the normal signed release.

Added JVM regressions cover linked proportions, independent dimensions, legacy curve preservation at many times, ten-channel decoding, independent Height persistence, cumulative rotation interpolation, pointer wrapping, invalid scale and property-specific navigation. Added Android UI coverage exercises tile geometry, full-turn dial gestures, live cancellation, playhead stability, undo/redo, reset scope and paired keys. A new encoded export test checks two full revolutions in each direction after save/reopen by sampling an asymmetric four-color image, with independent ffmpeg decoding.

Physical Samsung/DeX gesture feel, accessibility with assistive technology, and hardware performance remain unverified. Existing older releases remain available.

## Signed Preview upgrade

Published October 22, 2026 (America/Phoenix). Package `com.blackice.bc1.preview`, version `1.4.1-dev`, versionCode `28`. The exact qualified CI APK was signed with the retained Preview certificate (SHA-256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`); manifest and resulting signature verified. Signing material is excluded.

[Download the upgrade](BC-1-v1.4.1-dev.apk) · [SHA256](SHA256SUMS.txt) · [Actual screenshots](screenshots)

Install over the existing Preview; do not uninstall it. Small phone panels scroll vertically to reach the remaining tiles and controls. Effects remains Coming Later. Physical Samsung hardware and full assistive-technology testing remain unverified.

Build: JDK 17, Gradle 8.11.1, Android SDK 36; from `BC-1/`, run `gradle --no-daemon -Pbc1SideBySide testDebugUnitTest lintDebug assembleDebug`. The GitHub workflow generates media fixtures and runs emulator/export checks.

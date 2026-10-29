# Black Ice Editor 1.4.6 — Effects enabled

Version **1.4.6**, build **40**, package **com.blackice.bc1.preview**. Android 8.0/API 26 or newer; ARM64 and x86-64 included.

[Preview APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.6/Black-Ice-Editor-v1.4.6-Preview-build40.apk) · [SHA-256](SHA256SUMS.txt) · [Source PR #16](https://github.com/uran39281-bit/bc-1/pull/16) · [Changelog](../docs/CHANGELOG-v1.4.6.md)

Install over **Black Ice Editor Preview** 1.4.5. The original Preview signing certificate is retained. No uninstall or data clearing is required. The Integration app remains separate.

## Implemented

- **Effects render again.** The runtime bypass is removed: the live preview, the playback proxy and both export engines (ICE Lite and Media3) render each clip's saved effect with the shared layer shader. Projects saved by earlier versions keep their effect values, which now render.
- **Effect Catalog:** for a selected video or picture, tap an effect to apply it, or tap the applied effect to remove it. One effect per clip; each change is one undo step. Audio clips and text/shape layers show the catalog without applying anything.
- **Effects panel:** shows the applied effect, an animatable **Effect strength** control (centred effects note that 50% is neutral) and **Remove effect**.
- **Caption limit:** exceeding 2,000 captions reports a caption-specific message.
- **Export:** the saved export options are used unless a caller requests a specific resolution; the plan is computed once.
- **Storage:** projects are written as compact JSON, which reduces file and undo-history size for caption-heavy projects. Earlier pretty-printed files still load.
- Removed the undeclared `MainActivity` and the “Effects — Coming Later” stub.

## Qualification

Qualified source: `8e611103b940d1690971292c8ecb516c56890bfd`.

[CI run 40817895753](https://github.com/uran39281-bit/bc-1/actions/runs/40817895753) passed:

| Check | Result |
| --- | --- |
| Unit tests, lint, app and instrumentation APK build | Passed |
| Android instrumentation, API 35 emulator | 90 passed (attempt 2) |
| Independent FFmpeg export checks | Passed |

Attempt 1 of the GPU job failed one timing-based input test, `InstantFillTest.toolbarMarkerDragCreatesOnceAndCancelLeavesProjectUntouched`, which injects a real drag immediately after a window resize. It passed on the previous run with identical app code and on the single re-run. It is recorded here rather than treated as fixed.

New `EffectsRenderTest` (JVM) covers effect persistence through render snapshots and save/load, and the caption-limit message. Existing `LivePreviewTest` and `BcCoreExportTest` render effects through the live preview and ICE Lite export, and now exercise the same path the app uses.

The publication gate installs signed Preview 1.4.5 build 39, saves a fixture project with a saved effect, installs this APK with `adb install -r`, and verifies the project, original media bytes, trim, transforms, motion keys, shapes and the effect value survive and are re-saved as compact schema-11 JSON.

## Remaining limits

- Physical Samsung/DeX hardware and real-device effect performance were not tested. Distortion and blur effects are heavier than colour effects on low-end GPUs.
- One effect per clip; effect stacking is not implemented.
- Text, shape and image overlay layers do not take effects.
- `BC-1/.github/workflows/build-android.yml` is inert and was not removed.

## Release integrity

Certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.

APK SHA-256: see [SHA256SUMS.txt](SHA256SUMS.txt), produced with `release.json` by `.github/scripts/bc1-make-release-json.py` on the private signing machine.

Only public ZIP/signature metadata is committed. The private signing key and credentials are never included in the repository or APK delivery.

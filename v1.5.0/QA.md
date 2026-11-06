# Black Ice Editor 1.5.0 — Layer Tools and compact toolbar

Version **1.5.0**, build **44**, package **com.blackice.bc1.preview**.

> **Status: upgrade verified. The retained-key signed APK is published with this record.** Signed Preview 1.4.9 build 43 was upgraded in place to 1.5.0 build 44; the fixture, edits, saved effect, custom and legacy easing curves and original media were preserved.

## Qualified source and checks

Source `ee4441451aada4121ea89d896624d2fd3c3e582f`; [CI run 41387688120](https://github.com/uran39281-bit/bc-1/actions/runs/41387688120), attempt 2 passed. [PR #22](https://github.com/uran39281-bit/bc-1/pull/22) merged as `f92b413f8cb41895d4f20a0fdb744c8de6556574`. Main's app subtree matches this tested source exactly.

| Check | Result |
| --- | --- |
| JVM tests | 271 passed; 0 failures, 0 ignored |
| Android instrumentation, API 35 emulator | 99 passed on re-run |
| Independent export checks | 30 passed, plus reversed audio direction check |
| Reverse frame order and Restore Forward | Passed; exact original 0 ms in-point restored |
| Reversed audio direction | First 0.3 s max −91.0 dB; final 0.3 s max −0.2 dB; required difference >20 dB passed |
| Lint | 0 errors, 109 warnings |
| Artifact ZIP hashes | Candidate and both qualification reports verified |
| Retained certificate, APK signature, 16 KiB ZIP alignment | Passed |
| Non-signature APK payloads compared with CI | Identical |
| Publication restore script | Byte-for-byte identical signed APK |

Artifacts: candidate `13115981803`, build reports `13116162291`, passing GPU reports **`13117621707`**. Failed first-attempt GPU artifact `13115958773` was inspected for test history and is not used for qualification.

### Re-run history

The first attempt on this same source had nine failures: CombinedAudioWorkspaceTest, ExportDiagnosticsUiTest, HomeBrowserTest, InstantFillTest, MediaShapesTest, PreviewScrubTest, RecoveryLifecycleTest, WorkflowControlsTest and Workspace144Test. The full unchanged re-run passed all 99 tests. The cause of those first-attempt failures was not fixed in this signing session; they remain a reliability limitation, not a production-stability certification.

The earlier LayerTools reverse-restore and four-versus-five Media Settings card failures were fixed in the source before this run. The new passing end-to-end reverse test verifies backwards frame ordering, restores the original URI and exact 0 ms in-point, then exercises undo/redo. The independent FFmpeg check verifies the opening source tone moves to the end of the reversed file.

## Changes

- Layer Tools has separate Speed, Reverse, Screenshot, Joint Movement and Copy All tabs.
- Reverse prepares a derived file in background; Restore Forward returns to the original source. The original trimmed span is retained when the derivative reports a duration one frame short.
- Screenshot captures the selected visible layer at the playhead into a new managed transparent image layer.
- Joint Movement provides parent/child relationships, with coverage for pose preservation, following a moved parent, cycle rejection, deletion, undo and save/reopen.
- Copy All snapshots transformations/effects; existing attribute paste controls apply the copied settings.
- Timeline toolbar uses compact pointer controls and separated touch targets. Timeline zoom controls/ruler are removed; the remaining timeline tools and Fit remain.

## Installation and upgrade gate

Install over **Black Ice Editor Preview 1.4.9** without uninstalling or clearing data. The separate Integration app remains separate.

The publication workflow first installs retained-key 1.4.9 build 43, saves a fixture and installs this signed 1.5.0 build 44 APK with `adb install -r`. It must preserve original media bytes, trims, transforms, effects, shapes, and custom/legacy easing keys. [Publication run 41452462412](https://github.com/uran39281-bit/bc-1/actions/runs/41452462412) passed. [Upgrade evidence](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.0/upgrade-verification.json) confirms the fixture and original media were preserved.

## Actual screenshots

Captures are from the passing 1.5.0 CI attempt. `v147-` filenames and the “1.4.4 • Workspace” project name are retained fixture identifiers. The `v150-layer-tools-copy-all` capture actually shows the Speed tab; its filename does not establish the visible tab. The tabs scroll horizontally in narrow inspectors.

- [v150-toolbar-pointer](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.0/screenshots/v150-toolbar-pointer.png)
- [v150-toolbar-touch](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.0/screenshots/v150-toolbar-touch.png)
- [v150-reverse-tab](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.0/screenshots/v150-reverse-tab.png)
- [v150-screenshot-result](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.0/screenshots/v150-screenshot-result.png)
- [v150-joint-movement](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.0/screenshots/v150-joint-movement.png)
- [v150-layer-tools-copy-all](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.0/screenshots/v150-layer-tools-copy-all.png)

## Limits and integrity

Reverse is limited to 3,600 frames per operation (about two minutes at 30 fps). A reversed clip's own keys before its trimmed start are dropped. Splitting a parent leaves children following the left half; duplicating a parent does not duplicate its children. The upgrade fixture proves older projects survive but does not exercise every newly introduced relationship/reverse combination.

Physical Samsung/DeX performance and a full accessibility audit remain pending. Screenshot review is not pixel-perfect certification. New 9:16 canvas creation remains paused, while existing projects remain supported. One effect per clip; no effect stacking. The first-attempt failures and lint warnings above remain recorded limitations.

APK SHA-256: `f1f1552c2e9fd1b9a06a3c61dbd7b887a2e9e7cf976543d8f84e95023c5c7436`.

Certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.

[Download APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.0/Black-Ice-Editor-v1.5.0-Preview-build44.apk) · [Checksums](SHA256SUMS.txt) · [Changelog](../docs/CHANGELOG-v1.5.0.md)

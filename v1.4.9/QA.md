# Black Ice Editor 1.4.9 — Menus and fixed-width panels

Version **1.4.9**, build **43**, package **com.blackice.bc1.preview**.

> **Status: upgrade verified. The retained-key signed APK is published with this record.** Signed Preview 1.4.8 build 42 was upgraded in place to 1.4.9 build 43; the fixture, edits, saved effect, custom and legacy easing curves and original media were preserved.

## Qualified build

Source `698ec2b88598835ec0533d0b441fd224584c45a5`; [CI run 41371743145](https://github.com/uran39281-bit/bc-1/actions/runs/41371743145), attempt 2 passed. PR #21 is merged; main's app subtree matches this tested source exactly.

| Check | Result |
| --- | --- |
| JVM tests | 253 passed, 0 failures, 0 ignored |
| Android instrumentation, API 35 | 94 passed on re-run |
| Independent export checks | 30 passed |
| Lint | 0 errors, 107 warnings |
| Artifact ZIP hashes | All four verified |
| Retained certificate, APK signature, 16 KiB ZIP alignment | Passed |
| Non-signature APK payloads versus CI | Identical |
| Publication restore script | Byte-for-byte identical signed APK |

Candidate artifact: `13109950452`; build reports: `13108789561`; passing GPU reports: **`13110758878`**. Do not use failed GPU artifact `13110630239` for qualification.

The first GPU attempt failed `InstantFillTest.toolbarMarkerDragCreatesOnceAndCancelLeavesProjectUntouched` (expected one marker, observed zero). The same source passed all 94 tests on re-run. No fix for that intermittent failure is claimed by this release.

## Changes

- Dynamic panel resizing is removed: side panels keep their fixed widths again. Fit and Compare return over the picture.
- Position, Rotation, Scale, Blending & Opacity and Effects use blue accents, underlined tabs, a toggle-style Lock W and H, a joined Previous/Next bar in Effects, larger angle-preset buttons and value text. Normal blend and Export remain green.
- New 9:16 project creation and canvas selection remain paused; existing 9:16 projects still open, edit and export. Portrait interface orientation remains supported.
- Project schema remains 11.

## Install and upgrade gate

Install over **Black Ice Editor Preview 1.4.8** without uninstalling or clearing data. The separate Integration app remains separate.

Publication requires the signed 1.4.8 build 42 → 1.4.9 build 43 emulator upgrade to pass. It checks source media bytes, trims, transforms, shapes, saved effect and the custom/legacy easing key IDs and coordinates. [Publication run 41376465253](https://github.com/uran39281-bit/bc-1/actions/runs/41376465253) passed. [Upgrade evidence](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.9/upgrade-verification.json) confirms preservation of the fixture and its original media.

## Actual screenshots

The `v147-` filenames and “1.4.4 • Workspace” project name are retained test identifiers, not the installed version. Captures are from the passing 1.4.9 CI attempt. Isolated panels cover 300, 360 and 450 dp; some isolated numeric readouts are blank, while full-editor captures show real values.

- [v147-phone-position](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.9/screenshots/v147-phone-position.png)
- [v147-wide-position](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.9/screenshots/v147-wide-position.png)
- [v147-panel-position-360](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.9/screenshots/v147-panel-position-360.png)
- [v147-panel-rotation-360](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.9/screenshots/v147-panel-rotation-360.png)
- [v147-panel-scale-360](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.9/screenshots/v147-panel-scale-360.png)
- [v147-panel-blending-360](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.9/screenshots/v147-panel-blending-360.png)
- [v147-panel-effects-360](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.9/screenshots/v147-panel-effects-360.png)

## Limits and integrity

The phone capture still shows header/status-bar crowding. Physical Samsung/DeX testing of the signed APK, performance and a full accessibility audit remain pending. Automated screenshots are not pixel-perfect certification. One effect per clip; no stacking. Cubic handles remain bounded to 0–1. Lint warnings and the first-attempt marker-drag failure remain recorded limitations.

APK SHA-256: `dd0068f1311aa0ebe9960e169c865af5b2d7b8c2955f238070c3c08cde2286c3`.

Certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.

[Download APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.9/Black-Ice-Editor-v1.4.9-Preview-build43.apk) · [Checksums](SHA256SUMS.txt) · [Source PR #21](https://github.com/uran39281-bit/bc-1/pull/21)

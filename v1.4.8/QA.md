# Black Ice Editor 1.4.8 — Menu polish and dynamic window

Version **1.4.8**, build **42**, package **com.blackice.bc1.preview**.

> **Status: upgrade verified. The retained-key signed APK is published with this record.** Signed Preview 1.4.7 build 41 was upgraded in place to 1.4.8 build 42; the fixture, edits, saved effect, custom and legacy easing curves and original media were preserved.

## Qualified build

Source `2cbf66a82d3535eaf9feabb454008fc0877711cb`; [CI run 41265528019](https://github.com/uran39281-bit/bc-1/actions/runs/41265528019), attempt 1 passed. Main's app subtree is identical to this tested source.

| Check | Result |
| --- | --- |
| JVM tests | 253 passed; 0 failures, 0 ignored |
| Android instrumentation (API 35) | 95 passed |
| Independent export checks | 30 passed |
| Lint | 0 errors, 107 warnings |
| Candidate ZIP digest | `015b734b014138baeb3fcadb3e7b0c0fdd745e382b2a7fa4b70434820094f412` verified |
| Retained certificate, APK signature and 16 KiB ZIP alignment | Passed |
| Non-signature APK payloads versus CI | Identical |
| Publication restore script | Byte-for-byte identical signed APK |

Artifact IDs: candidate `13066774712`, build reports `13067133186`, GPU reports `13066759477`. All artifact ZIP hashes were verified. The ZIP digest differs from the signed APK hash below.

## Changes

Menu styling preserves dark outlined cards and mint selected states. Compact key navigation avoids the previous broken label wrapping. The graph card, Effects heading and rulers follow the supplied reference details. Wide-layout panels expand into available preview side space while retaining the picture size, then return to their resting width. Fit and Compare move to the transport row.

**9:16 creation is paused:** new-project and canvas selection offer 16:9, 4:5, 1:1 and 4:3. Existing 9:16 projects still open, edit and export. This concerns project canvas options, not whether the interface supports portrait phone orientation.

## Installation and upgrade

Install over **Black Ice Editor Preview 1.4.7** without uninstalling or clearing data. The separate Integration app remains separate.

The publication workflow must first install signed 1.4.7 build 41, save a fixture, install this APK with `adb install -r` and verify its original media, trims, transforms, saved effect, shapes, key IDs and custom/legacy easing survive. [Publication run 41270861726](https://github.com/uran39281-bit/bc-1/actions/runs/41270861726) passed. [Upgrade evidence](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.8/upgrade-verification.json) records the preserved project and original media.

## Screenshots

Actual captures come from this qualified 1.4.8 run. `v147-` filenames and the “1.4.4 • Workspace” fixture name are retained test identifiers, not app versions. Isolated 300/360/450 dp panel renders show the entire menus; some numeric readouts are blank in those isolated renders. Full editor captures also exist. These captures are evidence of layout, not a pixel-perfect certification.

- [v147-phone-position](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.8/screenshots/v147-phone-position.png)
- [v147-wide-dynamic-working](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.8/screenshots/v147-wide-dynamic-working.png)
- [v147-wide-dynamic-catalog](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.8/screenshots/v147-wide-dynamic-catalog.png)
- [v147-panel-position-300](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.8/screenshots/v147-panel-position-300.png)
- [v147-panel-effects-450](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.8/screenshots/v147-panel-effects-450.png)

## Limits and integrity

The portrait capture still shows header/status-bar crowding. Physical Samsung/DeX testing of this signed APK remains pending. Lint warnings remain. One effect per clip; no effect stacking. Cubic handles remain bounded to 0–1.

APK SHA-256: `e090cc77c79b28cf680048e54c8696facce67783187bbffae7256938ed4f9fb3`.

Certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.

[Download APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.8/Black-Ice-Editor-v1.4.8-Preview-build42.apk) · [Changelog](../docs/CHANGELOG-v1.4.8.md) · [Checksums](SHA256SUMS.txt)

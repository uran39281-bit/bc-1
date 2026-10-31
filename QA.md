# Black Ice Editor 1.4.7 — Menus and Easing Curve Editor

Version **1.4.7**, build **41**, package **com.blackice.bc1.preview**.

> **Status: upgrade verified. The retained-key signed APK is published with this record.** Signed Preview 1.4.6 build 40 was upgraded in place to 1.4.7 build 41; the fixture, edits, saved effect, custom and legacy easing curves and original media were preserved.

[Source PR #19](https://github.com/uran39281-bit/bc-1/pull/19) · [Changelog](../docs/CHANGELOG-v1.4.7.md)

## Source and qualification

Source commit: `d52de04e2ef8c70763e7b19f9b340dec804f318c`.
CI: [41161341076](https://github.com/uran39281-bit/bc-1/actions/runs/41161341076).

[CI run 41161341076](https://github.com/uran39281-bit/bc-1/actions/runs/41161341076) passed on the exact source above: **250 JVM tests, 93 Android instrumentation tests and 30 independent export checks**. Lint: **0 errors, 107 warnings**. No tests were skipped in the JVM report. The new Height regression passed in the emulator. PR #19 was merged as `994aab756b92414a4803339e2b65989d3e8e4978`.

Candidate artifact: `13026914316`; passing GPU reports: `13027134148`; build reports: `13027014052`. The earlier `645d65f` candidate is superseded and is not released.

Retained certificate verification, APK signature verification, 16 KiB ZIP alignment, identical non-signature payloads versus CI, and byte-for-byte reconstruction through the publication restore script all passed.

APK SHA-256: `3c886c98e3eefdaed233c701a5ed51672a9a2fc058638397ed8bbb562e0badf9`.

The new regression drives the Height diamond on static and animated legacy-scale clips and shape overlays, checks that Width remains unchanged, and checks undo restores the legacy representation. The fix targets the requested property after separating legacy scale rather than writing to Height's former read-through Width track.

## Implemented

- Position, Rotation, Scale, Blending & Opacity and Effects use the native Black Ice controls and the existing project model, renderer and undo system.
- Two-handle easing editor changes one outgoing keyframe interval. It supports expansion, cancellation, reset, keyboard and accessibility actions.
- Legacy analytic easing remains unchanged until edited; custom cubic coordinates and stable key IDs remain stored in schema 11.
- Cumulative rotation, linked or independent dimensions, seven visible blend choices plus the remaining clip modes under More, and real effect strength.
- Add Effects opens the catalog even with no selected layer, provided the editor is idle and unlocked.
- Height keyframe creation/removal on legacy uniform-scale layers preserves Width's animation.

## Upgrade and integrity

Install over **Black Ice Editor Preview** 1.4.6 without uninstalling or clearing app data. The separate Integration app is not replaced.

The publication workflow installs signed Preview 1.4.6 build 40, saves a fixture, installs this APK with `adb install -r`, and verifies original media, transforms, trims, shapes, effect values, custom cubic handles, legacy easing and key IDs before publication. This is pending until recorded below.

Expected retained Preview certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.

## Remaining limits

- Physical Samsung and DeX hardware, performance and a full TalkBack audit are not covered by emulator qualification.
- One effect per clip; no effect stacking. Existing text/shape overlay effect limitations remain.
- Cubic handles stay within 0–1; overshoot is not supported.
- Legacy Ease In/Out is piecewise. Its displayed handles are approximate until edited; the plotted curve and render evaluation preserve the original analytic easing.
- Automated captures cover all five menus in portrait and Position in landscape, not all five menus in both layouts.
- Lint warnings remain; zero errors is not described as warning-free.

## Actual screenshots

The fixture name “1.4.4 • Workspace” is a project name, not the installed version. These are unmodified captures from the qualified 1.4.7 run. Portrait panels scroll; these captures show the top of each panel, not every control or the complete easing graph. The portrait capture shows header/status-bar crowding, and the narrow landscape Previous Key label wraps awkwardly. Visual polish remains; the functional tests do not establish pixel-perfect fidelity.

- [phone-position](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.7/screenshots/v147-phone-position.png)
- [phone-rotation](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.7/screenshots/v147-phone-rotation.png)
- [phone-scale](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.7/screenshots/v147-phone-scale.png)
- [phone-blending](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.7/screenshots/v147-phone-blending.png)
- [phone-effects](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.7/screenshots/v147-phone-effects.png)
- [wide-position](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.4.7/screenshots/v147-wide-position.png)

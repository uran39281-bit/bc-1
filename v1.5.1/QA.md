# Black Ice Editor Preview 1.5.1 — build 45

> **Status: qualified and signed locally; publication awaits the retained-key 1.5.0 → 1.5.1 upgrade gate.**

## Changes

- Compact, scrollable editor inspector controls in the wide workspace. Position, Rotation, Scale, Blending and Effects share spacing and flexible numeric rows. Other inspector action menus use the same density. Portrait retains 48 dp controls; wide controls start at 36 dp and grow with font scaling.
- Previous Key / Reset all / Next Key stay together. Keyframe diamond, property name, exact value and Reset share a row where their measured text fits; very narrow panels and large fonts can reflow safely.
- Position offers a saved Ruler / Slider / Trackpad preference, with Ruler as the default. Exact values, keyframes, reset and easing remain available in every mode. Preference changes do not alter projects.
- Relative trackpad movement updates both axes in one undo transaction; cancel, interruption and disable roll back. Arrow keys move one project pixel, Shift moves ten. Focus survives committed edits, and focused arrows do not seek the timeline.
- Existing project schema, media access, engine, preview, timeline and export implementations are retained. No preview/timeline scaling or icon artwork changes.

## Exact qualification

| Item | Value |
|---|---|
| Qualified source | `7c76a0211273d0bc2f4c084b590286e6849dc040` |
| PR | [#23](https://github.com/uran39281-bit/bc-1/pull/23) |
| Merge | `29835f452f3a404792ac87e4e0edb0ec61c549af` |
| Passing source CI | [41565979442](https://github.com/uran39281-bit/bc-1/actions/runs/41565979442) |
| Candidate artifact | `13199386310` — Black-Ice-Editor-v1.5.1-ci-candidate |
| Candidate ZIP SHA-256 | `0ebef2d0345b5ce6aa460683379ff68514bb8b23495588b1bb211a4016bc9f23` |
| Build reports | `13199036438` |
| Passing GPU reports | `13200310513` |
| GPU ZIP SHA-256 | `5e5da08369bee0fb69062ff72ab891373546982ff02a21d0b17cb6ced3613a63` |
| Package | `com.blackice.bc1.preview` |
| Version | 1.5.1 (45) |
| Retained Preview certificate SHA-256 | `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d` |
| Signed APK SHA-256 | `2c1b69156276c45e723eea98deb01100d8be3904ab1697d980067a49d7a4f707` |

## Checks performed

- **271 unit tests passed**, zero failures and zero ignored tests.
- **Lint: zero errors, 110 warnings.** Warnings remain, including existing localization/custom-view warnings; they have not been hidden.
- **4 inspector preflight tests passed**, followed by the **complete 103 Android tests**, all passing. Preflight supplements the full suite.
- Tests cover static and animated clips and shape overlays, simultaneous X/Y motion, one undo per gesture, undo/redo, cancellation, disabling during a gesture, locked editing, numeric values, sliders, repeated keyboard arrows without seeking, preference recreation and measured compact rows with scrollable graphs.
- **30 independent export/decode checks passed**, including Media3 and ICE Lite, H.264/HEVC, cumulative rotation, canvas ratios, shapes, animated fills and recovered framing/easing.
- The reverse end-to-end Android test passed, including original trim restoration and frame order. Independent reversed-audio inspection found the first 0.3 s at -91.0 dB and the last 0.3 s at -0.2 dB, consistent with the reversed fixture.
- Qualified candidate, build reports and GPU report ZIP digests verified before extraction.
- Signed the exact CI candidate with the retained Preview key. Verified certificate, package/version, 16 KiB ZIP alignment and every nonsignature ZIP entry payload, including META-INF build metadata. Public release-manifest reconstruction matches the signed APK byte for byte.
- Inspected actual wide Position/Trackpad, Scale and graph captures and the portrait Position menu from the qualified run. Captures for all five primary menus and Layer Tools are included. `1.4.4 • Workspace` in screenshots is the test project's name, not the installed app version.

## Test history

The first full emulator run on `5f92f9d` passed 102/103 tests and exposed missing trackpad focus in touch mode. The subsequent preflight on `f6b5141` exposed arrow keys reaching timeline shortcuts rather than moving the layer. These were real integration failures: touch focus and focused-key routing were fixed. The unchanged assertions, plus a playhead-preservation assertion, pass on the qualified source above. Superseded candidates were not published. Early runs cancelled by later source pushes are not qualification.

## Upgrade and installation

Publication is gated on upgrading retained-key signed Preview **1.5.0 build 44** in place with `adb install -r`, without uninstalling or clearing data. The gate verifies the fixture can open/save before and after, retaining media bytes/URI, trims, transforms, keys, custom and legacy easing, shape fill animation, canvas/frame rate and a saved effect.

Once published, install this APK as an update to the retained-key Preview app. Do not uninstall the existing app to work around a signature mismatch.

## Remaining limitations

- Physical Samsung S24+ / Samsung DeX hardware has not been tested for this release; validation uses an Android 15 GPU emulator and real window-dimension changes.
- Very narrow inspectors or large accessibility fonts may reflow a value field to keep it readable. Lower controls and graphs are reached by scrolling; the graph can also expand.
- Some full-workspace screenshots caught the asynchronous preview before its fixture frame appeared. The rendered-output and export tests pass separately; captures are UI evidence, not a substitute for those checks.
- Existing engine limits remain: one effect per clip, bounded cubic handles without overshoot, and unavailable audio visualizer/templates/freehand editing.

# Black Ice Editor Preview 1.5.4 — build 48

> **Status: signed candidate; upgrade verification and publication pending.** Full source qualification passed. The retained-key signed APK is ready for the separate in-place upgrade gate; this record does not claim that gate has already passed.

## Exact build and signature

| Field | Verified value |
|---|---|
| Package / version | `com.blackice.bc1.preview` / 1.5.4 (48) |
| Qualified source | `62b2ca19250d329a29af7f9e19028cb5fcecb75f` |
| PR / merge | [#28](https://github.com/uran39281-bit/bc-1/pull/28) / `f2892a54ff6feca34989ccac3d1a900007427d06` |
| Full CI | [42171039606](https://github.com/uran39281-bit/bc-1/actions/runs/42171039606), success on that exact source |
| Candidate artifact | `13480931985` — Black-Ice-Editor-v1.5.4-ci-candidate |
| Passing GPU reports | `13481993893` — BC-1-GPU-test-reports |
| Build reports | `13481585995` |
| Focused native review | [42171039613](https://github.com/uran39281-bit/bc-1/actions/runs/42171039613), artifact `13482080607` |
| CI APK SHA256 | `ff64107f064fed5bf7048d5b80895100854ee53254b8a402e7099d0e176a11f8` |
| Signed APK | `Black-Ice-Editor-v1.5.4-Preview-build48.apk`, 66,106,129 bytes |
| Signed APK SHA256 | `929b6372c6afb79c0fff3d5471be0d804e5a7e55daf8a895ead964b601a1bbd0` |
| Retained certificate SHA256 | `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d` |

The merge tree equals the qualified source tree. Signing changed no application payload: all 57 nonsignature ZIP entries match the exact CI candidate. APK signatures v1/v2/v3, ZIP 4-byte and native-library 16-KB alignment were verified locally. The public release manifest reconstructs the signed file byte for byte and checks both complete APK hashes. No private key or passwords are committed or uploaded to CI.

## Changes

- Ordinary menu parameters use the shared ruler with exact entry, original units/ranges, reset, keyframes and undo. Position also offers the existing trackpad. Playback seeking, the timeline time ruler, rotation dial and color wheel/ramp keep their dedicated controls.
- Real easing graphs start collapsed and can expand while the inspector scrolls. Shared joined Previous/Next Key controls use strict neighboring keys; square native key buttons contain centered vector diamonds.
- Compact shared spacing, semantic icons and dark teal/mint selection states apply across the native menus. Shape & Style removes duplicate navigation; Color & Fill uses the real custom curve while preserving legacy interpolation.
- Responsive native Export, Settings and Create Project dialogs retain the existing settings, destination picker, service, project registry and cancellation flows. All five project ratios include 9:16 independently of workspace orientation.
- Up to three independently identified effects per supported video/image clip, with independent strength tracks and existing named controls. Schema 12 migrates the legacy effect once; copy, removal and undo preserve independent instances. Ordered effects are rendered in preview, ICE Lite exports and Media3 exports.
- Installed version metadata appears in editor/home chrome; it is absent from the render pipeline. The editor label is an inset-safe overlay so it does not reduce preview/timeline space.
- Effect passes specialize their opcode at shader compile time. A single effect retains a single scratch target; the second reusable target is allocated only for an actual stack.

## Executed checks

| Check | Actual result |
|---|---|
| JVM tests | 281 passed; 0 failures, 0 ignored |
| Lint | 0 errors; 118 existing warnings remain |
| Full Android suite | 112 passed; 0 failures, 0 skipped |
| Inspector preflight | 4 passed (overlapping the full suite) |
| Focused native suite | 18 passed (overlapping the full suite) |
| Independent MP4 checks | 32 passed, including both three-effect portrait exports |
| Preview redraw | 95 ms against the unchanged 1,500-ms limit |
| Reverse frame order / Restore Forward | Passed; exact original in-point 0 ms asserted |
| Independent reversed-audio direction | First 0.3 s max −91.0 dB; last 0.3 s max −0.2 dB |

The reports were read, not inferred from workflow badges. Native checks exercised phone 824×1784 at 320 dpi and wide 1920×1080 at 160 dpi, on API 35 x86_64 with SwiftShader. They cover ruler edits/cancellation/undo, square key bounds, collapsed/expanded graphs, stack cap/identity/selected-instance edits/keys/removal/undo, legacy migration and repeated reload, real 9:16 creation/save/reload, Settings reachability/preferences and preview geometry.

Independent FFmpeg decoding sampled Exposure → Invert → Grayscale at 0, 600 and 1,000 ms against CPU-computed expected pixels: maximum channel difference 2 for ICE Lite and 3 for Media3. Both outputs are 180×320 at 30 fps, with no decoder errors. ICE produced 36 frames / 1.200 s; Media3 produced 34 frames / approximately 1.148 s. Exact endpoint/frame-count parity is not claimed. Uniform fixture output has zero full-frame channel spread, consistent with no UI/version text in the exports.

## Screenshots and review

The qualified native artifacts contain actual phone/wide Position (collapsed/expanded), Blending, Effects, Color & Fill, Shape & Style, Export, Settings and Create Project captures. The delivery bundle adds current-source Rotation, Scale, trackpad and scrolled-graph captures with an exact source/run/hash mapping. These are native app images, not mockups. Some historical test project names contain “1.4.4”; installed chrome correctly shows v1.5.4.

Source-to-reference and screenshot review was performed by the implementing agent, separately from assertions and independent decoding. Screenshots captured shortly after edits demonstrate layout; they are not used as proof of completed live GPU composition. The dedicated render/export and LivePreview tests provide that evidence.

## Failure history and limits

Earlier sources were not released. A first full run found two tests still looking for Settings in the old activity root and a real small-phone preview regression caused by a 14-dp footer. Tests now use the actual modal and retain their functional assertions; the version is an overlay. A remaining preference lookup was corrected. A later run passed 111/112 tests but exceeded the preview redraw limit (1,890 ms); shader specialization and lazy target allocation corrected the actual rendering path. The original 1,500-ms assertion was not weakened. The final exact-source full run passed all 112 tests.

Physical Samsung/S24+/DeX testing and an exhaustive human two-pass review of every nested menu, display scale, mouse/keyboard state, external picker provider and all four workspace/canvas combinations have not been completed. Automated coverage is listed above rather than representing that manual matrix as complete.

Generated shape/text overlays and audio retain their existing visual-effect exclusions. Each supported clip has one to three effects, independently animated strength and existing static named controls; catalog duplicate-type policy is unchanged and no reorder UI is added. Cubic handles remain bounded without overshoot. All 118 lint warnings remain. Media3 endpoint frame-count difference is recorded above.

## Installation and upgrade gate

Once published, install this APK as **Update** over the existing Preview app. Do not uninstall or clear application data. The publication workflow must install signed 1.5.3 build 47, save its real fixture, install 1.5.4 build 48 with `-r`, and verify original media bytes/URIs, trims, transforms, keys, shapes, effect migration, legacy/custom curves, project settings and saved state. The immutable download branch is created only after `upgrade-verification.json` records PASS. That result is pending at the time this candidate record is committed.

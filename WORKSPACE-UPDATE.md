# Black Ice Editor workspace update — v1.5.0-dev

This update extends the existing Android workspace. The main menu, project browser, media references, timeline and export engines remain in the same project.

## Implemented

- Local diagnostics: bounded operation history, asynchronous session checkpoints, Java exception types/stacks, technical project context, frame timings and Android previous-process exit classifications. Reports can be viewed, copied, shared, exported and cleared. A previous checkpoint is explicitly not proof of a crash. Exception messages and native trace bodies are omitted because they can contain private media names and paths.
- Volume keyframes use source time across trims and speed changes. The preview and both export audio paths evaluate volume automation while retaining saved legacy fades. Mute remains explicit. Noise Reduction, Loudness Normalization, Audio Visualization and EQ remain disabled Coming Later entries.
- Blending & Opacity has strictly previous/next opacity-key navigation, endpoint disabling and evaluated controls that refresh after seeking. Blend mode remains static.
- Project Media uses the navy/cyan theme, icon categories, search, favorites, sorting and grid/list modes. Rectangle, ellipse, triangle and line insert actual vector timeline elements. Redundant Add content/Layers navigation is removed from that panel. Source thumbnails decode on a worker and update existing cards.
- Five numbered, colored project landmarks support add, seek, label, move, delete and undo/redo. They persist independently of clips and do not extend export duration. Existing beat cues and beat detection remain available separately.
- Ruler tick strips follow the drag underneath a fixed indicator. Conventional slider thumbs keep their normal direction.
- Shared dark app dialogs replace the gray surfaces; system permission/document pickers remain Android UI. Timeline Settings navigation is removed; essential controls and saved preferences remain.
- One shortcut registry drives the searchable guide, dispatch and customization. Rebinding checks conflicts and requires explicit reassignment. Text fields retain ordinary typing. Existing copy/paste-attributes and timeline zoom commands remain in the registry.
- A separate monotonic project work clock checkpoints foreground time, pauses on lifecycle transitions, supports manual pause/resume and confirmed reset, and uses stable project IDs. It does not enter undo history or invalidate preview caches.

## Validation

Final validation passed for source `30de9a9096aa51f14e6f931207d4d7f40c679dce` in [workflow 40068647234](https://github.com/uran39281-bit/bc-1/actions/runs/40068647234): **154 JVM tests, all 62 Android device tests, and all 16 independent export checks passed**. Android lint passed with zero errors and 64 warnings; the warnings remain outstanding.

The first completed device suite ran 62 tests: 61 passed and one older test read an asynchronous diagnostic report before it had been written. That assertion now waits on the instrumentation thread with a bounded deadline. It retains the exception-type and private-path exclusion checks. This was a test synchronization failure, not evidence of an app crash.

Coverage includes real project creation/import/rename/duplicate/delete, bundle media recovery and damaged-project handling; save/reopen and layout changes; live preview and scrubbing; opacity-key navigation; markers and undo; keyboard conflict reassignment and text-field exclusions; foreground/background clock handling; bounded diagnostic retention and the next-launch checkpoint restoration routine; and trimmed, double-speed volume automation through both export engines. The diagnostic restart check invokes the restoration routine with a saved checkpoint; it is not a physical process-kill/ANR test.

The exported bytes were independently decoded with FFmpeg and checked with ffprobe: 1080p H.264/AAC, A/V speed synchronization, ICE Lite frame counts and 48 kHz stereo AAC, 60 fps output, HEVC, staged rendering, shape-only output, four source orientations, and cumulative rotations in both directions. All 16 checks passed locally on the earlier output and again in the final CI run after all 62 device tests passed.

Screenshot review confirmed dark Project Settings and Export dialog surfaces and inner controls, the responsive Project Media categories, native shapes, five landmarks and work clock. The review found missing Video/Image/Favorites icon paths; those now use distinct line icons. Phone Project Media remains inside the existing scrollable Add drawer, so lower controls require scrolling at compact heights.

The first same-emulator comparison (100 image assets, 24 rapid search changes; Android 35 x86_64/SwiftShader) measured search-burst completion at 986 ms before and 492 ms after. The measurement includes a 200 ms settling allowance. Landscape scrolling p95 total frame duration was 121.2 ms before / 141.1 ms after; portrait settings scrolling was 150.2 / 150.3 ms. These results demonstrate faster search, not resolved scrolling stutter. They are synthetic emulator measurements, not physical Samsung DeX results.

A second same-emulator run after avoiding unchanged-label and cached-thumbnail updates measured:

| Probe | Baseline | Updated |
| --- | ---: | ---: |
| Landscape search burst | 2347 ms | 569 ms |
| Landscape scroll p95 UI work | 24.38 ms | 2.97 ms |
| Landscape scroll p95 total frame duration | 189.24 ms | 108.22 ms |
| Portrait settings scroll p95 UI work | 7.85 ms | 2.78 ms |
| Portrait settings scroll p95 total frame duration | 200.17 ms | 219.77 ms |

UI work sums input, animation, measure/layout and draw durations from Android FrameMetrics. Total frame timing includes other pipeline delays. These are individual synthetic runs; host/emulator variability is visible between runs. The reduction in UI work supports the redundant-update fixes. Portrait total frame duration did not improve, and every sampled emulator frame still exceeded 16.7 ms. No physical-device 60 fps claim is made.

Baseline: `0d55b0da87a50f7d05ddeddc7825975f0d7c6f0e`. Second comparison: `74bbbf1e735f73023f1d5bdab5ed16ec65a36dcb`, workflow [40067683943](https://github.com/uran39281-bit/bc-1/actions/runs/40067683943).

## Delivery constraints

- The user's unexplained physical-device exits have not been reproduced from a shared device report. No particular crash cause is attributed to those exits.
- Physical Samsung DeX, mouse/trackpad drivers and device-specific codecs require testing on the target hardware.
- Diagnostics preserve exception types and stacks but intentionally omit exception messages and native/ANR trace bodies; complete crash evidence is therefore not guaranteed. No real ANR, native crash or low-memory kill was induced on the user's device.
- Clock rename/duplicate isolation follows stable project IDs and new duplicate IDs; physical screen-lock/crash recovery and multi-project timing have not received a complete end-to-end hardware test.
- Project Media retains the app's bounded 100-item model and native row views; it is not an unbounded virtualized media catalog.
- Development APK signing differs from the previously delivered preview. The separate `com.blackice.bc1.icelite` test package can be installed alongside that preview. Transfer a project with the existing project-and-media bundle flow; the new package cannot read another app's private project directory. Do not remove the earlier installation to work around a signature conflict.
- The separate test package defaults to ICE Lite; Media3 remains available in Settings → Export engine. Both engines are included in export validation.

## Install and investigate on the phone

The supplied `Black-Ice-Editor-v1.5.0-test.apk` is version `1.5.0-dev-icelite`, version code 29, package `com.blackice.bc1.icelite`. Its launcher name is **BC-1 ICE Lite test**. It is a development test build, not a production-signed upgrade to the earlier preview.

APK SHA-256: `822dc51aca3310189f7be1429a742756f65d58b9396075777d009b7c51183f6d`.

Keep the earlier app installed. Use its existing project bundle export to transfer a copy of a project and its media, then import that bundle into the test app. Files referenced outside a bundle may require access to be granted again.

If this build closes unexpectedly, reopen it and use Settings → Diagnostics / Crash Logs → Export report. Record the action immediately before the exit and whether the phone was in portrait, landscape or DeX. Reports stay local until deliberately exported or shared. A checkpoint on its own does not establish that a crash happened.

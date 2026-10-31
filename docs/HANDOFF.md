# v1.4.7 (build 41) — menu update and Easing Curve Editor (source, unsigned)

Implements the Black Ice Menu Update kit. Reference components are in `com.blackice.bc1.menu`; `MenuHost` adapts them to the real model/undo/preview and `MenuPanels` mounts them (Position, Rotation, Scale via `TransformPanel.build`; Blending & Opacity and Effects via `ToolPanels.category`). Pure curve logic is `editor/CurveEdits` (JVM-tested). Legacy presets are never rewritten until the person edits that interval; custom edits write one key's x1..y2 with `easingToNext=-1`. Rulers now follow the finger. See CHANGELOG-v1.4.7.md and v1.4.7/QA.md for the exact build to sign. Not signed or published: do not add `v1.4.7/release.json` or checksums until a real retained-key signed APK exists, then the publish workflow runs the 1.4.6 → 1.4.7 upgrade gate. Signing stays in the owner's signing environment.

# Published v1.4.6 (build 40) — effects enabled

Qualified source `8e611103b940d1690971292c8ecb516c56890bfd`, CI run 40817895753 (90 Android tests passed on attempt 2; one timing-based marker-drag test failed on attempt 1 and passed on re-run). Signed with the retained Preview key (certificate `3c578586…a29d`), APK SHA-256 `b3413e7a…bf3f`. Publication run 40824053276 passed the signed 1.4.5 build 39 → 1.4.6 build 40 in-place upgrade gate and published to `test-apk/preview/v1.4.6`. See v1.4.6/QA.md and CHANGELOG-v1.4.6.md.

Effect bypass (`ActiveFeatures`) removed; the Effect Catalog applies/removes effects and the Effects panel exposes strength. Projects are written as compact JSON. One effect per clip; effect stacking, effects on text/shape/image layers and physical Samsung/DeX testing remain open. `BC-1/.github/workflows/build-android.yml` is inert and not removed. Signing stays in the owner's signing environment; never generate or publish signing material.

# Published v1.4.1-dev — Media Settings and transforms

PR #7 merged; qualified source `2ab6fa383fc975992cffe8d726a0f22d6a205253`, run39718133591 passed 150 JVM /48 Android /16 export checks. Normal Preview upgrade code28 signed with the original certificate. See qa/BC-1-v1.4.1-MEDIA-TRANSFORM.md and ../v1.4.1-dev/. Preserve schema6 heightScale/11-channel compatibility. The separate `ui/media-transform` branch was an overlapping experiment and is NOT the released implementation; do not merge its conflicting 12-channel schema. Physical Samsung testing remains pending.

# v1.4.0-dev landscape stability — automated qualification passed

Source d3ee8b4498de6a6fabcfc37f6ffe02a3fcb84ac5, run39544615852: build/lint, 139 JVM tests, 46 Android tests and 14 independent export checks passed. PR #6 merged. Retained Preview signing backup recovered privately and certificate verified; normal upgrade package com.blackice.bc1.preview/code27. No signing material published or regenerated. Preserve all prior release folders. See qa/BC-1-v1.4.0-LANDSCAPE-STABILITY.md for changes, failed-candidate history, screenshots, qualification and explicit gaps. Physical Samsung and real camera fixtures still need validation; custom audio encoder controls/arbitrary canvas/background/storage/Open Location are not implemented. Do not call this development build production-stable.

# v1.3.3 centered playhead — qualified source

Source a4f8d98ee31b7bd79e25cfdf6208cddd5c2d2a61, run39510438799: build/unit/lint passed, 44 Android tests /10 independent export checks passed. Playhead stays centered; timeline swipes seek, touch hold-drag moves layers, trim grips remain direct. Zoom/fit and rebuilds retain current time at center. Actual screenshot reviewed. See qa/BC-1-v1.3.3-CENTERED-PLAYHEAD.md for test APK and signing limitation. Normal upgrade remains blocked; retain original key and all prior releases.

# v1.3.2 scrub fix — source qualified, upgrade signing blocked

Qualified source ec6e3e9c8725bd13a8f8ddb69bf52bc75794af56, run39492323400:137 JVM /44 Android /10 independent export checks passed. TimelineView hit-tests the visible playhead below the ruler; paused rendering hides the inactive playback SurfaceView. Visible screen pixels are checked after actual gestures, including playback/pause. See qa/BC-1-v1.3.2-PLAYHEAD.md for failed first run, corrected test assumptions, separate test APK and limitations. Normal preview upgrade signing remains blocked: retained private backup located but authorized materialization denied. Never regenerate or publish signing material. Existing releases remain untouched. User's physical device/footage still needs confirmation.

# Latest checkpoint — 1.3.1 two-state workspace (2026-10-20)

Qualified source `0e90227ddf82ec927450428920935c6a98468076`, run39472715226:137 JVM/43 Android/10 independent export checks passed, lint0errors49warnings. Source branch ui/mint-footage. Version1.3.1/code24, retained preview certificate. See ../v1.3.1/QA.md for screenshots and limitations. Native presentation remains MobileChrome/ToolPanels/TimelineView. LivePreview now queues redraw after GL surface binding; save callbacks ignore destroyed Activities and mismatched projects. Preserve old releases and signing material.

Physical-device/accessibility testing and unsupported shape/effect-stack capabilities remain outstanding. Large-font Footage properties scroll. Do not claim every requested feature complete or production-stable.

---

# Latest checkpoint — 1.3.0 Mobile V1 (2026-10-19)

Qualified source f3e1e02fc4a37f5ace49ebd2ee776ef8437d7e16, based on main 119e203 (1.2.1). CI 39299364162 passed 133 JVM / 42 Android / 10 independent export checks. Signed APK uses the retained preview certificate, package com.blackice.bc1.preview, code 23. See ../v1.3.0/QA.md and qa/BC-1-v1.3.0-MOBILE-V1.md for scope, evidence and limitations. Native phone presentation is in MobileChrome; shared vector geometry is ShapePaths. Do not replace or regenerate signing material. Preserve old release folders.

Remaining stage: freehand/node editing, templates, automatic captions, arbitrary canvas ratios, audio audition/multi-selection staging, close animation and physical-device/accessibility checks. Existing engine track/overlay limits remain.

---

# Published v1.2.0 — October 19, 2026 UTC

Signed qualified CI artifact12158081821 from run39186264263 / source20a59518a44587bb0cd2223ec96a72e80ecb3c58. Main886b6f1837c5201f45975fe18a92df4f268bc5ac has identical tree892657c9f2572fb1e76ce97f582fcf4f1e9e48e8. 132 JVM /39 Android tests, nine export checks passed; lint44warnings. Package com.blackice.bc1.preview, version1.2.0/code21; retained certificate and APK payload verified. See v1.2.0/QA.md. No signing material published or regenerated; older releases retained. Source unchanged during publication. Preview remains Media3; no ICE Lite paused live preview. Physical-device and 4K/120fps qualification remain outstanding.

---

# Published v1.1.2 — October 19, 2026 UTC

Qualified source c986f55cd257267fc2ec0af6ac363fc0c0232cc4, run39178553717: 88 JVM / 28 Android tests, five independent export checks, lint0errors44warnings. Fixes stale diagnostics version assertion; adds bounded recovery for silent seek stalls (including final seek). Upstream main51999d4 had failed fast-scrub and diagnostics tests. This qualification reached READY at2029ms without triggering recovery, so do not claim the Media3 buffering root cause fixed or the recovery path fully exercised. Existing export watchdog intermittency remains unresolved. Phone/desktop screenshots reviewed. Package unchanged, version1.1.2/code20; retained certificate and signed payload verified. See v1.1.2/QA.md. Never publish signing material. Older releases preserved.

---

# V1.1.1 phone media space — October 18, 2026 UTC

Based on main50cd4b8, preserving the newer media bin/free-placement implementation. Source `2db3fd0f2eabe1bdb0ffbc1ca5baa6c1591b0ee2`, workflow39118809055. See `v1.1.1/QA.md` for final qualification. Phone Add reserves a large independently scrollable browser, compact preview and drop target; Done/Back restores editing and focus preferences. No project schema or engine changes. Dynamic version labels and single-line narrow-phone branding were corrected during qualification. Source branch `bc-1-phone-media-space`; old releases remain intact. Continue to investigate the historical intermittent export watchdog issue; no production-stable claim.

---

# V1.1 cinematic redesign preview — October 18, 2026 UTC

Continue editor work in `uran39281-bit/bc-1`. Qualified source `6ff1b306876a3af2ec06d1bccf0504e43b57223c`, Actions39105942908: 78 unit / 25 Android tests; five independent export checks; lint0errors44warnings. Final phone1920-tall, desktop1920×1080 and compact780×420 screenshots reviewed. Native shared theme and responsive workspace, no project-schema/package changes. Version1.1.0/code18; retained signing certificate verified. Never publish signing material. Source branch `bc-1-v1.1-cinematic` and older branches/releases retained.

Read `v1.1.0/QA.md`, `docs/CHANGELOG-v1.1.md` and `docs/qa/BC-1-v1.1-REDESIGN.md`. This is a redesign preview, not full specification completion. Next work: separate asset catalog/desktop media drag-drop, phone drawer, reference parity, reverse/effect tracks/draw only with real engine support, dedicated new-control interaction tests and physical-device validation. Intermittent Media3 watchdog export stall remains unexplained; passing qualification does not fix it. Do not weaken timeouts/gates or claim stable.

---

# Published v1.0.5 — October 18, 2026 UTC

Qualified source 7c68ae9, Actions 39098242781: 78 unit / 25 Android tests and five export checks passed, lint 0 errors / 41 warnings. See v1.0.5/QA.md. Retained signing key recovered from the existing private BC-1-v0.3-Preview-Signing-Backup.zip; local private path /root/.android/bc1-preview.keystore. Never commit or publish the key. Certificate matches v1.0.4. Signed APK v1.0.5/BC-1-v1.0.5.apk, code17. Prior notes about unavailable signing material are historical and resolved. Continue editor development in bc-1 only.

---

# Editor reliability follow-up — 2026-10-18

Continue in `uran39281-bit/bc-1`, branch `bc-1-editor-reliability`, based on the newer v1.0.5 branch `62ef9fc`. Do not put editor changes back into Black-Ice-Emulator. Version remains 1.0.5 candidate; no replacement APK published.

Fixed float-to-int rounding in exact duration, handle source deltas and split offsets. Operations now use double-to-long rounding, preserving millisecond values beyond 2^24 and avoiding saturation at Integer.MAX_VALUE. Two JVM regressions cover long image durations/splits and video edge extensions. A direct Java model harness failed against the original implementation (16,777,217 became 16,777,216), and passes with the changes. Full Android CI remains required.

Rejected editor commands restore project objects; refresh the inspector after rollback so callbacks reference the restored clips. No project schema or saved-file changes. Retained preview signing key is absent in this environment; do not publish a differently signed APK as an in-place update.

---

# Active development — BC-1 v1.0.5 (candidate, not published; formerly labelled v2.0.0)

Branch `claude/app-update-4vo05i`: v1.0.5 / versionCode 17 (renumbered from 2.0.0 at the owner's request, no functional change), folding in the unpublished v1.1.0 timeline work (v1.1 branch `bc-1-v1.1-timeline` preserved). Selection-based workspace, + Add drawer, contextual tools with focused panels, layered timeline with centred playhead, layer visibility / solo / locks, arrangement tools, Move & Transform, direct preview editing, keyframe animation and reusable presets/elements. Read BC-1-v1.0.5-QA.md.

CI: dispatch `bc-1-android.yml` on the branch (it is not in the push list). Reduced QA screenshots are pushed to `qa-screenshots/<branch>` by the GPU job; failure summaries are repeated at the end of the job log. Local checks used during development: JVM unit tests against real org.json, and a javac pass against Robolectric android-all filtering Media3-only errors.

IMPORTANT: the retained preview signing key was not available, so v1.0.5 was not published to the APK repo; CI APKs use an ephemeral debug key and cannot update an existing Preview install. Fixed in this work: a Media3 forced-audio race with several visual sequences (NullPointerException in SequenceAssetLoader) — non-primary layers' sound now uses separate audio sequences. The older intermittent "Muxer error" remains unexplained (seen once more on 2026-10-17 before these changes).

---

# BC-1 overnight development checkpoint

## Latest verified development build: v1.0.4

Continue `bc-1-v1.0.4-diagnostics`; preserve all older branches. Tested source `ae912eec2ca3a9d4a63212ae8646646bd30c24c5`, Actions `38888011311`: build and GPU jobs both passed.36 unit tests (0 failures/0 skipped),20 Android tests, lint0errors30warnings. FOUR independent ffmpeg decode/ffprobe gates passed: three sequential H.2641920×1080/AAC exports and one valid export after a deliberately missing-media failure. Expected missing-file error is logged; failure cleanup, service shutdown, retained redacted report and successful recovery all passed. Final diagnostic-dialog, paused-first-frame, phone9:16, wide16:9 and compact-wide screenshots reviewed against the V1 direction.

New: Setup → Export diagnostics → Copy report. Copy is user-triggered; no automatic sending. Bounded report contains stage, Media3 code and cause class names, excluding media paths/names and exception messages. Last failure stays available after successful exports. Full exception chains are debug-only logs. Layout, GLES graph, transition policy, portable project schema and no-Page-Curl rule are unchanged.

APK: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.4/BC-1-v1.0.4.apk
QA: https://github.com/uran39281-bit/bc-1/blob/main/v1.0.4/QA.md
SHA256: `7e80ed1718bf84c2bf68749681c11a13c54ba9d5dbe558467316dfea1e732ffe`.
Package `com.blackice.bc1.preview`, versionName1.0.4/code16; retained certificate SHA256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d` verified.
APK repo commit `a149cde3a9957f1b54c32f8861054e783c1b603e`; raw download compared byte-for-byte. Local `/workspace/scratch/9d8e062dc089/deliverables/BC-1-v1.0.4.apk`; saved identity `libfile_b86329d0f7588191a7ff933406ce87b1`.
Artifacts: APK12039928235, build12040312524, GPU12040997551. Local QA `/workspace/scratch/9d8e062dc089/qa/v104-final/`.

IMPORTANT: v1.0.3 attempt1's intermittent muxer error was not reproduced by the unchanged rerun, three-export investigation or v1.0.4 qualification. Its initiating cause remains unknown, not claimed fixed. This remains a development-signed debug build, NOT production-stable certification. Physical Snapdragon8Gen3/SamsungDeX validation, thermal/battery/FPS measurements and RTX backend remain outstanding. Do not hide those limits in delivery.

## Completed investigation evidence

`bc-1-export-reliability` source664e2f2, Actions38881066895 succeeded:18 Android tests and all THREE independent H.2641920×1080/AAC/duration checks passed, build succeeded. Artifacts GPU12037617816/build12037586895. This did not reproduce or establish the cause of the earlier intermittent muxer failure. Preserve that caveat. Investigation artifact remains DO-NOT-PUBLISH (old manifest), and all older APKs remain intact.

## Preserved v1.0.3 thumbnail reliability

Continue `bc-1-v1.0.3-thumbnails` unless a newer active follow-up is recorded above. Tested source `261c82dea4bc2628953c62d2a0b262f37945764b`, Actions `38874891062` attempt2: 33 unit +18 Android tests, lint0errors30warnings; independent MP4 decode/format gate passed. Final paused-first-frame, phone9:16, wide16:9, compact-wide and thumbnail-stress screenshots reviewed. Baseline run38874632718 reproduced the stale thumbnail; current-view observer fix passes in both candidate attempts.

IMPORTANT: candidate attempt1 failed one export with `Muxer error`; unchanged rerun passed. This does not prove the initiating cause fixed. Keep this intermittent reliability caveat in QA; do not call the build production-stable. A useful next step is debug ExportException cause logging and repeat-export tests without skipping or weakening any gate.

APK: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.3/BC-1-v1.0.3.apk
QA: https://github.com/uran39281-bit/bc-1/blob/main/v1.0.3/QA.md
SHA256: `11b5a3cb9119492afc75c78dd4953a36cfcf8d2da13352376be2810b0876d653`.
Package `com.blackice.bc1.preview`, code15/name1.0.3; retained signing certificate matches. Published APK repo commit `df158f449c8ae7318801aa92fe81f0abdb2b5e91`; raw downloaded bytes compared identical. Saved identity `libfile_459142f5a7188191a1cbaddb8f965758`. Local APK `/workspace/scratch/9d8e062dc089/deliverables/BC-1-v1.0.3.apk`. Artifacts: APK12035420232, build12035250628, failing GPU12035510441, passing GPU12036120835.

## Preserved v1.0.2 V1 media browser

Continue `bc-1-v1.0.2-media`. Tested source `5e551384278f03270258ff7149c4f36a3fe0d1ce`, Actions run `38871745189`: both jobs succeeded. 33 unit +17 Android tests passed, lint 0 errors /30 warnings. Independent ffmpeg decode and ffprobe H.264 1920×1080/AAC/duration gate passed. Final phone, wide, filtered-wide and compact-wide screenshots visually reviewed.

New: responsive two-column Project Media grid, All/Video/Audio/Image filters and filename search. Search/filter state survives recreation. Filtered selection targets the original clip, and browser highlights follow inspector/timeline selection. Existing phone/compact-wide layouts, preview surface, effects and project schema are preserved.

APK: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.2/BC-1-v1.0.2.apk
QA: https://github.com/uran39281-bit/bc-1/blob/main/v1.0.2/QA.md
SHA256: `de63efff3bd6442e26419ae1693c0196d79a98219a1b1edfdf014c96bce3fa34`
Package `com.blackice.bc1.preview`, versionName `1.0.2`, versionCode `14`, GLES3 required.
Retained signing certificate verified; see below. Published APK repo commit `13de548b6c1a93555feb91cf8c894664376bebee`. Raw download byte-compared successfully.
Artifacts: APK12032814882, build12033765250, GPU12033875888.
Local APK: /workspace/scratch/9d8e062dc089/deliverables/BC-1-v1.0.2.apk
Saved identity: `libfile_079c61f0980c8191ae6f51cf52ab3025`.

## Preserved v1.0.1 V1 interface polish

Historical branch `bc-1-v1.0.1-ui`. Tested code commit `27e09676cd88f875353dd5e86409dafd2d6f4d1c`; Actions run `38869704876`. This continues v0.2–v1.0; never restart or force-push older branches.

Both jobs passed: 30 unit tests +17 Android tests, lint 0 errors /28 warnings. Independent ffmpeg decode and ffprobe H.264 1920×1080/AAC/duration check passed. Final paused-first-frame, phone9:16, wide16:9 and compact-wide screenshots visually reviewed; zoom/scroll pinned labels and corrected shortcut label confirmed. Artifact IDs: APK12032329690, build12032284915, GPU12032987524.

APK: https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0.1/BC-1-v1.0.1.apk
QA: https://github.com/uran39281-bit/bc-1/blob/main/v1.0.1/QA.md
SHA256: `5b01c353f54f60f89b2345f5d6b51245581abff427d043fae840ea800442b871`
Manifest: `com.blackice.bc1.preview`, versionName `1.0.1`, versionCode `13`, GLES3 required.
Signing certificate SHA256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`.
Local signed APK: /workspace/scratch/31791101a882/deliverables/BC-1-v1.0.1.apk.
Published in APK repo commit `5dc96c3c5e8d7ad93bda443986e001e12aaf1ec6`; raw download was byte-compared to the signed file. Persistently saved identity: `libfile_a99729c13ae88191a027ed9c611c18b1`.

## Official V1 references recovered

The actual user-selected reference images are now committed:
- `BC-1/design/v1/phone.png` (original 1000009084.png, 864×1536).
- `BC-1/design/v1/desktop.png` (original 1000009085.png, 1536×864).
- `BC-1/design/V1-REFERENCE.md` records direction and remaining differences.

Earlier v1.0 notes saying references were unavailable describe that older qualification only. Preserve the mountain mark, dark navy/black, icy cyan, monoline controls, large preview, thumbnail timeline, pinned track labels, phone bottom navigation and wide media/inspector panels. User-selectable Auto/Phone/Wide and UI Lock remain. No fake controls/taskbars; no 3D Page Curl.

v1.0.1 adds monoline icons, nine functional editing shortcuts, preview scrubber, pinned timeline gutter, larger timeline where space permits and desktop Undo/Redo/Save. Tests now zoom/scroll the phone timeline and guard shortcuts with UI Lock. Visual review caught/fixed a wrapped Chroma Key label and hardware-accelerated gutter scrolling. No project schema or effects changes.

## Next useful work

User authorized ongoing improvements until 2026-10-16 04:30 America/Phoenix (11:30 UTC); suppress notifications until then. Deliver the latest VERIFIED direct GitHub APK link at that time, with concise test results and physical-device limits. ChatGPT file links previously failed with a cross-site URL error.

Delivery: use the verified v1.0.4 direct APK link above, concise test totals and physical-device limitation. The04:30 delivery task is already enabled; do not create duplicates. Preserve this tested build rather than making a version-only change. Further work should start a new branch/versionCode>16: physical Snapdragon/DeX QA, capture actual recurring export failure reports, dedicated V1 project-flow screen and category-specific inspector. Read fresh branch heads before guarded updates; keep bc-1-v1.0's pointer current.

## Architecture and limits

Portable EditProject/Motion models and GLES3 effects form the boundary for future RTX integration. No RTX backend exists. No physical Snapdragon 8 Gen 3, Samsung DeX, thermal or battery measurements have been made. API35/SwiftShader emulator tests are not hardware performance measurements. Published APKs are development-signed debug builds; do not call them production-stable.

Image/multilayer timelines use PreviewProxy: cancellable cached H.264/AAC rendered through the SAME GLES graph as export at selected preview height. ExoPlayer displays paused frames with reliable seeks/resizing. Single-video projects retain direct CompositionPlayer. Preparation costs time/storage on long timelines. Cache key ignores playhead; edits invalidate it. Bump preview cache revision if renderer output changes for an unchanged project.

## Verification and build workflow

Use `.github/workflows/bc-1-android.yml` and `.github/scripts/bc1-device-checks.sh`: build/lint/36 unit tests,20 Android tests in v1.0.4, screenshots, independent ffmpeg decode + ffprobe H.264 1920×1080/AAC/duration check. Build and GPU jobs both must pass. Review actual paused first-frame, phone, wide and compact-wide screenshots, not just test totals.

Local SDK/Gradle distributions unavailable. Do not retry earlier auto-review-denied Gradle download; use GitHub CI. Cached Java dependencies can compile UI/model code but do not replace Android tests.

Retained private preview key: /root/.android/debug.keystore. NEVER print/upload/commit it. Sign with existing /tmp/SignBcApk.java/class and cached apksig 8.10.1; verify certificate above. If key unavailable, report the signing limitation and preserve old APKs.

Local QA: /workspace/scratch/9d8e062dc089/qa/v102-final/.
Separate APK repository: uran39281-bit/bc-1, main. Publish versioned APK, checksum and QA; verify raw downloaded bytes. Save the user-facing APK persistently when available; Git-backed source needs no duplicate file storage.

## Preserved history

- v1.0 source `1c60a8b3b1f963300d5b0f344ae95762133ac4e5`, run `38866426755`: 30 unit +17 Android tests passed, independent MP4 decode passed, lint0errors27warnings. Initial paused preview and proxy cancellation/cache checks fixed. Download https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v1.0/BC-1-v1.0.apk ; SHA256 `50ee96cd381abc3ce98e6cae27a822829327b814886233a24b533d530f0c2823`.
- v0.5 source `559cc1202d551cd0fb389e1037716d104c588cef`, run `38863083635`:29 unit +16 Android tests. Keyframes/easing, layers, chroma, audio fades/waveforms, project bundles and DeX controls. Download https://raw.githubusercontent.com/uran39281-bit/bc-1/main/v0.5/BC-1-v0.5-Preview.apk ; SHA256 `4420b3b81efa5137de90af81bb70237b0cfab55170fce8b98cfb696b2c72729d`.
- v0.4 APK and v0.2–v0.5 source branches preserved.
- v1.0 RC1 passed tests but black initial preview failed visual review; not published. RC2 fixed pixels but one fixture failed; final v1.0 corrected it and passed.

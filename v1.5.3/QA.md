# Black Ice Editor 1.5.3 Preview — build 47

> **Status: qualified, retained-key signed and prepublication upgrade verified; official publication pending.**

PR #26 was merged as `52cc963290147e389ff9cda6dac583ae4bf0a4a7`; its complete tree matches qualified source `87358e5eb71000ba63e3e2eeacbcaaeb0796d7ef`. The real release inputs were verified separately and are being added after that source merge.

## Fix
The new portrait workspace previously inherited the global Expand timeline preference, shrinking its preview to 60% of the reference height. Portrait now has an independent `portrait-workspace-focus` setting, defaulting to Balanced when absent. The saved wide/landscape preference remains unchanged. Explicit later portrait choices persist across reopening.

Native Base, Add Media and Selected Layer presentations use the supplied phone references. The preview/transport retain their geometry while opening drawers; six real timeline lanes occupy the balanced viewport and extra tracks scroll. Media lists use existing asynchronous thumbnails and native saved scroll state. Existing import, editing, undo, playback, project storage and export commands remain connected. Interface orientation and canvas ratio remain independent.

The release also includes the 33 per-effect parameter controls already present in main before this portrait patch. This PR changes no renderer, project-schema/storage or export implementation relative to pre-update main `537fefe076c43a23ee02f8224b2b5c16e55bb043`.

## Exact qualification
| Item | Value |
|---|---|
| App source / PR head | `87358e5eb71000ba63e3e2eeacbcaaeb0796d7ef` |
| Full CI | [41867013484](https://github.com/uran39281-bit/bc-1/actions/runs/41867013484), attempt 1, success |
| Candidate artifact expiry | `2026-12-04T16:28:15Z` |
| Candidate artifact | `13342143626`, Black-Ice-Editor-v1.5.3-ci-candidate |
| Candidate ZIP SHA-256 | `284934ae7590c394941abb4f265cdfe7d14a9cdddd25725b47150a96f9af2bb0` |
| CI APK SHA-256 | `bb1839b835e75fff4199e91a56bf214af9d02bccbca0473ecedb2dd8a760188a` |
| Passing GPU reports | `13345671052`; ZIP SHA-256 `45b1a37520312297f0d037ac44dfd4b0d6230cb14bcd590a18206168a7f42073` |
| Build reports | `13342203646` |
| Unit tests | 275 passed; zero failures/ignored |
| Lint | Zero errors; 114 warnings |
| Android tests | 109 passed; zero failures/ignored |
| Inspector preflight | 4 passed |
| Independent exports | 30 decoded and checked |
| Focused native run | [41867013307](https://github.com/uran39281-bit/bc-1/actions/runs/41867013307), attempt 1, 16/16 passed; artifact `13342483908` |

Both the reverse frame-order/Restore Forward test (exact original in-point 0 ms) and independent reversed-audio direction check passed. Reversed audio measured -91.0 dB at the head and -0.2 dB at the tail.

## Native visual checks
Captures cover 360/412 dp, 16:9 and 9:16 project canvases, 1.5 text scale, short 360×480 dp windows, all three phone states and a 412×892 dp window starting with legacy Expand timeline saved. The real space dialog expands preview/timeline, restores Balanced exactly, and preserves the independent wide setting. Recreation, library scrolling, insertion, duplication/undo, markers and transform keys passed.

Fresh baseline [41867670045](https://github.com/uran39281-bit/bc-1/actions/runs/41867670045), artifact `13342573676`, ran the unchanged pre-portrait production source plus capture/CI-only files (baseline commit `583c1e076fb97c8b39249200b1ef8358e80fc0e1`). All eight current focused wide images and visible view trees match exactly. The legacy-preference portrait preview and transport are pixel-identical while Add Media opens. An earlier full-suite capture had different available system-bar insets; fresh captures under the same setup were used for the exact comparison.

## Signed APK integrity
Package: `com.blackice.bc1.preview`; version `1.5.3`; versionCode `47`. Published 1.5.1 uses build 45; 1.5.2/build 46 is not used for this official version sequence.

APK: `Black-Ice-Editor-v1.5.3-Preview-build47.apk`

SHA-256: `ca20a3b02afb04970e763d36ae3a4586230f13f888cb7e8d16ee9df71acf7d59`

Retained Preview certificate SHA-256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`

The exact qualified CI candidate was signed with the retained key. APK verification, ZIP/16 KiB native-library alignment, all 57 identical nonsignature payloads, binary package/version checks and byte-for-byte public reconstruction passed. V2/V3 verify for the supported SDK range; the existing JAR signature was also verified separately with the SDK-23 verifier. No private key or password is included in these files or CI.

## Upgrade gate
[Signed upgrade run 41873840129](https://github.com/uran39281-bit/bc-1/actions/runs/41873840129), attempt 1, passed. Artifact `13346825345` (ZIP SHA-256 `a81f240a63126f045077f5ca8d516dbc6ad5b6c617519d2b392ad0feab5225de`) contains the exact tested APK, byte-identical to this delivery, and its verification record/captures. The read-only workflow ran on metadata commit `434ff407b70bb2d74cabcb8f8330256dedd72470`; application source remains the fully qualified `87358e5eb71000ba63e3e2eeacbcaaeb0796d7ef`.

Published retained-key Preview 1.5.1 (45) was installed and saved a real fixture. This exact 1.5.3 (47) APK was installed over it using `adb install -r`, with no uninstall or application-data clear. Source URI and image bytes, trims, opacity, position, rotation, scale, motion keys, custom cubic and legacy easing interval representations, shape/gradient keys, canvas, frame rate, saved effect and paused work clock (123456 ms) survived. The saved wide/landscape Expand timeline choice remained intact while the Samsung-shaped portrait opened at full Balanced height; Add Media showed all six filters and Back retained the graph/media. That earlier read-only verification did not publish the APK. The official publishing workflow repeats the same signed upgrade gate before creating the version download branch.

## Test history
Earlier PR source timed out installing media tools before emulator tests; the install timeout was increased to ten minutes. The first portrait-fix source failed Android-test compilation because its new test referenced an absent UI testing library; the current native AlertDialog test needs no added library. Source `0d713e5` ran 109 Android tests with one failure: the old PhoneMediaSpaceTest still wrote the global field directly. Its preview-expansion, timeline-expansion and Balanced-restoration assertions are retained and now operate the actual portrait dialog. An earlier focused attempt failed downloading the emulator before running tests; its unchanged-source retry was superseded. Final source `87358e5eb71000ba63e3e2eeacbcaaeb0796d7ef` passed full and focused qualification on attempt 1. No failed/superseded candidate is delivered.

## Remaining limits
Physical Samsung and DeX hardware remain untested. Existing one-effect-per-clip, unsupported effects on generated overlays, bounded cubic handles/no overshoot and engine limits remain. The APK is a Preview build; PR #26 is merged with an identical qualified tree. The immutable `test-apk/preview/v1.5.3` download is created only after the official workflow passes the signed upgrade gate. Its status and canonical APK/checksums are available through [the publish workflow](https://github.com/uran39281-bit/bc-1/actions/workflows/bc1-v153-publish.yml).

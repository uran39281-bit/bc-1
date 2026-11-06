# Black Ice 1.5.5 phone and diagnostics integration — qualified source

Qualified application/test/workflow source: `73fc56a9d4d73e44b94d10f253aa301f747bdac6`.
[CI run 42264463542](https://github.com/uran39281-bit/bc-1/actions/runs/42264463542) passed both build and gpu-tests. Subsequent qualification-document changes do not alter application, test or workflow source.

Base main: `e698909fdf2307c2b2477007d20c7f4b8f93fe8c` (1.5.4).
Claude PR #30: `93a31540557100043f37d0a1862fcd475185e075`, included as ancestry; Claude's branch remains unchanged.
Integration: [PR #31](https://github.com/uran39281-bit/bc-1/pull/31). Neither PR is merged by this work. Version remains 1.5.5 / build 49. Signing configuration and the published 1.5.4 / build 48 upgrade base are retained. No official signing, publication, release manifest or signed upgrade verification is claimed.

## Changes

Phone workspace, independently of project canvas ratio:

- Outlined Back and a Settings gear between Save and Export.
- Compact selected-layer drawer: standard height about one third of the usable window; continuous resize below the preview/transport/ruler; cancellation and recreation preserve UI state. More footage tools stays at the bottom when space permits. Body scrolling is independent.
- Remove replaces Duration in the quick row and reuses the existing deletion/undo command. Source media and duration editing elsewhere are preserved.
- Ruler ticks follow the drag. Incremental clamping consumes overscroll so reversing at a bound responds immediately. Wide ruler mappings, including behavior at bounds, remain unchanged.
- Phone inspector density is scoped to the phone workspace; keyframe squares retain their sizing.

Both workspaces:

- Buffered local JSONL action, movement, lifecycle and processing events with UTC/elapsed timestamps, session/operation/input IDs, numeric changes, effect controls and keyframes. Pointer history, hover and wheel input are retained; separate dialog and popup windows use the same recorder. Typed text, media content/paths and clipboard text are excluded. Raw input action codes are separate from semantic event labels.
- Copy All takes a serialized snapshot of retained events plus crash reports without hidden truncation. Viewer truncation is labeled. Above 400 KB or on clipboard failure, the UI directs the user to full text export.
- Foreground per-project work time is read-only. Existing totals survive migration; the former manual-pause override is removed. A serial worker commits checkpoints before reporting them confirmed. In-process handoffs preserve the latest total, while restart recovery uses confirmed checkpoints. Clock storage is independent of undo and log retention.
- Bounded capture queues reserve semantic-event capacity; queue/storage gaps and retention are explicit. Persistence runs off input/render threads.

Notable source: ActionTrace, TraceDialogBuilder, TraceInputLayout, ProjectClock, WorkClockLedger, ProjectSession, PortraitChrome, PhoneWorkspaceState, ToolPanels, RulerView/RulerGesture, NumberRuler, MediaImporter, ExportService and LivePreview. Remaining dialog-builder substitutions provide shared input coverage. See the PR Files changed tab for the exact diff.

## Verification

| Check | Result |
|---|---|
| JVM tests | 289 passed; also compiled/run locally with real org.json |
| Android lint, application APK and test APK | Passed |
| Focused Android regressions | 12 passed |
| Full Android emulator suite | 117 passed, no failures |
| Independent FFmpeg/ffprobe export gates | Full existing device-checks script passed |
| Effects | Existing render tests, including the 18 new effects/controls and layer stacks, passed |
| Remove/Undo, drawer recreation, clock migration | Passed |
| Historical pointer samples and wheel axes | Passed |
| Bounded trace pressure and explicit gaps | Passed |

The earlier failures are resolved: tests were migrated from the former Duration/rightward-increase/manual-pause contracts; the preview-size comparison now waits for layout; initial null tool context is normalized; raw input action codes no longer overwrite pointer/keyboard event labels. No failed check is counted as a pass.

## Native evidence

Evidence revision: `dc285051f856a5b1a2da74a6a77b1fe08e5bd868`, published by the qualified CI run.

- [Phone standard drawer](https://raw.githubusercontent.com/uran39281-bit/bc-1/dc285051f856a5b1a2da74a6a77b1fe08e5bd868/v155-phone-standard.png)
- [Phone expanded drawer](https://raw.githubusercontent.com/uran39281-bit/bc-1/dc285051f856a5b1a2da74a6a77b1fe08e5bd868/v155-phone-expanded.png)
- [Retained structured log sample](https://github.com/uran39281-bit/bc-1/blob/dc285051f856a5b1a2da74a6a77b1fe08e5bd868/v155-action-log.txt)

All eight native wide-workspace captures match Claude's baseline outside a 5-by-6 pixel rectangle containing the work-clock seconds. Each has 23 changed pixels at x=415..419, y=5..10 in the 540-by-288 published capture. Header/menu geometry, tool cards and ruler presentation are otherwise pixel-identical.

The saved trace contains 6,833 actual records, including 274 measured render submissions: median 90 ms and maximum 4,620 ms on the software-rendered emulator, including cold setup. These are render/submission measurements, not physical display latency or Samsung performance. TextureView updates are labeled as observations of the latest submitted buffer; updates may coalesce and unavailable display correlation is explicit.

## Remaining limits

Physical Samsung/DeX use, thermal/battery/FPS qualification, reboot/crash stress, storage-failure injection and clipboard-service failure injection were not performed. Snapshot ordering, retained content, pointer history/wheel input and queue pressure were tested. The interface provides full-log export for clipboard limits, but no physical-device clipboard-limit result is claimed. ASD-STE100 guides wording; formal language certification is not claimed.

This remains a qualified development source candidate. An official APK requires the retained signing key and the existing signed publication/upgrade process.

# 1.5.5 phone and diagnostics integration

Base main: `e698909fdf2307c2b2477007d20c7f4b8f93fe8c`.
Claude PR #30 source: `93a31540557100043f37d0a1862fcd475185e075`.
The integration descends from that source and leaves Claude's branch unchanged.
Claude's source build/GPU/baseline checks passed in runs 42241884937 and 42241885119.
Those results do not qualify this follow-up.

The follow-up implements the supplied Black-Ice-1.5.5-Phone-and-Diagnostics-Update.zip:

- Phone workspace: Remove replaces Duration in the first quick row; the original delete/undo command preserves imported media. Header Back is outlined and a Settings gear is between Save and Export.
- Phone drawer: drag the handle upward/downward, bounded below preview/transport/timeline ruler. Cancel restores the previous height. Height is UI state retained across panels/recreation; it never enters project or undo data. Tap the handle or Back retains dismissal. The body scrolls independently.
- Phone rulers: direct tick-strip motion, using incremental clamping so reversing at a bound responds immediately. Wide RulerView direction is unchanged. Existing NumberRuler direction is retained; it also consumes overscroll.
- Phone inspector control density is scoped to the workspace, independently of canvas ratio. Keyframe squares retain their sizing.
- Both workspaces: read-only foreground per-project clock, migration keeps accumulated milliseconds and discards the former manual-pause override. Ledger remains in project-work-clocks preferences, independent of edits/log retention. A serial worker commits ten-second/lifecycle checkpoints before reporting them confirmed; in-process handoffs keep the latest total. Restarts recover confirmed time; unconfirmed crash intervals are explicitly unavailable.
- Both workspaces: buffered JSONL events, UTC and elapsed time, session and operation IDs, numeric before/after values including stacked effect controls and keyframes; command, selection, input/window/lifecycle, import/save/export and preview processing boundaries. Raw app pointer samples include historical points, hover and wheel axes. Typed text, key characters, media paths/content and clipboard text are excluded. Dialog windows use the same input hook.
- Copy All takes a serialized snapshot of every retained action segment plus crash reports, with no hidden truncation. Viewer truncation is labeled. Over 400 KB, copying explains the limit and directs the user to the existing full text export.
- Capture buffers are bounded, with reserved space for semantic events; queue/storage gaps and retention are explicit. Persistence is off the input/render thread.

Local checks: complete application, JVM-test and Android-test Java compilation against Android API 35 / Media3 1.11.1 classes passed (generated resource/OptIn declarations supplied for compilation only). All 289 JVM tests passed with real org.json, including six new ruler, trace privacy/effect-keyframe, and superseded-save queue checks. Initial integration CI 42254107964 stopped at old tests that set the timer fields directly; these tests now assert the read-only/migration contract. The first custom test launch used the repository root and five shader-path lookups failed; the corrected app working directory passes all tests.

Required Android build/lint, full emulator/GPU/export suite, phone screenshots, timer/recreation and reserved-buffer/gap tests, and actual measured latency: pending the integration branch CI. Do not merge or claim these pass before the checks finish. Physical Samsung/DeX and performance qualification remain untested.

TextureView surface-update events are labeled as observations of the latest submitted buffer; updates may coalesce. A GPU buffer submission is not claimed to prove an exact physical display timestamp. Pending frame requests are reported as superseded; missing surfaces are explicit. No renderer/shader/export algorithm, project schema, effect definitions or signing configuration is changed by this follow-up.

This branch is source-only until qualified. No official signed APK, release manifest or upgrade qualification is claimed. Retain the existing signing/publication process.

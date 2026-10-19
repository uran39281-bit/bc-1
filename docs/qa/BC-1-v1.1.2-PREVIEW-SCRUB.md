# V1.1.2 preview scrubbing fix — source qualification

Source: branch `fix/preview-scrub`, merged into main. Version 1.1.2, code 20. No project-schema, export-graph or package-identity changes.

## CI evidence (GitHub Actions run 39171928481, commit ebe62ce)

- Attempt 2: build job (unit tests, lint, APK) passed. Device job: **28 / 28 Android tests passed**, including `PreviewScrubTest.fastScrubbingKeepsTheLivePreviewWorking` (~400 seeks; unchanged assertion and timeouts), with no preview player errors. Immediately after the tests, `adb` exited with code 255 and the emulator aborted (`libc++abi: terminating`), so the logcat pull and the independent ffmpeg/ffprobe export checks did **not** run in this attempt.
- Attempt 1: `PreviewScrubTest` passed with no preview errors; `VideoEditorTest.missingMediaRecordsRedactedCauseAndNextExportRecovers` hit the known intermittent Media3 export watchdog stall ("no output sample written in the last 25000 milliseconds"), recorded since v1.1.0 as unresolved. The export graph is unchanged by this fix, and the same test passed on the branch's three previous runs.
- Earlier runs on this branch (c8c7bdd, 00ae340, b38ed29) are the diagnostic history that identified the two Media3 causes listed in the changelog.

## Outstanding at the original handoff (resolved below)

- One run with the export decode checks completing (they were skipped by the adb abort above).
- Signing with the retained BC-1 Preview key (certificate SHA-256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`), the `v1.1.2/` release folder (APK, SHA256SUMS, QA, screenshots) and the README download link — handled in the original signing chat. No key was generated or published here.
- Physical-device scrubbing check (Snapdragon / DeX) is not measured by the emulator.

## Follow-up qualification (Codex)

Main run 39175800648 at 51999d4 reproduced a silent buffering stall in PreviewScrubTest (state 2, position/playhead 2029 ms, no player error, zero recoveries), plus a diagnostics assertion that still expected v1.1.1. This was not a fully qualified release.

Candidate c986f55cd257267fc2ec0af6ac363fc0c0232cc4 adds a watchdog to every seek, including the final seek when no further touch events arrive. A seek still stalled after three seconds rebuilds the preview at the latest requested timeline position instead of sending another seek into the stalled player. Error and stall recovery share the existing limit of three rebuilds per 30 seconds. Releasing the preview cancels the watchdog. The diagnostic copy test checks the installed package version rather than a stale literal. The original fast-scrub test assertions and timeouts are unchanged; export graph and saved-project schema are unchanged.

The watchdog is recovery for the observed silent stall; it does not establish or fix the underlying Media3 buffering deadlock. Physical Snapdragon/DeX performance remains unmeasured.

Qualification run: https://github.com/uran39281-bit/bc-1/actions/runs/39178553717
Build: 88 JVM tests, zero failures/skips; lint zero errors / 44 warnings. All 28 Android tests and five independent export checks passed. Both jobs succeeded. Final target 2029 ms reached READY without invoking watchdog recovery in this run; the new recovery path remains incompletely exercised. Retained-certificate signed APK and screenshots are published in `v1.1.2/`; see its QA.md for evidence and limitations.

# V1.1.2 preview scrubbing fix — source qualification

Source: branch `fix/preview-scrub`, merged into main. Version 1.1.2, code 20. No project-schema, export-graph or package-identity changes.

## CI evidence (GitHub Actions run 39171928481, commit ebe62ce)

- Attempt 2: build job (unit tests, lint, APK) passed. Device job: **28 / 28 Android tests passed**, including `PreviewScrubTest.fastScrubbingKeepsTheLivePreviewWorking` (~400 seeks; unchanged assertion and timeouts), with no preview player errors. Immediately after the tests, `adb` exited with code 255 and the emulator aborted (`libc++abi: terminating`), so the logcat pull and the independent ffmpeg/ffprobe export checks did **not** run in this attempt.
- Attempt 1: `PreviewScrubTest` passed with no preview errors; `VideoEditorTest.missingMediaRecordsRedactedCauseAndNextExportRecovers` hit the known intermittent Media3 export watchdog stall ("no output sample written in the last 25000 milliseconds"), recorded since v1.1.0 as unresolved. The export graph is unchanged by this fix, and the same test passed on the branch's three previous runs.
- Earlier runs on this branch (c8c7bdd, 00ae340, b38ed29) are the diagnostic history that identified the two Media3 causes listed in the changelog.

## Outstanding before publishing a signed APK

- One run with the export decode checks completing (they were skipped by the adb abort above).
- Signing with the retained BC-1 Preview key (certificate SHA-256 `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d`), the `v1.1.2/` release folder (APK, SHA256SUMS, QA, screenshots) and the README download link — handled in the original signing chat. No key was generated or published here.
- Physical-device scrubbing check (Snapdragon / DeX) is not measured by the emulator.

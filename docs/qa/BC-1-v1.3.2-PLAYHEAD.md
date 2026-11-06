# BC-1 v1.3.2 playhead preview correction

Status: emulator-qualified source; normal upgrade APK blocked by retained-key access. Do not claim the reported Samsung black-preview problem is proven resolved until the user tests their footage.

A concrete interaction defect was found: below the ruler, the visible green playhead was not hit-tested, so dragging it over media could move a clip instead of seeking. The playhead now scrubs throughout the timeline. Selected trim grips retain priority; taps still select footage. Scrubbing creates no undo entry and does not alter clip timing.

Paused ICE Lite rendering now hides the inactive playback SurfaceView, while starting the real player restores it. This avoids leaving two independent video surfaces visible during the paused preview handoff. This is a defensive compositor fix, not a confirmed diagnosis of the user's hardware.

PreviewScrubTest now drags the actual timeline line over red/blue videos, checks the requested time, unchanged clip positions/history, live texture pixels and actual screen pixels, then repeats backward scrubbing after playback/pause. Existing fast-scrub and export regression checks remain.

No project schema, package identity, source media or export pipeline changes. v1.3.2/code25, com.blackice.bc1.preview. Retained signing material must not be regenerated or published.

## Validation history

- Run 39490278911, source c94af708: build and 137 JVM tests passed; lint 0 errors / 49 warnings. Android: 42/44 passed. Independent export decoding was correctly blocked by the failed test gate.
- The new gesture test incorrectly assumed the imported AAC/video fixture ended at exactly 3000ms; Android reports 3021ms. It now snapshots actual placements before the gesture and asserts that both remain unchanged.
- EditorWorkspaceTest missed short playback because ActivityScenario.onActivity waits for UI idleness. Logs showed READY at 20:18:07 and ENDED at 20:18:11, while the assertion timed out at 20:18:40. Playback observation now runs directly on the main thread without waiting for the animation/rendering queue to become idle. It still requires actual isPlaying and an advancing media clock.
- Follow-up source ec6e3e9c8725bd13a8f8ddb69bf52bc75794af56, run 39492323400: passed: 137 JVM tests, 44 Android tests (including visible screen pixel scrubbing and playback handoff), and 10 independent export decoding/metadata checks. Build and lint passed (0 errors / 49 warnings). API35 x86_64 SwiftShader; not physical Samsung qualification.

## Signing limitation

The retained preview backup was located, but its authorized download failed with an access-denied response in this session. No retained signing material was regenerated, printed or published. A normal upgrade APK is not published. CI's separate com.blackice.bc1.icelite test build is not an in-place update and does not share the installed Preview app's project storage. Keep the existing Preview installation and older releases.

## Separate test download

[BC-1 v1.3.2 ICE Lite test APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/00b67d2d103353f2cd84ae70acf06ca23ee73aa1/BC-1-ICE-Lite-test.apk)

Built from qualified source ec6e3e9c8725bd13a8f8ddb69bf52bc75794af56. Package com.blackice.bc1.icelite, versionName 1.3.2-icelite, code25. Downloads were retrieved and SHA-256 checked: f482d10869ca8905066a882f241834ce2b6e756037eb1b2c0e678f94220919fa. Import a clip into this separate test app to verify paused scrubbing on the affected phone. Do not uninstall the existing Preview app. This test build does not carry the retained Preview certificate and cannot update that installation.

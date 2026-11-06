# V1.1 cinematic redesign — implementation checkpoint

Native Android Views; existing EditorActivity, ToolPanels, TimelineView, PreviewEditor and Media3/GLES3 pipeline retained. BlackIceTheme owns presentation tokens and lightweight static orbital decoration. No schema, package or source-media changes.

Implemented: shared requested palette, translucent gradient surfaces, consistent monoline cube/logo/icons, slimmer wide controls, persistent desktop navigation/actions, autosave-aware dirty marker, Inspector/Project tabs, full-width desktop timeline and height drag handle, selected-only timeline toolbar, media favorites (long press a card), sort by project/name/duration and grid/list controls, view-only Fit/Fill/100%, preview volume, fullscreen with exit, previous/next frame buttons and reduced-motion preference. Existing panel resize/docking, touch timeline, keyboard shortcuts, save/reopen, GPU effects and export retained.

Build: JDK17 / Gradle8.11.1 / Android SDK36, `cd BC-1 && gradle -Pbc1SideBySide testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`. GitHub workflow runs unit/lint/build and API35 device/export checks. VersionName1.1.0, code18. Qualified development-preview APK: [v1.1.0/QA.md](../../v1.1.0/QA.md).

Still incomplete: exact reference parity, separate pre-insertion asset catalog/desktop asset drag-drop, draggable phone drawer, reverse playback, independent effect tracks and pen/draw. Existing media browser lists project clips; its preview selects the existing timeline clip. No fake controls for unsupported editing operations. No live blur. UI animations honor Android animator-disable and Reduced motion. Preview gestures still re-prepare media on release. Physical DeX/Snapdragon performance unmeasured.

## Qualification findings

- First run 39103969429: preview drag test assumed a fixed 25 dp gesture always moved a layer more than 0.05 normalized units. The enlarged desktop canvas invalidated that assumption. The test now drags 10% of the surface width and retains selected-layer, other-layer and undo assertions.
- Runs 39104080720 and 39104959581 exposed nondeterministic sampling after timed playback. The test now explicitly checks that playback advances, pauses, seeks to a known covered frame, and performs the same PixelCopy color check. This is a deterministic sampling change, not a claimed renderer fix.
- Run 39104656801 confirmed the new toolbar left too little preview height in the 780×420 window. In short wide windows the duplicate timeline toolbar is omitted; existing transport and inspector actions remain. The original minimum-size assertion is retained.
- Run 39104959581 reproduced the historical intermittent export failure. The log identifies a Media3 watchdog abort after **25 seconds without an output sample**. The initiating pipeline stall remains unexplained. No timeout was increased and no export gate was disabled. A later passing run would not establish that this issue is fixed.

New controls for favorites/sorting/list mode and preview scaling/volume/fullscreen are implemented; dedicated automated interaction coverage for every one of those controls is not yet complete. Existing broad regressions plus new frame-button/navigation assertions are used. Exact mockup parity and physical-device performance are not claimed.

- Run 39105225950 stopped on a null workspace-test control after a fixed 3.5-second startup delay. The test now waits for loading to finish, verifies the exact fixture project ID, and reports missing controls explicitly. This removes a timing assumption; it does not establish the cause of that null result.
- Visual review found a stale initial project caption. The preview information is now refreshed from the loaded project; the device test checks its name. Media/Audio navigation also resets the add category correctly and checks selected-tab state without altering project duration.

## Latest candidate

Source `6ff1b306876a3af2ec06d1bccf0504e43b57223c`, Actions [39105942908](https://github.com/uran39281-bit/bc-1/actions/runs/39105942908). Build passed: 78 unit tests, 0 failures/ignored; lint 0 errors, 44 warnings. Device qualification completed: 25 Android tests and five independent exported-video checks passed; both jobs succeeded. Final phone, wide and compact-wide screenshots reviewed. Signed candidate verified against the retained preview certificate; all 41 non-signature APK entries match the CI artifact. No private signing material is included in the repository.

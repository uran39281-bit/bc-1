# V1.1 cinematic redesign — implementation checkpoint

Native Android Views; existing EditorActivity, ToolPanels, TimelineView, PreviewEditor and Media3/GLES3 pipeline retained. BlackIceTheme owns presentation tokens and lightweight static orbital decoration. No schema, package or source-media changes.

Implemented: shared requested palette, translucent gradient surfaces, consistent monoline cube/logo/icons, slimmer wide controls, persistent desktop navigation/actions, autosave-aware dirty marker, Inspector/Project tabs, full-width desktop timeline and height drag handle, selected-only timeline toolbar, media favorites (long press a card), sort by project/name/duration and grid/list controls, view-only Fit/Fill/100%, preview volume, fullscreen with exit, previous/next frame buttons and reduced-motion preference. Existing panel resize/docking, touch timeline, keyboard shortcuts, save/reopen, GPU effects and export retained.

Build: JDK17 / Gradle8.11.1 / Android SDK36, `cd BC-1 && gradle -Pbc1SideBySide testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`. GitHub workflow runs unit/lint/build and API35 device/export checks. VersionName1.1.0, code18. No APK qualified yet.

Still incomplete: exact reference parity, separate pre-insertion asset catalog/desktop asset drag-drop, draggable phone drawer, reverse playback, independent effect tracks and pen/draw. Existing media browser lists project clips; its preview selects the existing timeline clip. No fake controls for unsupported editing operations. No live blur. UI animations honor Android animator-disable and Reduced motion. Preview gestures still re-prepare media on release. Physical DeX/Snapdragon performance unmeasured.

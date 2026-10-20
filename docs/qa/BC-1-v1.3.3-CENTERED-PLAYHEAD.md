# Centered playhead — v1.3.3 emulator-qualified source

User correction: the playhead stays in the middle while timeline content moves underneath. Replaces the moving-line interaction from v1.3.2.

- Shared timeline time-to-position mapping starts time zero in the center; seeks and playback center the active time, including endpoints.
- Normal horizontal swipes over clips or empty lanes scrub without editing media. Hold before dragging to move a clip; mouse editing and selected trim grips remain direct. Vertical navigation remains separate.
- Zoom keeps the active time at the fixed playhead. Resizing restores the current time at the new center.
- Paused preview still renders through ICE Lite. No project schema, source media or export engine changes.
- Gesture regression checks exercise visible video pixels before and after playback, center position, unchanged clip timing/history and zoom. Existing editing tests use the revised hold-to-move gesture.

Qualified source a4f8d98ee31b7bd79e25cfdf6208cddd5c2d2a61, CI run 39510438799: build/unit/lint job passed; 44 Android tests and 10 independent export checks passed. The actual phone screenshot was inspected: playhead at screen center, timeline time mapping aligned. Normal Preview signing remains blocked by retained-backup access. No key generated or published. Physical Samsung testing remains outstanding.


## Test download and screenshot

[Separate v1.3.3 ICE Lite APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/f3aef407de54c8dda4cfad40911b50ee757c3de2/BC-1-ICE-Lite-test.apk)

Package com.blackice.bc1.icelite, version 1.3.3-icelite/code26. Download SHA-256 verified: 3492cd078fb9f93f464c3a833e3dcd529a950e05e7970eb182869c52d0a28b99. It does not update Preview or share its project storage. Keep the existing Preview app; import footage into the separate test app for physical-device confirmation. CI test builds may have different debug certificates from previous test builds.

[Actual centered phone screenshot](https://raw.githubusercontent.com/uran39281-bit/bc-1/28d1fbb9b59928d119f3f785ce159c08d6866377/v131-footage-selected.png)


# BC-1 v1.1.1 — phone media space

Source: `2db3fd0f2eabe1bdb0ffbc1ca5baa6c1591b0ee2`, based on main50cd4b8 and its media-bin changes.
[Actions39118809055](https://github.com/uran39281-bit/bc-1/actions/runs/39118809055).

## What changed

- Phone Add uses a large scrollable media browser rather than a leftover strip below the full editor.
- Preview shrinks to80dp; an88dp timeline remains as a drop target. Playback and toolbars return when leaving Add.
- Done / Android Back returns to editing without replacing the preview surface or modifying the project.
- No redundant Inspector/Project tabs while browsing; category touch targets remain48dp.
- Clip-specific timeline actions are hidden until selection.
- Existing desktop workspace, media bin, tap insertion, drag placement and project schema retained.

Build passed:88 unit tests,0failures/ignored; lint0errors44warnings. 27 Android tests passed, plus five independent ffmpeg/ffprobe export checks (three1080p exports, failure recovery and synchronized speed export). Both workflow jobs succeeded.

New device regression measures actual browser space at1080×2340/density420 and1080×1920/density480, verifies insertion and save/reopen. Existing phone/DeX/timeline/GPU/export suite also runs.

Package `com.blackice.bc1.preview`, version1.1.1/code19. Retained preview signing certificate verified; signed APK payload matches CI. Install over the current Preview; do not uninstall first.

This remains a development build. The earlier intermittent Media3 export watchdog stall is not claimed fixed. Physical Samsung/DeX/Snapdragon performance remains unmeasured. Other V1.1 feature limitations remain except those subsequently addressed by main50cd4b8 (media bin/free placement).

## Qualification findings

First run39117833882 passed the phone-space, insertion and save/reopen regression, but failed one of27 device tests because the diagnostics report still contained a hardcoded1.1.0 label. Diagnostics and Settings now read the installed package version. Screenshot review also caught wrapped branding at360dp width; the wordmark is now single-line with adaptive font sizing. The existing preview/timeline focus settings remain honored when returning to editing.

Final screenshots reviewed: [phone picker](screenshots/phone-picker.png) and [small phone picker](screenshots/small-phone-picker.png). The browser-space assertion passed on both sizes; title wrapping is fixed. Lower browser content is intentionally scrollable. Actual device-gallery permissions/import behavior is retained; the screenshot uses a generated test asset, not bundled sample footage.

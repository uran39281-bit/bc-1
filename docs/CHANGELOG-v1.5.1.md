# Black Ice Editor 1.5.1 — Compact inspector controls

Version code: 45. Published with the retained Preview key.

- Editor inspector actions share compact wide-workspace sizing, while portrait controls retain touch sizing. Icons, preview and timeline sizes are preserved.
- Position, Rotation, Scale, Blending and Effects use consistent card padding, flexible value rows and readable scrollable easing graphs. Position Previous Key / Reset all / Next Key share a row.
- Position offers Ruler, Slider and Trackpad. The choice is saved on the device and does not change project data. Exact-value entry and keyframe buttons remain available in every mode.
- Trackpad keeps keyboard focus across committed edits and routes its arrow keys before timeline shortcuts. Trackpad gestures adjust X and Y together through the existing project transaction and produce one undo step. Cancellation, disable and detach roll back the gesture. Arrow keys move one pixel, Shift moves ten; accessibility actions are available.
- Position sliders span one canvas dimension in either direction around the starting value, rather than the engine's enormous safety limits. New edits recenter this range.

Qualification passed on source `7c76a0211273d0bc2f4c084b590286e6849dc040`, CI run [41565979442](https://github.com/uran39281-bit/bc-1/actions/runs/41565979442): 271 unit tests, 103 Android tests, four supplemental inspector preflight tests, zero lint errors (110 warnings) and 30 independent export checks. The exact CI candidate was signed and its certificate, alignment and unchanged payloads verified. [Publish run 41568636856](https://github.com/uran39281-bit/bc-1/actions/runs/41568636856) passed the retained-key in-place upgrade from Preview 1.5.0 build 44, preserving the test project's media and edits.

[Download Preview 1.5.1 build 45](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.1/Black-Ice-Editor-v1.5.1-Preview-build45.apk) · [QA and screenshots](../v1.5.1/QA.md).

Physical Samsung phone/DeX validation remains outstanding.

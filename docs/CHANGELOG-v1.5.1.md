# Black Ice Editor 1.5.1 — Compact inspector controls

Version code: 45. Not published yet.

- Editor inspector actions share compact wide-workspace sizing, while portrait controls retain touch sizing. Icons, preview and timeline sizes are preserved.
- Position, Rotation, Scale, Blending and Effects use consistent card padding, flexible value rows and readable scrollable easing graphs. Position Previous Key / Reset all / Next Key share a row.
- Position offers Ruler, Slider and Trackpad. The choice is saved on the device and does not change project data. Exact-value entry and keyframe buttons remain available in every mode.
- Trackpad keeps keyboard focus across committed edits and routes its arrow keys before timeline shortcuts. Trackpad gestures adjust X and Y together through the existing project transaction and produce one undo step. Cancellation, disable and detach roll back the gesture. Arrow keys move one pixel, Shift moves ten; accessibility actions are available.
- Position sliders span one canvas dimension in either direction around the starting value, rather than the engine's enormous safety limits. New edits recenter this range.

Qualification is pending. The full build/lint/unit suite, Android emulator tests, independent export checks and actual screenshot inspection must pass before signing the exact candidate. Publication requires an in-place retained-key upgrade from Preview 1.5.0 build 44.

Physical Samsung phone/DeX validation remains outstanding.

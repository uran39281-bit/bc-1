# Centered playhead — v1.3.3 candidate

User correction: the playhead stays in the middle while timeline content moves underneath. Replaces the moving-line interaction from v1.3.2.

- Shared timeline time-to-position mapping starts time zero in the center; seeks and playback center the active time, including endpoints.
- Normal horizontal swipes over clips or empty lanes scrub without editing media. Hold before dragging to move a clip; mouse editing and selected trim grips remain direct. Vertical navigation remains separate.
- Zoom keeps the active time at the fixed playhead. Resizing restores the current time at the new center.
- Paused preview still renders through ICE Lite. No project schema, source media or export engine changes.
- Gesture regression checks exercise visible video pixels before and after playback, center position, unchanged clip timing/history and zoom. Existing editing tests use the revised hold-to-move gesture.

Awaiting CI. Normal Preview signing remains blocked by retained-backup access. No key generated or published. Physical Samsung testing remains outstanding.

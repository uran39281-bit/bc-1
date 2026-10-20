# BC-1 v1.3.2 playhead preview correction

Status: candidate, awaiting CI and device confirmation. Do not claim the reported Samsung black-preview problem is proven resolved until the user tests their footage.

A concrete interaction defect was found: below the ruler, the visible green playhead was not hit-tested, so dragging it over media could move a clip instead of seeking. The playhead now scrubs throughout the timeline. Selected trim grips retain priority; taps still select footage. Scrubbing creates no undo entry and does not alter clip timing.

Paused ICE Lite rendering now hides the inactive playback SurfaceView, while starting the real player restores it. This avoids leaving two independent video surfaces visible during the paused preview handoff. This is a defensive compositor fix, not a confirmed diagnosis of the user's hardware.

PreviewScrubTest now drags the actual timeline line over red/blue videos, checks the requested time, unchanged clip positions/history, live texture pixels and actual screen pixels, then repeats backward scrubbing after playback/pause. Existing fast-scrub and export regression checks remain.

No project schema, package identity, source media or export pipeline changes. v1.3.2/code25, com.blackice.bc1.preview. Retained signing material must not be regenerated or published.

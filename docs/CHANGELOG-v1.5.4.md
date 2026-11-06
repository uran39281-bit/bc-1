# Black Ice Editor 1.5.4 — build 48

Status: exact-source CI passed and retained-key APK signed; in-place upgrade/publication gate pending.

Applies the supplied thirteen-item update to the native phone and wide editor menus:
- Shared ruler parameters, joined Effects-style key navigation, square centered vector keyframe controls and collapsible easing sections.
- Color & Fill keeps its color wheel/ramp and uses the shared custom easing graph. Legacy interpolation remains unchanged until edited.
- Shape & Style removes duplicate transform/duration/animation routes; their owning panels and saved data remain.
- Consistent dark teal/mint selection, shared spacing and icons, responsive Export/Settings/Create Project dialogs, all five project ratios including 9:16, and real version metadata in chrome.
- Up to three independently identified effects on each supported video/image clip. Adds ordered render passes through ICE Lite and Media3; schema 12 migrates a legacy effect into one instance. Copy/duplicate and undo retain independent tracks.

The source inventory is in `docs/v1.5.4/SOURCE-INVENTORY.md`. Actual evidence is in `v1.5.4/QA.md` and `docs/v1.5.4/VERIFICATION.md`: 281 unit tests, 112 Android tests, 18 focused native checks and 32 independent export checks passed (the focused checks overlap the full suite). Lint has 0 errors and 118 warnings; preview redraw is 95 ms under the unchanged 1,500-ms limit. The retained-key publication workflow requires that successful run and an in-place 1.5.3 (47) → 1.5.4 (48) upgrade gate. No signing key is stored in CI.

Existing exclusions remain: generated shape/text overlays cannot receive visual clip effects; audio does not receive visual effects; cubic handles remain bounded without overshoot; physical Samsung/DeX verification is still required.

# 1.5.4 menu update — verification scope

This report distinguishes source inspection, executed native assertions and screenshot inspection. It does not turn an expected outcome into a test result. The exact-source full CI passed. Retained-key signing is verified; the separate in-place upgrade gate is pending.

## Source and build

Baseline main: `38493d4aa5019bf950162416e6c0170c658894f9` (1.5.3 build 47).
Candidate source: `62b2ca19250d329a29af7f9e19028cb5fcecb75f` (1.5.4 build 48), PR #28.
Native Android Java Views; existing project commands, ProjectStore, RulerView, CurveEditorView, V1Icon, live-adjust/undo and both render/export engines are used.
Local unit/lint/Android-test compile passed: 281 tests, no failures or skips; lint 0 errors / 118 warnings. Full CI run 42171039606 passed: 112/112 Android tests, 4/4 preflight and 32 independent export checks. Focused native run 42171039613 passed 18/18. Preview redraw was 95 ms under the unchanged 1,500-ms limit.

## Coverage against the thirteen requested changes

| Item | Implemented routes | Executed assertion / image evidence | Remaining manual matrix |
|---|---|---|---|
| 1 Rulers | MenuScreenFactory; ToolPanels generic values; FillPanel brightness/alpha/stop/angle; AudioToolsPanel EQ; EditorActivity legacy percent/preview volume | InspectorControlsTest gesture/cancel/keyboard/undo; native inspector scans reject SeekBar in Transform, Blending, Effects, Color & Fill and Shape & Style in both workspaces. Playback scrubber is explicitly retained. | Every nested audio/chroma/border/transition field with mouse and increased text scale not independently visited by a human. |
| 2 Collapsible graphs | MenuScreenFactory and EasingControls, including Color & Fill | Initial GONE body; opening does not change encoded project or undo; native graph keyboard edit and undo/redo; per-property expansion saved in Bundle. | Exhaustive recreation/scroll/focus sweep of every property not manually run. |
| 3 Key navigation | Shared KeyNavigation for all menu paths, ToolPanels and FillPanel | Effects joined control visible in both layouts; MenuUpdateTest exercises strict property neighbors and key creation invariants. | Manual exact/between/end jumps on every named property not claimed. |
| 4 Diamonds | KeyframeButton and canonical vector path; timeline V1Icon | Native measured square view bounds in both workspaces; path centered in a square viewport; phone/wide captures. | Enlarged-text hit-target measurements across every menu not run. |
| 5 Shape/Fill cleanup | ToolPanels Shape & Style; EasingControls.buildFill | Duplicate links absent from actual phone/wide inspectors; real FillStyle custom curve edit, undo/redo and roundtrip tests. | Full manual owning-route/recreation matrix not run. |
| 6 Spacing | Shared MenuPalette 8/12/16 dp tokens; InspectorStyle; adaptive rows and dialogs | Actual phone and wide screenshots reviewed. Wide key controls stay square while surrounding buttons remain compact; phone content scrolls. | Every long translation/font scaling case not run. |
| 7 Icons | Shared key/nav/reset/expand vector components; V1Icon; dialog choice adapter | Source inventory and actual repeated controls; Settings lock icon prioritized over the word layout. | Independent human optical review not claimed. |
| 8 Mint selection | MenuPalette; menu modes/effect instances; Home; choices | Selected native controls use dark tint/mint outlines. Cap action checked disabled and dimmed; removal restores full opacity/enabled state. | All settings child-choice focus states not manually visited. |
| 9 Export | ExportWorkspace and BlackIceDialog | Phone stacked and wide two-column screenshots; actual summary; real stack exports through both engines. Existing Android destination picker and progress service retained. | Real external-provider cancellation, keyboard and all validation combinations not manually run. |
| 10 Settings | Existing settings handlers in BlackIceDialog; native nested choices | Actual phone/wide Settings capture, existing shortcut/settings checks; source icon coverage. | Every nested option, Escape/focus restoration and touch leakage case not manually exercised. |
| 11 Three effects | EditProject/EffectInstance/ProjectStore/Attributes/ReverseOps; RenderPlan/Compositor/StackEffect | Model cap, identity/removal/copy/timing/migration tests; phone/wide selected-instance edit/key/undo; actual sequential preview and both engine exports. | Full combinatorial stack of all 47 effects on physical GPUs is not claimed. |
| 12 Version | Home/Editor dedicated bottom-right chrome | Captures show installed v1.5.4; render graph has no version overlay; real output fixture inspected separately. | Other physical inset/navigation configurations not run. |
| 13 Create/9:16 | NewProjectDialog and CanvasFormat | Five proportional native choices; both workspaces create/save/reload 1080 × 1920; both actual export backends produce 180 × 320 files. Existing naming/cap tests retained. | Full manual 2 × 2 workspace/canvas/picker/font matrix not run. |

## Geometry

KeyframeButton measures a square at a requested 48 dp, with a 44 dp minimum in compact adaptive rows. The canonical diamond uses a square 24-unit viewport and points (12,4), (20,12), (12,20), (4,12); it is drawn in a centered 20 dp square. Native MenuConsistencyTest compares actual measured width to height in each workspace. This is a real layout assertion; a complete numerical hit-target/gap log for every menu is not claimed.

## Two review passes

Pass 1 is native automation executed separately at wide 1920 × 1080 / 160 dpi and phone 824 × 1784 / 320 dpi. Pass 2 is the same agent’s independent source-to-reference and native-screenshot review, plus FFmpeg decoding and pixel comparisons. A separate human reviewer and exhaustive manual two-pass checklist are NOT RUN. Native assertions and screenshots support only the routes/states listed above.

## Honest completion status

Implementation is complete for the requested source changes. Automated regression and retained-key signing passed. Signed upgrade, publication and delivery remain pending. See v1.5.4/QA.md for hashes, exact artifact IDs and actual results. The exhaustive manual checklist remains partly NOT RUN as detailed above; do not describe that checklist as fully complete. Physical Samsung/DeX performance is unverified. Generated shape/text overlays and audio retain their existing visual-effect exclusions; each supported clip has up to three effects, independent animated strength and existing static named controls. Cubic handles remain bounded without overshoot.

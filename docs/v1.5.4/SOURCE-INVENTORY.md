# 1.5.4 source inventory

Baseline: main 38493d4aa5019bf950162416e6c0170c658894f9, 1.5.3 build 47.
Native Android Java Views. Both workspaces share the inspector model; phone drawers and wide containers remain separate.

| Route | Existing binding | Canonical replacement / coverage |
|---|---|---|
| Position/Rotation/Scale, Blending, Effects | MenuScreenFactory → MenuHost → transform/live-adjust commands | RulerView; square KeyframeButton; Effects joined KeyNavigation; CollapsibleSection/CurveEditorView |
| Audio, border/shadow, chroma, transitions | ToolPanels.valueRow → liveAdjust | RulerView with original domain, display unit and one gesture transaction |
| Color & Fill | FillPanel → FillStyle.Track | Rulers for brightness/alpha/stop/angle; preserved wheel/ramp; same real CurveEditorView |
| Equalizer, preview volume, legacy percent editor | AudioToolsPanel / EditorActivity | Draft-bound ruler; Apply/Cancel unchanged |
| Export | ExportWorkspace → ACTION_CREATE_DOCUMENT → ExportService | Shared BlackIceDialog; real ExportOptions/EncoderCaps summary |
| Settings + child choices | EditorActivity.settingsPanel / projectSettingsDialog | Same themed shell and semantic icons; original settings retained |
| Create | NewProjectDialog → ProjectStore.save | Same themed shell, proportional preview and all five CanvasFormat ratios; registry allocates names atomically |
| Effects storage/history/copy | EditProject.Clip, ProjectStore, Attributes | Schema 12; primary legacy adapter + stable additional instances; ordered cap enforced in model |
| Preview/export/layer capture | RenderPlan, Compositor, ClipEffect/CompositionFactory | Same ordered effect passes, two reusable targets in ICE Lite; Media3 pooled passes |

Playback seeking and the timeline time ruler remain purpose-specific controls. Timeline zoom UI is not added. WorkbenchActivity is an isolated diagnostic activity, not a production editor menu.

Runtime evidence is pending. Source inspection is not a runtime pass.

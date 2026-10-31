# v1.4.7 (build 41) — Menu update and Easing Curve Editor

Source implementation of the *Black Ice Menu Update* kit (October 30, 2026) in the existing native-Views editor. The kit's reference components were imported as `com.blackice.bc1.menu`, adapted to the real project model, undo, key selection and renderers through `MenuHost`, and mounted into the inspector through `MenuPanels`. No Compose, WebView or new project store was added.

## The five menus
Position, Rotation, Scale, Blending & Opacity and Effects follow the supplied reference order and styling (`menu/MenuScreenFactory`, `MenuPalette`, `MenuIconDrawable`).

- **Position:** key navigation with *Reset all*; X and Y cards with pixel readouts and rulers; one graph that follows the card you select.
- **Rotation:** circular dial and *Angle presets*; stored angles are cumulative, so 360° + 45° reads 405° and 0° − 360° reads −360°.
- **Scale:** *Fit Canvas* / *Fill Canvas*, Width and Height with *Lock W and H*, and a graph with Width / Height tabs. The ruler window is local; the supported range (5%–10000% width) is unchanged.
- **Blending & Opacity:** opacity 0–100% with Apply/Reset, the seven pictured blend modes, and an opacity graph.
- **Effects:** *Add Effects*; with an effect applied, its real catalog name, an *Effect strength* slider with Apply/Reset and key navigation, its graph, and *Remove Effect*. With none applied only *Add Effects* is shown.

## Easing Curve Editor
- Replaces the four Linear / Ease In / Ease Out / Ease In/Out buttons in these five menus. Two cyan handles edit one outgoing interval; endpoints are fixed; handles are bounded to 0–1 (no overshoot).
- Progress is solved by inverting x(t) before reading y(t). The graph plots `Motion.ease`, the evaluator shared by the live preview and both export engines.
- One drag is one undo step. Cancelling a gesture, or the selected interval changing during it, restores the original curve and adds no history. *Reset curve* returns that one interval to linear.
- Handles are keyboard and TalkBack adjustable. *Expand* opens the same editor in a larger window.
- Opening a menu, moving the playhead or switching property never creates keyframes. The interval label (for example `X · Key 1 → Key 2`) selects the outgoing key; a diamond follows the real key state.

## Compatibility with existing projects
- Presets 0–3 stay analytic quadratics. Opening or saving a legacy interval never rewrites it. Ease In and Ease Out are drawn with their exact cubic forms; Ease In/Out is piecewise and has no single cubic, so its handles are an approximation (within 0.5%) labelled as such until you edit it.
- A custom edit writes only that key's four coordinates and `easingToNext = -1`. Existing custom cubics reopen with exactly their stored handles. No schema change (still 11).
- Stable key ids, source-time keys, trimming and splitting are untouched; `Keyframes.easeAll` is not used.

## Behaviour changes to know about
- **Rulers follow the finger:** dragging right increases the value (previously the strip followed the finger and the value decreased). Applies to X, Y, Width, Height and Opacity.
- The old *Opacity* tool and the *Blending & Opacity* tile open the same combined menu.
- Clips keep every existing blend mode: the seven pictured modes appear in the grid, and the remaining ones are behind *More…* in the empty grid slot. Shapes, text and images keep their seven stored modes.
- *Remove Effect* also clears that effect's strength animation and resets strength to its 60% default, in one undo step. Other animation is preserved.
- *Reset* on effect strength uses the model default (60%). There is no per-effect default table.

## Not changed
- Audio menus, the Animation panel (its per-property *Easing* button and *Custom curve…* dialog), the Effect Catalog, text/shape panels and export are unchanged.
- One effect per clip; no effect stacking.

## Tests
- JVM: `CubicBezierTest`, `RotationDragTest`, `BlendChoicesTest`, `CurveEditsTest` (23 tests, run locally and in CI).
- Instrumentation: `MenuUpdateTest` drives all five menus and captures them on phone and wide layouts; `DailyUpdateTest`, `TransformControlsTest`, `WorkflowControlsTest`, `LandscapeStabilityTest` and `WorkspaceV2Test` were updated to the new controls.

## Publishing 1.4.7
`.github/workflows/bc1-v147-publish.yml` mirrors the 1.4.6 gate. It runs when `v1.4.7/release.json` lands on `main`, restores the offline-signed APK from the qualified CI APK, checks the retained certificate, installs published Preview 1.4.6 build 40, saves a fixture project (custom cubic, legacy Ease Out, saved effect), upgrades in place with `adb install -r` (`.github/scripts/bc1-v147-upgrade.py`) and verifies the project, media and every easing interval are unchanged, and only then publishes to `test-apk/preview/v1.4.7`.

Published from qualified source `d52de04e2ef8c70763e7b19f9b340dec804f318c`, CI run [41161341076](https://github.com/uran39281-bit/bc-1/actions/runs/41161341076): 250 JVM tests, 93 Android tests and 30 independent export checks passed; lint has 0 errors and 107 warnings. [Publication run 41163504778](https://github.com/uran39281-bit/bc-1/actions/runs/41163504778) passed the retained-key 1.4.6 → 1.4.7 upgrade. The signed APK, public release manifest and checksum are available; see [QA and screenshots](../v1.4.7/QA.md). Height-key writes on legacy uniform-scale clips and overlays now target Height without modifying Width; the regression covers static and animated layers and undo.

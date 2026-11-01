# v1.4.8 (build 42) — Menu polish, dynamic window, 9:16 paused

Follow-up to 1.4.7 after device testing on Samsung DeX showed the redesigned menus did not match the reference images. No project-schema change (still 11); saved projects and easing curves are untouched.

## Menus now match the references
- **Styling:** buttons no longer inherit the app theme's flat blue-grey `backgroundTint`, which had erased the dark outlined cards, borders and mint selected outlines. Tabs get the mint underline.
- **Narrow panels:** below 420 dp (phones, the DeX column) the menus use smaller type, a narrower Back button and a two-row key navigation (Previous / Next, then Reset all). From 420 dp up they use the reference's three-across row. Labels no longer wrap ("Prev ious").
- **Reference details:** the graph card has its curve icon with the interval label under the title, the info icon in the footer, and a Reset curve button; Effects has the sparkle heading, divider and double-chevron key buttons; ruler labels that would be clipped at an edge are skipped.
- **Phone:** the bottom sheet opens taller for these menus so the graph is reachable.

## Dynamic window (wide layout)
- The panel being worked in grows into the black bars beside the preview: a focused tool menu widens the right panel (up to about 520 dp), and the Effect Catalog widens the left bin (up to about 420 dp). Leaving the panel returns it to its resting width. A manually dragged panel width is respected as the resting width.
- The picture never shrinks, and keeps a margin so its selection handles are not clipped.
- Fit and Compare no longer sit on top of the picture; they are in the transport row beside Fullscreen.
- Menus re-lay themselves out in place when the panel crosses the compact width.
- Fullscreen preview and the phone layout are unchanged.

## 9:16 paused
- Portrait 9:16 is no longer offered when creating a project or changing the canvas (16:9, 4:5, 1:1 and 4:3 remain). Existing 9:16 projects still open, edit and export. `CanvasFormat.SELECTABLE` controls the offered list; restoring 9:16 is a one-line change.

## Tests
- New `DynamicWindowTest` (panel grows without shrinking the picture, shrinks back, the catalog widens the left bin, controls leave the picture) and `SelectableRatioTest`; the new-project test now checks that 9:16 is not offered.
- `MenuUpdateTest` renders every menu at 300, 360 and 450 dp for review against the reference images.

## Publishing 1.4.8
`.github/workflows/bc1-v148-publish.yml` mirrors the 1.4.7 gate: it runs when `v1.4.8/release.json` lands on `main`, restores the retained-key signed APK from the qualified CI APK, installs published Preview 1.4.7 build 41, upgrades in place and verifies the project, media and every easing interval are unchanged (`.github/scripts/bc1-v148-upgrade.py`), then publishes to `test-apk/preview/v1.4.8`.

The signed APK, `v1.4.8/release.json`, `v1.4.8/SHA256SUMS.txt` and `v1.4.8/QA.md` do not exist yet and are not committed as placeholders.

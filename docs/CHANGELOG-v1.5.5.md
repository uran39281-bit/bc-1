# v1.5.5 (build 49) — 18 new grading and look effects

No project-schema change (still 11). Effect ids 48–65 are appended, so saved projects and the existing effect ids are unaffected. Version 1.5.4 (build 48) belongs to a separate line of work; this release is based on published Preview 1.5.3 (build 47).

## New effects
Eighteen effects join the 47 existing ones (66 in all). Each has strength plus its own controls (slider, readout and Reset in the Effects menu), and all work in the preview and in both export engines.

| Effect | Controls | Notes |
|---|---|---|
| Highlights | Range | Centered strength: below 50% pulls highlights down, above lifts them |
| Shadows | Range | Centered: below 50% deepens shadows, above lifts them |
| Fade | Black lift, Desaturate | Matte, lifted-black look |
| Temperature | none | Centered cool ↔ warm white balance |
| Tint | none | Centered green ↔ magenta |
| Vibrance | none | Saturation that protects already-saturated colour |
| Clarity | Radius | Mid-tone local contrast |
| Dehaze | Recovery | Removes a milky veil and restores depth |
| S-Curve | Midpoint | Contrast curve around a movable midpoint |
| Color Balance | Shadow hue, Highlight hue, Balance | Split tone |
| Selective Color | Target hue, Hue shift, Saturation, Brightness | Adjusts one colour range; strength widens the range |
| Night Boost | Blue tint | Lifts dark footage and cools the shadows |
| Auto Enhance | Pop | Contrast, vibrance and a little lift together |
| B&W Mix | Reds, Greens, Blues | Black and white with per-colour filters |
| Particles | Size, Speed, Density | Drifting bokeh-style specks |
| Tinted Vignette | Tint hue, Size, Softness | Coloured edge darkening |
| Filmic Tone | Exposure | Filmic highlight roll-off |
| Chroma Key (Blue) | Softness | Blue-screen counterpart of Chroma Key (Green) |

They appear in the Effect Catalog under Color & Light, Blur & Stylize, Patterns & Texture and Mask & Reveal.

## About the Black Ice Effects Integration Lab pack
The pack lists 103 packages taken from another application's effect resources (Lua scripts, material graphs, shaders and textures, with their original names and binaries unchanged). Those files are **not** copied into this project or its APK: they are another vendor's assets and the pack carries no licence for redistribution. The new effects are written from scratch for BC-1's own shader; they cover the same kinds of adjustment as the pack's colour-grading entries and share no code or textures with them.

How the pack maps to the editor:
- **Already in the editor:** Brightness (3 entries), Contrast (3), Saturation (2), Sharpen (2), Vignetting (the plain one), Chroma.
- **Now covered by a new effect:** Highlight (2), Shadow and Shadow Highlight (5), Fade, Temperature and Tone Temperature (3), Tone (2), HSL (2), Curves, Primary Wheel and Log Wheel, Structure, Clear, Night Enhance, Image Enhance (3), Black & White Levels, Particle (3), Vignetting V1, Chroma V320 / V1370.
- **Not implemented, because they are features rather than shader effects:** 21 face and beauty entries (need face tracking), 9 brush tools (touch painting), 13 cutout, matting and removal entries (need segmentation or mattes), 8 split-screen layouts (multi-layer compositing), 5 text and animation templates, Area Locked and Draw Locked Box (tracking), and entries whose function cannot be identified (Dark Mask, Light Sensation, Combine Adjust, Unidentified Filter, 00Origin Pic).

## Publishing 1.5.5
`.github/workflows/bc1-v155-publish.yml` mirrors the 1.5.3 gate: it runs when `v1.5.5/release.json` lands on `main`, restores the retained-key signed APK from the qualified CI APK, installs published Preview 1.5.3 build 47, upgrades in place, verifies the project and publishes to `test-apk/preview/v1.5.5`. If 1.5.4 is published first, point the gate at that build instead. The signed APK, `release.json`, checksums and QA do not exist yet and are not committed as placeholders.

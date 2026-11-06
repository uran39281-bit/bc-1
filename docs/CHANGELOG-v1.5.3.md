# v1.5.3 (build 47) — Effect controls and portrait workspace

No project-schema change (still 11). Version 1.5.2 (build 46) is intentionally not used; this release follows published Preview 1.5.1 (build 45).

## Per-effect controls
Most effects used to have only a strength slider. 33 of the 47 effects now also have up to four named controls, shown under **Effect strength** in the Effects menu, each with a slider, a readout (percent, or degrees for angles) and its own Reset:

- **Blur and sharpen:** Gaussian Blur, Box Blur, Sharpen, Unsharp Mask: radius. Motion Blur: angle and length. Spin Blur and Zoom Blur: centre X and Y. Glow / Bloom: radius and threshold.
- **Distortion:** Swirl, Bulge, Pinch, Fisheye: radius or curvature and centre X/Y. Kaleidoscope: rotation and centre. Ripple and Wave: frequency and speed. Shake: speed. Mirror: mirror line.
- **Colour and light:** Grayscale: contrast. Ice Grade: tint. Levels: white point. Light Leak: position X/Y and size. Vignette: size and softness. Chroma Key and Luma Key: softness.
- **Stylize and edges:** Pixelate: cell size. Edge Detect: thickness. Emboss: depth and light angle. Film Grain: grain size and speed. Scanlines: line density. Glitch: speed and blocks. VHS: wobble and noise. RGB Split: angle. Halftone: dot angle and dot size.
- The remaining 14 effects are fully described by their strength (Opacity, Exposure, Saturation, Contrast, Brightness, Gamma, Hue Shift, Warm, Cool, Posterize, Invert, Sepia, Ice Duotone, Threshold).

Defaults reproduce each effect's original look exactly, so existing projects render as before. Controls are stored per clip only while the clip keeps that effect, are static (strength stays animatable), and are carried by Copy / Paste attributes and Layer Tools → Copy All. Every edit is one undo step with a live preview, and the preview, ICE Lite export and the Media3 export path all read the same values.

## Portrait workspace
Native phone portrait layout follows the supplied Base, Add Media and Selected Layer references. The preview, transport and six real timeline lanes retain their bounds while opening drawers; extra tracks and long menus scroll. The new portrait space preference starts Balanced, so a saved wide/landscape Expand timeline choice cannot shrink the portrait preview. Wide/DeX and phone-landscape retain their existing preference and layout. Project storage, renderer and export implementation are unchanged.

## Published Preview 1.5.3
[Download the retained-key Preview APK](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.3/Black-Ice-Editor-v1.5.3-Preview-build47.apk). Official [publish run 42156486365](https://github.com/uran39281-bit/bc-1/actions/runs/42156486365) passed the signed 1.5.1 (45) → 1.5.3 (47) upgrade gate and created immutable `test-apk/preview/v1.5.3` at `451b757173fd2a7979db7f6d68852923e86daa2b`. Source, media, edits, curves, fill, clock state and wide layout preference were retained; portrait starts at full Balanced height.

Qualified application source: `87358e5eb71000ba63e3e2eeacbcaaeb0796d7ef`; full CI run `41867013484` passed 275 unit tests, zero lint errors (114 warnings), 109 Android tests, four inspector preflight tests and 30 independent export checks. Focused native run `41867013307` passed 16 checks; all eight wide menu captures and view trees match the unchanged baseline. The signed APK is byte-identical to the previously delivered build.

APK SHA-256: `ca20a3b02afb04970e763d36ae3a4586230f13f888cb7e8d16ee9df71acf7d59`. See [QA](../v1.5.3/QA.md) and [published upgrade evidence](https://raw.githubusercontent.com/uran39281-bit/bc-1/test-apk/preview/v1.5.3/upgrade-verification.json). Physical Samsung/DeX verification and existing engine limitations remain as documented.

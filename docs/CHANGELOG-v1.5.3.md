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

## Publishing 1.5.3
`.github/workflows/bc1-v153-publish.yml` mirrors the 1.5.1 gate: it runs when `v1.5.3/release.json` lands on `main`, restores the retained-key signed APK from the qualified CI APK, installs published Preview 1.5.1 build 45, upgrades in place, verifies the project and publishes to `test-apk/preview/v1.5.3`. The signed APK, `release.json`, checksums and QA do not exist yet and are not committed as placeholders.

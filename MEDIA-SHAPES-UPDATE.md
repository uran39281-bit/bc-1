# Black Ice Editor 1.4.2 — media browser and shapes

VersionName remains **1.4.2**, with Android versionCode **36**.

## Changed behavior

- **Import Media** opens the native dark Browse Files window beside Project Media when there is enough width. Smaller windows use a contained popup. Android's system picker remains available for local and cloud files.
- Device browsing has media filters, album filters, search, asynchronous cached thumbnails, loading/access/error messages and import status. Closing or cancelling browsing does not add files. Imports enter Project Media before timeline placement.
- **All** shows video, audio and images. **Shapes** is the only media category that displays vector shapes. Long-press and drag a shape to a visual timeline row; tapping it offers placement at the playhead and favorites. Each insertion is one undo step.
- The catalog includes square, rectangle, triangle, circle, oval, hexagon, diamond, octagon, rhombus, star, heart, half moon, cross, line, half circle, arrow, three-leaf clover and a stylized 2D monkey head. Nine additional legacy shapes remain available. Duplicate hexagon in the request is represented once.
- New shapes use equal square reference bounds and natural proportions. Shape & Style exposes geometry, color/fill, border/shadow, duration, animation and a proportions reset. Move & Transform supports position, rotation and linked dimensions.
- **Effects → Styles / Transitions / Animation** organizes existing controls. Legacy shader effects remain disabled and are labelled unavailable; this update does not add a new effects engine.

## Project compatibility

Existing shape IDs and geometry are retained. New IDs are appended. Schema 9 records the expanded catalog; older supported projects still load, with a backup made before migration on save. Older app versions reject schema 9 instead of reinterpreting new shapes. Managed media copies, original source files, playback and both export backends retain their existing handling.

## Verification

Application source `505b640620ab51e0ecf2740470a5afd41b71b43e` passed compilation and APK assembly in [the build and device run](https://github.com/uran39281-bit/bc-1/actions/runs/40245622088). [Integration packaging run](https://github.com/uran39281-bit/bc-1/actions/runs/40245679362), source `93cb0b9aa27110fb5ce86814fd02ece9c6a63abe`, passed 197 unit tests, APK assembly and lint (0 errors, 72 warnings). It contains the same application files and a packaging-workflow comment update.

All **69 Android tests and 16 independent export checks passed** on API 35. This includes the rapid window-change/recreation regression and the existing workspace regression.

New Android coverage checks a real long-press shape drag onto the timeline, one-step undo/redo, separation of All and Shapes, geometry raster bounds, editing and reopening a monkey shape, mixed image/audio imports, and transition controls. A MediaStore gallery fixture checks actual file browsing/import, preservation of the source file, project-bin-only insertion, and restoration of search/filter/album state across window changes and activity recreation.

The common-square shape geometry is shared by catalog thumbnails, preview and both export backends. Existing tests also exercise shape-only export, project persistence, cumulative rotation, orientation, playback recovery, main-video and audio exports.

All 19 DEX/native-library/asset entries match the emulator-target APK byte-for-byte.

The built manifest verifies versionName `1.4.2`, versionCode `36`, package `com.blackice.bc1.integration`. APK SHA-256: `0d03b3f8302381598df0fe52514c64dc56dab54fa496aad5dc8d151b55145aec`.

## Screenshots

Actual emulator screenshots from the passing source revision were visually reviewed: the wide popup sits beside Project Media, the restored phone popup remains inside safe areas, and the shape cards retain their natural proportions.

- [Wide Browse Files beside Project Media](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/media-browser-shapes/v142-wide-import-gallery.png)
- [Phone Browse Files after resize/recreation](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/media-browser-shapes/v142-phone-import-gallery.png)
- [Wide shape catalog](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/media-browser-shapes/v142-wide-shapes.png)
- [Phone shape shelf and selected shape](https://raw.githubusercontent.com/uran39281-bit/bc-1/qa-screenshots/feature/media-browser-shapes/v142-phone-shapes.png)

## Limits

The existing limit is 20 regular shape/text/image overlays per project. The monkey is an original stylized 2D vector icon, not Blender's 3D Suzanne asset. Physical Samsung/DeX verification is outstanding. Development APK signing compatibility with existing installations is not guaranteed; keep the existing app and its project backups if Android reports a signature conflict.

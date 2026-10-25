# Offline audio integration

The editor includes noise reduction (soft gate plus 70 Hz rumble filter), five-band EQ, preamp, and measured loudness normalization. Controls are available on selected video/audio clips. Edits are non-destructive, stored with the project and undoable. Normalization measures the selected clip after its processing, speed, volume and fades; remeasure after changing those settings. Gain is bounded to ±24 dB and a −1 dBFS sample-peak ceiling. It is not a true-peak limiter or voice/background separator.

Ice Voice 01 is the product name for bundled pretrained Whisper Base English q5_1, not a newly trained model. English recognition, audio processing, caption editing, and subtitle export run locally. The merged manifest explicitly removes the INTERNET permission. The 59,721,011-byte model is included in each APK, and copied/verified into private app storage on first use. First preparation therefore needs additional free disk space. Cancelled extraction is safely restarted.

Caption times come from recognizer token timestamps and decoded media timestamps. Java wrapping does not divide timings by text length or extend words into silence. Manual timing shift and per-caption review remain available because automatic recognition/timing can still be wrong. Interrupted jobs resume from their saved audio cursor, and results are acknowledged only after saving the project.

The audio visualizer is temporarily disabled. Existing visualizer layer identifiers and styles round-trip through project files, but OverlayPainter skips them. Ordinary timeline audio waveforms remain available. Audio-only projects use a black preview canvas; MP4 export still requires a visual layer or caption overlay.

## Building

Use JDK 21, Gradle 8.11.1, Android SDK 36, NDK 27.2.12479018 and CMake 3.22.1. `gradle testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` builds/tests both supported ABIs (ARM64 and x86_64). Use `-Pbc1Abis=arm64-v8a` to build only for a phone, or `-Pbc1SideBySide` for a separate preview package.

Build-time dependencies require network access. CMake fetches whisper.cpp v1.9.5 with a fixed SHA-256; Gradle downloads the English model and refuses to package it unless its exact size and SHA-256 match. To build using an existing local model, pass `-Pbc1ModelFile=/absolute/path/ggml-base.en-q5_1.bin`. No dependency/model download occurs on the phone.

Recovered components came from the saved original source archive and the project's own v1.7 APK. Current editor menu, transform, project transaction and playback recovery code were retained. Schema 8 combines per-clip audio settings, caption receipts and preserved visualizer references with workspace landmarks and volume keyframes. Both earlier schema-7 variants load with defaults for absent fields. Older applications reject schema 8 instead of silently dropping the other feature set. Volume automation and legacy fades multiply normalization gain; noise reduction and EQ remain in the processing chain.

Physical Samsung/DeX verification is still necessary before claiming device-specific crash or playback behavior is resolved.

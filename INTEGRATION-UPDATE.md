# Black Ice Editor v1.9.0 integration

This combines the workspace changes in PR #10 with the offline speech/audio implementation already merged through PR #11. The main menu, project browser, editor, timeline and both export engines remain connected to the existing project system.

## Behavior

- Offline English speech-to-text uses the bundled Whisper Base English q5_1 model. Caption review, manual timing, resume and SRT/VTT export remain available under Settings → Speech & captions. Text-to-speech is not included.
- Selected clips expose noise reduction, five-band EQ/preamp and measured loudness normalization. These processors work with source-time volume keyframes, explicit mute and saved legacy fades in preview and both export engines.
- Workspace media categories, shapes, search/list/grid, landmarks, shortcuts, work clock and diagnostics are retained. The visualizer stays disabled and its saved identifiers round-trip.
- Schema 8 combines the two distinct schema-7 feature sets. Both older variants load with defaults for absent fields; older app versions reject schema 8 rather than silently discarding new fields.

## Validation

Application source `fa0be0585843b4deda3e4161397cda62b336365d` passed [run 40223892095](https://github.com/uran39281-bit/bc-1/actions/runs/40223892095): **193 unit tests, 65 Android tests, lint with zero errors and 71 warnings**, plus independent FFmpeg/ffprobe export checks.

Coverage includes project actions and save/reopen, offline English recognition, portrait/landscape navigation, playback/recovery, and processed animated-volume exports through Media3 and ICE Lite. Export checks cover H.264/AAC, HEVC, 60 fps, staged rendering, speed synchronization, rotation and vector output. Tests run on an API 35 x86_64 emulator; physical Samsung/DeX behavior has not been verified.

Reviewed actual screenshots of Home, Audio, Equalizer and Speech & captions in both layouts. Images use deterministic test fixtures. The portrait Audio screenshot precedes preview readiness; the subsequent EQ/caption screenshots show decoded video.

## Independent installer

The previous v1.8 preview uses a different ephemeral signing certificate. [The integration workflow](.github/workflows/bc1-integration-preview.yml) changes only the package suffix and launcher label to produce `com.blackice.bc1.integration`, named **Black Ice Editor Integration**. It preserves installed release, preview and ICE Lite test projects. Transfer a copy using project-and-media bundle export/import; do not remove an existing installation to resolve a signature conflict.

[Packaging run 40224523778](https://github.com/uran39281-bit/bc-1/actions/runs/40224523778), from `f3ddbb9647a601fe07fc8941b4fc84258a572563`, passed unit tests, lint and APK assembly. All 19 DEX/native-library/asset entries match the emulator-tested APK byte-for-byte. The APK contains ARM64 and x86_64 libraries, the verified 59,721,011-byte model, and no INTERNET permission. Media3 remains the default; Settings → Export engine can select ICE Lite.

APK SHA-256: `0ec6036bb93be94d912757f5ae1542275d9729ff61e7b8ebcf5d1afc7da901e2`.

## Limits

English recognition/timing still needs human review. Noise reduction is a soft gate/rumble filter, not source separation; normalization is not a true-peak limiter and must be remeasured after changing processing/volume/speed/fades. Audio-only MP4 export needs a visual layer or caption overlay. Model preparation requires additional storage. Production signing and physical-device verification remain outstanding.

[WORKSPACE-UPDATE.md](WORKSPACE-UPDATE.md) is the historical v1.5 report; its disabled audio-tool list and earlier test counts do not describe v1.9.

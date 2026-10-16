# BC-1 v0.5 QA

Source commit: `559cc1202d551cd0fb389e1037716d104c588cef` on `bc-1-v0.5-motion`.

[Build and Android test run](https://github.com/uran39281-bit/Black-Ice-Emulator/actions/runs/38863083635): 29 JVM unit tests and 16 API35 Android instrumentation tests passed. Lint: zero errors, 25 warnings. Verified layered transforms/opacity keyframes/chroma pixels, seven transition exports, audio offsets/fades and waveform decoding, 1080p H.264/AAC export, cancellation, project/bundle round-trip and traversal rejection, UI resizing and image preview pixels after playback, GPU context restoration and effect bounds. Acceptance MP4 decodes with ffmpeg without errors.

SHA256: `4420b3b81efa5137de90af81bb70237b0cfab55170fce8b98cfb696b2c72729d`.
Certificate SHA256: `3c578586198b9ebadd9496d38e25851ec0e90d918684dc4994e03d1d7f29a29d` (same as retained v0.3/v0.4 Preview).

Known limitation: image compositions may initially show a black paused preview until Play. A first-frame fix is being tested in the v1.0 branch. No physical Snapdragon/DeX performance, thermal or battery verification. Portable architecture is present; RTX backend is not implemented.

#!/usr/bin/env bash
set -euo pipefail
bash .github/scripts/bc1-media-fixtures.sh
(
  cd BC-1
  gradle --no-daemon -Pbc1SideBySide assembleDebug assembleDebugAndroidTest
)
mkdir -p BC-1/app/build/reports/androidTests
adb install -r BC-1/app/build/outputs/apk/debug/app-debug.apk
adb install -r BC-1/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c
adb shell am instrument -w -r com.blackice.bc1.preview.test/androidx.test.runner.AndroidJUnitRunner | tee BC-1/app/build/reports/androidTests/instrumentation.txt
adb logcat -d > BC-1/app/build/reports/androidTests/logcat.txt
# Artifacts are not always retrievable, so surface export failures directly in the job log.
rg -n -A40 'BC1Export\b|MuxerException|Muxer error' BC-1/app/build/reports/androidTests/logcat.txt | rg -v 'WindowManager|SplashScreen|TopTaskTracker|OptProp' | head -150 || true
# Preview player lifecycle and errors (PreviewScrubTest and friends), also kept as a separate report.
rg -n 'BC1Preview|VideoFrameProcessingException|ExoPlaybackException|PlaybackException|CompositionPlayer.*(rror|xception)' BC-1/app/build/reports/androidTests/logcat.txt > BC-1/app/build/reports/androidTests/preview-log.txt || true
echo "Preview log: $(wc -l < BC-1/app/build/reports/androidTests/preview-log.txt) lines (errors and lifecycle first, then the last seeks)"
rg -v ' D BC1Preview' BC-1/app/build/reports/androidTests/preview-log.txt | head -120 || true
rg ' D BC1Preview' BC-1/app/build/reports/androidTests/preview-log.txt | tail -60 || true
mkdir -p BC-1/app/build/reports/androidTests/qa
adb exec-out run-as com.blackice.bc1.preview tar -C files/qa -cf - . > BC-1/app/build/reports/androidTests/qa.tar
tar -xf BC-1/app/build/reports/androidTests/qa.tar -C BC-1/app/build/reports/androidTests/qa
# Repeat the instrumentation failure summary last, so it is visible at the end of the job log.
sed -n '/^There w\(as\|ere\) [0-9]* failure/,/^FAILURES!!!/p' BC-1/app/build/reports/androidTests/instrumentation.txt | head -120 || true
if rg 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' BC-1/app/build/reports/androidTests/instrumentation.txt; then exit 1; fi
rg '^OK \([0-9]+ tests?\)' BC-1/app/build/reports/androidTests/instrumentation.txt

# Validate the exported bytes independently of the Android media stack.
for name in acceptance-1080p-0 acceptance-1080p-1 acceptance-1080p-2 recovery-1080p; do
ACCEPTANCE_MP4="BC-1/app/build/reports/androidTests/qa/${name}.mp4"
ffmpeg -v error -xerror -i "$ACCEPTANCE_MP4" -f null -
ffprobe -v error -show_streams -show_format -of json "$ACCEPTANCE_MP4" > "${ACCEPTANCE_MP4}.ffprobe.json"
python3 - "${ACCEPTANCE_MP4}.ffprobe.json" <<'PYVERIFY'
import json, sys
from pathlib import Path
report=json.loads(Path(sys.argv[1]).read_text())
video=next(s for s in report['streams'] if s['codec_type']=='video')
audio=next(s for s in report['streams'] if s['codec_type']=='audio')
assert (video['codec_name'],video['width'],video['height'])==('h264',1920,1080), video
assert audio['codec_name']=='aac', audio
assert 3 < float(report['format']['duration']) < 5, report['format']
print('Independent MP4 check passed:', sys.argv[1], 'H.264 1920x1080 + AAC; decoded without errors')
PYVERIFY
done

# A/V speed changes must change both presentation duration and exported timestamps.
SPEED_MP4="BC-1/app/build/reports/androidTests/qa/speed-180p.mp4"
ffmpeg -v error -xerror -i "$SPEED_MP4" -f null -
ffprobe -v error -show_streams -show_format -of json "$SPEED_MP4" > "${SPEED_MP4}.ffprobe.json"
python3 - "${SPEED_MP4}.ffprobe.json" <<'PYVERIFY'
import json,sys
report=json.load(open(sys.argv[1]))
video=next(s for s in report['streams'] if s['codec_type']=='video')
audio=next(s for s in report['streams'] if s['codec_type']=='audio')
assert video['codec_name']=='h264' and audio['codec_name']=='aac'
assert 4.8<float(report['format']['duration'])<5.2, report['format']
assert abs(float(video['duration'])-float(audio['duration']))<.2, (video,audio)
print('Independent speed MP4 check passed: synchronized H.264/AAC, five seconds')
PYVERIFY

# ICE Lite (custom engine) export: decoded independently, frame count and A/V sync checked.
CORE_MP4="BC-1/app/build/reports/androidTests/qa/bc-core-1080p.mp4"
ffmpeg -v error -xerror -i "$CORE_MP4" -f null -
ffprobe -v error -count_frames -show_streams -show_format -of json "$CORE_MP4" > "${CORE_MP4}.ffprobe.json"
python3 - "${CORE_MP4}.ffprobe.json" <<'PYVERIFY'
import json,sys
report=json.load(open(sys.argv[1]))
video=next(s for s in report['streams'] if s['codec_type']=='video')
audio=next(s for s in report['streams'] if s['codec_type']=='audio')
assert (video['codec_name'],video['width'],video['height'])==('h264',1920,1080), video
assert audio['codec_name']=='aac' and int(audio['sample_rate'])==48000 and int(audio['channels'])==2, audio
assert 3.3<float(report['format']['duration'])<3.7, report['format']
assert int(video['nb_read_frames'])==105, video['nb_read_frames']
assert abs(float(video['duration'])-float(audio['duration']))<.15, (video['duration'],audio['duration'])
print('Independent ICE Lite MP4 check passed: H.264 1920x1080 105 frames + AAC 48 kHz stereo, synchronized')
PYVERIFY

# ICE Lite 60 fps export of a 120 fps source: exact frame count and rate, decoded independently.
FPS_MP4="BC-1/app/build/reports/androidTests/qa/bc-core-60fps.mp4"
ffmpeg -v error -xerror -i "$FPS_MP4" -f null -
ffprobe -v error -count_frames -show_streams -show_format -of json "$FPS_MP4" > "${FPS_MP4}.ffprobe.json"
python3 - "${FPS_MP4}.ffprobe.json" <<'PYVERIFY'
import json,sys
report=json.load(open(sys.argv[1]))
video=next(s for s in report['streams'] if s['codec_type']=='video')
assert video['codec_name']=='h264' and (video['width'],video['height'])==(640,360), video
assert int(video['nb_read_frames'])==120, video['nb_read_frames']
num,den=map(int,video['avg_frame_rate'].split('/'))
assert abs(num/den-60)<0.5, video['avg_frame_rate']
print('Independent ICE Lite 60 fps check passed: 120 frames at', video['avg_frame_rate'])
PYVERIFY

# ICE Lite HEVC export and multi-pass (staged) export: decoded independently.
for spec in "bc-core-hevc.mp4:hevc:105" "bc-core-staged.mp4:h264:60"; do
IFS=: read -r name codec frames <<<"$spec"
MP4="BC-1/app/build/reports/androidTests/qa/$name"
ffmpeg -v error -xerror -i "$MP4" -f null -
ffprobe -v error -count_frames -show_streams -show_format -of json "$MP4" > "$MP4.ffprobe.json"
python3 - "$MP4.ffprobe.json" "$codec" "$frames" <<'PYVERIFY'
import json,sys
report=json.load(open(sys.argv[1]))
video=next(s for s in report['streams'] if s['codec_type']=='video')
assert video['codec_name']==sys.argv[2], video['codec_name']
assert int(video['nb_read_frames'])==int(sys.argv[3]), video['nb_read_frames']
assert any(s['codec_type']=='audio' and s['codec_name']=='aac' for s in report['streams'])
print('Independent ICE Lite check passed:', sys.argv[1], sys.argv[2], sys.argv[3], 'frames + AAC')
PYVERIFY
done

# Native shape-only projects must export without a dummy source video.
SHAPES_MP4="BC-1/app/build/reports/androidTests/qa/v130-shapes-only.mp4"
ffmpeg -v error -xerror -i "$SHAPES_MP4" -f null -
ffprobe -v error -count_frames -show_streams -show_format -of json "$SHAPES_MP4" > "$SHAPES_MP4.ffprobe.json"
python3 - "$SHAPES_MP4.ffprobe.json" <<'PYVERIFY'
import json,sys
report=json.load(open(sys.argv[1]))
video=next(s for s in report['streams'] if s['codec_type']=='video')
assert video['codec_name']=='h264' and video['height']==180,video
assert int(video['nb_read_frames'])==30,video
assert .9<float(report['format']['duration'])<1.2,report['format']
print('Independent vector-only export check passed: H.264, 180p, 30 frames, one second')
PYVERIFY

# Source orientation exports must decode independently too (pixel positions checked on Android).
for rotation in 0 90 180 270; do
  ffmpeg -v error -xerror -i "BC-1/app/build/reports/androidTests/qa/orientation-${rotation}.mp4" -f null -
  echo "Independent orientation export check passed: ${rotation} degrees"
done

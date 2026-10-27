#!/usr/bin/env bash
set -euo pipefail
(cd BC-1 && gradle --no-daemon -Pbc1SideBySide assembleDebug assembleDebugAndroidTest)
adb install -r BC-1/app/build/outputs/apk/debug/app-debug.apk
adb install -r BC-1/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p v144-focused-qa/qa
adb logcat -c
adb logcat -v threadtime > v144-focused-qa/logcat.txt 2>&1 &
bc1_focused_log=$!
trap 'kill "$bc1_focused_log" 2>/dev/null || true' EXIT
adb shell am instrument -w -r -e class com.blackice.bc1.Workspace144Test,com.blackice.bc1.CanvasShapeExportTest,com.blackice.bc1.HomeBrowserTest com.blackice.bc1.preview.test/androidx.test.runner.AndroidJUnitRunner | tee v144-focused-qa/instrumentation.txt
adb exec-out run-as com.blackice.bc1.preview tar -C files/qa -cf - . > v144-focused-qa/qa.tar
tar -xf v144-focused-qa/qa.tar -C v144-focused-qa/qa
if rg 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' v144-focused-qa/instrumentation.txt; then exit 1; fi
rg '^OK \([0-9]+ tests?\)' v144-focused-qa/instrumentation.txt
for file in v144-focused-qa/qa/v144-*.mp4; do
 ffmpeg -v error -xerror -i "$file" -f null -
 ffprobe -v error -show_streams -show_format -of json "$file" > "$file.ffprobe.json"
 echo "Independent canvas/shape decode passed: $file"
done

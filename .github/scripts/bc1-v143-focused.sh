#!/usr/bin/env bash
set -euo pipefail
(cd BC-1 && gradle --no-daemon -Pbc1SideBySide assembleDebug assembleDebugAndroidTest)
adb install -r BC-1/app/build/outputs/apk/debug/app-debug.apk
adb install -r BC-1/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p v143-focused-qa/qa
adb logcat -c
adb logcat -v threadtime > v143-focused-qa/logcat.txt 2>&1 &
bc1_focused_log=$!
trap 'kill "$bc1_focused_log" 2>/dev/null || true' EXIT
adb shell am instrument -w -r -e class com.blackice.bc1.InstantFillTest,com.blackice.bc1.FillExportTest,com.blackice.bc1.EditorWorkspaceTest,com.blackice.bc1.PhoneMediaSpaceTest,com.blackice.bc1.WorkspaceV2Test com.blackice.bc1.preview.test/androidx.test.runner.AndroidJUnitRunner | tee v143-focused-qa/instrumentation.txt
adb exec-out run-as com.blackice.bc1.preview tar -C files/qa -cf - . > v143-focused-qa/qa.tar
tar -xf v143-focused-qa/qa.tar -C v143-focused-qa/qa
if rg 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' v143-focused-qa/instrumentation.txt; then exit 1; fi
rg '^OK \([0-9]+ tests?\)' v143-focused-qa/instrumentation.txt
for engine in ice media3; do
 ffmpeg -v error -xerror -i "v143-focused-qa/qa/v143-fill-${engine}.mp4" -f null -
 echo "Independent animated-fill decode passed: ${engine}"
done

#!/usr/bin/env bash
set -euo pipefail
bash .github/scripts/bc1-media-fixtures.sh
(cd BC-1 && gradle --no-daemon -Pbc1SideBySide assembleDebug assembleDebugAndroidTest)
mkdir -p phone-baseline
adb install -r BC-1/app/build/outputs/apk/debug/app-debug.apk
adb install -r BC-1/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r -e class com.blackice.bc1.PortraitWideBaselineTest com.blackice.bc1.preview.test/androidx.test.runner.AndroidJUnitRunner | tee phone-baseline/tests.txt
for bc1_root_attempt in 1 2 3; do
  adb root || true
  adb wait-for-device
  if [ "$(adb shell id -u | tr -d '\r')" = 0 ]; then break; fi
done
[ "$(adb shell id -u | tr -d '\r')" = 0 ]
adb pull /data/user/0/com.blackice.bc1.preview/files/qa/. phone-baseline/
if rg 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed' phone-baseline/tests.txt; then exit 1; fi
rg '^OK \([0-9]+ tests?\)' phone-baseline/tests.txt

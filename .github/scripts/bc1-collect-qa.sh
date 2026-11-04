#!/usr/bin/env bash
set -euo pipefail
# Disposable emulator only. adbd may close the requesting connection while restarting as root.
# Verify the resulting UID rather than treating that expected disconnect as lost QA evidence.
bc1_qa_destination=${1:?Pass the QA output directory}
mkdir -p "$bc1_qa_destination"
for bc1_root_attempt in 1 2 3; do
  adb root || true
  adb wait-for-device
  if [ "$(adb shell id -u | tr -d '\r')" = 0 ]; then break; fi
done
if [ "$(adb shell id -u | tr -d '\r')" != 0 ]; then
  echo 'Cannot collect complete emulator QA files: adbd is not running as root.' >&2
  exit 1
fi
adb pull /data/user/0/com.blackice.bc1.preview/files/qa/. "$bc1_qa_destination/"

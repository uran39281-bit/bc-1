#!/usr/bin/env bash
set -euo pipefail
# Runner images already contain package indexes. A stalled optional repository must
# not consume the Android test budget; installation still fails if tools are absent.
bc1_apt_options=(-o Acquire::Retries=2 -o Acquire::http::Timeout=20 -o Acquire::https::Timeout=20)
if ! sudo timeout 180s apt-get "${bc1_apt_options[@]}" update; then
  echo 'Package index refresh timed out; trying the runner’s cached indexes.'
fi
sudo timeout 180s apt-get "${bc1_apt_options[@]}" install -y ffmpeg ripgrep
ffmpeg -version >/dev/null
rg --version >/dev/null

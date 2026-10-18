#!/usr/bin/env bash
set -euo pipefail
mkdir -p BC-1/app/src/androidTest/assets
for color in red blue; do
  frequency=440
  if [ "$color" = blue ]; then frequency=660; fi
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=$color:s=320x180:r=30:d=3" -f lavfi -i "sine=frequency=$frequency:sample_rate=48000:duration=3" -vf "drawbox=x=0:y=0:w=24:h=24:color=white:t=fill" -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 96k -shortest "BC-1/app/src/androidTest/assets/$color.mp4"
done
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=880:sample_rate=48000:duration=1' -c:a pcm_s16le BC-1/app/src/androidTest/assets/music.wav
# 120 fps source for BC Core high-frame-rate tests: red for 1 s, then blue for 1 s.
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=red:s=320x180:r=120:d=1" -f lavfi -i "color=c=blue:s=320x180:r=120:d=1" -f lavfi -i "sine=frequency=550:sample_rate=48000:duration=2" -filter_complex "[0:v][1:v]concat=n=2:v=1:a=0[v]" -map "[v]" -map 2:a -r 120 -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 96k -shortest BC-1/app/src/androidTest/assets/fast120.mp4

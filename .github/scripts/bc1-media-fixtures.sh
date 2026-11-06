#!/usr/bin/env bash
set -euo pipefail
mkdir -p BC-1/app/src/androidTest/assets
for color in red blue; do
  frequency=440
  if [ "$color" = blue ]; then frequency=660; fi
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=$color:s=320x180:r=30:d=3" -f lavfi -i "sine=frequency=$frequency:sample_rate=48000:duration=3" -vf "drawbox=x=0:y=0:w=24:h=24:color=white:t=fill" -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 96k -shortest "BC-1/app/src/androidTest/assets/$color.mp4"
done
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=880:sample_rate=48000:duration=1' -c:a pcm_s16le BC-1/app/src/androidTest/assets/music.wav
# 120 fps source for ICE Lite high-frame-rate tests: red for 1 s, then blue for 1 s.
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=red:s=320x180:r=120:d=1" -f lavfi -i "color=c=blue:s=320x180:r=120:d=1" -f lavfi -i "sine=frequency=550:sample_rate=48000:duration=2" -filter_complex "[0:v][1:v]concat=n=2:v=1:a=0[v]" -map "[v]" -map 2:a -r 120 -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 96k -shortest BC-1/app/src/androidTest/assets/fast120.mp4
# Flat colour sources for blend-mode checks (warm over cool), six seconds each, video only.
for spec in warm:0xC08040 cool:0x4080C0; do
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=${spec#*:}:s=320x180:r=30:d=6" -c:v libx264 -pix_fmt yuv420p "BC-1/app/src/androidTest/assets/${spec%%:*}.mp4"
done
# Numbered frames for reverse tests: luma = 4*frame+8, so every frame's position is readable from its brightness.
# B-frames and a short GOP exercise presentation-order handling; a 0.2 s tone at the very start marks audio direction.
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=black:s=320x180:r=30:d=2,format=yuv420p,geq=lum='clip(4*N+8\,0\,255)':cb=128:cr=128" -f lavfi -i "aevalsrc='sin(2*PI*440*t)*lt(t\,0.2)':s=48000:d=2" -c:v libx264 -pix_fmt yuv420p -g 15 -bf 2 -c:a aac -b:a 96k -shortest BC-1/app/src/androidTest/assets/numbered.mp4

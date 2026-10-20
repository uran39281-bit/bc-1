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
# Speech for auto-captions tests: the whisper.cpp JFK sample (11 s, public domain) at 48 kHz stereo AAC
# behind 1 s of silence, over a grey picture; and a music bed that must not be captioned.
ffmpeg -hide_banner -loglevel error -y -f lavfi -i "color=c=gray:s=320x180:r=30:d=12" -f lavfi -i "anullsrc=r=48000:cl=stereo:d=1" -i BC-1/third_party/whisper.cpp/samples/jfk.wav \
  -filter_complex "[2:a]aresample=48000,aformat=channel_layouts=stereo[s];[1:a][s]concat=n=2:v=0:a=1[a]" -map 0:v -map "[a]" -c:v libx264 -pix_fmt yuv420p -c:a aac -b:a 128k -shortest BC-1/app/src/androidTest/assets/speech.mp4

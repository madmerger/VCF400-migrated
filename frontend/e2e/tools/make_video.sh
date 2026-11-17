#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p ../video .e2e
node e2e/tools/video_results.mjs prepare
ffmpeg -hide_banner -loglevel warning -y -f concat -safe 0 -i .e2e/concat.txt -vf 'scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1' -r 25 -an -c:v libx264 -preset fast -crf 24 -pix_fmt yuv420p -movflags +faststart .e2e/combined.mp4
mv .e2e/combined.mp4 ../video/2_Web_React.mp4
node e2e/tools/video_results.mjs finalize
ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height,pix_fmt -show_entries format=duration,size -of json ../video/2_Web_React.mp4

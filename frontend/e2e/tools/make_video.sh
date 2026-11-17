#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p ../video .e2e
node --input-type=module <<'JS'
import {readFileSync,writeFileSync,existsSync} from 'node:fs';
import {resolve} from 'node:path';
const results=JSON.parse(readFileSync('test-results/web_e2e_results.json')).results;
if(results.length!==58||results.some(r=>r.status!=='合格'||!existsSync(resolve('..',r.recording))))throw Error('58 successful recordings required');
writeFileSync('.e2e/concat.txt',results.map(r=>`file '${resolve('..',r.recording).replaceAll("'","'\\''")}'`).join('\n'));
JS
ffmpeg -hide_banner -loglevel warning -y -f concat -safe 0 -i .e2e/concat.txt -vf 'scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2,setsar=1' -r 25 -an -c:v libx264 -preset fast -crf 24 -pix_fmt yuv420p -movflags +faststart ../video/2_Web_React.mp4
ffprobe -v error -select_streams v:0 -show_entries stream=codec_name,width,height,pix_fmt -show_entries format=duration,size -of json ../video/2_Web_React.mp4

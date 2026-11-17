import {existsSync, readFileSync, writeFileSync} from 'node:fs';
import {resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

export function writeReport(report,path='test-results/web_e2e_results.json') {
  writeFileSync(path,JSON.stringify(report,null,2)+'\n');
  const {summary,results}=report;
  writeFileSync(path.replace(/\.json$/,'.md'),`# Web E2E結果\n\n全${summary.total}件 / 合格 ${summary.passed} / 不合格 ${summary.failed}\n\n|ケース|合否|期待値|実際値|動画|\n|---|---|---|---|---|\n`+results.map(r=>`|${r.caseId}|${r.status}|${JSON.stringify(r.expected)}|${JSON.stringify(r.actual)}|${r.video}|`).join('\n')+'\n');
}

function validate(report) {
  if(!report.runId||report.results.length!==58||report.results.some(r=>r.status!=='合格'||!r.recording||!existsSync(resolve('..',r.recording))))throw Error('58 successful current-run recordings required');
}

export function prepareVideo(path) {
  const report=JSON.parse(readFileSync(path,'utf8'));
  for(const result of report.results)result.video='';
  delete report.videoGeneratedAt;
  writeReport(report,path);
  validate(report);
  return {runId:report.runId,concat:report.results.map(r=>`file '${resolve('..',r.recording).replaceAll("'","'\\''")}'`).join('\n')};
}

export function finalizeVideo(path,runId,video='video/2_Web_React.mp4') {
  const report=JSON.parse(readFileSync(path,'utf8'));
  validate(report);
  if(report.runId!==runId||!existsSync(resolve('..',video)))throw Error('Video does not belong to current result run');
  report.videoGeneratedAt=new Date().toISOString();
  for(const result of report.results)result.video=video;
  writeReport(report,path);
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const path='test-results/web_e2e_results.json';
  if(process.argv[2]==='prepare') {
    const {runId,concat}=prepareVideo(path);
    writeFileSync('.e2e/concat.txt',concat);writeFileSync('.e2e/video-run-id',runId);
  }else if(process.argv[2]==='finalize')finalizeVideo(path,readFileSync('.e2e/video-run-id','utf8'));
  else throw Error('Expected prepare or finalize');
}

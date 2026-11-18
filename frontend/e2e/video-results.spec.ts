import {test,expect} from '@playwright/test';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {resolve} from 'node:path';
import {prepareVideo,finalizeVideo,writeReport} from './tools/video_results.mjs';

test('Combined video is attached only after successful current-run generation',()=>{
  mkdirSync('.e2e',{recursive:true});const dir=mkdtempSync(resolve('.e2e/video-test-'));
  try{
    const path=resolve(dir,'results.json');const recording=resolve(dir,'case.webm');const video=resolve(dir,'combined.mp4');
    writeFileSync(recording,'recording');writeFileSync(video,'video');
    const results=Array.from({length:58},(_,i)=>({caseId:`E2E-${i}`,status:'合格',recording,video:''}));
    const report={runId:'current',summary:{total:58,passed:58,failed:0},results};
    writeReport(report,path);expect(JSON.parse(readFileSync(path,'utf8')).results.every((r:{video:string})=>r.video==='')).toBeTruthy();
    const prepared=prepareVideo(path);expect(prepared.concat.split('\n')).toHaveLength(58);
    expect(()=>finalizeVideo(path,'older',video)).toThrow('current result run');
    expect(()=>finalizeVideo(path,prepared.runId,resolve(dir,'missing.mp4'))).toThrow('current result run');
    finalizeVideo(path,prepared.runId,video);
    expect(JSON.parse(readFileSync(path,'utf8')).results.every((r:{video:string})=>r.video===video)).toBeTruthy();
    expect(readFileSync(path.replace('.json','.md'),'utf8')).toContain(video);
    prepareVideo(path);expect(JSON.parse(readFileSync(path,'utf8')).results.every((r:{video:string})=>r.video==='')).toBeTruthy();
    for(const invalid of [[],results.map((r,i)=>({...r,status:i===0?'不合格':'合格',video})),results.map(r=>({...r,recording:''})),results.map(r=>({...r,recording:resolve(dir,'missing.webm')}))]){
      writeReport({...report,results:invalid},path);expect(()=>prepareVideo(path)).toThrow('58 successful current-run recordings required');
      expect(JSON.parse(readFileSync(path,'utf8')).results.every((r:{video:string})=>r.video==='')).toBeTruthy();
    }
  }finally{rmSync(dir,{recursive:true,force:true});}
});

import type { Reporter, TestCase, TestResult } from '@playwright/test/reporter';
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { resolve, relative } from 'node:path';
type Json = null | boolean | number | string | Json[] | {[key:string]:Json};
function canonical(value:Json):string {
  if(Array.isArray(value))return `[${value.map(canonical).join(',')}]`;
  if(value&&typeof value==='object')return `{${Object.keys(value).sort().map(k=>`${JSON.stringify(k)}:${canonical(value[k])}`).join(',')}}`;
  return JSON.stringify(value);
}
const document=JSON.parse(readFileSync(resolve('../tests/cases.json'),'utf8'));
export default class WebReporter implements Reporter {
  private results:Record<string,unknown>[]=[];
  onTestEnd(test:TestCase,result:TestResult){
    const id=test.title.match(/E2E-\d+/)?.[0];
    const scenario=document.cases.find((c:{id:string})=>c.id===id);
    if(!scenario)return;
    const fingerprint=createHash('sha256').update(canonical({inputs:scenario.inputs,expected:scenario.expected,setup:scenario.setup,baseline:document.baseline})).digest('hex');
    const video=result.attachments.find(a=>a.contentType==='video/webm');
    const actual=result.attachments.find(a=>a.name==='actual');
    this.results.push({caseId:id,name:scenario.name,fingerprint,executedAt:new Date(result.startTime).toISOString(),environment:'Web (React/Chromium)',status:result.status==='passed'?'合格':'不合格',expected:scenario.expected,actual:actual?.body?JSON.parse(actual.body.toString()):{},video:'video/2_Web_React.mp4',recording:video?.path?relative(resolve('..'),video.path):'',notes:result.status==='passed'?'Chromium UI・実H2・画面操作と保存件数を検証':'失敗。Playwright出力を確認'});
  }
  onEnd(){
    this.results.sort((a,b)=>String(a.caseId).localeCompare(String(b.caseId)));
    const passed=this.results.filter(r=>r.status==='合格').length;
    const summary={total:this.results.length,passed,failed:this.results.length-passed};
    mkdirSync('test-results',{recursive:true});
    writeFileSync('test-results/web_e2e_results.json',JSON.stringify({summary,results:this.results},null,2)+'\n');
    writeFileSync('test-results/web_e2e_results.md',`# Web E2E結果\n\n全${summary.total}件 / 合格 ${passed} / 不合格 ${summary.failed}\n\n|ケース|合否|期待値|実際値|動画|\n|---|---|---|---|---|\n`+this.results.map(r=>`|${r.caseId}|${r.status}|${JSON.stringify(r.expected)}|${JSON.stringify(r.actual)}|${r.video}|`).join('\n')+'\n');
  }
}

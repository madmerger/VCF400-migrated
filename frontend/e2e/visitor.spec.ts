import { test, expect, type Page } from '@playwright/test';
import { readFileSync, mkdirSync } from 'node:fs';
import { resolve } from 'node:path';
import caseDocument from '../../tests/cases.json' with { type: 'json' };

type Scenario = typeof caseDocument.cases[number];
type Values = Record<string, unknown>;
const fixtureHeaders={'X-E2E-Key':process.env.VCF_E2E_KEY!};
const backend=`http://127.0.0.1:${process.env.VCF_BACKEND_PORT||8080}`;
const ledger=JSON.parse(readFileSync(resolve('../spec/tools/spec_data.json'),'utf8'))['画面台帳'] as string[][];
async function responseTo(page:Page,path:string,perform:()=>Promise<unknown>){
  const waiting=page.waitForResponse(r=>r.url().includes('/api'+path));
  await perform();const response=await waiting;
  expect(response.headers()['cache-control']).toBe('no-store');
  return await response.json() as Values;
}
async function menu(page:Page,number:number){
  await page.getByTestId(`menu-${number}`).click();
  await expect(page.getByTestId('entry-form').or(page.getByTestId('learn-card')).or(page.getByTestId('completion'))).toBeVisible();
  await expect(page.locator('.workspace')).toHaveAttribute('aria-busy','false');
}
async function kiosk(page:Page,profile:string){
  await page.getByTestId('nav-exhibits').click();
  await page.getByTestId(`exhibit-${profile}`).getByRole('button').click();
  await expect(page.getByTestId('kiosk-selection')).toBeVisible();
}
async function openAuth(page:Page,profile='EXH001'){
  await kiosk(page,profile);await page.getByTestId('kiosk-selection').fill('7');
  await page.getByRole('button',{name:'選択',exact:true}).click();
  await expect(page.getByTestId('password')).toBeVisible();
}
async function fill(page:Page,values:Values,names:string[]){
  for(const name of names)await page.getByTestId(name).fill(String(values[name]??''));
}
async function learnTo(page:Page,from:number){
  if(from===8){await page.goto('/?page=8&collection=global');await expect(page.getByTestId('page-number')).toHaveText('PAGE 08');return;}
  await menu(page,1);
  if(from>=2){await page.keyboard.press('F5');await expect(page.getByTestId('page-number')).toHaveText('PAGE 02');}
  if(from>=5){await page.keyboard.press('F5');await expect(page.getByTestId('page-number')).toHaveText('PAGE 05');}
  if(from>=7){await page.keyboard.press('F5');await expect(page.getByTestId('call-title')).toBeVisible();await page.getByTestId('call-return').click();await expect(page.getByTestId('page-number')).toHaveText('PAGE 07');}
}

for(const scenario of caseDocument.cases){
  test(`${scenario.id} ${scenario.name}`,async({page,request},testInfo)=>{
    const c=scenario as Scenario;
    const input=c.inputs as Values;
    const expected=c.expected as Values;
    const reset=await request.post(`${backend}/api/e2e/reset`,{headers:fixtureHeaders,data:c.setup});
    expect(reset.ok()).toBeTruthy();const before=await reset.json();
    await page.addInitScript(({id,name})=>{
      document.addEventListener('DOMContentLoaded',()=>{
        const banner=document.createElement('div');banner.id='e2e-case-banner';banner.textContent=`${id} · ${name}`;
        Object.assign(banner.style,{position:'fixed',top:'0',left:'0',right:'0',zIndex:'9999',padding:'8px 18px',background:'#172b34',color:'white',font:'12px sans-serif',pointerEvents:'none'});
        document.body.style.paddingTop='32px';
        document.body.appendChild(banner);
      });
    },{id:c.id,name:c.name});
    await page.goto('/');await expect(page.getByTestId('screen-title')).toHaveText('AS/400 DEMO MENU');
    let actual:Values={};
    if(c.function==='投票'){
      if(input.profile){await kiosk(page,String(input.profile));actual=await responseTo(page,'/vote',()=>page.getByTestId('kiosk-1').click());}
      else actual=await responseTo(page,'/vote',()=>page.getByTestId('menu-11').click());
      if(input.open){await expect(page.getByTestId('exhibit')).toHaveValue(String(expected.exhibit));await expect(page.getByTestId('exhibit')).toHaveAttribute('readonly','');}
      else if(input.action==='F12'){
        await fill(page,{badge:1002,exhibit:'EXH001',award:1},['badge','exhibit','award']);
        await page.keyboard.press('F12');await expect(page.getByTestId('screen-title')).toHaveText('AS/400 DEMO MENU');actual={saved:false,returned:true};
      }else if(input.selectMenuOption){await expect(page.getByTestId('badge')).toHaveCount(0);}
      else {await fill(page,input,['badge','exhibit','award']);actual=await responseTo(page,'/vote',()=>page.keyboard.press('F5'));}
    }else if(c.function==='ゲストブック投稿'){
      if(input.profile){await kiosk(page,String(input.profile));actual=await responseTo(page,'/guestbook/form',()=>page.getByTestId('kiosk-3').click());}
      else actual=await responseTo(page,'/guestbook/form',()=>page.getByTestId('menu-12').click());
      if(input.open){await expect(page.getByTestId('exhibit')).toHaveValue(String(expected.exhibit));await expect(page.getByTestId('exhibit')).toHaveAttribute('readonly','');}
      else if(input.action==='F12'){
        await fill(page,{name:'Visitor',exhibit:'EXH001',comment:'Cancelled'},['name','exhibit','comment']);
        await page.getByTestId('key-F12').click();await expect(page.getByTestId('screen-title')).toHaveText('AS/400 DEMO MENU');actual={saved:false,returned:true};
      }else {await fill(page,input,['name','exhibit','comment']);actual=await responseTo(page,'/guestbook',()=>page.getByTestId('key-F5').click());}
    }else if(c.function==='ゲストブック閲覧'){
      if(input.profile){await kiosk(page,String(input.profile));actual=await responseTo(page,'/guestbook/1',()=>page.getByTestId('kiosk-4').click());}
      else actual=await responseTo(page,'/guestbook/1',()=>page.getByTestId('menu-13').click());
      if(!input.open){await page.getByTestId('id').fill(String(input.id));actual=await responseTo(page,`/guestbook/${input.id}`,()=>page.keyboard.press('F5'));}
      for(const [key,id] of Object.entries({name:'comment-name',comment:'comment-text',title:'comment-title',CMTID:'comment-id'}))if(key in expected)await expect(page.getByTestId(id)).toHaveText(String(expected[key]));
      if('displayedTotal' in expected)await expect(page.getByTestId('displayed-total')).toHaveText(`Comments: ${expected.displayedTotal}`);
    }else if(c.function==='LEARN/400'){
      if(input.open){
        if(input.open==='global')actual=await responseTo(page,'/learn/global',()=>page.getByTestId('menu-1').click());
        else {await kiosk(page,String(input.open));actual=await responseTo(page,`/learn/${input.open}`,()=>page.getByTestId('kiosk-2').click());}
      }else{
        await learnTo(page,Number(input.from||1));
        actual=await responseTo(page,'/learn/global/resolve',()=>page.keyboard.press(String(input.action)));
        if(actual.callScreen){await expect(page.getByTestId('call-title')).toHaveText('VCF/400 - Credits and Copyright');await page.getByTestId('call-return').click();await expect(page.getByTestId('page-number')).toHaveText('PAGE 07');}
      }
      if('page' in expected)await expect(page.getByTestId('page-number')).toHaveText(`PAGE ${String(expected.page).padStart(2,'0')}`);
      if('content' in expected)await expect(page.getByTestId('learn-content')).toHaveText(String(expected.content));
      if(expected.ended)await expect(page.getByTestId('learn-ended')).toBeVisible();
      if(expected.allowForward===false){await expect(page.getByTestId('learn-eof')).toBeVisible();await expect(page.getByTestId('key-F5')).toBeDisabled();}
      if(expected.validationError)await expect(page.getByTestId('error-row')).toHaveText(String(actual.message));
    }else if(c.function==='キオスク'&& !('password' in input)){
      await page.getByTestId('nav-exhibits').click();
      actual=await responseTo(page,`/kiosk/${input.profile}`,()=>page.getByTestId(`exhibit-${input.profile}`).getByRole('button').click());
      for(const n of expected.visibleOptions as number[])await expect(page.getByTestId(`kiosk-${n}`)).toBeVisible();
      for(const n of (expected.blockedOptions||[]) as number[])await expect(page.getByTestId(`kiosk-${n}`)).toHaveCount(0);
      await expect(page.getByTestId('kiosk-7')).toHaveCount(0);await expect(page.getByText(/^7\s/)).toHaveCount(0);
    }else if(c.function==='管理者認証'||c.function==='キオスク'){
      await openAuth(page,String(input.profile||'EXH001'));
      const password=input.password==='${VCF_SEED_ADMPSWRD}'?process.env.VCF_SEED_ADMPSWRD!:String(input.password);
      await page.getByTestId('password').fill(password);
      actual=await responseTo(page,'/admin/auth',()=>page.getByTestId('auth-submit').click());
      if(actual.authenticated){
        await expect(page.getByTestId('voting-status')).toHaveText('受付中 / Open');
        await page.getByTestId('toggle-voting').click();await expect(page.getByTestId('voting-status')).toHaveText('受付終了 / Closed');
        await page.getByTestId('return-menu').click();await page.getByTestId('kiosk-1').click();
        await expect(page.getByTestId('screen-title')).toHaveText('The voting period has ended and you can no longer vote.');
        await expect(page.getByTestId('badge')).toHaveCount(0);
      }else {await expect(page.getByTestId('auth-rejected')).toBeVisible();await expect(page.getByTestId('password')).toHaveValue('');}
      delete actual.token;
    }else if(c.function==='展示一覧'){
      actual=await responseTo(page,'/exhibits',()=>page.getByTestId('nav-exhibits').click());
      await expect(page.locator('[data-testid^="exhibit-"]')).toHaveCount(Number(expected.count));
      for(const id of expected.ids as string[])await expect(page.getByTestId(`exhibit-${id}`)).toBeVisible();
      if(expected.count===0)await expect(page.getByTestId('empty')).toBeVisible();
    }else if(c.function==='設定'){
      actual=await responseTo(page,'/settings',()=>page.getByTestId('nav-settings').click());
      await expect(page.getByTestId('voting-status')).toHaveText('受付中 / Open');
      await expect(page.getByTestId('toggle-voting')).toHaveCount(0);
      await expect(page.locator('body')).not.toContainText(process.env.VCF_SEED_ADMPSWRD!);
    }else if(input.operation==='compareLedger'){
      actual=await verifyLedger(page,input.screens as string[]);
    }
    if(actual.screenTitle)expect(ledger.map(row=>row[3])).toContain(actual.screenTitle);
    if(expected.error||expected.errorDisplay)await expect(page.getByTestId('error-row')).toHaveText(String(expected.errorDisplay||expected.error));
    if(expected.saved===true)await expect(page.getByTestId('completion')).toBeVisible();
    if(expected.screen)await expect(page.getByTestId('screen-title')).toHaveText(String(actual.screenTitle||actual.screen));
    if(expected.empty)await expect(page.getByTestId('empty')).toBeVisible();
    const captured:Values={};
    for(const [key,value] of Object.entries(expected)){
      if(key==='note')continue;
      expect(actual[key],`${c.id} ${key}`).toEqual(value);captured[key]=actual[key];
    }
    const counts=await request.get(`${backend}/api/e2e/counts`,{headers:fixtureHeaders});
    const after=await counts.json();
    expect(after.votes).toBe(before.votes+(c.function==='投票'&&expected.saved===true?1:0));
    expect(after.comments).toBe(before.comments+(c.function==='ゲストブック投稿'&&expected.saved===true?1:0));
    if(['E2E-001','E2E-003','E2E-027','E2E-037','E2E-048','E2E-058'].includes(c.id)){
      mkdirSync('test-results/screenshots',{recursive:true});
      await page.screenshot({path:`test-results/screenshots/${c.id}.png`,fullPage:true});
    }
    await testInfo.attach('actual',{body:Buffer.from(JSON.stringify(captured)),contentType:'application/json'});
  });
}

async function verifyLedger(page:Page,requiredScreens:string[]){
  const visited=new Set<string>();
  async function checkTitle(screen:string,title:string,source=true){
    await expect(page.getByTestId('screen-title')).toHaveText(title);
    if(source)expect(ledger.map(row=>row[3])).toContain(title);
    visited.add(screen);
  }
  const menu=await (await page.request.get('/api/menu')).json();
  for(const item of menu.items){
    await expect(page.getByTestId(`menu-${item.number}`)).toContainText(item.title);
    await expect(page.getByTestId(`menu-${item.number}`)).toContainText(String(item.number).padStart(2,'0'));
  }
  const routes:Record<string,()=>Promise<void>>={
    VCFMAIN:async()=>{await page.getByTestId('nav-menu').click();await expect(page.getByTestId('screen-title')).toHaveText('AS/400 DEMO MENU');},
    VOTE1:()=>menuPage(page,11),ADDCMT:()=>menuPage(page,12),READCMT:()=>menuPage(page,13),MAIN:()=>menuPage(page,1)
  };
  const api:Record<string,string>={VCFMAIN:'/menu',VOTE1:'/vote',ADDCMT:'/guestbook/form',READCMT:'/guestbook/1',MAIN:'/learn/global'};
  for(const [screen,open] of Object.entries(routes)){
    await open();const info=await (await page.request.get('/api'+api[screen])).json();
    await expect(page.getByTestId('screen-title')).toHaveText(info.screenTitle);
    expect(ledger.map(row=>row[3])).toContain(info.screenTitle);
    visited.add(screen);
    for(const row of ledger.filter(r=>r[2]==='Fキー')){
      const target=row[1]==='VCFMAIN'?'VCFMAIN':row[1].split(' → ').at(-1)==='LEARN/400'?'MAIN':row[1].split(' → ').at(-1);
      if(target!==screen)continue;
      const [key,...role]=row[3].split(' ');
      await expect(page.getByTestId(`key-${key}`)).toHaveText(key+role.join(' '));
    }
    for(const axis of ['row','column','length'])if(info.errorPosition)await expect(page.getByTestId('error-row')).toHaveAttribute(`data-${axis}`,String(info.errorPosition[axis]));
    if(info.inputOrder?.length){const inputs=await page.locator('.entry-form input,.entry-form textarea').evaluateAll(nodes=>nodes.map(n=>n.getAttribute('data-testid')));expect(inputs).toEqual(info.inputOrder);}
  }
  await page.getByTestId('nav-menu').click();await expect(page.getByTestId('screen-title')).toHaveText('AS/400 DEMO MENU');
  await page.keyboard.press('F3');await expect(page.getByTestId('screen-title')).toHaveText('VCF/400 - How to Navigate');
  visited.add('INTERTEST');
  await kiosk(page,'EXH001');await checkTitle('MENU','WELCOME TO...');
  for(const n of [1,2,3,4])await expect(page.getByTestId(`kiosk-${n}`)).toBeVisible();
  await expect(page.getByTestId('kiosk-7')).toHaveCount(0);
  await page.getByTestId('kiosk-selection').fill('7');await page.getByRole('button',{name:'選択',exact:true}).click();
  await checkTitle('ADMPSWRD','Are you sure you want to exit the kiosk?');
  await expect(page.getByTestId('password')).toHaveAttribute('type','password');
  await page.getByTestId('password').fill(process.env.VCF_SEED_ADMPSWRD!);await page.getByTestId('auth-submit').click();
  await checkTitle('SETTINGS','Visitor Settings',false);
  await page.getByTestId('toggle-voting').click();await expect(page.getByTestId('voting-status')).toHaveText('受付終了 / Closed');
  await page.getByTestId('nav-menu').click();await page.getByTestId('menu-11').click();
  await checkTitle('ENDOFCON','The voting period has ended and you can no longer vote.');
  await expect(page.getByTestId('badge')).toHaveCount(0);
  await page.getByTestId('nav-settings').click();await page.getByTestId('toggle-voting').click();
  await expect(page.getByTestId('voting-status')).toHaveText('受付中 / Open');
  await menuPage(page,11);await fill(page,{badge:1002,exhibit:'EXH001',award:1},['badge','exhibit','award']);
  const vote=await responseTo(page,'/vote',()=>page.getByTestId('key-F5').click());
  expect(vote.saved).toBe(true);await checkTitle('VOTEEND',String(vote.screenTitle));
  await menuPage(page,11);await fill(page,{badge:1003,exhibit:'DAVE',award:1},['badge','exhibit','award']);
  await page.getByTestId('key-F5').click();await checkTitle('DAVE','DAVE',false);
  await menuPage(page,12);await fill(page,{name:'Ledger',exhibit:'EXH001',comment:'Screen verification'},['name','exhibit','comment']);
  await page.getByTestId('key-F5').click();await checkTitle('ENDCMT','Thank you for commenting on this exhibit! Your Comment ID Is:');
  await expect(page.getByTestId('new-comment-id')).toHaveText('6');
  await page.getByTestId('nav-menu').click();await expect(page.getByTestId('menu-1')).toBeVisible();
  await learnTo(page,5);await page.getByTestId('key-F5').click();
  await expect(page.getByTestId('call-title')).toHaveText('VCF/400 - Credits and Copyright');visited.add('CREDITS');
  expect(ledger.map(row=>row[3])).toContain('VCF/400 - Credits and Copyright');
  await page.getByTestId('call-return').click();await expect(page.getByTestId('page-number')).toHaveText('PAGE 07');
  await page.getByTestId('nav-menu').click();await page.getByTestId('menu-80').click();
  await checkTitle('SIGNOFF',menu.items.find((item:{number:number})=>item.number===80).title,false);
  await page.route('**/api/exhibits',route=>route.abort());
  await page.getByTestId('nav-exhibits').click();
  await expect(page.getByTestId('error-row')).toHaveText('接続できません。接続を確認して再試行してください。');
  await page.unroute('**/api/exhibits');
  await page.getByTestId('nav-menu').click();await expect(page.getByTestId('screen-title')).toHaveText('AS/400 DEMO MENU');
  expect([...visited].sort()).toEqual([...requiredScreens].sort());
  expect((await page.request.post(`${backend}/api/e2e/reset`,{headers:fixtureHeaders,data:{}})).ok()).toBeTruthy();
  return {matchesAllInScopeScreens:true};
}
async function menuPage(page:Page,number:number){await page.getByTestId('nav-menu').click();await expect(page.getByTestId(`menu-${number}`)).toBeVisible();await menu(page,number);}

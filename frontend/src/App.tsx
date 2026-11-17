import { useEffect, useRef, useState } from 'react';

type Screen = {
  screen?: string; screenTitle?: string; banner?: string; fKeys?: Record<string, string>;
  error?: string; errorDisplay?: string; message?: string; errorPosition?: {row: number; column: number; length: number};
  items?: {number: number; title: string; group: string; action: string; implemented: boolean}[];
  exhibits?: Exhibit[]; exhibit?: Exhibit | string; exhibitEditable?: boolean;
  CMTID?: number; displayedTotal?: number; name?: string; comment?: string; title?: string;
  page?: number; content?: string; collection?: string; backPage?: number; ended?: boolean; empty?: boolean;
  allowForward?: boolean; callScreen?: Screen; visibleOptions?: number[]; blockedOptions?: number[];
  authenticated?: boolean; token?: string; ALWVOTE?: string;
};
type Exhibit = {EXHUSRPRF: string; EXHBITOR: string; EXHBTITLE: string; EXHBDESC: string};
type View = 'menu'|'vote'|'post'|'read'|'learn'|'kiosk'|'auth'|'settings'|'exhibits'|'info';
const labels: Record<string, string> = {badge:'Badge Number',exhibit:'Exhibit ID',award:'Award ID',name:'Your Name',comment:'Your Comment',id:'Comment ID',password:'Password'};

export function App() {
  const [view,setView] = useState<View>('menu');
  const [data,setData] = useState<Screen>({});
  const [fields,setFields] = useState<Record<string,string>>({});
  const [profile,setProfile] = useState('MM2024');
  const [collection,setCollection] = useState('global');
  const [busy,setBusy] = useState(false);
  const [networkError,setNetworkError] = useState('');
  const [call,setCall] = useState<Screen>();
  const token = useRef('');
  const action = useRef<(key:string)=>void>(()=>{});
  const endpoint = (path:string, p=profile) => `${path}${path.includes('?')?'&':'?'}profile=${encodeURIComponent(p)}`;
  async function request(path:string, method='GET', body?:unknown):Promise<Screen> {
    setBusy(true);setNetworkError('');
    try {
      const response=await fetch('/api'+path,{method,cache:'no-store',headers:{'Content-Type':'application/json',...(token.current?{Authorization:`Bearer ${token.current}`}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
      const result=await response.json();
      if(!response.ok){setNetworkError(result.message || '要求を処理できません');return result;}
      return result;
    } catch {setNetworkError('接続できません。接続を確認して再試行してください。');return {};}
    finally {setBusy(false);}
  }
  async function logout() {
    try {if(token.current)await request('/admin/logout','POST');}
    finally {token.current='';setFields({});setCall(undefined);}
  }
  async function signOff() {
    await logout();setView('info');setData({screen:'SIGNOFF',screenTitle:'Sign Off the System'});
  }
  async function open(next:View, p=profile, c=collection) {
    setCall(undefined);setFields({});setProfile(p);setCollection(c);setView(next);
    const paths:Partial<Record<View,string>>={menu:'/menu',vote:endpoint('/vote',p),post:endpoint('/guestbook/form',p),read:endpoint('/guestbook/1',p),learn:`/learn/${encodeURIComponent(c)}`,kiosk:`/kiosk/${encodeURIComponent(p)}`,exhibits:'/exhibits',settings:'/settings'};
    if(next==='auth'){setData({screen:'ADMPSWRD',screenTitle:'Are you sure you want to exit the kiosk?'});return;}
    if(paths[next]){
      const result=await request(paths[next]!);setData(result);
      if(next==='vote'||next==='post')setFields({exhibit:typeof result.exhibit==='string'?result.exhibit:''});
    }
  }
  useEffect(()=>{
    const params=new URLSearchParams(window.location.search);
    if(params.get('page')){
      setView('learn');setCollection(params.get('collection')||'global');
      void request(`/learn/${encodeURIComponent(params.get('collection')||'global')}/pages/${Number(params.get('page'))}`).then(setData);
    }else void open('menu');
  },[]);
  useEffect(()=>{
    const listener=(event:KeyboardEvent)=>{if(['F3','F5','F8','F12'].includes(event.key)){event.preventDefault();action.current(event.key);}};
    window.addEventListener('keydown',listener);return ()=>window.removeEventListener('keydown',listener);
  },[]);
  async function submit() {
    if(busy)return;
    if(view==='vote')setData(await request(endpoint('/vote'),'POST',{badge:Number(fields.badge||0),exhibit:fields.exhibit||'',award:Number(fields.award||0)}));
    if(view==='post')setData(await request(endpoint('/guestbook'),'POST',{name:fields.name||'',exhibit:fields.exhibit||'',comment:fields.comment||''}));
    if(view==='read')setData(await request(endpoint(`/guestbook/${Number(fields.id||0)}`)));
    if(view==='auth'){
      const result=await request('/admin/auth','POST',{password:fields.password||''});setFields({});
      if(result.authenticated){token.current=result.token!;await open('settings');}else setData({...data,authenticated:false});
    }
  }
  async function keypress(key:string){
    if(busy||call)return;
    if(key==='F3'&&['settings','auth'].includes(view)){
      await logout();await open(profile==='MM2024'?'menu':'kiosk');return;
    }
    if(key==='F12'&&['vote','post','read'].includes(view)){await open(profile==='MM2024'?'menu':'kiosk');return;}
    if(view==='learn'&&data.fKeys?.[key]){
      const result=await request(`/learn/${encodeURIComponent(collection)}/resolve`,'POST',{from:data.page||0,action:key,backPage:data.backPage||0});
      if(result.message)return;
      if(result.callScreen)setCall(result.callScreen);
      setData(result.allowForward===false?{...result,page:data.page}:result);return;
    }
    if(key==='F5'&&data.fKeys?.F5)await submit();
    if(key==='F3'&&view==='menu'){setView('info');setData({screen:'INTERTEST',screenTitle:'VCF/400 - How to Navigate',content:'番号を選んで機能を開き、画面下部のFキーボタンまたはキーボードで操作します。'});}
  }
  action.current=(key)=>{void keypress(key);};
  function field(name:string,maxLength:number,numeric=false){
    return <label className="field" key={name}>{labels[name]}<span className="field-hint">{numeric?'数字を入力':`最大${maxLength}文字`}</span>{name==='comment'?<textarea data-testid={name} maxLength={maxLength} value={fields[name]||''} onChange={e=>setFields({...fields,[name]:e.target.value})}/>:<input data-testid={name} type={name==='password'?'password':'text'} autoComplete="off" inputMode={numeric?'numeric':undefined} pattern={numeric?'[0-9]*':undefined} maxLength={maxLength} readOnly={name==='exhibit'&&profile!=='MM2024'} value={fields[name]||''} onChange={e=>{if(!numeric||/^\d*$/.test(e.target.value))setFields({...fields,[name]:e.target.value});}}/>}</label>;
  }
  const formVisible=(view==='vote'&&data.screen==='VOTE1')||(view==='post'&&data.screen==='ADDCMT')||view==='read'||view==='auth';
  const displayError=networkError||data.errorDisplay||data.error||'';
  const heading=data.screenTitle||(view==='exhibits'?'Exhibits':view==='settings'?'Visitor Settings':data.screen||'Visitor Studio');
  const kioskNames:Record<number,string>={1:'Nominate Exhibit for Award',2:'Learn AS/400 Navigation',3:'Sign Exhibit Guestbook',4:'Read a Guestbook Comment'};
  return <div className="app-shell">
    <aside className="sidebar"><a className="brand" href="#" onClick={e=>{e.preventDefault();void open('menu','MM2024');}}>VCF<span>/400</span></a><div className="edition">VISITOR STUDIO<br/>A living computer history</div>
      <nav aria-label="Visitor navigation"><button data-testid="nav-menu" className={view==='menu'?'active':''} onClick={()=>void open('menu','MM2024')}>01 <span>メインメニュー</span></button><button data-testid="nav-exhibits" onClick={()=>void open('exhibits')}>02 <span>展示を探す</span></button><button data-testid="nav-kiosk" onClick={()=>void open('kiosk','EXH001','EXH001')}>03 <span>展示キオスク</span></button><button data-testid="nav-settings" onClick={()=>void open('settings')}>04 <span>投票受付状況</span></button></nav>
      <div className="terminal-status"><i/>SYSTEM ONLINE<small>IBM midrange · Modern experience</small></div>
    </aside>
    <main><header className="topbar"><span>VINTAGE COMPUTER FESTIVAL</span><span className="profile">{profile==='MM2024'?'Visitor access':`Kiosk · ${profile}`}</span></header>
      <section className="workspace" aria-busy={busy}><div className="eyebrow">EXPLORE. CONNECT. REMEMBER.</div><h1 data-testid="screen-title">{heading}</h1><div className="intro">コンピューターの歴史を、体験から。展示を巡り、学び、あなたの声を残しましょう。</div>
        <div className="error-row" data-testid="error-row" role="alert" data-row={data.errorPosition?.row} data-column={data.errorPosition?.column} data-length={data.errorPosition?.length}>{displayError||'\u00a0'}</div>
        {view==='menu'&&<div className="menu-grid">{['Start Here','Office Tasks','Entertainment','Exhibit Tasks','System Tasks','Administration'].map(group=><section className="menu-group" key={group}><h2>{group}</h2>{data.items?.filter(item=>item.group===group).map(item=><button className="menu-option" key={item.number} data-testid={`menu-${item.number}`} disabled={!item.implemented} onClick={()=>{
          if(item.action==='signoff'){void signOff();return;}
          const next:Record<string,View>={learn:'learn',vote:'vote','guestbook-post':'post','guestbook-read':'read'};void open(next[item.action],'MM2024','global');
        }}><span className="option-number">{item.number.toString().padStart(2,'0')}</span><span>{item.title}</span><span className="arrow">↗</span></button>)}</section>)}</div>}
        {view==='exhibits'&&<div className="exhibit-grid">{data.exhibits?.map(exhibit=><article className="exhibit-card" key={exhibit.EXHUSRPRF} data-testid={`exhibit-${exhibit.EXHUSRPRF}`}><div className="eyebrow">{exhibit.EXHUSRPRF}</div><h2>{exhibit.EXHBTITLE}</h2><p>{exhibit.EXHBDESC}</p><small>{exhibit.EXHBITOR}</small><button onClick={()=>void open('kiosk',exhibit.EXHUSRPRF,exhibit.EXHUSRPRF)}>展示を開く ↗</button></article>)}{data.exhibits?.length===0&&<p data-testid="empty">展示はまだありません。</p>}</div>}
        {view==='kiosk'&&<div className="panel"><h2>{typeof data.exhibit==='object'?data.exhibit.EXHBTITLE:''}</h2><div className="kiosk-options">{data.visibleOptions?.map(number=><button data-testid={`kiosk-${number}`} key={number} onClick={()=>void open(({1:'vote',2:'learn',3:'post',4:'read'} as Record<number,View>)[number],profile,profile)}><b>{number}</b>{kioskNames[number]} ↗</button>)}</div><form className="option-entry" onSubmit={e=>{e.preventDefault();const option=fields.option;if(option==='7')void open('auth');else if(data.visibleOptions?.includes(Number(option)))void open(({1:'vote',2:'learn',3:'post',4:'read'} as Record<number,View>)[Number(option)],profile,profile);}}><label>Selection<input aria-label="Selection" data-testid="kiosk-selection" inputMode="numeric" value={fields.option||''} onChange={e=>setFields({option:e.target.value})}/></label><button type="submit">選択</button></form></div>}
        {formVisible&&<form className="panel entry-form" data-testid="entry-form" onSubmit={e=>{e.preventDefault();void submit();}}>
          {view==='vote'&&<>{field('badge',4,true)}{field('exhibit',9)}{field('award',3,true)}</>}
          {view==='post'&&<>{field('name',16)}{field('exhibit',9)}{field('comment',200)}</>}
          {view==='read'&&<>{field('id',4,true)}<small data-testid="displayed-total">Comments: {data.displayedTotal}</small></>}
          {view==='auth'&&<>{field('password',9)}<button data-testid="auth-submit" type="submit" disabled={busy}>確認</button>{data.authenticated===false&&<p data-testid="auth-rejected" role="status">認証されませんでした。もう一度入力してください。</p>}</>}
          <button className="sr-only" tabIndex={-1} type="submit">Submit</button>
        </form>}
        {view==='vote'&&data.screen==='VOTE1'&&<div className="award-reference"><strong>Available Awards for This Year:</strong><span>001. Best in Show Award</span><span>002. The Ed Fair Award</span><small>You may only vote for ONE award. Choose your nomination carefully!</small></div>}
        {view==='read'&&<article className="panel comment-card" data-testid="comment-card"><span data-testid="comment-id">{data.CMTID}</span><h2 data-testid="comment-name">{data.name}</h2><small data-testid="comment-title">{data.title}</small><p data-testid="comment-text">{data.comment}</p></article>}
        {view==='learn'&&<article className="panel learn-card" data-testid="learn-card">{data.ended?<p data-testid="learn-ended">学習を終了しました。</p>:<><span className="page-number" data-testid="page-number">{data.page?`PAGE ${String(data.page).padStart(2,'0')}`:'LEARN/400'}</span><p data-testid="learn-content">{data.content}</p>{data.allowForward===false&&<p data-testid="learn-eof">これ以上のページはありません。</p>}</>}</article>}
        {data.empty&&<p data-testid="empty">表示するデータはありません。</p>}
        {['VOTEEND','ENDCMT','ENDOFCON','DAVE'].includes(data.screen||'')&&<div className="panel success-card" data-testid="completion"><span className="success-icon">{data.screen==='ENDOFCON'?'◷':'✓'}</span>{data.CMTID!==undefined&&<h2 data-testid="new-comment-id">{data.CMTID}</h2>}<button onClick={()=>void open(profile==='MM2024'?'menu':'kiosk')}>メニューへ戻る</button></div>}
        {view==='settings'&&<div className="panel"><div className="eyebrow">VOTING PERIOD</div><h2 data-testid="voting-status">{data.ALWVOTE==='Y'?'受付中 / Open':'受付終了 / Closed'}</h2>{token.current?<button data-testid="toggle-voting" disabled={busy} onClick={async()=>{const result=await request('/settings','PATCH',{ALWVOTE:data.ALWVOTE==='Y'?'N':'Y'});if(result.ALWVOTE)setData(result);}}>投票受付を切り替える</button>:<p>変更には展示キオスクでの管理者認証が必要です。</p>}</div>}
        {view==='info'&&data.content&&<article className="panel">{data.content}</article>}
        <footer className="actions">{Object.entries(data.fKeys||{}).sort().map(([key,label])=><button data-testid={`key-${key}`} key={key} disabled={busy||(view==='learn'&&(data.ended||(key==='F5'&&data.allowForward===false)))} onClick={()=>void keypress(key)}><kbd>{key}</kbd>{label}</button>)}{['settings','auth'].includes(view)&&<button data-testid="admin-exit" disabled={busy} onClick={()=>void keypress('F3')}><kbd>F3</kbd>Exit</button>}{view!=='menu'&&<button className="quiet" data-testid="return-menu" onClick={()=>void open(profile==='MM2024'?'menu':'kiosk')}>メニューへ戻る</button>}</footer>
      </section><footer className="page-footer"><span>VCF/400</span><span>Preserving the past. Inspiring what's next.</span></footer>
    </main>
    {call&&<div className="modal-backdrop"><section className="panel modal" role="dialog" aria-modal="true" aria-labelledby="call-title"><div className="eyebrow">LEARN/400 · PROGRAM</div><h2 id="call-title" data-testid="call-title">{call.screenTitle}</h2><p>Vintage Computer Festival · VCF/400</p><button data-testid="call-return" onClick={()=>setCall(undefined)}>LEARN/400へ戻る</button></section></div>}
  </div>;
}

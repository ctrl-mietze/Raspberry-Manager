import React,{useCallback,useEffect,useRef,useState} from 'react';
import {ArrowLeft,Check,ChevronRight,Cloud,Copy,ExternalLink,KeyRound,Laptop,LockKeyhole,Monitor,Plus,RefreshCw,Search,Server,ShieldCheck,Terminal as TerminalIcon,Trash2,Wifi,WifiOff,X} from 'lucide-react';
import {Terminal} from '@xterm/xterm';
import {FitAddon} from '@xterm/addon-fit';
import type {Profile,ConnectState,ConnectDevice,ConnectAction,HostKeyPrompt,SshMessage} from '../types';
type Toast=(message:string)=>void;
function TerminalView({id,onClose}:{id:string;onClose:()=>void}){
  const ref=useRef<HTMLDivElement>(null);
  const terminal=useRef<Terminal|null>(null);
  const fit=useRef<FitAddon|null>(null);
  useEffect(()=>{
    if(!ref.current)return;
    const term=new Terminal({cursorBlink:true,fontSize:13,fontFamily:'Cascadia Code,Consolas,monospace',
      theme:{background:'#100f16',foreground:'#f3e6ed',cursor:'#ef447b',selectionBackground:'#9f275d88'},
      allowTransparency:true,scrollback:3000});
    const addon=new FitAddon();
    term.loadAddon(addon);term.open(ref.current);addon.fit();
    term.writeln('\x1b[38;5;212mRaspberry Manager SSH terminal\x1b[0m');
    terminal.current=term;fit.current=addon;
    const data=term.onData(text=>{void window.rm.sshWrite(id,text)});
    const unlisten=window.rm.on('ssh:message',(message:SshMessage)=>{
      if(message.id!==id)return;
      if(message.type==='data'&&message.data)term.write(message.data);
      if(message.type==='error')term.writeln('\r\n\x1b[31m'+message.message+'\x1b[0m');
      if(message.type==='closed')term.writeln('\r\n\x1b[33mConnection closed.\x1b[0m');
    });
    const observer=new ResizeObserver(()=>{addon.fit();void window.rm.sshResize(id,term.cols,term.rows)});
    observer.observe(ref.current);
    return ()=>{observer.disconnect();data.dispose();unlisten();term.dispose();terminal.current=null};
  },[id]);
  return <div className="terminal-shell">
    <div className="terminal-top"><span className="terminal-led"/><span>Connected SSH session · {id.slice(0,8)}</span>
      <button className="btn btn-small btn-quiet" onClick={onClose}><X size={14}/> Disconnect</button></div>
    <div className="xterm-holder" ref={ref}/>
    <div className="terminal-foot">Encrypted SSH · credentials aren't stored in Raspberry Manager</div>
  </div>;
}
function ConnectView({toast}:{toast:Toast}){
  const [state,setState]=useState<ConnectState|null>(null);
  const [query,setQuery]=useState('');
  const [filter,setFilter]=useState<'all'|'online'|'offline'>('all');
  const viewRef=useRef<HTMLDivElement>(null);
  const [launching,setLaunching]=useState(true);
  const refresh=useCallback(async()=>{
    try{const s=await window.rm.connectState();setState(s)}
    catch(e){toast('Connect: '+String(e))}
  },[toast]);
  useEffect(()=>{
    let active=true;
    window.rm.connectStart().then(()=>{if(active)setLaunching(false);void refresh()})
      .catch(e=>{if(active){setLaunching(false);toast(String(e))}});
    const tick=setInterval(()=>void refresh(),1450);
    const changed=window.rm.on('connect:changed',()=>void refresh());
    return ()=>{active=false;clearInterval(tick);changed();void window.rm.connectViewport({x:0,y:0,width:0,height:0},false)};
  },[refresh,toast]);
  const webVisible=Boolean(state?.auth||state?.session);
  useEffect(()=>{
    const update=()=>{
      if(!viewRef.current){void window.rm.connectViewport({x:0,y:0,width:0,height:0},false);return}
      const r=viewRef.current.getBoundingClientRect();
      void window.rm.connectViewport({x:r.left,y:r.top,width:r.width,height:r.height},webVisible);
    };
    update();
    const observer=new ResizeObserver(update);
    if(viewRef.current)observer.observe(viewRef.current);
    window.addEventListener('resize',update);
    return ()=>{observer.disconnect();window.removeEventListener('resize',update);
      void window.rm.connectViewport({x:0,y:0,width:0,height:0},false)};
  },[webVisible,state?.snapshot.page]);
  const run=async(a?:ConnectAction)=>{
    if(!a||!a.enabled){toast(a?.reason||'Connect has not offered this action.');return}
    if(!state)return;
    try{const ok=await window.rm.connectAction(state.snapshot.epoch,a.handle);
      if(!ok)toast('The Connect action is no longer available. Refresh devices.');
      await refresh();
    }catch(e){toast(String(e))}
  };
  const devices=(state?.snapshot.devices||[]).filter(d=>
    d.name.toLowerCase().includes(query.toLowerCase())&&
    (filter==='all'||d.status===filter)).sort((a,b)=>(a.status==='online'?0:1)-(b.status==='online'?0:1));
  return <div className="connect-view">
    <div className="connect-banner">
      <div className="connect-brand"><div className="quick-icon pink"><Cloud size={24}/></div><div>
        <strong>Raspberry Pi Connect</strong><span>Official Connect service · native device controls</span></div></div>
      <button className="btn btn-quiet btn-small" onClick={()=>{void window.rm.connectRefresh().then(refresh)}}><RefreshCw size={15}/> Refresh</button>
    </div>
    {webVisible?<div className="provider-shell">
      <div className="provider-context"><ShieldCheck size={17}/> {state?.auth?'Secure official Raspberry Pi ID sign-in':'Official remote shell / screen sharing'}
        <span>{state?.auth?'id.raspberrypi.com':'connect.raspberrypi.com'}</span></div>
      <div className="provider-viewport" ref={viewRef} aria-label="Official Raspberry Pi Connect WebView"/>
      <div className="provider-bottom"><LockKeyhole size={15}/> This page is hosted in a dedicated, isolated Chromium profile inside the app. No login data is sent to Raspberry Manager.</div>
    </div>:
    <div className="connect-native">
      {launching||!state||state.loading?<div className="empty-large"><span className="spinner"/><h3>Loading Raspberry Pi Connect…</h3><p>Connecting to the official provider.</p></div>:
      state.error?<div className="empty-large"><WifiOff size={36}/><h3>Connect needs attention</h3><p>{state.error}</p><button className="btn btn-primary" onClick={()=>void window.rm.connectRefresh().then(refresh)}>Try again</button></div>:
      state.snapshot.page==='signin'?<div className="empty-large sign-in-panel">
          <div className="quick-icon pink"><Cloud size={32}/></div>
          <h3>Your Raspberry. Anywhere.</h3><p>Sign in to Raspberry Pi Connect using your official Raspberry Pi ID. The login page stays inside this app.</p>
          <button className="btn btn-primary" onClick={()=>void run(state.snapshot.actions.find(a=>a.kind==='signin'))}>
            <LockKeyhole size={18}/> Sign in with Raspberry Pi ID</button>
        </div>:
      state.snapshot.page==='devices'?<>
        <div className="connect-toolbar"><div className="search-field"><Search size={17}/><input value={query} placeholder="Search your Raspberry devices" onChange={e=>setQuery(e.target.value)}/></div>
        <div className="segmented tiny"><button className={filter==='all'?'selected':''} onClick={()=>setFilter('all')}>All</button><button className={filter==='online'?'selected':''} onClick={()=>setFilter('online')}>Online</button><button className={filter==='offline'?'selected':''} onClick={()=>setFilter('offline')}>Offline</button></div></div>
        <div className="inline-summary">{state.snapshot.devices.length} devices linked · {state.snapshot.devices.filter(d=>d.status==='online').length} online</div>
        {devices.length?<div className="connect-device-grid">
          {devices.map(d=><div className="panel connect-device-card" key={d.key}>
            <div className="device-card-title"><div className="small-pi"><Server size={20}/></div><div><strong>{d.name}</strong><span className={'status-text '+d.status}><i/>{d.status==='online'?'Online':d.status==='offline'?'Offline':'Status unverified'}</span></div></div>
            <div className="device-card-actions"><button className="btn btn-small" onClick={()=>void run(d.shell)}><TerminalIcon size={15}/> Remote shell</button><button className="btn btn-small" onClick={()=>void run(d.screen)}><Monitor size={15}/> Screen sharing</button></div>
            <button className="text-link" onClick={()=>void run(d.detail)}>View device details <ChevronRight size={14}/></button>
          </div>)}
        </div>:<div className="empty-large"><Cloud size={34}/><h3>No matching linked devices</h3><p>Link a Raspberry Pi to your Connect account and refresh.</p></div>}
      </>:
      <div className="empty-large"><Cloud size={34}/><h3>{state.snapshot.title||'Raspberry Pi Connect'}</h3>
        <p>{state.snapshot.notice||'Use the controls provided by the official service.'}</p>
        {(state.snapshot.actions||[]).map(a=><button className="btn btn-primary" key={a.handle} onClick={()=>void run(a)}>{a.label}</button>)}
        <button className="btn btn-quiet" onClick={()=>void window.rm.connectRefresh().then(refresh)}>Back to devices</button></div>}
      <div className="provider-caption">Sessions use official WebRTC encryption and Raspberry Pi account permissions. Screen sharing requires a compatible Wayland desktop; Raspberry Pi OS Lite supports shell only.</div>
    </div>}
  </div>;
}
export default function Devices({toast}:{toast:Toast}){
 const [view,setView]=useState<'local'|'connect'>('local');
 const [profiles,setProfiles]=useState<Profile[]>([]);
 const [editing,setEditing]=useState(false);
 const [form,setForm]=useState<Partial<Profile>>({name:'My Raspberry Pi',host:'',user:'pi',port:22,keyPath:''});
 const [selected,setSelected]=useState<Profile|null>(null);
 const [secret,setSecret]=useState('');
 const [connected,setConnected]=useState(false);
 const [pendingKey,setPendingKey]=useState<HostKeyPrompt|null>(null);
 const [busy,setBusy]=useState(false);
 const refresh=useCallback(()=>window.rm.listProfiles().then(setProfiles).catch(e=>toast(String(e))),[toast]);
 useEffect(()=>{void refresh()},[refresh]);
 useEffect(()=>window.rm.on('ssh:hostkey',(prompt:HostKeyPrompt)=>setPendingKey(prompt)),[]);
 const save=async()=>{
   if(!form.host?.trim()){toast('Enter a host name or IP address.');return}
   try{await window.rm.saveProfile(form);setEditing(false);await refresh();toast('SSH profile saved');}
   catch(e){toast(String(e))}
 };
 const open=(profile:Profile)=>{setSelected(profile);setConnected(false);setSecret('')};
 const connect=async()=>{if(!selected)return;setBusy(true);try{
   await window.rm.sshConnect(selected.id,secret);setConnected(true);setSecret('');toast('SSH connected');
 }catch(e){toast('SSH: '+String(e))}finally{setBusy(false)}};
 const close=async()=>{if(selected)await window.rm.sshDisconnect(selected.id);setConnected(false);setSelected(null)};
 return <div className="page devices-page">
   <div className="page-heading"><div><span className="section-kicker">DEVICE MANAGEMENT</span><h1>Raspberry devices</h1><p>Manage trusted SSH profiles and your Raspberry Pi Connect sessions.</p></div></div>
   <div className="section-tabs"><button className={view==='local'?'active':''} onClick={()=>setView('local')}><TerminalIcon size={18}/> Local SSH devices</button><button className={view==='connect'?'active':''} onClick={()=>setView('connect')}><Cloud size={18}/> Raspberry Pi Connect</button></div>
   {view==='connect'?<ConnectView toast={toast}/>:<>
    <div className="devices-bar"><div><strong>Your saved Raspberry Pis</strong><p>{profiles.length} SSH profiles stored locally</p></div><button className="btn btn-primary" onClick={()=>{setForm({name:'My Raspberry Pi',host:'',user:'pi',port:22,keyPath:''});setEditing(true)}}><Plus size={17}/> Add a Raspberry</button></div>
    <div className="device-grid">
      {profiles.map(p=><button className="device-profile" key={p.id} onClick={()=>open(p)}>
        <div className="small-pi"><Server size={24}/></div><div><strong>{p.name}</strong><span>{p.user}@{p.host}:{p.port}</span><small>SSH profile · Tap to connect</small></div><ChevronRight size={20}/></button>)}
      {!profiles.length&&<div className="empty-large full-span"><Server size={37}/><h3>No SSH profiles yet</h3><p>Connect directly to your Pi, or open the built-in Raspberry Pi Connect view above.</p><button className="btn btn-primary" onClick={()=>setEditing(true)}><Plus size={17}/> Add Raspberry</button></div>}
    </div>
    <div className="notice-line"><ShieldCheck size={17}/> No SSH passwords are saved. Newly seen SSH host fingerprints require your approval; changes are rejected.</div>
    {editing&&<div className="modal-backdrop"><div className="modal dialog-card">
      <div className="modal-head"><div><span className="section-kicker">LOCAL ACCESS</span><h2>{form.id?'Edit Raspberry':'Add a Raspberry'}</h2></div><button className="icon-action" onClick={()=>setEditing(false)}><X/></button></div>
      <label>Friendly name<input value={form.name||''} onChange={e=>setForm({...form,name:e.target.value})}/></label>
      <label>IP address or hostname<input placeholder="raspberrypi.local" value={form.host||''} onChange={e=>setForm({...form,host:e.target.value})}/></label>
      <div className="field-row"><label>Username<input value={form.user||''} onChange={e=>setForm({...form,user:e.target.value})}/></label><label>Port<input type="number" value={form.port||22} onChange={e=>setForm({...form,port:Number(e.target.value)})}/></label></div>
      <label>Private SSH key (optional)<div className="file-field"><span>{form.keyPath||'Use password authentication'}</span><button className="btn btn-small" onClick={()=>void window.rm.chooseKey().then(p=>p&&setForm({...form,keyPath:p}))}>Browse</button></div></label>
      <div className="modal-actions"><button className="btn btn-quiet" onClick={()=>setEditing(false)}>Cancel</button><button className="btn btn-primary" onClick={()=>void save()}><Check size={16}/> Save profile</button></div>
    </div></div>}
    {selected&&<div className="modal-backdrop"><div className="modal terminal-modal">
      <div className="modal-head"><div><span className="section-kicker">REMOTE SHELL</span><h2>{selected.name}</h2><p>{selected.user}@{selected.host}:{selected.port}</p></div><button className="icon-action" onClick={()=>void close()}><X/></button></div>
      {connected?<TerminalView id={selected.id} onClose={()=>void close()}/>:<div className="terminal-login">
        <div className="small-pi"><KeyRound size={25}/></div><h3>Establish an encrypted connection</h3>
        <p>Using {selected.keyPath?'private key authentication':'password authentication'}.</p>
        <label>{selected.keyPath?'Private key passphrase (if required)':'SSH password'}
          <input type="password" autoComplete="off" value={secret} onChange={e=>setSecret(e.target.value)}
            onKeyDown={e=>e.key==='Enter'&&void connect()}/></label>
        <button className="btn btn-primary" disabled={busy} onClick={()=>void connect()}>{busy?'Connecting…':'Connect over SSH'}<ChevronRight size={16}/></button>
        <div className="terminal-options"><button className="text-link" onClick={()=>{setForm(selected);setSelected(null);setEditing(true)}}>Edit profile</button><button className="text-link danger" onClick={()=>{if(window.confirm('Remove saved SSH profile?'))void window.rm.removeProfile(selected.id).then(()=>{setSelected(null);refresh()})}}>Remove saved profile</button></div>
      </div>}
    </div></div>}
    {pendingKey&&<div className="modal-backdrop top-modal"><div className="modal dialog-card"><ShieldCheck className="pink-text" size={30}/><h2>Trust this SSH host?</h2>
      <p>Verify the server fingerprint through a trusted channel before accepting.</p>
      <div className="fingerprint"><strong>{pendingKey.host}</strong><code>{pendingKey.fingerprint}</code></div>
      <div className="modal-actions"><button className="btn btn-quiet" onClick={()=>{void window.rm.sshApprove(pendingKey.id,false);setPendingKey(null)}}>Reject</button>
        <button className="btn btn-primary" onClick={()=>{void window.rm.sshApprove(pendingKey.id,true);setPendingKey(null)}}>Trust host</button></div>
    </div></div>}
   </>}
 </div>;
}

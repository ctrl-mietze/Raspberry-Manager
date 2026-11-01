import React,{useCallback,useEffect,useState} from 'react';
import {createRoot} from 'react-dom/client';
import {Blocks,CheckCircle2,ChevronDown,ChevronRight,Cloud,Download,ExternalLink,HardDrive,Home as HomeIcon,Info,Laptop,Menu,Monitor,Network,RefreshCw,Settings as SettingsIcon,ShieldCheck,Usb,Workflow,X} from 'lucide-react';
import BerryMark from './components/Brand';
import Home from './components/Home';
import Devices from './components/Devices';
import Drives from './components/Drives';
import Builder from './components/Builder';
import type {Drive,Profile} from './types';
import '@xyflow/react/dist/style.css';
import '@xterm/xterm/css/xterm.css';
import './style.css';
type Tab='home'|'devices'|'drives'|'builder'|'settings';
const tabs:[Tab,string,React.ComponentType<any>][]=[
 ['home','Home',HomeIcon],['devices','Devices',Laptop],['drives','Drives',HardDrive],
 ['builder','Builder',Workflow],['settings','Settings',SettingsIcon]];
function App(){
 const [tab,setTab]=useState<Tab>('home');
 const [version,setVersion]=useState('10.3.0');
 const [profiles,setProfiles]=useState<Profile[]>([]);
 const [drives,setDrives]=useState<Drive[]>([]);
 const [theme,setTheme]=useState('berry');
 const [sidebar,setSidebar]=useState(true);
 const [toastText,setToastText]=useState<string|null>(null);
 const [busyFlash,setBusyFlash]=useState(false);
 const toast=useCallback((msg:string)=>setToastText(String(msg).slice(0,450)),[]);
 useEffect(()=>{
   let cancelled=false;
   window.rm.meta().then(meta=>{if(!cancelled)setVersion(meta.version)}).catch(()=>{});
   window.rm.getSettings().then(s=>{if(!cancelled)setTheme(s.theme)}).catch(()=>{});
   window.rm.listProfiles().then(v=>{if(!cancelled)setProfiles(v)}).catch(()=>{});
   window.rm.listDrives().then(v=>{if(!cancelled)setDrives(v)}).catch(()=>{});
   return ()=>{cancelled=true};
 },[]);
 useEffect(()=>{
   if(!toastText)return;
   const timer=setTimeout(()=>setToastText(null),6200);
   return ()=>clearTimeout(timer);
 },[toastText]);
 useEffect(()=>{
   if(tab==='home')void Promise.all([window.rm.listProfiles(),window.rm.listDrives()])
     .then(([p,d])=>{setProfiles(p);setDrives(d)}).catch(()=>{});
 },[tab]);
 const flash=useCallback(async()=>{
   setBusyFlash(true);
   try{
     const action=await window.rm.launchImager();
     if(action==='launched')toast('Official Raspberry Pi Imager opened.');
     if(action==='installer')toast('Official Imager installer opened. Run the setup, then open Imager again.');
     if(action==='official-site')toast('Official Raspberry Pi Imager download opened.');
   }catch(e){toast('Imager: '+String(e))}finally{setBusyFlash(false)}
 },[toast]);
 const changeTheme=(value:string)=>{setTheme(value);void window.rm.saveSettings({theme:value})};
 return <div className={'app-shell theme-'+theme+(sidebar?'':' sidebar-collapsed')}>
  <aside className="sidebar">
    <div className="sidebar-brand">
      <BerryMark size={45}/>
      {sidebar&&<div className="sidebar-brand-text"><strong>Raspberry Manager</strong><span>Mega Manager X · VX</span></div>}
    </div>
    <div className="sidebar-divider"/>
    {sidebar&&<span className="sidebar-label">WORKSPACE</span>}
    <nav className="sidebar-nav" aria-label="Main navigation">
      {tabs.map(([id,label,Icon])=><button key={id} title={label} className={'nav-item '+(tab===id?'active':'')} onClick={()=>setTab(id)}>
        <Icon size={20} strokeWidth={tab===id?2.3:1.9}/>{sidebar&&<span>{label}</span>}{sidebar&&tab===id&&<span className="nav-active-dot"/>}</button>)}
    </nav>
    <div className="sidebar-flex"/>
    <div className="sidebar-device-widget">
      {sidebar&&<div><span>YOUR WORKSPACE</span><strong>Raspberry Pi tools</strong><small>Local-first · v{version}</small></div>}
      <div className="tiny-verified"><ShieldCheck size={17}/></div>
    </div>
    <button className="sidebar-collapse" title={sidebar?'Collapse sidebar':'Expand sidebar'} onClick={()=>setSidebar(!sidebar)}>
      <Menu size={17}/>{sidebar&&'Collapse menu'}</button>
  </aside>
  <div className="main-column">
    <header className="topbar">
      <div className="breadcrumbs"><span>Workspace</span><ChevronRight size={14}/><strong>{tabs.find(t=>t[0]===tab)?.[1]}</strong></div>
      <div className="header-actions">
        <div className="header-build"><span className="green-dot"/> Windows desktop</div>
        <button className="topbar-flash" title="Open Raspberry Pi Imager" disabled={busyFlash} onClick={flash}><Usb size={20}/> <span>Open Pi Imager</span></button>
        <div className="header-avatar"><BerryMark size={32}/></div>
      </div>
    </header>
    <main className={'content '+(tab==='builder'?'content-builder':'')} key={tab}>
      {tab==='home'&&<Home goto={setTab} imager={flash} profiles={profiles} drives={drives} version={version}/>}
      {tab==='devices'&&<Devices toast={toast}/>}
      {tab==='drives'&&<Drives toast={toast} flash={flash}/>}
      {tab==='builder'&&<Builder toast={toast} flash={flash}/>}
      {tab==='settings'&&<div className="page settings-page">
        <div className="page-heading"><div><span className="section-kicker">YOUR PREFERENCES</span><h1>Settings</h1><p>Customize Raspberry Manager and review the supported integrations.</p></div></div>
        <div className="settings-panels">
          <div className="panel settings-panel"><h3>Appearance</h3><p>Keep your workspace comfortable. Controls stay consistent with Raspberry Manager for Android.</p>
            <div className="settings-choice"><button className={theme==='berry'?'active':''} onClick={()=>changeTheme('berry')}><span className="color-sample pink-sample"/> Raspberry Berry</button>
              <button className={theme==='graphite'?'active':''} onClick={()=>changeTheme('graphite')}><span className="color-sample gray-sample"/> Graphite Night</button></div>
          </div>
          <div className="panel settings-panel"><h3>Raspberry Pi Connect</h3><p>Official account pages load in the app's dedicated Chromium profile. Raspberry Manager does not store your Raspberry Pi ID password or copy cookies from another browser.</p><div className="settings-info"><Cloud size={20}/> Real remote shell and screen sharing are provided by Raspberry Pi Connect.</div></div>
          <div className="panel settings-panel"><h3>Disk writing</h3><p>The Windows application displays devices without changing them. Actual flashing uses Raspberry Pi Imager and requires explicit confirmation of the target.</p><button className="btn btn-primary" onClick={flash}><Usb size={17}/> Open official Imager</button></div>
          <div className="panel settings-panel"><h3>About this build</h3><p>Raspberry Manager · Mega Manager X · VX</p><div className="settings-info"><ShieldCheck size={19}/> Windows v{version} · Companion of Android v10.3.0</div><p className="fineprint">Independent implementation. Raspberry Pi, Raspberry Pi Connect and Raspberry Pi Imager are marks and products of Raspberry Pi Ltd. Use of official services follows their terms. No unofficial OAuth token handling.</p></div>
        </div>
      </div>}
    </main>
    <footer className="app-footer"><span>Raspberry Manager · Mega Manager X</span><span>Build · Flash · Connect · Manage</span></footer>
  </div>
  {toastText&&<div className="toast" role="status"><CheckCircle2 size={20}/><span>{toastText}</span><button onClick={()=>setToastText(null)}><X size={16}/></button></div>}
 </div>;
}
createRoot(document.getElementById('root')!).render(<App/>);

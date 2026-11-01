import React from 'react';
import { ArrowUpRight,Blocks,ChevronRight,Cloud,HardDrive,Monitor,Network,Play,Plus,ShieldCheck,Usb,Workflow } from 'lucide-react';
import type {Drive,Profile} from '../types';
type Tab='home'|'devices'|'drives'|'builder'|'settings';
export default function Home({goto,imager,profiles,drives,version}:{goto:(t:Tab)=>void;imager:()=>void;profiles:Profile[];drives:Drive[];version:string}){
 return <div className="page page-home">
  <section className="hero">
    <div className="hero-copy">
      <div className="eyebrow"><span className="dot"/> MEGA MANAGER X · WINDOWS EDITION</div>
      <h1>Your Raspberry<br/><em>workspace.</em></h1>
      <p>Build, flash, connect and manage your Raspberry Pi devices in one considered workspace.</p>
      <div className="hero-actions">
        <button className="btn btn-primary" onClick={()=>goto('builder')}><Blocks size={18}/> Open image builder <ChevronRight size={17}/></button>
        <button className="btn btn-outlined" onClick={()=>goto('devices')}><Network size={18}/> My Raspberry devices</button>
      </div>
      <div className="hero-facts"><span><ShieldCheck size={15}/> Local-first tools</span><span><Cloud size={15}/> Official Pi Connect</span><span><Workflow size={15}/> Visual workbench</span></div>
    </div>
    <div className="hero-art" aria-hidden="true">
      <div className="grid-halo"/>
      <div className="hero-device">
        <div className="hero-device-top"><span className="status-light"/><span>PI MAGIC WORKBENCH</span><span>● ● ●</span></div>
        <div className="hero-device-body">
          <div className="hero-work-node"><small>OPERATING SYSTEM</small><strong>Raspberry Pi OS</strong><span>16 GB allocated</span></div>
          <svg viewBox="0 0 250 80"><path d="M125 0 V40 Q125 50 137 50 H214 V80" fill="none" stroke="#F04580" strokeWidth="2" strokeDasharray="5 6"/></svg>
          <div className="hero-work-row"><div><small>NETWORK</small><strong>Ethernet</strong></div><div><small>RUNTIME</small><strong>Docker</strong></div></div>
        </div>
      </div>
      <span className="art-orbit orbit-one"/><span className="art-orbit orbit-two"/>
    </div>
  </section>
  <div className="section-head"><div><span className="section-kicker">YOUR TOOLS</span><h2>Quick launch</h2></div><span className="muted">Everything you need, right here</span></div>
  <div className="quick-grid">
    <button className="quick-card" onClick={()=>goto('builder')}><div className="quick-icon pink"><Blocks size={25}/></div><div className="quick-text"><h3>Pi Magic Image Designer</h3><p>Stack builder, dependencies and Workbench</p></div><ArrowUpRight className="quick-arrow" size={18}/></button>
    <button className="quick-card" onClick={()=>goto('devices')}><div className="quick-icon purple"><Monitor size={25}/></div><div className="quick-text"><h3>Raspberry devices</h3><p>SSH profiles, remote shell and Pi Connect</p></div><ArrowUpRight className="quick-arrow" size={18}/></button>
    <button className="quick-card" onClick={()=>goto('drives')}><div className="quick-icon blue"><HardDrive size={25}/></div><div className="quick-text"><h3>Drives & storage</h3><p>Inspect USB drives, cards and disks</p></div><ArrowUpRight className="quick-arrow" size={18}/></button>
    <button className="quick-card" onClick={imager}><div className="quick-icon green"><Usb size={25}/></div><div className="quick-text"><h3>Raspberry Pi Imager</h3><p>Launch official, confirmed-device flashing</p></div><ArrowUpRight className="quick-arrow" size={18}/></button>
  </div>
  <div className="home-bottom">
    <div className="panel">
      <div className="panel-head"><div><h3>Device overview</h3><span>Local SSH profiles</span></div><button className="icon-action" onClick={()=>goto('devices')}><ArrowUpRight size={18}/></button></div>
      {profiles.length ? profiles.slice(0,4).map(p=><div className="simple-device" key={p.id}><div className="small-pi"><Monitor size={19}/></div><div><strong>{p.name}</strong><span>{p.user}@{p.host}</span></div><ChevronRight size={17}/></div>) :
       <div className="empty-inline"><Monitor size={26}/><strong>No local Raspberry Pi yet</strong><p>Save your first SSH profile, or sign in with Raspberry Pi Connect.</p><button className="btn btn-small" onClick={()=>goto('devices')}><Plus size={14}/> Add Raspberry</button></div>}
    </div>
    <div className="panel">
      <div className="panel-head"><div><h3>Storage overview</h3><span>Disks detected on this computer</span></div><button className="icon-action" onClick={()=>goto('drives')}><ArrowUpRight size={18}/></button></div>
      <div className="metric-cards">
        <div><strong>{drives.length}</strong><span>Total drives</span></div><div><strong>{drives.filter(d=>d.removable).length}</strong><span>Removable</span></div>
      </div>
      <div className="simple-info"><ShieldCheck size={18}/> Disks are read-only until you explicitly enter the official Imager.</div>
      <div className="footer-version">Raspberry Manager · v{version}</div>
    </div>
  </div>
 </div>;
}

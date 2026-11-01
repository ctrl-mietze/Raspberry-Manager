import React,{useEffect,useState} from 'react';
import {AlertTriangle,ArrowUpRight,HardDrive,RefreshCw,ShieldCheck,Usb} from 'lucide-react';
import type {Drive} from '../types';
const readable=(n:number)=>n?((n/1e9).toFixed(n>=1e12?0:1)+' GB'):'Unknown';
export default function Drives({toast,flash}:{toast:(t:string)=>void;flash:()=>void}){
 const [drives,setDrives]=useState<Drive[]>([]);
 const [busy,setBusy]=useState(false);
 const refresh=async()=>{setBusy(true);try{setDrives(await window.rm.listDrives())}catch(e){toast('Drive detection: '+String(e))}finally{setBusy(false)}};
 useEffect(()=>{void refresh()},[]);
 return <div className="page drives-page">
  <div className="page-heading"><div><span className="section-kicker">STORAGE & IMAGING</span><h1>Drives & storage</h1><p>Inspect your Windows disks. Flashing opens the official Raspberry Pi Imager.</p></div>
    <button className="btn btn-outlined" disabled={busy} onClick={()=>void refresh()}><RefreshCw size={17} className={busy?'spin':''}/> Refresh drives</button></div>
  <div className="stats-row"><div className="stat-panel"><span>Detected drives</span><strong>{drives.length}</strong></div>
    <div className="stat-panel"><span>Removable media</span><strong>{drives.filter(d=>d.removable).length}</strong></div>
    <div className="stat-panel"><span>Combined capacity</span><strong>{readable(drives.reduce((sum,d)=>sum+d.size,0))}</strong></div></div>
  <div className="section-head"><div><span className="section-kicker">CONNECTED STORAGE</span><h2>Available devices</h2></div></div>
  <div className="drive-grid">
   {drives.map((d,i)=><div className="drive-card panel" key={d.device||i}>
     <div className="drive-main"><div className={'drive-art '+(d.removable?'usb-art':'')}><HardDrive size={30}/></div>
       <div><h3>{d.model}</h3><span>{d.interface} · {d.partitions} partition(s)</span></div>
       <span className={'drive-badge '+(d.removable?'removable':'internal')}>{d.removable?'Removable':'Internal'}</span></div>
     <div className="drive-capacity"><strong>{readable(d.size)}</strong><span>{d.device}</span></div>
     <div className="drive-footer"><div className="drive-safe"><ShieldCheck size={15}/> Read-only information</div>
       {d.removable&&<button className="btn btn-small" onClick={flash}><Usb size={15}/> Open Imager</button>}</div>
   </div>)}
   {!busy&&drives.length===0&&<div className="empty-large full-span"><HardDrive size={38}/><h3>No storage devices returned by Windows</h3><p>Check your disk connections, then refresh. Raspberry Manager does not need administrator permissions just to list drives.</p></div>}
  </div>
  <div className="drive-warning"><AlertTriangle size={20}/><div><strong>Writing an image erases data on the chosen target.</strong>
   <p>Raspberry Manager never writes to a drive directly from this screen. The official Imager handles device selection, image verification and a separate erase confirmation.</p></div></div>
  <button className="btn btn-primary flash-wide" onClick={flash}><Usb size={18}/> Open Raspberry Pi Imager <ArrowUpRight size={16}/></button>
 </div>;
}

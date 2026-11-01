import React,{useEffect,useMemo,useState} from 'react';
import {ArrowDown,ArrowLeft,ArrowRight,ArrowUp,CheckCircle2,ChevronDown,CircleHelp,Cpu,FileCheck,HardDrive,Link2,Minus,Plus,RotateCcw,Save,Server,Settings2,ShieldAlert,Trash2,Usb,Workflow,X} from 'lucide-react';
import {BLOCK_CATALOG,cardSizes,magicDiagnostics,newMagic} from '../logic';
import type {MagicBlock} from '../types';
import Workbench from './Workbench';
type Props={toast:(message:string)=>void;flash:()=>void};
const categoryName=(kind:string)=>({OS:'Operating systems',BOOT:'Boot components',DRIVER:'Drivers',PATCH:'Patches',SERVICE:'Services',RECOVERY:'Recovery',FREE:'Storage'}[kind]||kind);
export default function Builder({toast,flash}:Props){
 const [mode,setMode]=useState<'stack'|'workbench'>('stack');
 const [blocks,setBlocks]=useState<MagicBlock[]>([]);
 const [capacity,setCapacity]=useState(64);
 const [loaded,setLoaded]=useState(false);
 const [library,setLibrary]=useState(false);
 const [ready,setReady]=useState(false);
 const [report,setReport]=useState(false);
 const [pickerFilter,setPickerFilter]=useState('');
 useEffect(()=>{window.rm.getMagic().then(x=>{setBlocks(x.blocks);setCapacity(x.capacity);setLoaded(true)}).catch(e=>toast(String(e)))},[toast]);
 useEffect(()=>{if(!loaded)return;const timer=setTimeout(()=>void window.rm.saveMagic(blocks,capacity).catch(e=>toast(String(e))),350);return ()=>clearTimeout(timer)},[blocks,capacity,loaded,toast]);
 const allocated=blocks.reduce((a,b)=>a+b.sizeGb,0);
 const reportLines=useMemo(()=>magicDiagnostics(blocks,capacity),[blocks,capacity]);
 const errors=reportLines.filter(x=>x.startsWith('ERROR:'));
 const move=(from:number,to:number)=>{if(to<0||to>=blocks.length)return;const next=[...blocks];next.splice(to,0,next.splice(from,1)[0]);setBlocks(next)};
 const patch=(id:string,change:Partial<MagicBlock>)=>setBlocks(old=>old.map(b=>b.id===id?{...b,...change}:b));
 const remove=(id:string)=>setBlocks(old=>old.filter(b=>b.id!==id).map(b=>b.dependency===id?{...b,dependency:''}:b));
 const makeTemplate=()=>{
   if(blocks.length&&!window.confirm('Replace the current draft with a basic Pi OS boot layout?'))return;
   const os=newMagic('Raspberry Pi OS','OS');os.sizeGb=16;
   const boot=newMagic('Boot A','BOOT');os.dependency=boot.id;
   setBlocks([boot,os]);toast('Raspberry Pi OS starter layout created');
 };
 if(mode==='workbench')return <Workbench toast={toast} onBack={()=>setMode('stack')}/>;
 return <div className="page builder-page">
  <div className="page-heading"><div><span className="section-kicker">IMAGE CREATION</span><h1>Pi Magic Image Designer</h1><p>Non-destructive stack planning, connected blocks and reviewable dependencies.</p></div>
    <button className="btn btn-outlined" onClick={()=>setMode('workbench')}><Workflow size={18}/> Advanced Workbench <ArrowRight size={16}/></button></div>
  <div className="designer-shell">
   <div className="designer-main">
    <div className="designer-header"><div className="title-row"><div className="quick-icon pink"><Workflow size={25}/></div><div><h2>Stack Builder</h2><p>Plan your image layout without touching a disk</p></div></div>
      <button className="btn btn-small" onClick={makeTemplate}>Create Pi OS template</button></div>
    <div className="designer-capacity"><span>Target card</span><div className="capacity-options">{cardSizes.map(v=><button key={v} onClick={()=>setCapacity(v)} className={capacity===v?'active':''}>{v}G</button>)}</div></div>
    <div className="capacity-track"><div style={{width:Math.min(100,allocated/Math.max(capacity,1)*100)+'%',background:allocated>capacity?'#ee625d':''}}/></div>
    <div className="capacity-summary"><span>{allocated} GB allocated</span><span>{Math.max(0,capacity-allocated)} GB unreserved</span></div>
    <div className="designer-label"><span>BOOT STACK</span><span>{blocks.length} blocks</span></div>
    <div className="stack-list">
      {blocks.map((b,i)=><div className="stack-block" key={b.id}>
        <div className="stack-main">
          <div className={'stack-icon '+(b.kind==='OS'?'os':'')}><Cpu size={20}/></div>
          <div><strong>{b.name}</strong><span>{categoryName(b.kind)} Â· {b.sizeGb} GB reserved</span></div>
          <button className="icon-action" title="Remove block" onClick={()=>remove(b.id)}><X size={17}/></button>
        </div>
        <div className="block-controls">
          <label>Reserved GB<input type="number" min={0} max={capacity} value={b.sizeGb} onChange={e=>patch(b.id,{sizeGb:Math.max(0,Number(e.target.value)||0)})}/></label>
          <label>Depends on<select value={b.dependency} onChange={e=>patch(b.id,{dependency:e.target.value})}><option value="">No dependency</option>{blocks.filter(other=>other.id!==b.id).map(other=><option key={other.id} value={other.id}>{other.name}</option>)}</select></label>
          <button className="icon-action" title="Move up" disabled={i===0} onClick={()=>move(i,i-1)}><ArrowUp size={16}/></button>
          <button className="icon-action" title="Move down" disabled={i===blocks.length-1} onClick={()=>move(i,i+1)}><ArrowDown size={16}/></button>
        </div>
      </div>)}
      <button className="add-stack" onClick={()=>setLibrary(true)}><Plus size={22}/> Add stack block</button>
    </div>
   </div>
   <aside className="designer-aside">
    <div className="aside-panel"><span className="section-kicker">STATUS</span><div className="designer-stat">{errors.length? <ShieldAlert size={30}/>:<CheckCircle2 size={30}/>}<strong>{errors.length?'Needs review':'Ready for review'}</strong></div><p>{errors.length?errors[0]:'Your layout is saved locally. Validate dependencies before flashing.'}</p></div>
    <div className="aside-panel"><span className="section-kicker">QUICK TOOLS</span>
      <button onClick={()=>setReport(true)}><FileCheck size={18}/> EmulateLoad diagnostic <ArrowRight size={16}/></button>
      <button onClick={()=>setMode('workbench')}><Workflow size={18}/> Open visual workbench <ArrowRight size={16}/></button>
      <button onClick={makeTemplate}><RotateCcw size={18}/> Pi OS starter layout <ArrowRight size={16}/></button>
    </div>
    <div className="aside-panel muted-note"><ShieldAlert size={18}/><p>Stack Builder is a non-destructive design tool. The real Imager chooses a compatible OS image and destination drive separately.</p></div>
   </aside>
  </div>
  <div className="designer-bottom"><div><strong>Ready for the next step?</strong><p>Review the stack, run EmulateLoad or launch the real imaging wizard.</p></div><button className="btn btn-primary" onClick={()=>setReady(true)}><CheckCircle2 size={19}/> Ready Â· Continue <ArrowRight size={18}/></button></div>
  {library&&<div className="modal-backdrop"><div className="modal library-modal">
    <div className="modal-head"><div><span className="section-kicker">STACK CATALOG</span><h2>Add a block</h2></div><button className="icon-action" onClick={()=>setLibrary(false)}><X/></button></div>
    <div className="search-field"><input placeholder="Search operating systems, drivers, bootâ€¦" value={pickerFilter} onChange={e=>setPickerFilter(e.target.value)}/></div>
    <div className="stack-catalog">{BLOCK_CATALOG.filter(([n,k])=>(n+k).toLowerCase().includes(pickerFilter.toLowerCase())).map(([n,k])=>
      <button key={n} onClick={()=>{setBlocks([...blocks,newMagic(n,k)]);setLibrary(false)}}><div className="stack-icon"><Cpu size={19}/></div><div><strong>{n}</strong><span>{categoryName(k)}</span></div><Plus size={19}/></button>)}</div>
  </div></div>}
  {ready&&<div className="modal-backdrop"><div className="modal ready-modal"><button className="dismiss-x" onClick={()=>setReady(false)}><X/></button><div className="success-ring"><CheckCircle2 size={31}/></div>
    <span className="section-kicker">NEXT STEP</span><h2>{errors.length?'Review your configuration':'Ready to continue'}</h2><p>{blocks.length} stack blocks Â· {allocated}/{capacity} GB reserved</p>
    {errors.length>0&&<div className="modal-alert">{errors.map(e=><p key={e}>{e}</p>)}</div>}
    <button className="btn btn-primary btn-wide" onClick={()=>{setReady(false);setReport(true)}}><FileCheck size={18}/> EmulateLoad Â· Run diagnostic</button>
    <button className="btn btn-outlined btn-wide" onClick={()=>{setReady(false);flash()}}><Usb size={18}/> Flash on Device Â· Open Imager</button>
    <div className="fineprint">The official Imager does not write this draft automatically; image, device and erasure must be selected and confirmed.</div>
  </div></div>}
  {report&&<div className="modal-backdrop"><div className="modal report-modal">
    <div className="modal-head"><div><span className="section-kicker">EMULATELOAD</span><h2>Diagnostic report</h2></div><button className="icon-action" onClick={()=>setReport(false)}><X/></button></div>
    <div className="report-list">{reportLines.map((line,i)=><div className={line.startsWith('ERROR:')?'report-error':''} key={i}><FileCheck size={16}/>{line}</div>)}</div>
    <div className="modal-actions"><button className="btn btn-outlined" onClick={()=>void window.rm.exportFile('Raspberry-Stack-Diagnostic.txt',reportLines.join('\n'))}><Save size={16}/> Save report</button><button className="btn btn-primary" onClick={()=>setReport(false)}>Done</button></div>
  </div></div>}
 </div>;
}

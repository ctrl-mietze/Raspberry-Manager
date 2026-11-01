import React,{useCallback,useEffect,useMemo,useRef,useState} from 'react';
import {ReactFlow,Background,Controls,MiniMap,Handle,Position,BackgroundVariant,addEdge,applyNodeChanges,type Node,type Edge,type Connection,type NodeChange} from '@xyflow/react';
import {AlignCenter,AlignHorizontalJustifyCenter,AlignLeft,ArrowLeft,BoxSelect,CheckCircle2,ChevronDown,ChevronRight,Copy,Download,FileCheck,FileJson,FileUp,FolderOpen,Grid3X3,Layers,Link2,Lock,Maximize2,Minus,MoreHorizontal,Move,Play,Plus,Redo2,RotateCcw,Save,Search,Settings2,ShieldAlert,Trash2,Undo2,Unlink,Unlock,Workflow,X} from 'lucide-react';
import {CATALOG,CATEGORY_ORDER,createNode,emptyProject,findKind,newId,newLink,parseProject,projectReport,scenarioReport,serializeProject,templates,validateProject} from '../logic';
import type {Project,WbNode,WbLink} from '../types';
const BERRY='#ef447d',dark='#221720';
const categoryColors:Record<string,string>={
  'Operating systems':'#f1729d','Boot':'#e94278','Storage':'#c6a0bc','Drivers':'#ad98d8',
  'Network':'#75b9e5','Runtimes':'#e2b982','Minecraft':'#9ec876','Services':'#c691de','Notes':'#bbb3b8'};
const groupIcons:Record<string,string>={'Operating systems':'◉','Boot':'⌁','Storage':'▣','Drivers':'⚙','Network':'◈','Runtimes':'</>','Minecraft':'▥','Services':'▤','Notes':'✎'};
function BerryNode({data,selected}:any){
  const color=categoryColors[data.category]||BERRY;
  return <div className={'flow-node '+(selected?'is-selected':'')} style={{'--node-color':color} as React.CSSProperties}>
    <Handle type="target" position={Position.Top} className="flow-handle"/>
    <div className="flow-node-label"><i style={{background:color}}/>{String(data.category).toUpperCase()}</div>
    <strong>{data.label}</strong>
    <span>{data.detail}</span>
    <small>{data.enabled?'ON':'OFF'} {data.locked?'· LOCKED':''} {data.group?'· '+data.group:''}</small>
    <Handle type="source" position={Position.Bottom} className="flow-handle"/>
  </div>;
}
const nodeTypes={berry:BerryNode};
const toFlowNodes=(p:Project):Node[]=>p.nodes.map(n=>({
  id:n.id,type:'berry',position:{x:n.x,y:n.y},draggable:!n.locked,
  data:{label:n.name,category:findKind(n.type)?.category||'Notes',
    detail:n.params.reserveMiB? n.params.reserveMiB+' MiB reserved':n.type,
    enabled:n.enabled,locked:n.locked,group:n.group}
}));
const toFlowEdges=(p:Project):Edge[]=>p.links.map(l=>({
 id:l.id,source:l.from,target:l.to,type:'smoothstep',animated:false,
 label:l.relation==='REQUIRES'?'':l.relation.toLowerCase(),
 style:{stroke:l.relation==='CONFLICT'?'#fe796a':l.relation==='FALLBACK'?'#d4b16e':BERRY,strokeWidth:2},
 labelStyle:{fill:'#ecc7d7',fontSize:10},labelBgStyle:{fill:'#20171e'},markerEnd:{type:'arrowclosed',color:BERRY}
}));
const escapeXml=(v:string)=>v.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
function svgExport(p:Project){
  const minX=Math.min(0,...p.nodes.map(n=>n.x)),minY=Math.min(0,...p.nodes.map(n=>n.y));
  const width=Math.max(900,...p.nodes.map(n=>n.x+300))-minX+70,height=Math.max(520,...p.nodes.map(n=>n.y+168))-minY+70;
  const X=(x:number)=>x-minX+30,Y=(y:number)=>y-minY+30;
  const lines=p.links.map(l=>{
    const a=p.nodes.find(n=>n.id===l.from),b=p.nodes.find(n=>n.id===l.to);
    return a&&b?'<path d="M '+(X(a.x)+112)+' '+(Y(a.y)+112)+' L '+(X(b.x)+112)+' '+Y(b.y)+'" stroke="#e94278" stroke-width="2" fill="none" marker-end="url(#arr)"/>':'';
  }).join('');
  const blocks=p.nodes.map(n=>'<g><rect x="'+X(n.x)+'" y="'+Y(n.y)+'" width="224" height="112" rx="15" fill="#241820" stroke="#d13e76"/><text x="'+(X(n.x)+13)+'" y="'+(Y(n.y)+31)+'" fill="#e96897" font-family="Arial" font-size="10">'+escapeXml(findKind(n.type)?.category||n.type)+'</text><text x="'+(X(n.x)+13)+'" y="'+(Y(n.y)+65)+'" fill="#fff" font-family="Arial" font-size="17">'+escapeXml(n.name.slice(0,22))+'</text><text x="'+(X(n.x)+13)+'" y="'+(Y(n.y)+91)+'" fill="#c8b9c2" font-family="Arial" font-size="11">'+escapeXml(n.enabled?'ON':'OFF')+'</text></g>').join('');
  return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 '+width+' '+height+'"><defs><marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="5" markerHeight="5" orient="auto"><path d="M0,0 L10,5 L0,10" fill="#e94278"/></marker></defs><rect width="100%" height="100%" fill="#09090d"/>'+lines+blocks+'</svg>';
}
function arrange(nodes:WbNode[],ids:Set<string>,mode:string){
  const chosen=nodes.filter(n=>ids.has(n.id)&&!n.locked).sort((a,b)=>a.y-b.y||a.x-b.x);
  if(!chosen.length)return nodes;
  const left=Math.min(...chosen.map(n=>n.x)),top=Math.min(...chosen.map(n=>n.y));
  const right=Math.max(...chosen.map(n=>n.x)),bottom=Math.max(...chosen.map(n=>n.y));
  const positions=new Map<string,{x:number;y:number}>();
  chosen.forEach((n,i)=>{
    let x=n.x,y=n.y;
    if(mode==='row'){x=left+i*280;y=top}
    if(mode==='column'){x=left;y=top+i*172}
    if(mode==='grid'){x=left+(i%3)*280;y=top+Math.floor(i/3)*172}
    if(mode==='left')x=left;
    if(mode==='top')y=top;
    if(mode==='right')x=right;
    if(mode==='bottom')y=bottom;
    if(mode==='spreadX')x=left+(i*280);
    if(mode==='spreadY')y=top+(i*172);
    positions.set(n.id,{x,y});
  });
  return nodes.map(n=>positions.has(n.id)?{...n,...positions.get(n.id)}:n);
}
type Toast=(s:string)=>void;
export default function Workbench({toast,onBack}:{toast:Toast;onBack:()=>void}){
 const [project,setProject]=useState<Project>(emptyProject);
 const [loaded,setLoaded]=useState(false);
 const [nodes,setNodes]=useState<Node[]>([]);
 const [selected,setSelected]=useState<string[]>([]);
 const [category,setCategory]=useState('All');
 const [query,setQuery]=useState('');
 const [projectModal,setProjectModal]=useState(false);
 const [templatesModal,setTemplatesModal]=useState(false);
 const [toolsModal,setToolsModal]=useState(false);
 const [inspect,setInspect]=useState(true);
 const [sidebar,setSidebar]=useState(true);
 const [diagnostic,setDiagnostic]=useState<'validate'|'simulate'|'preview'|''>('');
 const [projectList,setProjectList]=useState<{id:string;name:string;modified:number;nodes:number}[]>([]);
 const [simulated,setSimulated]=useState<string[]>([]);
 const [showSettings,setShowSettings]=useState(false);
 const [reportCopied,setReportCopied]=useState(false);
 const [multi,setMulti]=useState(false);
 const undo=useRef<Project[]>([]),redo=useRef<Project[]>([]);
 const initial=useRef(false);
 const selectedNode=project.nodes.find(n=>n.id===selected[0]);
 const problems=useMemo(()=>validateProject(project),[project]);
 const errors=problems.filter(p=>p.severity==='error');
 const change=(next:Project,history=true)=>{
   if(history){undo.current=[...undo.current.slice(-59),project];redo.current=[];}
   setProject(next);
 };
 useEffect(()=>{
   window.rm.listProjects().then(async result=>{
     setProjectList(result);
     if(result.length){
       const loaded=await window.rm.loadProject(result.sort((a,b)=>b.modified-a.modified)[0].id);
       if(loaded)setProject(loaded);
     }
     setLoaded(true);initial.current=true;
   }).catch(e=>{toast('Workbench: '+String(e));setLoaded(true)});
 },[toast]);
 useEffect(()=>{setNodes(toFlowNodes(project))},[project.nodes]);
 useEffect(()=>{
   if(!loaded||!initial.current)return;
   const timer=setTimeout(()=>void window.rm.saveProject(project).catch(e=>toast('Autosave: '+String(e))),550);
   return ()=>clearTimeout(timer);
 },[project,loaded,toast]);
 const add=(type:string)=>{
   if(project.nodes.length>=256){toast('256 block limit');return}
   const offset=project.nodes.length%12;
   const n=createNode(type,90+(offset%4)*265,65+Math.floor(offset/4)*168);
   change({...project,nodes:[...project.nodes,n]});setSelected([n.id]);
 };
 const remove=()=>{
   if(!selected.length)return;
   const ids=new Set(selected);
   change({...project,nodes:project.nodes.filter(n=>!ids.has(n.id)),links:project.links.filter(l=>!ids.has(l.from)&&!ids.has(l.to))});
   setSelected([]);
 };
 const duplicate=()=>{
   if(!selected.length)return;
   const ids=new Set(selected);
   const chosen=project.nodes.filter(n=>ids.has(n.id));
   if(project.nodes.length+chosen.length>256){toast('Block limit reached');return}
   const newIds=Object.fromEntries(chosen.map(n=>[n.id,newId()]));
   const copies=chosen.map(n=>({...n,id:newIds[n.id],name:(n.name+' copy').slice(0,100),x:n.x+42,y:n.y+42,locked:false}));
   const edges=project.links.filter(l=>ids.has(l.from)&&ids.has(l.to)).map(l=>({...l,id:newId(),from:newIds[l.from],to:newIds[l.to]}));
   change({...project,nodes:[...project.nodes,...copies],links:[...project.links,...edges]});
   setSelected(copies.map(n=>n.id));
 };
 const undoAction=()=>{
   const snapshot=undo.current.pop();if(!snapshot)return;
   redo.current.push(project);setProject(snapshot);setSelected([]);
 };
 const redoAction=()=>{
   const snapshot=redo.current.pop();if(!snapshot)return;
   undo.current.push(project);setProject(snapshot);setSelected([]);
 };
 const updateNode=(id:string,update:Partial<WbNode>)=>{
   change({...project,nodes:project.nodes.map(n=>n.id===id?{...n,...update}:n)});
 };
 const reposition=(id:string,x:number,y:number)=>{
   if(project.nodes.find(n=>n.id===id)?.locked)return;
   change({...project,nodes:project.nodes.map(n=>n.id===id?{...n,x:project.snap?Math.round(x/16)*16:x,y:project.snap?Math.round(y/16)*16:y}:n)});
 };
 const layout=(mode:string)=>change({...project,nodes:arrange(project.nodes,new Set(selected.length?selected:project.nodes.map(n=>n.id)),mode)});
 const exportJson=async()=>{
   await window.rm.exportFile('Raspberry-Workbench-'+project.id.slice(0,8)+'.rwb.json',serializeProject(project));
 };
 const importProject=async()=>{
   try{const content=await window.rm.importFile();if(!content)return;
     const imported=parseProject(content);
     change({...imported,id:newId(),name:imported.name+' (imported)'});setSelected([]);
     toast('Project imported; original remains unchanged.');
   }catch(e){toast(String(e))}
 };
 const selectTemplate=(p:Project)=>{
   change(p);setSelected([]);setTemplatesModal(false);toast('Template loaded as a new workspace.');
 };
 const newProject=()=>{change(emptyProject());setSelected([]);setProjectModal(false)};
 const useProjects=async()=>{
   setProjectList((await window.rm.listProjects()).sort((a,b)=>b.modified-a.modified));setProjectModal(true);
 };
 const selectSaved=async(id:string)=>{
   const saved=await window.rm.loadProject(id);if(saved){setProject(saved);undo.current=[];redo.current=[];setSelected([]);}
   setProjectModal(false);
 };
 const report=diagnostic==='simulate'?scenarioReport(project,simulated):diagnostic==='preview'?serializeProject(project):projectReport(project);
 const onNodeChange=useCallback((changes:NodeChange[])=>setNodes(current=>applyNodeChanges(changes,current)),[]);
 const onConnect=useCallback((connection:Connection)=>{
   if(!connection.source||!connection.target||connection.source===connection.target)return;
   if(project.links.length>=1024){toast('Connection limit reached');return}
   if(project.links.some(l=>l.from===connection.source&&l.to===connection.target))return;
   change({...project,links:[...project.links,newLink(connection.source,connection.target)]});
 },[project,toast]);
 const selectedSet=new Set(selected);
 const catalog=CATALOG.filter(k=>(category==='All'||k.category===category)&&
   (k.title+' '+k.id+' '+k.category).toLowerCase().includes(query.toLowerCase()));
 return <div className="workbench">
   <div className="workbench-top">
     <button className="icon-action" title="Back to Stack Builder" onClick={onBack}><ArrowLeft/></button>
     <div className="workbench-emblem"><Workflow size={22}/></div>
     <div className="workbench-brand"><strong>Pi Magic Workbench</strong><span>{project.name}</span></div>
     <div className="wb-actions">
       <button className="btn btn-small" onClick={()=>setTemplatesModal(true)}><Layers size={15}/> Templates</button>
       <button className="btn btn-small" onClick={()=>void useProjects()}><FolderOpen size={15}/> Projects</button>
       <button className="btn btn-small" onClick={()=>setShowSettings(true)}><Settings2 size={15}/> Project</button>
       <button className="btn btn-primary btn-small" onClick={()=>void exportJson()}><Download size={15}/> Export</button>
     </div>
   </div>
   <div className="wb-toolbar">
     <button title="Toggle component library" className={sidebar?'active':''} onClick={()=>setSidebar(!sidebar)}><Layers size={17}/> Library</button>
     <button onClick={()=>setToolsModal(true)}><Plus size={17}/> Add</button>
     <button disabled={!selected.length} onClick={()=>setInspect(!inspect)}><Settings2 size={17}/> Inspect</button>
     <button disabled={!selected.length} onClick={duplicate}><Copy size={17}/> Duplicate</button>
     <button disabled={!selected.length} onClick={remove}><Trash2 size={17}/> Delete</button>
     <div className="wb-toolbar-divider"/>
     <button disabled={!undo.current.length} onClick={undoAction}><Undo2 size={17}/> Undo</button>
     <button disabled={!redo.current.length} onClick={redoAction}><Redo2 size={17}/> Redo</button>
     <button onClick={()=>layout('grid')}><Grid3X3 size={17}/> Arrange</button>
     <button onClick={()=>setDiagnostic('validate')}><FileCheck size={17}/> Validate</button>
     <button className="wb-more" onClick={()=>setToolsModal(true)}><MoreHorizontal size={19}/> More tools</button>
   </div>
   <div className="wb-content">
     {sidebar&&<aside className="wb-library">
       <div className="library-search"><Search size={16}/><input placeholder="Find a component" value={query} onChange={e=>setQuery(e.target.value)}/></div>
       <div className="library-categories"><button className={category==='All'?'active':''} onClick={()=>setCategory('All')}>All</button>
         {CATEGORY_ORDER.map(group=><button key={group} className={category===group?'active':''} onClick={()=>setCategory(group)}>{group}</button>)}</div>
       <div className="wb-library-title">{catalog.length} COMPONENTS</div>
       <div className="library-items">
         {catalog.map(c=><button key={c.id} onClick={()=>add(c.id)} className="lib-item"><span className="lib-icon" style={{color:categoryColors[c.category]}}>{groupIcons[c.category]}</span><span><strong>{c.title}</strong><small>{c.category}</small></span><Plus size={16}/></button>)}
         {!catalog.length&&<p className="muted">No matching components.</p>}
       </div>
     </aside>}
     <div className="wb-canvas-wrap">
       <div className="wb-canvas-meta"><span>{project.nodes.length} blocks · {project.links.length} connections</span><span>{Math.max(0,project.cardMiB-project.nodes.reduce((a,n)=>a+(Number(n.params.reserveMiB)||0),0))/1024} GiB free</span><button className={project.snap?'active':''} onClick={()=>change({...project,snap:!project.snap})}>Snap {project.snap?'on':'off'}</button></div>
       <div className="wb-canvas">
         <ReactFlow nodes={nodes} edges={toFlowEdges(project)} nodeTypes={nodeTypes}
           onNodesChange={onNodeChange}
           onConnect={onConnect} onNodeClick={(_,node)=>setSelected(multi?(selected.includes(node.id)?selected.filter(s=>s!==node.id):[...selected,node.id]):[node.id])}
           onPaneClick={()=>setSelected([])}
           onNodeDragStop={(_,node)=>reposition(node.id,node.position.x,node.position.y)}
           onNodesDelete={deleted=>{const ids=new Set(deleted.map(n=>n.id));change({...project,nodes:project.nodes.filter(n=>!ids.has(n.id)),links:project.links.filter(l=>!ids.has(l.from)&&!ids.has(l.to))})}}
           onEdgesDelete={deleted=>{const ids=new Set(deleted.map(e=>e.id));change({...project,links:project.links.filter(l=>!ids.has(l.id))})}}
           fitView fitViewOptions={{padding:.27,maxZoom:1.35}} snapToGrid={project.snap} snapGrid={[16,16]}
           nodesConnectable colorMode="dark" deleteKeyCode={['Delete','Backspace']} multiSelectionKeyCode={['Shift','Control']}>
           <Background variant={BackgroundVariant.Dots} color="#513146" gap={28} size={1.2}/>
           <MiniMap zoomable pannable nodeColor={node=>categoryColors[String(node.data.category)]||BERRY} maskColor="rgba(16,11,19,.77)" className="wb-minimap"/>
           <Controls showInteractive={false} className="wb-controls"/>
         </ReactFlow>
         {project.nodes.length===0&&<div className="canvas-empty"><Workflow size={35}/><h3>Your Raspberry workspace</h3><p>Choose a component or a project template to get started.</p><button className="btn btn-primary btn-small" onClick={()=>setTemplatesModal(true)}>Choose template</button></div>}
       </div>
       <div className="wb-status"><span>{selectedNode?.name||'No block selected'}</span><span>{problems.length?problems.length+' validation finding(s)':'Ready · Local autosave'}</span><button className="btn btn-small" onClick={()=>setDiagnostic('validate')}><ShieldAlert size={14}/> Validate</button></div>
     </div>
     {inspect&&<aside className="wb-inspector">
       <div className="inspector-head"><span>BLOCK INSPECTOR</span><button className="icon-action" onClick={()=>setInspect(false)}><X size={17}/></button></div>
       {selectedNode?<div className="inspector-body">
          <div className="inspector-intro"><div className="lib-icon" style={{color:categoryColors[findKind(selectedNode.type)?.category||'']}}>{groupIcons[findKind(selectedNode.type)?.category||'Notes']}</div>
            <div><strong>{findKind(selectedNode.type)?.title||selectedNode.type}</strong><span>{findKind(selectedNode.type)?.category}</span></div></div>
          <label>Block name<input value={selectedNode.name} onChange={e=>updateNode(selectedNode.id,{name:e.target.value.slice(0,100)})}/></label>
          <div className="mini-switches">
            <label><input type="checkbox" checked={selectedNode.enabled} onChange={e=>updateNode(selectedNode.id,{enabled:e.target.checked})}/> Enabled</label>
            <label><input type="checkbox" checked={selectedNode.locked} onChange={e=>updateNode(selectedNode.id,{locked:e.target.checked})}/> Lock position</label>
            <label><input type="checkbox" checked={selectedNode.optional} onChange={e=>updateNode(selectedNode.id,{optional:e.target.checked})}/> Optional</label>
          </div>
          <label>Group<input value={selectedNode.group} onChange={e=>updateNode(selectedNode.id,{group:e.target.value.slice(0,80)})}/></label>
          <label>Scheduling priority<input type="number" min={0} max={999} value={selectedNode.priority} onChange={e=>updateNode(selectedNode.id,{priority:Math.min(999,Math.max(0,Number(e.target.value)||0))})}/></label>
          <label>Notes<textarea rows={3} value={selectedNode.notes} onChange={e=>updateNode(selectedNode.id,{notes:e.target.value.slice(0,4096)})}/></label>
          <h4>Component settings</h4>
          {Object.entries(selectedNode.params).map(([key,value])=><label key={key}>{key.replace(/([A-Z])/g,' $1')}
            {['true','false'].includes(value)?
              <select value={value} onChange={e=>updateNode(selectedNode.id,{params:{...selectedNode.params,[key]:e.target.value}})}><option>true</option><option>false</option></select>:
              <input value={value} onChange={e=>updateNode(selectedNode.id,{params:{...selectedNode.params,[key]:e.target.value.slice(0,2048)}})}/>}
          </label>)}
          <div className="inspector-actions">
            <button className="btn btn-small" onClick={duplicate}><Copy size={15}/> Duplicate</button>
            <button className="btn btn-small" onClick={remove}><Trash2 size={15}/> Delete</button>
          </div>
       </div>:<div className="inspector-empty"><Settings2 size={30}/><h3>Select a block</h3><p>Move, connect or double-click a component to edit its settings.</p></div>}
     </aside>}
   </div>
   {templatesModal&&<div className="modal-backdrop"><div className="modal templates-modal"><div className="modal-head"><div><span className="section-kicker">PROJECT STARTERS</span><h2>Workspace templates</h2></div><button className="icon-action" onClick={()=>setTemplatesModal(false)}><X/></button></div>
      <div className="template-grid">{Object.entries(templates()).map(([name,p])=><button key={name} onClick={()=>selectTemplate(p)}><Layers size={25}/><strong>{name}</strong><span>{p.nodes.length} blocks · Reviewable plan</span><ChevronRight size={17}/></button>)}</div></div></div>}
   {projectModal&&<div className="modal-backdrop"><div className="modal projects-modal"><div className="modal-head"><div><span className="section-kicker">YOUR DOCUMENTS</span><h2>Saved workspaces</h2></div><button className="icon-action" onClick={()=>setProjectModal(false)}><X/></button></div>
      <div className="modal-actions"><button className="btn btn-primary" onClick={newProject}><Plus size={16}/> New project</button><button className="btn btn-outlined" onClick={()=>void importProject()}><FileUp size={16}/> Import Android/Windows JSON</button></div>
      <div className="saved-projects">{projectList.map(p=><button key={p.id} onClick={()=>void selectSaved(p.id)}><FolderOpen size={23}/><div><strong>{p.name}</strong><span>{p.nodes} blocks · {new Date(p.modified).toLocaleString()}</span></div><ChevronRight size={17}/></button>)}</div>
   </div></div>}
   {showSettings&&<div className="modal-backdrop"><div className="modal project-settings-modal"><div className="modal-head"><div><span className="section-kicker">TARGET ENVIRONMENT</span><h2>Project settings</h2></div><button className="icon-action" onClick={()=>setShowSettings(false)}><X/></button></div>
     <div className="settings-grid">
       <label>Project name<input value={project.name} onChange={e=>change({...project,name:e.target.value.slice(0,120)})}/></label>
       <label>Target board<select value={project.board} onChange={e=>change({...project,board:e.target.value})}>{['Pi 5','Pi 4','Pi 3','Pi Zero 2 W','External x86_64 host'].map(x=><option key={x}>{x}</option>)}</select></label>
       <label>Architecture<select value={project.architecture} onChange={e=>change({...project,architecture:e.target.value})}>{['arm64','arm32','x86_64'].map(x=><option key={x}>{x}</option>)}</select></label>
       <label>Boot source<select value={project.bootSource} onChange={e=>change({...project,bootSource:e.target.value})}>{['sd','usb','nvme','network'].map(x=><option key={x}>{x}</option>)}</select></label>
       <label>Storage (MiB)<input type="number" min={512} value={project.cardMiB} onChange={e=>change({...project,cardMiB:Math.max(512,Number(e.target.value)||512)})}/></label>
       <label>RAM (MiB)<input type="number" min={128} value={project.memoryMiB} onChange={e=>change({...project,memoryMiB:Math.max(128,Number(e.target.value)||128)})}/></label>
       <label>Network<select value={project.network} onChange={e=>change({...project,network:e.target.value})}><option>online</option><option>offline</option></select></label>
       <label>Target OS<select value={project.targetOs} onChange={e=>change({...project,targetOs:e.target.value})}><option>linux</option><option>android</option><option>other</option></select></label>
       <label>Active OS<select value={project.activeOsId} onChange={e=>change({...project,activeOsId:e.target.value})}><option value="">Not selected</option>{project.nodes.filter(n=>n.type.startsWith('os.')).map(n=><option key={n.id} value={n.id}>{n.name}</option>)}</select></label>
     </div><button className="btn btn-primary btn-wide" onClick={()=>setShowSettings(false)}><CheckCircle2 size={18}/> Done</button>
   </div></div>}
   {toolsModal&&<div className="modal-backdrop"><div className="modal wb-tools-modal"><div className="modal-head"><div><span className="section-kicker">VISUAL EDITOR</span><h2>Workbench tools</h2></div><button className="icon-action" onClick={()=>setToolsModal(false)}><X/></button></div>
      <div className="wb-tools-list">
        {[
          ['Duplicate selection','Copy',duplicate],['Delete selection','Delete',remove],
          ['Select all','Select',()=>setSelected(project.nodes.map(n=>n.id))],['Select connected','Connect',()=>setSelected([...new Set([...selected,...project.links.filter(l=>selectedSet.has(l.from)||selectedSet.has(l.to)).flatMap(l=>[l.from,l.to])])])],
          ['Group selection','Group',()=>change({...project,nodes:project.nodes.map(n=>selectedSet.has(n.id)?{...n,group:'Group '+(new Date().getMinutes())}:n)})],
          ['Ungroup selection','Ungroup',()=>change({...project,nodes:project.nodes.map(n=>selectedSet.has(n.id)?{...n,group:''}:n)})],
          ['Arrange grid','Layout',()=>layout('grid')],['Arrange row','Layout',()=>layout('row')],['Arrange column','Layout',()=>layout('column')],
          ['Align left','Align',()=>layout('left')],['Align right','Align',()=>layout('right')],['Align top','Align',()=>layout('top')],['Align bottom','Align',()=>layout('bottom')],
          ['Distribute horizontally','Arrange',()=>layout('spreadX')],['Distribute vertically','Arrange',()=>layout('spreadY')],
          ['Lock selection','Lock',()=>change({...project,nodes:project.nodes.map(n=>selectedSet.has(n.id)?{...n,locked:true}:n)})],
          ['Unlock selection','Lock',()=>change({...project,nodes:project.nodes.map(n=>selectedSet.has(n.id)?{...n,locked:false}:n)})],
          ['Toggle multi-select','Selection',()=>setMulti(!multi)],
          ['Export JSON','Export',()=>void exportJson()],['Export SVG','Export',()=>void window.rm.exportFile('Raspberry-Workbench.svg',svgExport(project))],
          ['Import project','Import',()=>void importProject()],['Diagnostic report','Check',()=>setDiagnostic('validate')],
          ['EmulateLoad simulation','Check',()=>setDiagnostic('simulate')],['Preview JSON','Check',()=>setDiagnostic('preview')],
        ].map(([label,group,fn],i)=><button key={i} onClick={()=>{(fn as ()=>void)();setToolsModal(false)}}><span>{label as string}</span><small>{group as string}</small><ChevronRight size={16}/></button>)}
      </div>
   </div></div>}
   {diagnostic&&<div className="modal-backdrop"><div className="modal diagnostics-modal"><div className="modal-head"><div><span className="section-kicker">WORKBENCH ANALYSIS</span><h2>Validate & preview</h2></div><button className="icon-action" onClick={()=>setDiagnostic('')}><X/></button></div>
     <div className="section-tabs compact">
       <button className={diagnostic==='validate'?'active':''} onClick={()=>setDiagnostic('validate')}>Analysis</button>
       <button className={diagnostic==='simulate'?'active':''} onClick={()=>setDiagnostic('simulate')}>EmulateLoad</button>
       <button className={diagnostic==='preview'?'active':''} onClick={()=>setDiagnostic('preview')}>Project JSON</button>
     </div>
     {diagnostic==='simulate'&&<div className="simulation-failures"><span>Simulate a failed component:</span>{project.nodes.map(n=><label key={n.id}><input type="checkbox" checked={simulated.includes(n.id)} onChange={e=>setSimulated(e.target.checked?[...simulated,n.id]:simulated.filter(x=>x!==n.id))}/>{n.name}</label>)}</div>}
     <div className="diagnostic-summary"><span className={errors.length?'danger':''}>{errors.length} errors</span><span>{problems.filter(x=>x.severity==='warning').length} warnings</span><span>{project.nodes.length} blocks</span></div>
     <pre className="diagnostics-text">{report}</pre>
     <div className="modal-actions"><button className="btn btn-outlined" onClick={()=>void window.rm.exportFile('Raspberry-Workbench-report.txt',report)}><Download size={16}/> Save to disk</button>
       <button className="btn btn-primary" onClick={()=>{void navigator.clipboard.writeText(report).then(()=>toast('Report copied'))}}><Copy size={16}/> Copy report</button></div>
   </div></div>}
 </div>;
}

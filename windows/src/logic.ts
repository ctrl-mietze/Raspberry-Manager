import catalogData from './catalog.json';
import type {CatalogItem,MagicBlock,Project,WbNode,WbLink} from './types';
export const CATALOG=catalogData as CatalogItem[];
export const CATEGORY_ORDER=['Operating systems','Boot','Storage','Drivers','Network','Runtimes','Minecraft','Services','Notes'];
export const newId=()=>globalThis.crypto?.randomUUID?.()||('m'+Math.random().toString(36).slice(2)+Date.now().toString(36));
export const cardSizes=[16,32,64,128,256,512];
export const BLOCK_CATALOG:[string,string][]=[
  ['Raspberry Pi OS','OS'],['Ubuntu','OS'],['DietPi','OS'],['Home Assistant OS','OS'],
  ['LibreELEC','OS'],['Kali Linux','OS'],['Android','OS'],
  ['Boot A','BOOT'],['Boot B','BOOT'],['Kernel / initramfs','BOOT'],
  ['EEPROM boot config','BOOT'],['Boot patch','PATCH'],['DTB overlay','DRIVER'],
  ['USB driver','DRIVER'],['Wi-Fi driver','DRIVER'],['Service partition','SERVICE'],
  ['Recovery image (optional)','RECOVERY'],['Free Space','FREE']
];
export const newMagic=(name:string,kind:string):MagicBlock=>({
  id:newId(),name,kind,sizeGb:['OS','SERVICE','RECOVERY'].includes(kind)?8:0,dependency:''
});
export const magicDiagnostics=(blocks:MagicBlock[],capacity:number):string[]=>{
  const messages:string[]=[];
  if(blocks.length===0)messages.push('Add a boot component to start your image layout.');
  if(!blocks.some(b=>b.kind==='OS'))messages.push('No operating system configured.');
  const allocated=blocks.reduce((a,b)=>a+b.sizeGb,0);
  if(allocated>capacity)messages.push('ERROR: '+(allocated-capacity)+' GB over card capacity.');
  const lookup=new Map(blocks.map(b=>[b.id,b]));
  for(const block of blocks){
    if(block.dependency&&!lookup.has(block.dependency))messages.push('ERROR: '+block.name+' requires a missing block.');
    let current:MagicBlock|undefined=block;
    const seen=new Set<string>();
    while(current?.dependency){
      if(seen.has(current.id)){messages.push('ERROR: Circular dependency near '+block.name+'.');break}
      seen.add(current.id);current=lookup.get(current.dependency);
    }
  }
  if(blocks.filter(b=>b.kind==='OS').length>1&&!blocks.some(b=>b.kind==='BOOT'))messages.push('Multi-OS plans need an actual boot selector.');
  if(messages.length===0)messages.push('Basic allocation and dependencies are valid.');
  messages.push('EmulateLoad is a planning/dry-run diagnostic, not a real Raspberry Pi boot.');
  messages.push('Hardware compatibility and image files remain unverified.');
  return [...new Set(messages)];
};
const defaultParams=(type:string):Record<string,string>=>{
  if(type.startsWith('os.'))return {reserveMiB:'16384',architecture:'arm64',version:'manual',sourceUrl:'',sha256:''};
  if(type==='storage.fat32')return {reserveMiB:'512',label:'BOOT',mount:'/boot/firmware'};
  if(type.startsWith('storage.'))return {reserveMiB:'1024',label:'',mount:''};
  if(type.startsWith('boot.'))return {path:'',slot:'A',sourceUrl:''};
  if(type==='runtime.java')return {major:'21',distribution:'OpenJDK',reserveMiB:'0'};
  if(type==='runtime.docker')return {reserveMiB:'1024'};
  if(type.startsWith('mc.'))return {port:type==='mc.java'||type==='mc.bukkit'||type==='mc.mod.forge'||type==='mc.mod.fabric'?'25565':'19132',
    memoryMiB:'2048',reserveMiB:'1024',maxPlayers:'10',mode:'survival',difficulty:'normal',
    world:'world',javaMajor:'21',eula:'false',sourceUrl:'',sha256:''};
  if(type.startsWith('net.'))return {interface:type==='net.ethernet'?'eth0':'wlan0',mode:'dhcp'};
  if(type.startsWith('service.'))return {autostart:'true',restart:'on-failure',memoryMiB:'128'};
  return {};
};
export function createNode(type:string,x:number,y:number):WbNode {
  const kind=CATALOG.find(k=>k.id===type);
  return {id:newId(),type,name:kind?.title||type,x,y,enabled:true,optional:false,locked:false,
    group:'',notes:'',priority:50,params:defaultParams(type),conditions:[]};
}
export const emptyProject=():Project=>({
  format:'raspberry-workbench',schema:1,id:newId(),name:'My Raspberry workspace',
  board:'Pi 5',architecture:'arm64',cardMiB:65536,memoryMiB:8192,
  bootSource:'sd',network:'online',targetOs:'linux',activeOsId:'',
  grid:true,snap:true,minimap:true,nodes:[],links:[]
});
export const newLink=(from:string,to:string,relation:WbLink['relation']='REQUIRES'):WbLink=>({id:newId(),from,to,relation});
export const findKind=(id:string)=>CATALOG.find(k=>k.id===id);
export function templates():Record<string,Project>{
  const out:Record<string,Project>={};
  out['Empty canvas']=emptyProject();
  const create=(name:string,extra:string[]=[])=>{
    const p=emptyProject();p.name=name;
    const types=['boot.a','os.pi64','net.ethernet',...extra];
    p.nodes=types.map((type,i)=>createNode(type,70+(i%3)*300,80+Math.floor(i/3)*205));
    p.links=p.nodes.slice(1).map((n,i)=>newLink(p.nodes[i].id,n.id));
    return p;
  };
  out['Pi server base']=create('Pi server base',['service.ssh','service.backup']);
  out['Minecraft Java']=create('Minecraft Java',['runtime.java','mc.java']);
  out['Minecraft Bedrock · Nukkit']=create('Minecraft Bedrock · Nukkit',['runtime.java','mc.nukkit']);
  out['Media center']=create('Media center',['server.media']);
  out['Network services']=create('Network services',['service.ssh','net.firewall','net.dns']);
  out['Raspberry Pi OS + Ubuntu']=create('Two OS choices',['boot.selector','os.ubuntu']);
  out['Custom Android image']=create('Custom Android image',['storage.fat32','storage.ext4']);
  const android=out['Custom Android image'].nodes.find(n=>n.type==='os.pi64');
  if(android){android.type='os.android';android.name='Android';}
  return out;
}
export type Finding={severity:'error'|'warning'|'info';text:string;node?:string};
export function validateProject(p:Project):Finding[]{
  const result:Finding[]=[];
  const issue=(severity:Finding['severity'],text:string,node?:string)=>result.push({severity,text,node});
  const ids=new Set(p.nodes.map(n=>n.id));
  if(p.nodes.length===0)issue('warning','Empty workbench. Add an operating system or service.');
  if(p.nodes.length>256)issue('error','The workbench supports at most 256 blocks.');
  if(p.links.length>1024)issue('error','Too many connections.');
  if(ids.size!==p.nodes.length)issue('error','Duplicate block identifiers.');
  for(const l of p.links){
    if(!ids.has(l.from)||!ids.has(l.to))issue('error','Connection references a missing block.');
    if(l.from===l.to)issue('error','A block cannot depend on itself.',l.from);
  }
  const active=p.nodes.filter(n=>n.enabled);
  const os=active.filter(n=>n.type.startsWith('os.'));
  if(os.length>1&&!p.activeOsId)issue('error','Multiple active operating systems require choosing an active OS.');
  if(p.activeOsId&&!os.some(n=>n.id===p.activeOsId))issue('error','Selected active OS does not exist.');
  const reserved=active.reduce((sum,n)=>sum+Math.max(0,Number(n.params.reserveMiB)||0),0);
  if(reserved>p.cardMiB)issue('error','Storage over capacity: '+Math.ceil((reserved-p.cardMiB)/1024)+' GiB.');
  const memory=active.reduce((sum,n)=>sum+Math.max(0,Number(n.params.memoryMiB)||0),0);
  if(memory>p.memoryMiB)issue('error','Planned memory budgets exceed Raspberry RAM.');
  for(const n of active){
    if(n.type==='mc.bedrock'&&p.architecture!=='x86_64')issue('error','Official Bedrock Dedicated Server needs x86_64; Raspberry ARM cannot run it natively.',n.id);
    if(n.type.startsWith('mc.')&&n.type!=='mc.bedrock'&&!active.some(x=>x.type==='runtime.java'))issue('warning',n.name+' needs a matching Java runtime.',n.id);
    if(n.type.startsWith('os.')&&n.params.architecture&&n.params.architecture!==p.architecture)issue('warning',n.name+' architecture differs from project target.',n.id);
    if(n.type==='mc.java'&&n.params.eula!=='true')issue('warning','Minecraft EULA has not been accepted; do not launch server.',n.id);
    if(n.conditions.length>16)issue('error','Too many conditions for '+n.name,n.id);
  }
  for(const l of p.links.filter(l=>l.relation==='CONFLICT')){
    if(active.some(n=>n.id===l.from)&&active.some(n=>n.id===l.to))issue('error','Two conflicting blocks are both enabled.',l.from);
  }
  const adjacency=new Map<string,string[]>();
  for(const l of p.links.filter(l=>['REQUIRES','BEFORE','AFTER'].includes(l.relation))){
    const from=l.relation==='AFTER'?l.to:l.from,to=l.relation==='AFTER'?l.from:l.to;
    adjacency.set(from,[...(adjacency.get(from)||[]),to]);
  }
  const visit=new Set<string>(),stack=new Set<string>();
  const walk=(id:string):boolean=>{
    if(stack.has(id))return true;if(visit.has(id))return false;visit.add(id);stack.add(id);
    for(const next of adjacency.get(id)||[]){if(walk(next))return true}
    stack.delete(id);return false;
  };
  if(p.nodes.some(n=>walk(n.id)))issue('error','Circular required boot/start dependencies detected.');
  return result;
}
export function projectReport(p:Project){
  const result=validateProject(p);
  const reserved=p.nodes.filter(n=>n.enabled).reduce((sum,n)=>sum+(Number(n.params.reserveMiB)||0),0);
  return ['RASPBERRY MANAGER WORKBENCH','Project: '+p.name,'Board: '+p.board+' · '+p.architecture,
    'Memory: '+p.memoryMiB+' MiB','Storage: '+p.cardMiB+' MiB, planned '+reserved+' MiB',
    'Boot: '+p.bootSource,'Blocks: '+p.nodes.length+' · Connections: '+p.links.length,
    '','VALIDATION',...result.map(x=>'['+x.severity.toUpperCase()+'] '+x.text),
    ...(result.length?[]:['No validation errors detected.']),
    '','BLOCKS',...p.nodes.map(n=>'• '+n.name+' ['+n.type+'] '+(n.enabled?'ON':'OFF')),
    '','CONNECTIVITY',
    'Physical firmware boot, actual partition creation and live process execution are not simulated.',
    'Exported declarations are not automatically deployable Raspberry Pi images.'].join('\n');
}
export function scenarioReport(p:Project,failedIds:string[]){
  const errors=validateProject(p).filter(f=>f.severity==='error');
  if(errors.length)return 'Scenario blocked by '+errors.length+' validation error(s).\n'+errors.map(x=>x.text).join('\n');
  const failed=new Set(failedIds);
  const lines=['EMULATELOAD · DEPENDENCY DRY-RUN','No real Pi firmware or server is started.'];
  for(const node of p.nodes.filter(n=>n.enabled)){
    if(failed.has(node.id)){lines.push('SIMULATED FAILURE · '+node.name);
      p.links.filter(l=>l.from===node.id&&l.relation==='FALLBACK').forEach(l=>{
        const target=p.nodes.find(n=>n.id===l.to);
        if(target&&target.enabled)lines.push('PLANNED FALLBACK · '+target.name);
      });
    }else if(!p.links.some(l=>l.to===node.id&&l.relation==='FALLBACK'))
      lines.push('PLANNED START · '+node.name);
  }
  return lines.join('\n');
}
export function serializeProject(p:Project){
  const {modified:_,...exported}=p;return JSON.stringify(exported,null,2);
}
export function parseProject(raw:string):Project{
  if(raw.length>2*1024*1024)throw Error('Project too large');
  const p=JSON.parse(raw);
  if(p.format!=='raspberry-workbench'||p.schema!==1||!Array.isArray(p.nodes)||!Array.isArray(p.links))
    throw Error('Unsupported Raspberry workbench project format.');
  if(p.nodes.length>256||p.links.length>1024)throw Error('Project exceeds limits.');
  if(!p.id||typeof p.id!=='string')throw Error('Missing project ID');
  return p as Project;
}

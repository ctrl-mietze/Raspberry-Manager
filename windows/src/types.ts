export type Profile={id:string;name:string;host:string;user:string;port:number;keyPath?:string};
export type Drive={model:string;size:number;device:string;interface:string;partitions:number;removable:boolean};
export type MagicBlock={id:string;name:string;kind:string;sizeGb:number;dependency:string};
export type CatalogItem={id:string;title:string;category:string};
export type WbNode={
  id:string;type:string;name:string;x:number;y:number;
  enabled:boolean;optional:boolean;locked:boolean;group:string;notes:string;priority:number;
  params:Record<string,string>;conditions:{key:string;operator:string;value:string}[];
  attachment?:{name:string;sha256:string;bytes:number;format:string}
};
export type WbLink={id:string;from:string;to:string;relation:'REQUIRES'|'BEFORE'|'AFTER'|'OPTIONAL'|'CONFLICT'|'FALLBACK'};
export type Project={
  format:'raspberry-workbench';schema:1;id:string;name:string;board:string;
  architecture:string;cardMiB:number;memoryMiB:number;bootSource:string;network:string;targetOs:string;
  activeOsId:string;grid:boolean;snap:boolean;minimap:boolean;
  nodes:WbNode[];links:WbLink[];modified?:number;
};
export type ConnectAction={handle:string;kind:string;label:string;enabled:boolean;reason?:string};
export type ConnectDevice={key:string;name:string;status:string;detail?:ConnectAction;shell?:ConnectAction;screen?:ConnectAction};
export type ConnectState={started:boolean;url:string;error?:string|null;auth:boolean;session:boolean;loading?:boolean;
  snapshot:{page:string;title?:string;devices:ConnectDevice[];actions:ConnectAction[];epoch:string;notice?:string;viewport?:string}};
export type SshMessage={id:string;type:'data'|'error'|'closed';data?:string;message?:string};
export type HostKeyPrompt={id:string;host:string;fingerprint:string};
export interface RaspberryApi {
  meta:()=>Promise<{version:string;imagerInstalled:boolean}>;
  getSettings:()=>Promise<{theme:string}>;saveSettings:(v:{theme:string})=>Promise<boolean>;
  listProfiles:()=>Promise<Profile[]>;saveProfile:(p:Partial<Profile>)=>Promise<Profile>;
  removeProfile:(id:string)=>Promise<boolean>;chooseKey:()=>Promise<string|null>;
  sshConnect:(id:string,secret:string)=>Promise<boolean>;sshApprove:(id:string,allow:boolean)=>Promise<boolean>;
  sshWrite:(id:string,data:string)=>Promise<boolean>;sshResize:(id:string,cols:number,rows:number)=>Promise<boolean>;
  sshDisconnect:(id:string)=>Promise<boolean>;listDrives:()=>Promise<Drive[]>;launchImager:()=>Promise<string>;
  getMagic:()=>Promise<{blocks:MagicBlock[];capacity:number}>;saveMagic:(blocks:MagicBlock[],capacity:number)=>Promise<boolean>;
  listProjects:()=>Promise<{id:string;name:string;modified:number;nodes:number}[]>;
  loadProject:(id:string)=>Promise<Project|null>;saveProject:(p:Project)=>Promise<Project>;
  removeProject:(id:string)=>Promise<boolean>;
  exportFile:(name:string,content:string)=>Promise<string|null>;importFile:()=>Promise<string|null>;
  connectStart:()=>Promise<boolean>;connectState:()=>Promise<ConnectState>;
  connectRefresh:()=>Promise<boolean>;connectViewport:(r:{x:number;y:number;width:number;height:number},visible:boolean)=>Promise<boolean>;
  connectAction:(epoch:string,handle:string)=>Promise<boolean>;
  on:(event:'ssh:message'|'ssh:hostkey'|'connect:changed',callback:(p:any)=>void)=>(()=>void);
}
declare global { interface Window{rm:RaspberryApi} }

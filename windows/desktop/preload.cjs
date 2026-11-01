'use strict';
const {contextBridge,ipcRenderer}=require('electron');
const api={
  meta:()=>ipcRenderer.invoke('app:meta'),
  getSettings:()=>ipcRenderer.invoke('settings:get'),
  saveSettings:v=>ipcRenderer.invoke('settings:save',v),
  listProfiles:()=>ipcRenderer.invoke('profiles:list'),
  saveProfile:p=>ipcRenderer.invoke('profiles:save',p),
  removeProfile:id=>ipcRenderer.invoke('profiles:remove',id),
  chooseKey:()=>ipcRenderer.invoke('ssh:key'),
  sshConnect:(id,secret)=>ipcRenderer.invoke('ssh:connect',id,secret),
  sshApprove:(id,allow)=>ipcRenderer.invoke('ssh:approve',id,allow),
  sshWrite:(id,text)=>ipcRenderer.invoke('ssh:write',id,text),
  sshResize:(id,cols,rows)=>ipcRenderer.invoke('ssh:resize',id,cols,rows),
  sshDisconnect:id=>ipcRenderer.invoke('ssh:disconnect',id),
  listDrives:()=>ipcRenderer.invoke('drives:list'),
  launchImager:()=>ipcRenderer.invoke('imager:open'),
  getMagic:()=>ipcRenderer.invoke('magic:get'),
  saveMagic:(blocks,capacity)=>ipcRenderer.invoke('magic:save',blocks,capacity),
  listProjects:()=>ipcRenderer.invoke('projects:list'),
  loadProject:id=>ipcRenderer.invoke('projects:load',id),
  saveProject:p=>ipcRenderer.invoke('projects:save',p),
  removeProject:id=>ipcRenderer.invoke('projects:remove',id),
  exportFile:(name,content)=>ipcRenderer.invoke('file:export',name,content),
  importFile:()=>ipcRenderer.invoke('file:import'),
  connectStart:()=>ipcRenderer.invoke('connect:start'),
  connectState:()=>ipcRenderer.invoke('connect:state'),
  connectRefresh:()=>ipcRenderer.invoke('connect:refresh'),
  connectViewport:(bounds,visible)=>ipcRenderer.invoke('connect:viewport',bounds,visible),
  connectAction:(epoch,handle)=>ipcRenderer.invoke('connect:action',epoch,handle),
  on:(channel,callback)=>{
    if(!['ssh:message','ssh:hostkey','connect:changed'].includes(channel))return ()=>{};
    const handler=(_event,data)=>callback(data);
    ipcRenderer.on(channel,handler);
    return ()=>ipcRenderer.removeListener(channel,handler);
  }
};
contextBridge.exposeInMainWorld('rm',Object.freeze(api));

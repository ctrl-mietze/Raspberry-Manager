'use strict';
const {app,BrowserWindow,WebContentsView,ipcMain,dialog,shell}=require('electron');
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const {execFile}=require('child_process');
const {Client}=require('ssh2');
const root=path.resolve(__dirname,'..');
const asset=name=>app.isPackaged?path.join(process.resourcesPath,'assets',name):path.join(root,'assets',name);
const dbPath=()=>path.join(app.getPath('userData'),'raspberry-state.json');
let win, web, started=false, db, lastSnapshot={page:'loading',devices:[],actions:[],epoch:''}, lastError=null;
const live=new Map(), pending=new Map(), allowed=new Set(['connect.raspberrypi.com','id.raspberrypi.com']);
const send=(name,data)=>{if(win&&!win.isDestroyed())win.webContents.send(name,data)};
function store(){
  if(db)return db;
  try{db=JSON.parse(fs.readFileSync(dbPath(),'utf8'))}catch{db={}}
  db={profiles:[],projects:[],magic:[],capacity:64,settings:{theme:'berry'},trusted:{},...db};return db;
}
function save(){
  const p=dbPath();fs.mkdirSync(path.dirname(p),{recursive:true});
  fs.writeFileSync(p+'.tmp',JSON.stringify(store(),null,2));fs.renameSync(p+'.tmp',p);
}
function safeUrl(raw){
  try{const u=new URL(raw);return u.protocol==='https:'&&allowed.has(u.hostname)&&(u.port===''||u.port==='443')&&!u.username&&!u.password}catch{return false}
}
function officialImager(){
  const base=process.env.ProgramFiles||'C:\\Program Files';
  return [
    path.join(base,'Raspberry Pi Imager','rpi-imager.exe'),
    path.join(base,'Raspberry Pi Imager','Raspberry Pi Imager.exe'),
    path.join(process.env.LOCALAPPDATA||'','Programs','Raspberry Pi Imager','rpi-imager.exe')
  ].find(fs.existsSync);
}
async function openImager(){
  const installed=officialImager();
  if(installed){const error=await shell.openPath(installed);if(error)throw Error(error);return 'launched';}
  const included=asset('imager-v2.0.12.exe');
  if(fs.existsSync(included)){
    const choice=await dialog.showMessageBox(win,{type:'question',title:'Official Raspberry Pi Imager',
      message:'Install Raspberry Pi Imager?',detail:'Starts the official installer. Nothing is written until you select and confirm a disk in the Imager.',
      buttons:['Install','Cancel'],defaultId:0,cancelId:1});
    if(choice.response===0){const error=await shell.openPath(included);if(error)throw Error(error);return 'installer';}
    return 'cancelled';
  }
  await shell.openExternal('https://www.raspberrypi.com/software/');return 'official-site';
}
function runPS(script){
  return new Promise((resolve,reject)=>execFile('powershell.exe',
    ['-NoLogo','-NoProfile','-NonInteractive','-Command',script],{windowsHide:true,timeout:15000,maxBuffer:1e6},
    (error,stdout,stderr)=>error?reject(Error((stderr||error.message).slice(0,500))):resolve(stdout)));
}
async function drives(){
  const stdout=await runPS("Get-CimInstance Win32_DiskDrive | Select-Object Model,Size,DeviceID,InterfaceType,MediaType,PNPDeviceID,Partitions | ConvertTo-Json -Compress");
  const result=JSON.parse(stdout||'[]');
  return (Array.isArray(result)?result:[result]).filter(Boolean).map(d=>({
    model:String(d.Model||'Unknown drive'),size:Number(d.Size)||0,device:String(d.DeviceID||''),
    interface:String(d.InterfaceType||'Unknown'),partitions:Number(d.Partitions)||0,
    removable:/usb|removable|sd/i.test([d.InterfaceType,d.MediaType,d.PNPDeviceID].join(' '))
  }));
}
const fingerprintKey=p=>p.host+':'+p.port;
function normalizeProfile(p){
  const data={
    id:/^[\w-]{1,64}$/.test(p.id||'')?p.id:crypto.randomUUID(),
    name:String(p.name||'Raspberry Pi').slice(0,70).trim(),
    host:String(p.host||'').slice(0,253).trim(),
    user:String(p.user||'pi').slice(0,70).trim(),
    port:Number(p.port||22),keyPath:String(p.keyPath||'').slice(0,500)
  };
  if(!/^[\w.:-]{1,253}$/.test(data.host)||!/^[-.\w]{1,70}$/.test(data.user)||
     !Number.isInteger(data.port)||data.port<1||data.port>65535)throw Error('Invalid SSH profile');
  return data;
}
function sshConnect(id,secret){
  const profile=store().profiles.find(p=>p.id===id);
  if(!profile)throw Error('Unknown SSH profile');
  if(live.has(id))throw Error('Already connected');
  const client=new Client();
  const options={host:profile.host,port:profile.port,username:profile.user,readyTimeout:20000,
    hostHash:'sha256',tryKeyboard:true,
    hostVerifier:(fp,callback)=>{
      const key=fingerprintKey(profile),trusted=store().trusted[key];
      if(trusted){callback(trusted===fp);return;}
      pending.set(id,{fp,callback,key});
      send('ssh:hostkey',{id,host:profile.host,fingerprint:fp});
      setTimeout(()=>{const current=pending.get(id);if(current?.callback===callback){pending.delete(id);callback(false)}},45000);
    }};
  if(profile.keyPath){options.privateKey=fs.readFileSync(profile.keyPath);if(secret)options.passphrase=secret}
  else options.password=secret||'';
  return new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>{client.end();reject(Error('SSH timeout'))},48000);
    const error=e=>{clearTimeout(timeout);send('ssh:message',{id,type:'error',message:e.message});reject(e)};
    client.once('error',error);
    client.on('keyboard-interactive',(_,__,___,prompts,reply)=>reply(prompts.map(()=>secret||'')));
    client.once('ready',()=>{
      client.shell({term:'xterm-256color',cols:108,rows:30},(e,stream)=>{
        clearTimeout(timeout);
        if(e){reject(e);client.end();return}
        client.removeListener('error',error);live.set(id,{client,stream});
        stream.on('data',bytes=>send('ssh:message',{id,type:'data',data:bytes.toString('utf8')}));
        stream.on('close',()=>{live.delete(id);client.end();send('ssh:message',{id,type:'closed'})});
        client.on('error',e=>send('ssh:message',{id,type:'error',message:e.message}));
        resolve(true);
      });
    });
    client.connect(options);
  });
}
let translator='';
function translation(){
  if(!translator)translator=fs.readFileSync(asset('connect_translation.js'),'utf8');
  return translator;
}
function hidden(){if(web)web.setBounds({x:-5000,y:0,width:900,height:700})}
async function startConnect(){
  if(web)return true;
  web=new WebContentsView({webPreferences:{partition:'persist:rm-connect',sandbox:true,nodeIntegration:false,contextIsolation:true,webSecurity:true,backgroundThrottling:false}});
  win.contentView.addChildView(web);hidden();
  web.webContents.setWindowOpenHandler(({url})=>{
    if(!safeUrl(url))return {action:'deny'};
    return {action:'allow',overrideBrowserWindowOptions:{width:1150,height:780,title:'Raspberry Pi Connect Ã‚Â· Raspberry Manager',
      autoHideMenuBar:true,backgroundColor:'#0c0b10',webPreferences:{nodeIntegration:false,contextIsolation:true,sandbox:true,partition:'persist:rm-connect'}}};
  });
  web.webContents.on('will-navigate',(e,url)=>{if(!safeUrl(url))e.preventDefault()});
  web.webContents.on('will-redirect',(e,url)=>{if(!safeUrl(url))e.preventDefault()});
  web.webContents.on('did-start-navigation',(_,url,_same,main)=>{
    if(main){lastSnapshot={page:'loading',devices:[],actions:[],epoch:''};send('connect:changed',url)}
  });
  web.webContents.on('did-fail-load',(_,code,text,url,main)=>{
    if(main&&code!==-3){lastError=text+' ('+code+')';send('connect:changed',url)}
  });
  await web.webContents.loadURL('https://connect.raspberrypi.com/');
  return true;
}
async function connectState(){
  if(!web)return {started:false,url:'',snapshot:lastSnapshot,error:lastError,auth:false,session:false};
  const url=web.webContents.getURL();
  const auth=url.startsWith('https://id.raspberrypi.com')||url.includes('/auth/');
  const valid=safeUrl(url);
  if(auth)lastSnapshot={page:'signin',devices:[],actions:[],epoch:''};
  else if(valid && url.startsWith('https://connect.raspberrypi.com/') && !web.webContents.isLoadingMainFrame()){
    try{
      const js=translation().replace('__EPOCH__',JSON.stringify(crypto.randomUUID()))+
        '\nJSON.stringify(window.__rmConnectTranslation?.snapshot() ?? null)';
      const raw=await web.webContents.executeJavaScript(js,true);
      const value=typeof raw==='string'?JSON.parse(raw):raw;
      if(value&&value.version===1&&typeof value.epoch==='string'&&Array.isArray(value.devices)&&value.devices.length<=100){
        lastSnapshot={page:value.page,devices:value.devices,actions:value.actions||[],epoch:value.epoch,
          viewport:value.viewport||'',notice:value.notice||'',title:value.title||''};lastError=null;
      }
    }catch(e){lastError='Connect adapter: '+e.message.slice(0,140)}
  }
  return {started:true,url,error:lastError,auth,loading:web.webContents.isLoadingMainFrame(),
    session:lastSnapshot.page==='session'&&['screen','terminal'].includes(lastSnapshot.viewport),snapshot:lastSnapshot};
}
function ipc(){
  ipcMain.handle('app:meta',()=>({version:app.getVersion(),imagerInstalled:!!officialImager()}));
  ipcMain.handle('settings:get',()=>store().settings);
  ipcMain.handle('settings:save',(_,v)=>{store().settings={theme:['berry','graphite'].includes(v.theme)?v.theme:'berry'};save();return true});
  ipcMain.handle('profiles:list',()=>store().profiles);
  ipcMain.handle('profiles:save',(_,p)=>{
    const v=normalizeProfile(p),all=store().profiles;
    const i=all.findIndex(x=>x.id===v.id);
    if(i<0)all.push(v);else all[i]=v;
    save();return v;
  });
  ipcMain.handle('profiles:remove',(_,id)=>{store().profiles=store().profiles.filter(x=>x.id!==id);save();return true});
  ipcMain.handle('ssh:key',async()=>{
    const answer=await dialog.showOpenDialog(win,{properties:['openFile'],title:'Choose SSH private key'});
    return answer.canceled?null:answer.filePaths[0];
  });
  ipcMain.handle('ssh:connect',(_,id,secret)=>sshConnect(id,secret));
  ipcMain.handle('ssh:approve',(_,id,allow)=>{
    const item=pending.get(id);if(!item)return false;
    pending.delete(id);if(allow){store().trusted[item.key]=item.fp;save()}
    item.callback(!!allow);return true;
  });
  ipcMain.handle('ssh:write',(_,id,data)=>{
    if(typeof data!=='string'||data.length>16000)return false;
    const s=live.get(id);if(!s)return false;s.stream.write(data);return true;
  });
  ipcMain.handle('ssh:resize',(_,id,cols,rows)=>{live.get(id)?.stream.setWindow(rows,cols,0,0);return true});
  ipcMain.handle('ssh:disconnect',(_,id)=>{live.get(id)?.client.end();live.delete(id);return true});
  ipcMain.handle('drives:list',drives);
  ipcMain.handle('imager:open',openImager);
  ipcMain.handle('magic:get',()=>({blocks:store().magic,capacity:store().capacity}));
  ipcMain.handle('magic:save',(_,blocks,capacity)=>{
    if(!Array.isArray(blocks)||blocks.length>40||![16,32,64,128,256,512].includes(capacity))throw Error('Invalid builder layout');
    store().magic=blocks.map(b=>({id:String(b.id).slice(0,80),name:String(b.name).slice(0,100),
      kind:String(b.kind).slice(0,35),sizeGb:Math.max(0,Math.min(512,Number(b.sizeGb)||0)),dependency:String(b.dependency||'').slice(0,80)}));
    store().capacity=capacity;save();return true;
  });
  ipcMain.handle('projects:list',()=>store().projects.map(x=>({id:x.id,name:x.name,modified:x.modified,nodes:x.nodes.length})));
  ipcMain.handle('projects:load',(_,id)=>store().projects.find(p=>p.id===id)||null);
  ipcMain.handle('projects:save',(_,value)=>{
    const raw=JSON.stringify(value);
    if(raw.length>2e6||!Array.isArray(value.nodes)||value.nodes.length>256||!Array.isArray(value.links)||value.links.length>1024)throw Error('Project too large');
    const p={...value,id:String(value.id||crypto.randomUUID()).slice(0,80),name:String(value.name||'Workspace').slice(0,100),modified:Date.now()};
    const all=store().projects;const i=all.findIndex(x=>x.id===p.id);
    if(i>=0)all[i]=p;else all.push(p);save();return p;
  });
  ipcMain.handle('projects:remove',(_,id)=>{store().projects=store().projects.filter(x=>x.id!==id);save();return true});
  ipcMain.handle('file:export',async(_,filename,data)=>{
    if(typeof data!=='string'||data.length>3e6)throw Error('Export too large');
    const answer=await dialog.showSaveDialog(win,{defaultPath:String(filename||'RaspberryManager.json').replace(/[^\w.-]/g,'_')});
    if(answer.canceled||!answer.filePath)return null;
    fs.writeFileSync(answer.filePath,data);return answer.filePath;
  });
  ipcMain.handle('file:import',async()=>{
    const answer=await dialog.showOpenDialog(win,{properties:['openFile'],filters:[{name:'Project JSON',extensions:['json']}]});
    if(answer.canceled)return null;
    const p=answer.filePaths[0];if(fs.statSync(p).size>2e6)throw Error('File too large');
    return fs.readFileSync(p,'utf8');
  });
  ipcMain.handle('connect:start',startConnect);
  ipcMain.handle('connect:state',connectState);
  ipcMain.handle('connect:refresh',async()=>{
    if(web)await web.webContents.loadURL('https://connect.raspberrypi.com/');return true;
  });
  ipcMain.handle('connect:viewport',(_,r,visible)=>{
    if(!web||!visible){hidden();return true}
    const b=win.getContentBounds();
    const x=Math.max(0,Math.min(b.width-200,Math.round(Number(r.x)||0)));
    const y=Math.max(0,Math.min(b.height-200,Math.round(Number(r.y)||0)));
    web.setBounds({x,y,width:Math.max(200,Math.min(b.width-x,Math.round(Number(r.width)||700))),
      height:Math.max(200,Math.min(b.height-y,Math.round(Number(r.height)||500)))});
    return true;
  });
  ipcMain.handle('connect:action',async(_,epoch,handle)=>{
    if(!web||epoch!==lastSnapshot.epoch||!/^a_\d{1,8}$/.test(handle)||!safeUrl(web.webContents.getURL()))return false;
    const script='JSON.stringify(window.__rmConnectTranslation?.dispatch('+
      JSON.stringify(epoch)+','+JSON.stringify(handle)+') ?? {ok:false})';
    const raw=await web.webContents.executeJavaScript(script,true);
    const result=typeof raw==='string'?JSON.parse(raw):raw;
    return result?.ok===true;
  });
}
function startWindow(){
  win=new BrowserWindow({width:1340,height:860,minWidth:920,minHeight:630,
    backgroundColor:'#0b0b10',autoHideMenuBar:true,show:false,icon:asset('app.png'),
    webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true,webSecurity:true}});
  win.once('ready-to-show',()=>win.show());
  win.webContents.setWindowOpenHandler(()=>({action:'deny'}));
  win.webContents.on('will-navigate',(e,url)=>{
    if((process.env.RM_DEV_SERVER&&url.startsWith(process.env.RM_DEV_SERVER))||url.startsWith('file://'))return;
    e.preventDefault();
  });
  if(process.env.RM_DEV_SERVER)win.loadURL(process.env.RM_DEV_SERVER);
  else win.loadFile(path.join(root,'dist','index.html'));
  if(process.env.RM_CAPTURE_DIR){
    win.webContents.once('did-finish-load',()=>{
      setTimeout(async()=>{
        const dir=process.env.RM_CAPTURE_DIR;
        fs.mkdirSync(dir,{recursive:true});
        const capture=async(name)=>fs.writeFileSync(path.join(dir,name), (await win.webContents.capturePage()).toPNG());
        const click=async(match)=>win.webContents.executeJavaScript(
          "Array.from(document.querySelectorAll('button')).find(b=>b.textContent?.includes("+JSON.stringify(match)+"))?.click()"
        );
        const pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
        try{
          await capture('windows-home.png');
          await click('Builder');await pause(800);await capture('windows-builder.png');
          await click('Advanced Workbench');await pause(1200);
          await click('Choose template');await pause(600);
          await win.webContents.executeJavaScript("document.querySelectorAll('.template-grid button')[1]?.click()");
          await pause(1100);await capture('windows-workbench.png');
          send('screenshot:done',dir);
        }catch(e){fs.writeFileSync(path.join(dir,'capture-error.txt'),String(e));}
        app.quit();
      },2400);
    });
  }
  win.on('closed',()=>{win=null});
}
app.whenReady().then(()=>{ipc();startWindow();app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)startWindow()})});
app.on('before-quit',()=>{for(const session of live.values())session.client.end();live.clear()});
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit()});

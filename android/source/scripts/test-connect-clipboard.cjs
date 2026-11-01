const fs = require('fs');
const vm = require('vm');
const path = require('path');
const assert = require('node:assert/strict');
const source = fs.readFileSync(process.argv[2] || path.join(__dirname, '../app/src/main/assets/connect_clipboard.js'), 'utf8').replaceAll('__CHANNEL_NONCE__', 'test-nonce');
let passes = 0;
function ok(name, fn) { fn(); passes++; console.log('PASS '+name); }
function context(origin='https://connect.raspberrypi.com', frame=false) {
  const listeners = new Map();
  const timers = new Map(); let timerId = 0;
  const w = {addEventListener:(k,v)=>listeners.set(k,v),removeEventListener:k=>listeners.delete(k)};
  w.top = frame ? {} : w;
  const c = {window:w,location:{origin}, navigator:{userActivation:{isActive:true}},DOMException,
    setTimeout:fn=>{timers.set(++timerId,fn);return timerId},clearTimeout:id=>timers.delete(id),console};
  vm.createContext(c); vm.runInContext(source,c);
  return {c,w,listeners,timers};
}
(async()=>{
  const id = context('https://id.raspberrypi.com');
  ok('No clipboard adapter on ID origin',()=>assert.equal(id.c.navigator.clipboard,undefined));
  const frame = context(undefined,true);
  ok('No clipboard adapter in child frames',()=>assert.equal(frame.c.navigator.clipboard,undefined));
  const evil = context('https://connect.raspberrypi.com.evil.test');
  ok('Reject suffix-host spoof',()=>assert.equal(evil.c.navigator.clipboard,undefined));
  const x = context();
  ok('Install text-only adapter on Connect',()=>assert.equal(typeof x.c.navigator.clipboard.readText,'function'));
  ok('Await one exact handshake',()=>assert.equal(x.listeners.size,1));
  let sent;
  const port={postMessage:s=>{sent=JSON.parse(s)},start:()=>{},close:()=>{port.closed=true}};
  x.listeners.get('message')({data:'wrong-nonce',ports:[port]});
  ok('Wrong handshake ignored',()=>assert.equal(x.listeners.size,1));
  await assert.rejects(x.c.navigator.clipboard.readText(),{name:'NotAllowedError'});passes++;console.log('PASS No access before channel');
  x.listeners.get('message')({data:'test-nonce',ports:[port]});
  ok('Handshake listener removed',()=>assert.equal(x.listeners.size,0));
  x.c.navigator.userActivation.isActive=false;
  await assert.rejects(x.c.navigator.clipboard.readText(),{name:'NotAllowedError'});passes++;console.log('PASS Require user gesture');
  x.c.navigator.userActivation.isActive=true;
  await assert.rejects(x.c.navigator.clipboard.writeText('x'.repeat(65537)),{name:'DataError'});passes++;console.log('PASS Reject oversized clipboard');
  const read=x.c.navigator.clipboard.readText();
  ok('Request read without clipboard polling',()=>assert.equal(sent.kind,'read'));
  port.onmessage({data:JSON.stringify({id:sent.id,ok:true,text:'example text'})});
  assert.equal(await read,'example text');passes++;console.log('PASS Resolve confirmed read');
  const write=x.c.navigator.clipboard.writeText('sample');
  ok('Write carries only supplied text',()=>assert.equal(sent.text,'sample'));
  port.onmessage({data:JSON.stringify({id:sent.id,ok:true})});
  assert.equal(await write,undefined);passes++;console.log('PASS Write Promise resolves correctly');
  const cancelled=x.c.navigator.clipboard.readText();
  port.onmessage({data:JSON.stringify({id:sent.id,ok:false})});
  await assert.rejects(cancelled,{name:'NotAllowedError'});passes++;console.log('PASS Native rejection respected');
  const unfinished=x.c.navigator.clipboard.readText();
  x.w.__raspberryConnectClipboard.stop();
  await assert.rejects(unfinished,{name:'AbortError'});passes++;console.log('PASS Navigation cancels outstanding requests');
  ok('Channel closed on navigation',()=>assert.equal(port.closed,true));
  ok('No lingering request timers',()=>assert.equal(x.timers.size,0));
  console.log('TOTAL '+passes+' passed, 0 failed');
})().catch(e=>{console.error(e);process.exitCode=1});

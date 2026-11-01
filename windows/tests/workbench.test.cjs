'use strict';
const { test, after } = require('node:test');
const assert = require('node:assert/strict');
let server;
const modulePromise = (async () => {
  const {createServer} = await import('vite');
  server = await createServer({configFile:false,server:{middlewareMode:true},appType:'custom'});
  return server.ssrLoadModule('/src/logic.ts');
})();
after(async () => {if(server)await server.close();});
test('catalog contains the 68 component types from Android',async()=>{
  const m=await modulePromise;
  assert.equal(m.CATALOG.length,68);
  assert.ok(m.CATALOG.some(k=>k.id==='os.android'));
  assert.ok(m.CATALOG.some(k=>k.id==='mc.bedrock'));
});
test('fresh workbench is well formed',async()=>{
  const m=await modulePromise,p=m.emptyProject();
  assert.equal(p.format,'raspberry-workbench');
  assert.equal(p.schema,1);
  assert.deepEqual(p.nodes,[]);
  assert.deepEqual(m.validateProject(p).filter(x=>x.severity==='error'),[]);
});
test('template IDs and names are distinguishable',async()=>{
  const m=await modulePromise;
  const p=m.templates();
  assert.ok(Object.keys(p).length>=7);
  assert.notEqual(p['Minecraft Java'].id,p['Pi server base'].id);
  assert.ok(p['Minecraft Java'].nodes.some(n=>n.type==='mc.java'));
});
test('stack detects over-capacity',async()=>{
  const m=await modulePromise;
  const blocks=[m.newMagic('Raspberry Pi OS','OS')];
  blocks[0].sizeGb=64;
  assert.ok(m.magicDiagnostics(blocks,16).some(x=>x.startsWith('ERROR:')));
});
test('stack catches circular dependencies',async()=>{
  const m=await modulePromise;
  const a=m.newMagic('Raspberry Pi OS','OS'),b=m.newMagic('Boot A','BOOT');
  a.dependency=b.id;b.dependency=a.id;
  assert.ok(m.magicDiagnostics([a,b],64).some(x=>x.includes('Circular dependency')));
});
test('empty layout is not presented as booted hardware',async()=>{
  const m=await modulePromise;
  const diagnostic=m.magicDiagnostics([],64).join(' ');
  assert.match(diagnostic,/not a real Raspberry Pi boot/);
});
test('Workbench detects dependency loops',async()=>{
  const m=await modulePromise,p=m.emptyProject();
  const a=m.createNode('os.pi64',0,0),b=m.createNode('boot.a',250,0);
  p.nodes=[a,b];
  p.links=[m.newLink(a.id,b.id),m.newLink(b.id,a.id)];
  assert.ok(m.validateProject(p).some(x=>x.severity==='error'&&x.text.includes('Circular')));
});
test('invalid architecture for native Bedrock is detected',async()=>{
  const m=await modulePromise,p=m.emptyProject();
  p.nodes=[m.createNode('mc.bedrock',0,0)];
  assert.ok(m.validateProject(p).some(x=>x.text.includes('x86_64')));
});
test('a JSON project can be round-tripped without changing structure',async()=>{
  const m=await modulePromise,p=m.emptyProject();
  p.nodes=[m.createNode('os.pi64',12,45)];
  const recovered=m.parseProject(m.serializeProject(p));
  assert.deepEqual(recovered,p);
});
test('unsupported project format is rejected',async()=>{
  const m=await modulePromise;
  assert.throws(()=>m.parseProject('{"format":"unknown","schema":1,"nodes":[],"links":[]}'));
});
test('unbounded project data is rejected',async()=>{
  const m=await modulePromise;
  assert.throws(()=>m.parseProject(' '.repeat(2*1024*1024+1)));
});
test('report clearly distinguishes real boot from plan',async()=>{
  const m=await modulePromise;
  assert.match(m.projectReport(m.emptyProject()),/not simulated/);
});
test('blocked scenario does not declare simulated success',async()=>{
  const m=await modulePromise,p=m.emptyProject();
  const a=m.createNode('boot.a',0,0),b=m.createNode('boot.b',220,120);
  p.nodes=[a,b];p.links=[m.newLink(a.id,b.id),m.newLink(b.id,a.id)];
  assert.match(m.scenarioReport(p,[]),/blocked/i);
});
test('physical disk actions are absent from Workbench simulations',async()=>{
  const m=await modulePromise,p=m.emptyProject();
  p.nodes=[m.createNode('os.pi64',0,0)];
  const text=m.scenarioReport(p,[]);
  assert.match(text,/No real Pi firmware/);
});

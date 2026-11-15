'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),net=require('node:net'),fs=require('node:fs'),vm=require('node:vm');
const {clipboardCredentials,readClipboard,cleanCheck,testTv,connect}=require('./xkiro-connect.cjs');
const {createPanel}=require('./panel.cjs');
const key='sk-xt-'+'e'.repeat(48),model='mistralai/ministral-8b';
const good={provider:'xkiro',model,http:200,inference_ok:true,plan_ok:true,kind:'ok',executed_actions:0,elapsed_ms:1};
async function fixture(){
 const calls=[],sockets=new Set();let hello;
 const server=net.createServer(socket=>{sockets.add(socket);socket.on('error',()=>{});socket.on('close',()=>sockets.delete(socket));let data='';socket.on('data',b=>{data+=b;const at=data.indexOf('\n');if(at<0)return;hello=JSON.parse(data.slice(0,at));socket.end(JSON.stringify({version:1,inference_check:{...good,key:'must-be-omitted'}})+'\n');});});
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const port=server.address().port;
 const adapter={readState:()=>({model:'MiTV-MSSP1',android:'9',sdk:'28'}),shellTimed:(...a)=>{calls.push(a);return 'Status: ok';},adb:(...a)=>{calls.push(a);return a[1]==='tcp:0'?String(port):'';}};
 return {adapter,calls,port,get hello(){return hello;},close:async()=>{for(const s of sockets)s.destroy();await new Promise(resolve=>server.close(resolve));}};
}
test('clipboard import accepts only xKiro keys, trims trailing Windows newlines and never includes a key in command args',()=>{
 assert.equal(clipboardCredentials(key+'\r\n').key,key);
 for(const bad of ['sk-or-v1-'+'a'.repeat(64),'private text',key+'\nextra','x'.repeat(2000)])assert.throws(()=>clipboardCredentials(bad));
 const p=readClipboard((exe,args,options)=>{assert.equal(exe,'powershell.exe');assert(args.includes('Get-Clipboard -Raw'));assert(!JSON.stringify(args).includes(key));assert.equal(options.stdio[1],'pipe');return{status:0,stdout:key+'\r\n'};});assert.equal(p.key,key);
 assert.throws(()=>readClipboard(()=>({status:1,stderr:key})),e=>!e.message.includes(key));
});
test('native check metadata cannot report TV actions or contain untrusted response text',()=>{
 assert.deepEqual(cleanCheck({...good,key,body:'private'}),good);
 for(const bad of [{...good,executed_actions:1},{...good,model:'paid/model'},{...good,plan_ok:false},{...good,http:403}])assert.throws(()=>cleanCheck(bad));
});
test('native inference RPC sends only authenticated hello and cleans up its own forwarding',async()=>{
 const f=await fixture();try{assert.deepEqual(await testTv(f.adapter),good);assert.deepEqual(Object.keys(f.hello).sort(),['token','type']);assert(f.calls.some(c=>c.includes('test_xkiro')));assert.deepEqual(f.calls.at(-1),['forward','--remove','tcp:'+f.port]);assert(!JSON.stringify(f.calls).includes(key));}finally{await f.close();}
});
test('automatic connection drops the key reference after transfer and tests exactly once only after accepted metadata',async()=>{
 for(const accepted of [false,true]){
  const p=clipboardCredentials(key);let sends=0,tests=0;
  const r=await connect({},p,{
   provision:async(a,credentials)=>{sends++;assert.equal(credentials.key,key);return{saved:true,provider:'xkiro',model,verified:false};},
   diagnose:async(a,pc,provider)=>{assert.equal(provider,'xkiro');assert.equal(p.key,'');return{tv:{probes:[{stage:'key',recognized:accepted}]}};},
   testTv:async()=>{tests++;return good;}
  });assert.equal(sends,1);assert.equal(tests,accepted?1:0);assert.equal(p.key,'');assert(!JSON.stringify(r).includes(key));
 }
});
test('a failed encrypted transfer also drops the clipboard key reference',async()=>{
 const p=clipboardCredentials(key);await assert.rejects(connect({},p,{provision:async()=>{throw Error('connection failed');}}));assert.equal(p.key,'');
});
test('failed native inference preserves the acknowledged save and diagnostics without leaking an error payload',async()=>{
 const p=clipboardCredentials(key);const r=await connect({},p,{
  provision:async()=>({saved:true,provider:'xkiro',model,verified:false}),
  diagnose:async()=>({tv:{probes:[{stage:'key',recognized:true}]}}),
  testTv:async()=>{throw Error(key);}
 });assert.equal(r.saved.saved,true);assert.equal(r.check,null);assert(r.check_error);assert(!JSON.stringify(r).includes(key));assert.equal(p.key,'');
});
test('panel native check accepts no supplied URL, key, model or command',async()=>{
 const f=await fixture(),panel=createPanel(f.adapter,{port:0,token:'d'.repeat(64)});await panel.start();const origin='http://127.0.0.1:'+panel.port;
 try{
  const call=p=>fetch(origin+'/api/xkiro-test',{method:'POST',headers:{Origin:origin,'X-Tv-Token':panel.token,'Content-Type':'application/json'},body:JSON.stringify(p)});
  for(const p of [{key},{url:'https://evil.example'},{command:'mute'},{model:'paid/model'}])assert.equal((await call(p)).status,400);
  assert.equal(f.calls.length,0);const r=await call({});assert.equal(r.status,200);const check=await r.json();assert.equal(check.plan_ok,true);assert.equal(check.executed_actions,0);assert(!JSON.stringify(check).includes('must-be-omitted'));
 }finally{await panel.close();await f.close();}
});
test('xKiro password input is cleared before sending and errors never display it',async()=>{
 const ids=['status','tv','frame-info','keys','nodes','check','report','screen','ui','assistant','run','command','or-save','or-key','or-model','or-result','or-diagnose','or-diagnostics','xk-save','xk-key','xk-model','xk-result','xk-diagnose','xk-diagnostics','xk-test','xk-check','weather','weather-check','voice','voice-check','mi-learn','mini-preview','mini-speech'];
 const nodes=Object.fromEntries(ids.map(id=>[id,{value:'',textContent:'',disabled:false}]));nodes['xk-key'].value=key;nodes['xk-model'].value=model;let sends=0;
 const context={location:{hash:'#'+'a'.repeat(64)},document:{getElementById:id=>nodes[id],querySelectorAll:()=>[]},fetch:async(url,options)=>{
  sends++;assert.equal(url,'/api/xkiro');assert.equal(nodes['xk-key'].value,'');assert.equal(nodes['xk-key'].disabled,true);assert.deepEqual(JSON.parse(options.body),{key,model});return{ok:true,json:async()=>({saved:true})};
 }};vm.runInNewContext(fs.readFileSync(require.resolve('./panel.js'),'utf8'),context);await nodes['xk-save'].onclick();assert.equal(sends,1);assert.equal(nodes['xk-key'].value,'');assert(!nodes['xk-result'].textContent.includes(key));
});

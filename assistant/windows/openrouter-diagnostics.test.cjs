'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),net=require('node:net'),fs=require('node:fs'),vm=require('node:vm');
const {cleanReport,receive,diagnoseTv,diagnose,publicKind}=require('./openrouter-diagnostics.cjs');
const {createPanel}=require('./panel.cjs');
const catalog={stage:'catalog',http:200,format:'json',kind:'ok',recognized:false,elapsed_ms:1};
const report={provider:'openrouter',model:'qwen/qwen3.5-9b',configured:true,key_fingerprint:'abcdef123456',probes:[catalog,{stage:'key',http:401,format:'json',kind:'unauthorized',recognized:false,elapsed_ms:2}]};
async function fixture(){
 let hello;const sockets=new Set();const server=net.createServer(socket=>{
  sockets.add(socket);socket.on('error',()=>{});socket.on('close',()=>sockets.delete(socket));let buffer='';socket.on('data',b=>{buffer+=b;const at=buffer.indexOf('\n');if(at<0)return;hello=JSON.parse(buffer.slice(0,at));socket.end(JSON.stringify({version:1,diagnostics:report})+'\n');});
 });await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const calls=[],adapter={readState:()=>({model:'MiTV-MSSP1',android:'9',sdk:'28'}),shellTimed:(...a)=>{calls.push(a);return 'Status: ok';},adb:(...a)=>{calls.push(a);return a[1]==='tcp:0'?String(server.address().port):'';}};
 return {adapter,calls,get hello(){return hello;},port:server.address().port,close:async()=>{for(const s of sockets)s.destroy();await new Promise(resolve=>server.close(resolve));}};
}
test('diagnostic reports discard secret fields and reject unknown model, status and classification',()=>{
 const safe=cleanReport({...report,key:'secret',probes:report.probes.map(p=>({...p,body:'secret'}))});assert.deepEqual(safe,report);assert(!JSON.stringify(safe).includes('secret'));
 for(const r of [{...report,model:'arbitrary/model'},{...report,key_fingerprint:'raw-secret'},{...report,probes:[{...catalog,http:-1},report.probes[1]]},{...report,probes:[catalog,{...report.probes[1],kind:'untrusted server text'}]}])assert.throws(()=>cleanReport(r));
});
test('read-only diagnostic wire sends only authenticated hello, ADB mode and own forwarding cleanup',async()=>{
 const f=await fixture();try{
  assert.deepEqual(await diagnoseTv(f.adapter),report);assert.deepEqual(Object.keys(f.hello).sort(),['token','type']);assert.equal(f.hello.type,'hello');
  assert(f.calls.some(c=>c.includes('setup_mode')&&c.includes('diagnose')));assert.deepEqual(f.calls.at(-1),['forward','--remove','tcp:'+f.port]);
  assert(!JSON.stringify(f.calls).includes('sk-or-'));assert(!JSON.stringify(f.calls).includes('https://'));
 }finally{await f.close();}
});
test('diagnostic transport rejects invalid port and session and masks adapter failures',async()=>{
 await assert.rejects(receive(0,'a'.repeat(64)));await assert.rejects(receive(12345,'wrong'));
 const r=await diagnose({readState(){throw Error('secret failure');}},async()=>catalog);
 assert.equal(r.pc.http,200);assert.equal(r.tv.available,false);assert(!JSON.stringify(r).includes('secret'));
});
test('public response classification preserves forbidden versus challenge without inventing a region',()=>{
 assert.equal(publicKind(403,'text/html','','<html>Just a moment cf-chl</html>'),'browser_challenge');assert.equal(publicKind(403,'application/json','','{}'),'forbidden');assert.equal(publicKind(200,'text/html','',''),'unexpected_response');
});
test('panel diagnostics reject supplied credentials or endpoints before device work and do not require a key',async()=>{
 const f=await fixture(),panel=createPanel(f.adapter,{port:0,token:'c'.repeat(64),pcProbe:async()=>catalog});await panel.start();const origin='http://127.0.0.1:'+panel.port;
 try{
  const call=p=>fetch(origin+'/api/openrouter-diagnostics',{method:'POST',headers:{Origin:origin,'X-Tv-Token':panel.token,'Content-Type':'application/json'},body:JSON.stringify(p)});
  let r=await call({key:'secret'});assert.equal(r.status,400);assert.equal(f.calls.length,0);
  r=await call({endpoint:'https://evil.example'});assert.equal(r.status,400);assert.equal(f.calls.length,0);
  r=await call({});assert.equal(r.status,200);const result=await r.json();assert.deepEqual(result.tv,report);assert.equal(result.pc.http,200);
 }finally{await panel.close();await f.close();}
});
test('diagnose button never reads or sends the PC key field and displays exact TV HTTP status',async()=>{
 const ids=['status','tv','frame-info','keys','nodes','check','report','screen','ui','assistant','run','command','or-save','or-key','or-model','or-result','or-diagnose','or-diagnostics','xk-save','xk-key','xk-model','xk-result','xk-diagnose','xk-diagnostics','xk-test','xk-check','weather','weather-check','voice','voice-check','mi-learn','mini-preview','mini-speech'];
 const nodes=Object.fromEntries(ids.map(id=>[id,{textContent:'',disabled:false}]));Object.defineProperty(nodes['or-key'],'value',{get(){throw Error('Must not read key');}});
 const calls=[],context={location:{hash:'#'+'e'.repeat(64)},document:{getElementById:id=>nodes[id],querySelectorAll:()=>[]},fetch:async(route,options)=>{calls.push(route);assert.equal(options.body,'{}');return{ok:true,json:async()=>({ok:true,tv:report,pc:catalog})};}};
 vm.runInNewContext(fs.readFileSync(require.resolve('./panel.js'),'utf8'),context);await nodes['or-diagnose'].onclick();
 assert.deepEqual(calls,['/api/openrouter-diagnostics']);assert(nodes['or-result'].textContent.includes('HTTP 401'));assert(nodes['or-result'].textContent.includes('авторизация отклонена'));assert(!nodes['or-diagnostics'].textContent.includes('secret'));
});

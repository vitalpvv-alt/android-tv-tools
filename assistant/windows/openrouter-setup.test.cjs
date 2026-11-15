'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),crypto=require('node:crypto'),net=require('node:net');
const {validateCredentials,transfer,provision}=require('./openrouter-setup.cjs');
const {createPanel}=require('./panel.cjs');
const fixture={model:'qwen/qwen3.5-9b',key:'sk-or-v1-'+ 'c'.repeat(64)};
async function fakeTv(mode='ok'){
 const pair=crypto.generateKeyPairSync('rsa',{modulusLength:2048});let received=null,wire='';const connections=new Set();
 const server=net.createServer(socket=>{connections.add(socket);socket.on('error',()=>{});socket.on('close',()=>connections.delete(socket));let buffer='',step=0,token;
  socket.on('data',chunk=>{wire+=chunk.toString();buffer+=chunk.toString();let at;while((at=buffer.indexOf('\n'))>=0){const line=buffer.slice(0,at);buffer=buffer.slice(at+1);try{
   const msg=JSON.parse(line);
   if(step++===0){token=msg.token;assert.equal(msg.type,'hello');assert.match(token,/^[a-f0-9]{64}$/);socket.write(JSON.stringify(mode==='bad-hello'?{version:0}:{version:1,public_key:pair.publicKey.export({type:'spki',format:'der'}).toString('base64')})+'\n');}
   else{
    const key=crypto.privateDecrypt({key:pair.privateKey,padding:crypto.constants.RSA_PKCS1_OAEP_PADDING,oaepHash:'sha256'},Buffer.from(msg.wrapped,'base64'));
    const aes=crypto.createDecipheriv('aes-256-gcm',key,Buffer.from(msg.iv,'base64')),box=Buffer.from(msg.payload,'base64');aes.setAAD(Buffer.from(token,'ascii'));aes.setAuthTag(box.subarray(-16));
    received=JSON.parse(Buffer.concat([aes.update(box.subarray(0,-16)),aes.final()]).toString());key.fill(0);
    socket.write(JSON.stringify(mode==='bad-ack'?{ok:true,provider:'openrouter',model:'wrong-model'}:{ok:true,provider:received.provider||'openrouter',model:received.model})+'\n');
   }
  }catch{socket.destroy();}}});
 });
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 return {port:server.address().port,get received(){return received;},get wire(){return wire;},close:async()=>{for(const s of connections)s.destroy();await new Promise(resolve=>server.close(resolve));}};
}
function adapterFor(tv){const calls=[];return {calls,readState(){calls.push(['state']);return{model:'MiTV-MSSP1',android:'9',sdk:'28'};},shellTimed(...args){calls.push(args);return 'Status: ok';},adb(...args){calls.push(args);return args[1]==='tcp:0'?String(tv.port):'';}};}
test('PC key entry rejects unknown models, keys, extra fields and endpoint overrides',()=>{
 validateCredentials(fixture);
 for(const bad of [{...fixture,model:'expensive/model'},{...fixture,key:'short'},{...fixture,key:fixture.key+'\n'},{...fixture,endpoint:'https://example.com'}])assert.throws(()=>validateCredentials(bad));
});
test('key crosses the wire encrypted and success replies contain no credential',async()=>{
 const tv=await fakeTv();try{const result=await transfer(tv.port,'a'.repeat(64),fixture);assert.deepEqual(tv.received,fixture);assert.equal(result.saved,true);assert.equal(result.verified,false);assert(!tv.wire.includes(fixture.key));assert(!JSON.stringify(result).includes(fixture.key));}finally{await tv.close();}
});
test('xKiro credentials use the same encrypted channel with a provider-bound acknowledgement',async()=>{
 const p={provider:'xkiro',model:'mistralai/ministral-8b',key:'sk-xt-'+'e'.repeat(48)},tv=await fakeTv();
 try{const r=await transfer(tv.port,'a'.repeat(64),p);assert.deepEqual(tv.received,p);assert.equal(r.provider,'xkiro');assert(!tv.wire.includes(p.key));assert(!JSON.stringify(r).includes(p.key));}finally{await tv.close();}
});
test('ADB setup contains only session values, removes only its forwarding and never puts key in commands',async()=>{
 const tv=await fakeTv(),adapter=adapterFor(tv);try{await provision(adapter,fixture);assert(!JSON.stringify(adapter.calls).includes(fixture.key));assert(adapter.calls.some(c=>c.includes('dev.mert.tvassistant/.KeySetupActivity')));assert.deepEqual(adapter.calls.at(-1),['forward','--remove','tcp:'+tv.port]);}finally{await tv.close();}
});
test('wrong TV stops before launching setup and invalid key stops before all device actions',async()=>{
 const adapter=adapterFor({port:1});adapter.readState=()=>({model:'wrong',android:'9',sdk:'28'});
 await assert.rejects(provision(adapter,fixture));assert.equal(adapter.calls.length,0);
 await assert.rejects(provision(adapter,{...fixture,key:'invalid'}));assert.equal(adapter.calls.length,0);
});
test('failed handshake and mismatched acknowledgement cannot claim a save and clean up forwarding',async()=>{
 for(const mode of ['bad-hello','bad-ack']){const tv=await fakeTv(mode),adapter=adapterFor(tv);try{await assert.rejects(provision(adapter,fixture));assert.deepEqual(adapter.calls.at(-1),['forward','--remove','tcp:'+tv.port]);}finally{await tv.close();}}
});
test('no arbitrary host, port or session can be supplied to key transport',async()=>{
 await assert.rejects(transfer(0,'a'.repeat(64),fixture));await assert.rejects(transfer(65536,'a'.repeat(64),fixture));await assert.rejects(transfer(12345,'../../x',fixture));
});
test('panel key route requires same-origin authorization and returns only saved model status',async()=>{
 const tv=await fakeTv(),adapter=adapterFor(tv),panel=createPanel(adapter,{port:0,token:'e'.repeat(64)});await panel.start();const origin='http://127.0.0.1:'+panel.port;
 try{
  let r=await fetch(origin+'/api/openrouter',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:JSON.stringify(fixture)});assert.equal(r.status,403);assert.equal(adapter.calls.length,0);
  r=await fetch(origin+'/api/openrouter',{method:'POST',headers:{Origin:'https://evil.example','X-Tv-Token':panel.token,'Content-Type':'application/json'},body:JSON.stringify(fixture)});assert.equal(r.status,403);assert.equal(adapter.calls.length,0);
  r=await fetch(origin+'/api/openrouter',{method:'POST',headers:{Origin:origin,'X-Tv-Token':panel.token,'Content-Type':'application/json'},body:JSON.stringify(fixture)});const result=await r.json();assert.equal(r.status,200);assert.equal(result.saved,true);assert(!JSON.stringify(result).includes(fixture.key));assert.deepEqual(tv.received,fixture);
 }finally{await panel.close();await tv.close();}
});
test('panel setup error never echoes a key from device-side command errors',async()=>{
 const adapter=adapterFor({port:1});adapter.shellTimed=()=>{throw Error('untrusted text '+fixture.key);};const panel=createPanel(adapter,{port:0,token:'f'.repeat(64)});await panel.start();const origin='http://127.0.0.1:'+panel.port;
 try{const r=await fetch(origin+'/api/openrouter',{method:'POST',headers:{Origin:origin,'X-Tv-Token':panel.token,'Content-Type':'application/json'},body:JSON.stringify(fixture)});const result=await r.text();assert.equal(r.status,400);assert(!result.includes(fixture.key));}finally{await panel.close();}
});
test('PC form clears and disables the key before sending and never includes it in status text',async()=>{
 const fs=require('node:fs'),vm=require('node:vm');
 for(const fail of [false,true]){
  const ids=['status','tv','frame-info','keys','nodes','check','report','screen','ui','assistant','run','command','or-save','or-key','or-model','or-result','or-diagnose','or-diagnostics','xk-save','xk-key','xk-model','xk-result','xk-diagnose','xk-diagnostics','xk-test','xk-check','weather','weather-check','voice','voice-check','mi-learn','mini-preview','mini-speech'];
  const nodes=Object.fromEntries(ids.map(id=>[id,{value:'',textContent:'',disabled:false}]));nodes['or-key'].value=fixture.key;nodes['or-model'].value=fixture.model;
  const calls=[];
  const context={location:{hash:'#'+'e'.repeat(64)},document:{getElementById:id=>nodes[id],querySelectorAll:()=>[]},fetch:async(route,options)=>{
   calls.push(route);assert.equal(nodes['or-key'].value,'');assert.equal(nodes['or-key'].disabled,true);assert.deepEqual(JSON.parse(options.body),fixture);
   if(fail)throw Error('Synthetic connection failure');return{ok:true,json:async()=>({ok:true,saved:true,model:fixture.model})};
  }};
  vm.runInNewContext(fs.readFileSync(require.resolve('./panel.js'),'utf8'),context);
  await nodes['or-save'].onclick();assert.equal(nodes['or-key'].value,'');assert.equal(nodes['or-key'].disabled,false);
  assert.deepEqual(calls,['/api/openrouter']);assert(!nodes.status.textContent.includes(fixture.key));assert(!nodes['or-result'].textContent.includes(fixture.key));
 }
});

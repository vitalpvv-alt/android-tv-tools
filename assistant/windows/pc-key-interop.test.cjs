'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),crypto=require('node:crypto'),path=require('node:path');
const {spawn}=require('node:child_process'),readline=require('node:readline');
const {transfer}=require('./openrouter-setup.cjs');
const {receive}=require('./openrouter-diagnostics.cjs');
const key='sk-or-v1-'+ 'd'.repeat(64),token='b'.repeat(64),model='qwen/qwen3.5-9b';
async function probe(mode){
 const p=mode==='ok-xkiro'?{provider:'xkiro',model:'mistralai/ministral-8b',key:'sk-xt-'+'e'.repeat(48)}:{key,model};
 if(!process.env.TV_ASSISTANT_JAVA_CP||!process.env.JAVA_HOME)throw Error('Set JAVA_HOME and TV_ASSISTANT_JAVA_CP after compiling KeyProvisioningProbe.java');
 const child=spawn(path.join(process.env.JAVA_HOME,'bin',process.platform==='win32'?'java.exe':'java'),['-cp',process.env.TV_ASSISTANT_JAVA_CP,'dev.mert.tvassistant.KeyProvisioningProbe',token,mode],{stdio:['ignore','pipe','pipe']});
 child.on('error',()=>{});let error='';child.stderr.on('data',b=>{error+=b;});
 const lines=readline.createInterface({input:child.stdout})[Symbol.asyncIterator]();
 try{
  const first=await lines.next();if(first.done)throw Error('JVM fixture did not start: '+error);const port=JSON.parse(first.value).port;
  if(mode==='diagnose'){
   const report=await receive(port,token);assert.equal(report.probes[1].http,403);assert.equal(report.probes[1].kind,'browser_challenge');assert(!JSON.stringify(report).includes(key));
  }else if(mode==='tamper')await assert.rejects(transfer(port,token,p));
  else assert.equal((await transfer(port,token,p)).saved,true);
  const next=await lines.next();assert.equal(next.done,false);const result=JSON.parse(next.value);
  if(mode==='diagnose')assert.equal(result.diagnostics_sent,true);
  else if(mode==='tamper')assert.equal(result.rejected,true);
  else{assert.equal(result.model,p.model);assert.equal(result.key_sha256,crypto.createHash('sha256').update(p.key).digest('base64'));}
 }finally{child.kill();}
}
test('real Node sender interoperates with the Java receiver using RSA-OAEP SHA256 and AES-GCM',{timeout:30000},()=>probe('ok'));
test('Java receiver rejects a modified encrypted key and sender cannot report success',{timeout:30000},()=>probe('tamper'));
test('Node diagnostics receive bounded Java metadata without receiving or resending a key',{timeout:30000},()=>probe('diagnose'));
test('real Node xKiro sender interoperates with the Java provider-aware encrypted receiver',{timeout:30000},()=>probe('ok-xkiro'));

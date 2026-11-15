'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const {parseReport,readReport}=require('./voice-setup.cjs');
const {createPanel}=require('./panel.cjs');
const sample={version:1,service_available:true,button_enabled:true,keys:[{code:219,action:'down'}],
 google_windows:[{class:'com.google.android.apps.tvsearch.SearchActivity',decision:'candidate',elapsed_ms:3000}],
 last_route:'google_to_assistant',voice_input:{active:false,outcome:'recognized',recognizer_available:true}};
test('voice report only reads its fixed native service and removes unrequested data',()=>{
 let calls=0;
 const r=readReport({shellTimed:(t,...args)=>{calls++;assert.equal(t,6000);assert.deepEqual(args,['dumpsys','activity','service','dev.mert.tvassistant/.NavigationService','voice']);
  return 'private log line\nTV_VOICE_JSON='+JSON.stringify({...sample,recognized_text:'must not be returned',voice_input:{...sample.voice_input,audio:'must not be returned'}});}});
 assert.equal(calls,1);assert.equal(r.last_route,'google_to_assistant');assert.deepEqual(r.keys,sample.keys);
 assert.doesNotMatch(JSON.stringify(r),/must not|private log|recognized_text|audio/);
});
test('unavailable, oversized and non-component reports are rejected',()=>{
 assert.equal(parseReport('TV_VOICE_JSON={"version":1,"available":false}'),null);
 assert.equal(parseReport('TV_VOICE_JSON='+JSON.stringify({...sample,google_windows:[{class:'some private text',decision:'candidate'}]})),null);
 assert.equal(parseReport('TV_VOICE_JSON='+JSON.stringify({...sample,keys:Array(33).fill({code:219,action:'down'})})),null);
 assert.equal(parseReport('x'.repeat(65537)),null);
});
test('voice endpoint accepts no commands, URLs or credentials and requires panel authentication',async()=>{
 let calls=0;const adapter={shellTimed:()=>{calls++;return 'TV_VOICE_JSON='+JSON.stringify(sample);}};
 const token='b'.repeat(64),panel=createPanel(adapter,{port:0,token});const url=await panel.start(),origin=new URL(url).origin;
 const headers={'Content-Type':'application/json',Origin:origin,'X-Tv-Token':token};
 try{
  let r=await fetch(origin+'/api/voice',{method:'POST',headers,body:'{}'});assert.equal(r.status,200);
  assert.equal((await r.json()).voice.last_route,'google_to_assistant');assert.equal(calls,1);
  for(const body of [{command:'input keyevent 219'},{url:'http://example.test'},{key:'secret'},[]]){
   r=await fetch(origin+'/api/voice',{method:'POST',headers,body:JSON.stringify(body)});assert.equal(r.status,400);
  }
  assert.equal(calls,1);
  r=await fetch(origin+'/api/voice',{method:'POST',headers:{...headers,'X-Tv-Token':'a'.repeat(64)},body:'{}'});
  assert.equal(r.status,403);assert.equal(calls,1);
 }finally{await panel.close();}
});
test('voice update never imports a key, changes the Google service or generates a model request',()=>{
 const s=fs.readFileSync(require.resolve('./voice-setup.cjs'),'utf8');
 assert.doesNotMatch(s,/clipboard|Get-Clipboard|provision\(|testTv\(|settings.*put|pm.*disable|voice_interaction_service/i);
});

'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const {verify}=require('./assistant-verify.cjs');
const w={version:1,visible:true,ambient_sensor_available:false,network:'http_403',has_usable_weather:false,probes:[{source:'MET Norway',http:403,kind:'http_403'}]};
test('update verifier uses saved TV credentials and reads the weather service only',async()=>{
 const commands=[];const adapter={readState:()=>({version:'code20'}),shell:(...a)=>commands.push(a),shellTimed:(t,...a)=>{assert.equal(t,6000);assert.deepEqual(a,['dumpsys','activity','service','dev.mert.tvassistant/.NavigationService','weather']);return 'TV_WEATHER_JSON='+JSON.stringify(w);}};
 const r=await verify(adapter,{sleep:async()=>{},testTv:async()=>({plan_ok:true,executed_actions:0})});
 assert.equal(r.check.plan_ok,true);assert.equal(r.weather.probes[0].http,403);assert.deepEqual(commands,[['input','keyevent','KEYCODE_HOME']]);
 const code=fs.readFileSync(require.resolve('./assistant-verify.cjs'),'utf8');assert.doesNotMatch(code,/clipboard|Get-Clipboard|readClipboard|provision\(/i);
});
test('an unavailable model does not erase the key or prevent weather diagnosis',async()=>{
 let reads=0;const r=await verify({readState:()=>({}),shell:()=>{},shellTimed:()=>{reads++;return 'TV_WEATHER_JSON='+JSON.stringify(w);}},{sleep:async()=>{},testTv:async()=>{throw Error('unavailable');}});
 assert.equal(reads,1);assert.ok(r.check_error);assert.equal(r.check,null);assert.equal(r.weather.network,'http_403');
});

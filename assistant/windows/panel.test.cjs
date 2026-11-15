'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const {createPanel,validatePoint,validateText,uiNodes,keyEvents}=require('./panel.cjs');
test('remote diagnostic returns only bounded key codes, never raw log text',()=>{
 assert.deepEqual(keyEvents('account private text\nTvAssistantKeys: keycode 231 action 0\nTvAssistantKeys: keycode 231 action 1\n'),[{code:231,action:'down'},{code:231,action:'up'}]);
 assert.equal(keyEvents('keycode 219 action 0\n'.repeat(100)).length,32);
});
test('stale frames and out-of-bounds taps cannot be sent to TV',()=>{
 const f={id:'new',at:Date.now(),width:1920,height:1080};validatePoint({frame:'new',x:100,y:100},f);
 assert.throws(()=>validatePoint({frame:'old',x:100,y:100},f));assert.throws(()=>validatePoint({frame:'new',x:1920,y:0},f));assert.throws(()=>validatePoint({frame:'new',x:1.1,y:0},f));
 assert.throws(()=>validatePoint({frame:'new',x:0,y:0},{...f,at:Date.now()-61000}));
});
test('text entry is bounded and rejects controls and unsupported encoding',()=>{validateText('Open YouTube');assert.throws(()=>validateText(''));assert.throws(()=>validateText('x'.repeat(201)));assert.throws(()=>validateText('x\nnext'));assert.throws(()=>validateText('Открой Ютуб'));});
test('visible UI parsing masks password text and keeps node package and bounds',()=>{
 const n=uiNodes('<hierarchy><node text="secret" content-desc="private" password="true" focused="true" class="android.widget.EditText" package="example" bounds="[1,2][3,4]"/><node text="Settings &amp; more" clickable="true" bounds="[5,6][7,8]"/></hierarchy>');
 assert.equal(n[0].text,'[поле пароля]');assert.equal(n[0].description,'');assert.equal(n[0].package,'example');assert.equal(n[1].text,'Settings & more');
});
test('local panel rejects unauthenticated, cross-origin and unknown commands',async()=>{
 let calls=0;const adapter={shellTimed(){calls++;},readState(){calls++;return{model:'MiTV-MSSP1'};}};
 const p=createPanel(adapter,{port:0,token:'a'.repeat(64)});await p.start();const origin='http://127.0.0.1:'+p.port;
 try{
  let r=await fetch(origin+'/api/check',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:'{}'});assert.equal(r.status,403);
  r=await fetch(origin+'/api/check',{method:'POST',headers:{Origin:'https://example.com','X-Tv-Token':p.token},body:'{}'});assert.equal(r.status,403);
  r=await fetch(origin+'/api/check',{method:'POST',headers:{Origin:origin,'X-Tv-Token':'é'.repeat(64)},body:'{}'});assert.equal(r.status,403);
  r=await fetch(origin+'/api/key',{method:'POST',headers:{Origin:origin,'X-Tv-Token':p.token},body:JSON.stringify({key:'shell'})});assert.equal(r.status,400);assert.equal(calls,0);
  r=await fetch(origin+'/api/check',{method:'POST',headers:{Origin:origin,'X-Tv-Token':p.token},body:'{}'});assert.equal(r.status,200);assert.equal(calls,1);
 }finally{await p.close();}
});
test('weather diagnostics expose only the scoped service report and accept no parameters',async()=>{
 let calls=0;const weather={version:1,visible:true,ambient_sensor_available:false,ambient_probe_error:null,network:'timeout',has_usable_weather:false};
 const adapter={shellTimed(timeout,...args){calls++;assert.equal(timeout,6000);assert.deepEqual(args,['dumpsys','activity','service','dev.mert.tvassistant/.NavigationService','weather']);return 'private-header-do-not-return\nTV_WEATHER_JSON='+JSON.stringify(weather);}};
 const p=createPanel(adapter,{port:0,token:'b'.repeat(64)});await p.start();const origin='http://127.0.0.1:'+p.port;
 try{
  const headers={Origin:origin,'X-Tv-Token':p.token,'Content-Type':'application/json'};
  let r=await fetch(origin+'/api/weather',{method:'POST',headers,body:'{}'});assert.equal(r.status,200);const result=await r.json();assert.deepEqual(result.weather,weather);assert.doesNotMatch(JSON.stringify(result),/private-header/);assert.equal(calls,1);
  r=await fetch(origin+'/api/weather',{method:'POST',headers,body:JSON.stringify({command:'ignored'})});assert.equal(r.status,400);assert.equal(calls,1);
 }finally{await p.close();}
});

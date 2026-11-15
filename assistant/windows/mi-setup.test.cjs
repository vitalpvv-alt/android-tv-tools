'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs');
const {control,prepare}=require('./mi-setup.cjs'),{parseReport}=require('./voice-setup.cjs'),{createPanel}=require('./panel.cjs');
const native={version:1,service_available:true,button_enabled:false,mini_mode:true,keys:[],google_windows:[],last_route:'mi_key',
 activation:{bound:false,keycode:null,scancode:null,learning:true,last_learning_event:'waiting'},
 mini:{visible:true,busy:false,mode:'learn',completed_commands:0,last_tool_steps:0,width_px:1120,full_screen_activity_started:false},
 voice_input:{active:false,outcome:'not_started',recognizer_available:true,error_code:0,recognizer_component:'com.google.android.katniss/.RecognitionService'}};
const dump=()=> 'private native line\nTV_VOICE_JSON='+JSON.stringify({...native,query:'private dictation',mini:{...native.mini,query:'private dictation'}});
test('mini diagnostic retains binding and overlay state without copying speech text',()=>{
 const r=parseReport(dump());assert.equal(r.mini_mode,true);assert.equal(r.activation.learning,true);assert.equal(r.mini.visible,true);
 assert.equal(r.mini.full_screen_activity_started,false);assert.equal(r.voice_input.recognizer_component,native.voice_input.recognizer_component);
 assert.doesNotMatch(JSON.stringify(r),/private dictation|private native line/);
});
test('mini control only launches fixed DUMP-protected actions',()=>{
 const calls=[];const adapter={shellTimed:(t,...args)=>{calls.push([t,...args]);return args[0]==='dumpsys'?dump():'Status: ok';}};
 assert.equal(control(adapter,'learn').activation.learning,true);
 assert.deepEqual(calls[0],[15000,'am','start','-W','-n','dev.mert.tvassistant/.MiniControlActivity','-a','dev.mert.tvassistant.LEARN_MI']);
 assert.deepEqual(calls[1],[6000,'dumpsys','activity','service','dev.mert.tvassistant/.NavigationService','voice']);
 assert.throws(()=>control(adapter,'input keyevent 191'));assert.equal(calls.length,2);
});
test('mini installer waits for compact service then arms actual-key learning without a model call',async()=>{
 const calls=[];let n=0;
 const adapter={shell:(...a)=>calls.push(a),shellTimed:(t,...a)=>{calls.push(a);return a[0]==='dumpsys'?(++n===1?'not ready':dump()):'Status: ok';}};
 const r=await prepare(adapter,{sleep:async()=>{}});assert.equal(r.activation.learning,true);
 assert.equal(calls.filter(a=>a[0]==='input').length,1);assert.equal(calls.filter(a=>a[0]==='am').length,1);
 assert.ok(calls.every(a=>['input','dumpsys','am'].includes(a[0])));
});
test('a legacy service cannot be mistaken for a working mini service',async()=>{
 const a={shell:()=>{throw Error('must not launch');},shellTimed:()=> 'TV_VOICE_JSON='+JSON.stringify({...native,mini_mode:false})};
 await assert.rejects(prepare(a,{sleep:async()=>{}}),/Мини-служба/);
});
test('updating keeps the learned physical Mi code and never re-arms learning',async()=>{
 const calls=[];const bound={...native,activation:{bound:true,keycode:191,scancode:400,learning:false,last_learning_event:'bound'}};
 const adapter={shell:(...a)=>calls.push(a),shellTimed:(t,...a)=>{calls.push(a);return a[0]==='dumpsys'?'TV_VOICE_JSON='+JSON.stringify(bound):'Status: ok';}};
 const report=await prepare(adapter,{sleep:async()=>{}});
 assert.equal(report.activation.scancode,400);assert.equal(report.activation.keycode,191);
 assert.ok(calls.some(a=>a.includes('dev.mert.tvassistant.PREVIEW_MINI')));
 assert.ok(!calls.some(a=>a.includes('dev.mert.tvassistant.LEARN_MI')));
});
test('speech diagnostics expose state but never spoken or recognized text',()=>{
 const report=parseReport('TV_VOICE_JSON='+JSON.stringify({...native,mini:{...native.mini,spoken_answers:{enabled:true,ready:true,speaking:false,outcome:'spoken',engine:'com.google.android.tts',text:'private phrase'},answer_scrollable:true}}));
 assert.equal(report.mini.spoken_answers.outcome,'spoken');assert.equal(report.mini.answer_scrollable,true);
 assert.doesNotMatch(JSON.stringify(report),/private phrase/);
});
test('mini panel rejects arbitrary keycodes and commands before any device operation',async()=>{
 let calls=0;const adapter={shellTimed:(t,...a)=>{calls++;return a[0]==='dumpsys'?dump():'Status: ok';}};
 const panel=createPanel(adapter,{port:0,token:'c'.repeat(64)}),url=await panel.start(),origin=new URL(url).origin;
 const headers={'Content-Type':'application/json',Origin:origin,'X-Tv-Token':'c'.repeat(64)};
 try{
  for(const route of ['mi-learn','mini-preview','mini-speech']){
   let r=await fetch(origin+'/api/'+route,{method:'POST',headers,body:JSON.stringify({keycode:191})});assert.equal(r.status,400);assert.equal(calls,0);
   r=await fetch(origin+'/api/'+route,{method:'POST',headers,body:'{}'});assert.equal(r.status,200);assert.equal((await r.json()).voice.mini.visible,true);
   calls=0;
  }
 }finally{await panel.close();}
});
test('mini installer neither reads keys nor starts the full-screen assistant',()=>{
 const s=fs.readFileSync(require.resolve('./mi-setup.cjs'),'utf8');
 assert.doesNotMatch(s,/clipboard|Get-Clipboard|provision\(|testTv\(|\.MainActivity|pm.*disable/);
 assert.match(s,/setup\.main\('--install-mini'\)/);
});

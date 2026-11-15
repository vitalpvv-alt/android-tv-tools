'use strict';
const fs=require('node:fs'),path=require('node:path');
const {spawn}=require('node:child_process');
const setup=require('./setup.cjs');
const marker='TV_VOICE_JSON=';
function parseReport(output){
 if(typeof output!=='string'||output.length>65536)return null;
 const line=output.split(/\r?\n/).find(x=>x.trim().startsWith(marker));if(!line)return null;
 try{
  const r=JSON.parse(line.trim().slice(marker.length));
  if(r.version!==1||typeof r.service_available!=='boolean'||typeof r.button_enabled!=='boolean')return null;
  if(!Array.isArray(r.keys)||r.keys.length>32||!Array.isArray(r.google_windows)||r.google_windows.length>8)return null;
  const keys=r.keys.map(k=>{
   if(!Number.isInteger(k.code)||k.code<0||k.code>500||!['down','up'].includes(k.action))throw Error();
   return{code:k.code,action:k.action};
  });
  const google_windows=r.google_windows.map(w=>{
   if(typeof w.class!=='string'||w.class.length>200||!(w.class==='[unknown]'||/^[A-Za-z0-9_.$]*$/.test(w.class)))throw Error();
   if(!['disabled','own_recognizer','keyboard','task_running','candidate','unmatched_window','mi_mode'].includes(w.decision))throw Error();
   return{class:w.class,decision:w.decision};
  });
  const vi=r.voice_input||{};
  const safeCode=s=>typeof s==='string'&&/^[a-z0-9_]{1,64}$/.test(s)?s:'unknown';
  let activation=null,mini=null;
  if(r.activation!=null){
   const a=r.activation;
   if(typeof a.bound!=='boolean'||typeof a.learning!=='boolean')throw Error();
   if(a.bound&&(!Number.isInteger(a.keycode)||a.keycode<1||a.keycode>500||!Number.isInteger(a.scancode)||a.scancode<0||a.scancode>65535))throw Error();
   activation={bound:a.bound,learning:a.learning,keycode:a.bound?a.keycode:null,scancode:a.bound?a.scancode:null,last_learning_event:safeCode(a.last_learning_event)};
  }
  if(r.mini!=null){
   const m=r.mini;
   if(typeof m.visible!=='boolean'||typeof m.busy!=='boolean')throw Error();
   mini={visible:m.visible,busy:m.busy,mode:safeCode(m.mode),completed_commands:Number.isSafeInteger(m.completed_commands)?m.completed_commands:0,
    last_tool_steps:Number.isInteger(m.last_tool_steps)&&m.last_tool_steps>=0&&m.last_tool_steps<=16?m.last_tool_steps:0,
    width_px:Number.isInteger(m.width_px)&&m.width_px>=0&&m.width_px<=7680?m.width_px:0,
    full_screen_activity_started:typeof m.full_screen_activity_started==='boolean'?m.full_screen_activity_started:null};
   if(m.spoken_answers&&typeof m.spoken_answers==='object'){
    const a=m.spoken_answers;
    mini.spoken_answers={enabled:a.enabled===true,ready:a.ready===true,speaking:a.speaking===true,outcome:safeCode(a.outcome),
     engine:typeof a.engine==='string'&&/^[A-Za-z0-9_.$]{0,200}$/.test(a.engine)?a.engine:'',
     language_status:Number.isInteger(a.language_status)?a.language_status:null,
     queued_parts:Number.isInteger(a.queued_parts)&&a.queued_parts>=0&&a.queued_parts<=100?a.queued_parts:0};
   }
   if(typeof m.answer_scrollable==='boolean')mini.answer_scrollable=m.answer_scrollable;
  }
  return{version:1,service_available:r.service_available,button_enabled:r.button_enabled,keys,google_windows,
   mini_mode:r.mini_mode===true,activation,mini,
   last_route:safeCode(r.last_route),voice_input:{active:vi.active===true,outcome:safeCode(vi.outcome),recognizer_available:typeof vi.recognizer_available==='boolean'?vi.recognizer_available:null,
    error_code:Number.isInteger(vi.error_code)&&vi.error_code>=0&&vi.error_code<=30?vi.error_code:0,
    recognizer_component:typeof vi.recognizer_component==='string'&&vi.recognizer_component.length<=200&&/^[A-Za-z0-9_.$/]*$/.test(vi.recognizer_component)?vi.recognizer_component:''}};
 }catch{return null;}
}
function readReport(adapter){
 return parseReport(adapter.shellTimed(6000,'dumpsys','activity','service','dev.mert.tvassistant/.NavigationService','voice'));
}
function summarize(r){
 if(!r)return['Голосовая диагностика недоступна. Для неё нужна новая сборка code21 и включённое управление экраном.'];
 const lines=[r.service_available?'Служба управления подключена.':'Служба управления не подключена.'];
 if(r.mini_mode){
  lines.push(r.activation?.learning?'Ожидается одно нажатие Mi на физическом пульте.':r.activation?.bound?'Кнопка Mi назначена: код '+r.activation.keycode+'.':'Кнопка Mi ещё не назначена. Нажми «Назначить кнопку Mi».');
  if(r.mini)lines.push('Мини-окно: '+(r.mini.visible?'показано':'скрыто')+'. Голосовой ввод: '+r.voice_input.outcome+'.');
  if(r.mini?.spoken_answers)lines.push('Русская озвучка: '+r.mini.spoken_answers.outcome+'.');
  return lines;
 }
 lines.push(r.button_enabled?'Переназначение голосовой кнопки включено.':'Включи кнопку микрофона: ТВ-помощник → Настройки → Разрешения.');
 if(r.voice_input.recognizer_available===false)lines.push('На ТВ не найден обработчик русского голосового ввода.');
 lines.push('Последний запуск: '+r.last_route+'. Голосовой ввод: '+r.voice_input.outcome+'.');
 if(r.google_windows.length)lines.push('Окно Google: '+r.google_windows.at(-1).class+' — '+r.google_windows.at(-1).decision+'.');
 else lines.push('Нажми физическую кнопку микрофона и повтори проверку.');
 return lines;
}
async function main(){
 console.log('Обновление голосовой кнопки. Ключ xKiro повторно не нужен.');
 await setup.main('--install');setup.shell('input','keyevent','KEYCODE_HOME');
 let voice=null;
 for(let n=0;n<8;n++){
  await new Promise(resolve=>setTimeout(resolve,700));
  try{voice=readReport(setup);}catch{}
  if(voice?.service_available)break;
 }
 const report={observed_at:new Date().toISOString(),state:setup.readState(),voice,physical_remote_verified:false};
 const dir=path.join(__dirname,'reports');fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'voice-after-update.json'),JSON.stringify(report,null,2)+'\n');
 console.log(summarize(voice).join('\n'));
 console.log('Теперь нажми микрофон на пульте и скажи «Покажи информацию о телевизоре».');
 const panel=require('./panel.cjs').createPanel(setup,{port:0,initialVoiceReport:report,
  initialReport:{note:'Ключ xKiro сохранён на ТВ. Для кнопки используй раздел «Голосовая кнопка». Модель этим запуском не вызывается.'}});
 const url=await panel.start();
 console.log('\nПанель проверки: '+url+'\nОставь это окно открытым.');
 spawn('rundll32.exe',['url.dll,FileProtocolHandler',url],{windowsHide:true,stdio:'ignore'}).on('error',()=>{});
}
module.exports={parseReport,readReport,summarize,main};
if(require.main===module)main().catch(e=>{console.error(e.message);process.exitCode=1;});

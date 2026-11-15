'use strict';
const fs=require('node:fs'),path=require('node:path');
const {spawn}=require('node:child_process');
const setup=require('./setup.cjs'),voice=require('./voice-setup.cjs');
const APP='dev.mert.tvassistant';
const actions=Object.freeze({learn:'dev.mert.tvassistant.LEARN_MI',preview:'dev.mert.tvassistant.PREVIEW_MINI',speech:'dev.mert.tvassistant.TEST_MINI_SPEECH'});
function control(adapter,mode){
 if(!Object.hasOwn(actions,mode))throw Error('Неизвестное действие мини-помощника.');
 const launch=adapter.shellTimed(15000,'am','start','-W','-n',APP+'/.MiniControlActivity','-a',actions[mode]);
 if(/Error:|Exception|does not exist/i.test(launch))throw Error('Телевизор не подтвердил запуск мини-окна.');
 return voice.readReport(adapter);
}
async function prepare(adapter,options={}){
 const sleep=options.sleep||((ms)=>new Promise(resolve=>setTimeout(resolve,ms)));
 let r=null;
 for(let n=0;n<8;n++){
  try{r=voice.readReport(adapter);}catch{}
  if(r?.service_available&&r.mini_mode)break;
  await sleep(700);
 }
 if(!r?.service_available||!r.mini_mode)throw Error('Мини-служба ещё не подключена. Повтори MI.cmd через несколько секунд.');
 adapter.shell('input','keyevent','KEYCODE_HOME');
 return control(adapter,r.activation?.bound?'preview':'learn');
}
async function main(){
 console.log('Мини-помощник по кнопке Mi. Погода и простые команды работают без ключа ИИ.');
 await setup.main('--install-mini');
 const report=await prepare(setup);
 const dir=path.join(__dirname,'reports');fs.mkdirSync(dir,{recursive:true});
 fs.writeFileSync(path.join(dir,'mini-after-update.json'),JSON.stringify({observed_at:new Date().toISOString(),voice:report,binding_preserved:report?.activation?.bound===true},null,2)+'\n');
 console.log(report?.activation?.bound?'Кнопка Mi сохранена. Нажми Mi и скажи «Найди сериал Шерлок».':'Сейчас на ТВ нажми Mi ОДИН раз для назначения. Затем нажми ещё раз и скажи команду.');
 const panel=require('./panel.cjs').createPanel(setup,{port:0,initialVoiceReport:{voice:report,summary:voice.summarize(report)},
  initialReport:{note:'Для свободных команд настройте свой ключ ИИ в панели. Мини-режим не запускает полноэкранный ТВ-помощник. Для назначения используй раздел кнопки Mi.'}});
 const url=await panel.start();console.log('\nПанель проверки: '+url+'\nОставь окно открытым.');
 spawn('rundll32.exe',['url.dll,FileProtocolHandler',url],{windowsHide:true,stdio:'ignore'}).on('error',()=>{});
}
module.exports={control,prepare,main};
if(require.main===module)main().catch(e=>{console.error(e.message);process.exitCode=1;});

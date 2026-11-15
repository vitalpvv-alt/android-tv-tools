'use strict';
const fs=require('node:fs'),path=require('node:path');
const {spawn}=require('node:child_process');
const setup=require('./setup.cjs');
const xkiro=require('./xkiro-connect.cjs');
const weather=require('./weather-setup.cjs');
const APP='dev.mert.tvassistant';
async function verify(adapter,options={}){
 const sleep=options.sleep||((ms)=>new Promise(r=>setTimeout(r,ms)));
 const test=options.testTv||xkiro.testTv;
 const report={observed_at:new Date().toISOString(),state:adapter.readState(),check:null,check_error:null,weather:null};
 try{report.check=await test(adapter);}catch{report.check_error='Проверка ответа ИИ не завершилась. Повтори её кнопкой в панели.';}
 adapter.shell('input','keyevent','KEYCODE_HOME');
 for(let n=0;n<25;n++){
  await sleep(1000);
  try{report.weather=weather.parseReport(adapter.shellTimed(6000,'dumpsys','activity','service',APP+'/.NavigationService','weather'));}catch{}
  if(report.weather&&(report.weather.has_usable_weather||report.weather.network!=='not_checked'||['disabled','no_clear_space'].includes(report.weather.visibility)))break;
 }
 return report;
}
async function main(){
 console.log('Обновление помощника и проверка сохранённого xKiro. Ввод ключа не требуется.');
 await setup.main('--install');
 const report=await verify(setup);
 const dir=path.join(__dirname,'reports');fs.mkdirSync(dir,{recursive:true});fs.writeFileSync(path.join(dir,'assistant-after-update.json'),JSON.stringify(report,null,2)+'\n');
 console.log(report.check?.plan_ok?'xKiro: ответ с телевизора получен, план проверен. Действия на ТВ не выполнялись.':'Проверка ИИ требует внимания — смотри результат в панели.');
 console.log(weather.summarize(report.weather).join('\n'));
 const panel=require('./panel.cjs').createPanel(setup,{port:0,initialReport:report});const url=await panel.start();
 console.log('\nПанель проверки: '+url+'\nОставь это окно открытым. Отчёт: reports/assistant-after-update.json.');
 spawn('rundll32.exe',['url.dll,FileProtocolHandler',url],{windowsHide:true,stdio:'ignore'}).on('error',()=>{});
}
module.exports={verify,main};
if(require.main===module)main().catch(e=>{console.error(e.message);process.exitCode=1;});

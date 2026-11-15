'use strict';
const net=require('node:net'),crypto=require('node:crypto'),fs=require('node:fs'),path=require('node:path');
const {spawnSync,spawn}=require('node:child_process');
const {XKIRO_MODELS,validateCredentials,provision,exchange}=require('./openrouter-setup.cjs');
const {diagnose}=require('./openrouter-diagnostics.cjs');
function clipboardCredentials(text){
 if(typeof text!=='string'||text.length>1024)throw Error('В буфере нужен отдельный API-ключ xKiro sk-xt-…');
 const p={provider:'xkiro',model:XKIRO_MODELS[0],key:text.trim()};validateCredentials(p);return p;
}
function readClipboard(run=spawnSync){
 // Constant command. The credential is captured in memory, never put in args or a file.
 const r=run('powershell.exe',['-NoProfile','-NonInteractive','-Command','Get-Clipboard -Raw'],{encoding:'utf8',windowsHide:true,timeout:10000,maxBuffer:65536,stdio:['ignore','pipe','pipe']});
 if(r.error||r.status!==0)throw Error('Не удалось прочитать ключ из буфера Windows. Скопируй ключ и запусти XKIRO.cmd ещё раз.');
 return clipboardCredentials(r.stdout);
}
function cleanCheck(r){
 const kinds=['ok','unexpected_plan','invalid_response','http_error','unavailable_free_model','io','timeout','cancelled','not_ready'];
 if(!r||r.provider!=='xkiro'||!(XKIRO_MODELS.includes(r.model)||r.model==='')||!Number.isInteger(r.http)||r.http<0||r.http>599||typeof r.inference_ok!=='boolean'||typeof r.plan_ok!=='boolean'||!kinds.includes(r.kind)||r.executed_actions!==0||!Number.isInteger(r.elapsed_ms)||r.elapsed_ms<0||r.elapsed_ms>120000)throw Error('Неверный результат проверки ИИ.');
 if((r.inference_ok&&(r.http!==200||!XKIRO_MODELS.includes(r.model)))||(r.plan_ok&&(!r.inference_ok||r.kind!=='ok'))||(r.kind==='ok'&&!r.plan_ok))throw Error('Непоследовательный результат проверки ИИ.');
 return {provider:'xkiro',model:r.model,http:r.http,inference_ok:r.inference_ok,plan_ok:r.plan_ok,kind:r.kind,executed_actions:0,elapsed_ms:r.elapsed_ms};
}
async function receiveCheck(port,token){
 if(!Number.isInteger(port)||port<1||port>65535||!/^[a-f0-9]{64}$/.test(token))throw Error('Неверная сессия проверки.');
 const socket=net.createConnection({host:'127.0.0.1',port});socket.on('error',()=>{});socket.setTimeout(55000);socket.setNoDelay(true);
 try{const r=await exchange(socket,{type:'hello',token});if(r.version!==1)throw Error();return cleanCheck(r.inference_check);}finally{socket.destroy();}
}
async function testTv(adapter){
 const session=crypto.randomBytes(16).toString('hex'),token=crypto.randomBytes(32).toString('hex');let port;
 try{
  const s=adapter.readState();if(s.model!=='MiTV-MSSP1'||s.android!=='9'||s.sdk!=='28')throw Error();
  const launch=adapter.shellTimed(15000,'am','start','-W','-n','dev.mert.tvassistant/.KeySetupActivity','--es','session_id',session,'--es','session_token',token,'--es','setup_mode','test_xkiro');
  if(/Error:|Exception|does not exist/i.test(launch))throw Error();
  const raw=adapter.adb('forward','tcp:0','localabstract:tvassistant.key.'+session);
  if(!/^\d{1,5}$/.test(raw)||Number(raw)<1||Number(raw)>65535)throw Error();port=Number(raw);
  return await receiveCheck(port,token);
 }catch{throw Error('Проверка ответа xKiro не завершилась. Нужны code 18, соединение ADB и свободный помощник.');}
 finally{if(port)try{adapter.adb('forward','--remove','tcp:'+port);}catch{}}
}
async function connect(adapter,p,checks={provision,diagnose,testTv}){
 validateCredentials(p);if(p.provider!=='xkiro')throw Error('Этот запуск предназначен для xKiro.');
 let saved;
 try{saved=await checks.provision(adapter,p);}finally{p.key='';}
 const diagnostics=await checks.diagnose(adapter,undefined,'xkiro');
 const auth=diagnostics.tv?.probes?.find(x=>x.stage==='key');
 let check=null,checkError=null;
 if(auth?.recognized===true)try{check=await checks.testTv(adapter);}catch{checkError='Ключ сохранён, но проверка ответа ИИ не завершилась. Повтори её отдельной кнопкой в панели.';}
 return {saved,diagnostics,check,check_error:checkError,observed_at:new Date().toISOString()};
}
module.exports={clipboardCredentials,readClipboard,cleanCheck,receiveCheck,testTv,connect};
if(require.main===module)(async()=>{
 if(process.platform!=='win32')throw Error('XKIRO.cmd предназначен для компьютера Windows.');
 const p=readClipboard(),setup=require('./setup.cjs');let report;
 try{await setup.main('--install');report=await connect(setup,p);}finally{p.key='';}
 const folder=path.join(__dirname,'reports');fs.mkdirSync(folder,{recursive:true});fs.writeFileSync(path.join(folder,'xkiro-check.json'),JSON.stringify(report,null,2)+'\n');
 console.log(report.check?.plan_ok?'xKiro: ответ с телевизора получен, JSON-план проверен. Действия на ТВ не выполнялись.':'Ключ сохранён. Диагностика или ответ модели требуют проверки — смотри результат в панели.');
 const panel=require('./panel.cjs').createPanel(setup,{port:0,initialReport:report});const url=await panel.start();
 console.log('\nПанель проверки ТВ: '+url+'\nОставь окно открытым. Отчёт без ключа: reports/xkiro-check.json');
 spawn('rundll32.exe',['url.dll,FileProtocolHandler',url],{windowsHide:true,stdio:'ignore'}).on('error',()=>{});
})().catch(e=>{console.error(e.message);process.exitCode=1;});

'use strict';
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),os=require('node:os'),crypto=require('node:crypto');
const setup=require('./setup.cjs');
const openrouterSetup=require('./openrouter-setup.cjs');
const openrouterDiagnostics=require('./openrouter-diagnostics.cjs');
const xkiroConnect=require('./xkiro-connect.cjs');
const weatherSetup=require('./weather-setup.cjs');
const voiceSetup=require('./voice-setup.cjs');
const miSetup=require('./mi-setup.cjs');
const APP='dev.mert.tvassistant';
const KEYS=Object.freeze({up:19,down:20,left:21,right:22,ok:23,back:4,home:3,tab:61,settings:176});
function validatePoint(p,frame){
  if(!frame||p.frame!==frame.id||Date.now()-frame.at>60000)throw Error('Кадр устарел. Обнови экран ТВ.');
  if(!Number.isInteger(p.x)||!Number.isInteger(p.y)||p.x<0||p.y<0||p.x>=frame.width||p.y>=frame.height)throw Error('Координаты вне экрана.');
}
function validateText(s){if(typeof s!=='string'||!s.trim()||s.length>200||!/^[\x20-\x7e]+$/.test(s))throw Error('Для этой проверки нужна команда латиницей, до 200 символов.');}
function decodeXml(s){return String(s||'').replace(/&quot;/g,'"').replace(/&apos;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&');}
function uiNodes(xml){
 const out=[];
 for(const match of xml.matchAll(/<node\s+([^>]*?)(?:\/>|>)/g)){
  const a={};for(const p of match[1].matchAll(/([\w-]+)="([^"]*)"/g))a[p[1]]=decodeXml(p[2]);
  const password=a.password==='true';
  if(a.text||a['content-desc']||a.focused==='true'||a.clickable==='true')out.push({text:password?'[поле пароля]':a.text,description:password?'':a['content-desc'],class:a.class,package:a.package,focused:a.focused==='true',clickable:a.clickable==='true',enabled:a.enabled==='true',password,bounds:a.bounds});
 }
 return out;
}
function keyEvents(log){
 return [...String(log).matchAll(/keycode (\d+) action ([01])/g)].slice(-32).map(m=>({code:Number(m[1]),action:m[2]==='0'?'down':'up'}));
}
function createPanel(adapter,options={}){
 const token=options.token||crypto.randomBytes(32).toString('hex');let port=options.port??17892,busy=false,frame=null;
 const origin=()=>`http://127.0.0.1:${port}`;
 const reply=(res,status,body,type='application/json; charset=utf-8')=>{
  res.writeHead(status,{'Content-Type':type,'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer','Content-Security-Policy':"default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"});
  res.end(Buffer.isBuffer(body)||typeof body==='string'?body:JSON.stringify(body));
 };
 const body=req=>new Promise((resolve,reject)=>{let s='';req.on('data',b=>{s+=b;if(s.length>4096){reject(Error('Слишком большой запрос'));req.destroy();}});req.on('end',()=>{try{resolve(s?JSON.parse(s):{});}catch{reject(Error('Некорректный JSON'));}});req.on('error',reject);});
 function readUi(){
  const remote='/data/local/tmp/tv-assistant-ui-'+crypto.randomBytes(8).toString('hex')+'.xml';
  try{adapter.shellTimed(15000,'uiautomator','dump','--compressed',remote);return uiNodes(adapter.shellTimed(10000,'cat',remote));}
  finally{try{adapter.shellTimed(5000,'rm','-f',remote);}catch{}}
 }
 function screenshot(){
  const remote='/data/local/tmp/tv-assistant-panel-'+crypto.randomBytes(8).toString('hex')+'.png';
  const dir=fs.mkdtempSync(path.join(os.tmpdir(),'tv-assistant-panel-')),local=path.join(dir,'screen.png');
  try{
   adapter.shellTimed(20000,'screencap','-p',remote);adapter.adb('pull',remote,local);
   const b=fs.readFileSync(local);
   if(b.length<24||b.length>16*1024*1024||!b.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))throw Error('Телевизор не вернул PNG');
   frame={id:crypto.randomBytes(12).toString('hex'),at:Date.now(),width:b.readUInt32BE(16),height:b.readUInt32BE(20)};
   return b;
  }finally{try{adapter.shellTimed(5000,'rm','-f',remote);}catch{}fs.rmSync(dir,{recursive:true,force:true});}
 }
 const server=http.createServer(async(req,res)=>{
  if(req.headers.host!==`127.0.0.1:${port}`)return reply(res,403,{error:'Разрешён только локальный адрес ноутбука'});
  const route=req.url.split('?')[0];
  const files={'/':'panel.html','/panel.js':'panel.js','/panel.css':'panel.css'};
  if(req.method==='GET'&&files[route]){
   let content=fs.readFileSync(path.join(__dirname,files[route]));
   if(route==='/'){
    const escaped=value=>JSON.stringify(value,null,2).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
    content=content.toString().replace('%XKIRO_START_REPORT%',escaped(options.initialReport||{note:'Сохранённый на ТВ ключ повторно передавать не нужно.'}))
      .replace('%VOICE_START_REPORT%',escaped(options.initialVoiceReport||{note:'Нажми физический микрофон на пульте, затем «Проверить голосовую кнопку».'}));
   }
   return reply(res,200,content,route==='/'?'text/html; charset=utf-8':route.endsWith('.js')?'text/javascript; charset=utf-8':'text/css; charset=utf-8');
  }
  if(!route.startsWith('/api/'))return reply(res,404,{error:'Нет такой страницы'});
  const supplied=String(req.headers['x-tv-token']||'');
  if(!/^[a-f0-9]{64}$/.test(supplied)||!crypto.timingSafeEqual(Buffer.from(supplied),Buffer.from(token)))return reply(res,403,{error:'Открой полную ссылку из окна установщика'});
  if(req.headers.origin&&req.headers.origin!==origin())return reply(res,403,{error:'Другая страница не имеет доступа'});
  if(req.method==='GET'&&route==='/api/frame')return reply(res,200,{frame,busy});
  if(req.method!=='POST'||req.headers.origin!==origin())return reply(res,403,{error:'Нужен локальный запрос из панели'});
  if(!['/api/check','/api/screen','/api/ui','/api/key','/api/tap','/api/command','/api/assistant','/api/keys','/api/openrouter','/api/openrouter-diagnostics','/api/xkiro','/api/xkiro-diagnostics','/api/xkiro-test','/api/weather','/api/voice','/api/mi-learn','/api/mini-preview','/api/mini-speech'].includes(route))return reply(res,404,{error:'Команда не разрешена'});
  if(busy)return reply(res,409,{error:'Дождись завершения команды'});
  busy=true;
  try{
   const p=await body(req);let result={};
   if(route==='/api/openrouter'||route==='/api/xkiro'){
    if(!p||Object.keys(p).sort().join(',')!=='key,model')throw Error('Нужны только модель и ключ выбранного сервиса.');
    frame=null;result=await openrouterSetup.provision(adapter,route==='/api/xkiro'?{...p,provider:'xkiro'}:p);
   }
   else if(route==='/api/openrouter-diagnostics'||route==='/api/xkiro-diagnostics'){
    if(!p||Array.isArray(p)||typeof p!=='object'||Object.keys(p).length)throw Error('Диагностика не принимает ключи, адреса или команды.');
    frame=null;result=await openrouterDiagnostics.diagnose(adapter,options.pcProbe,route==='/api/xkiro-diagnostics'?'xkiro':'openrouter');
   }
   else if(route==='/api/xkiro-test'){
    if(!p||Array.isArray(p)||typeof p!=='object'||Object.keys(p).length)throw Error('Проверка ИИ не принимает команды, ключи или адреса.');
    frame=null;result=await xkiroConnect.testTv(adapter);
   }
   else if(route==='/api/weather'){
    if(!p||Array.isArray(p)||typeof p!=='object'||Object.keys(p).length)throw Error('Проверка погоды не принимает адреса, ключи или команды.');
    const report=weatherSetup.parseReport(adapter.shellTimed(6000,'dumpsys','activity','service',APP+'/.NavigationService','weather'));
    result={weather:report,summary:weatherSetup.summarize(report),observed_at:new Date().toISOString()};
   }
   else if(route==='/api/voice'){
    if(!p||Array.isArray(p)||typeof p!=='object'||Object.keys(p).length)throw Error('Проверка кнопки не принимает команды, адреса или ключи.');
    const report=voiceSetup.readReport(adapter);
    result={voice:report,summary:voiceSetup.summarize(report),observed_at:new Date().toISOString()};
   }
   else if(route==='/api/mi-learn'||route==='/api/mini-preview'||route==='/api/mini-speech'){
    if(!p||Array.isArray(p)||typeof p!=='object'||Object.keys(p).length)throw Error('Мини-помощник не принимает команды, коды кнопок или ключи.');
    frame=null;const report=miSetup.control(adapter,route==='/api/mi-learn'?'learn':route==='/api/mini-speech'?'speech':'preview');
    result={voice:report,summary:voiceSetup.summarize(report),observed_at:new Date().toISOString()};
   }
   else if(route==='/api/check')result=adapter.readState();
   else if(route==='/api/keys')result={events:keyEvents(adapter.shellTimed(10000,'logcat','-d','-t','100','-s','TvAssistantKeys:I','*:S')),observed_at:new Date().toISOString()};
   else if(route==='/api/screen')return reply(res,200,screenshot(),'image/png');
   else if(route==='/api/ui')result={nodes:readUi(),observed_at:new Date().toISOString()};
   else if(route==='/api/key'){
    if(!Object.hasOwn(KEYS,p.key))throw Error('Неизвестная кнопка');
    adapter.shellTimed(10000,'input','keyevent',String(KEYS[p.key]));frame=null;
   }else if(route==='/api/tap'){
    validatePoint(p,frame);adapter.shellTimed(10000,'input','tap',String(p.x),String(p.y));frame=null;
   }else if(route==='/api/assistant'){
    result.launch=adapter.shellTimed(15000,'am','start','-W','-n',APP+'/.MainActivity');frame=null;
   }else if(route==='/api/command'){
    validateText(p.text);
    const focused=readUi().find(n=>n.focused&&n.class==='android.widget.EditText'&&n.package===APP&&!n.password);
    if(!focused)throw Error('Сначала выдели поле команды именно в TV Assistant. В других приложениях ввод заблокирован.');
    if(focused.text.length>200)throw Error('В поле слишком длинный текст — очисти его на ТВ вручную.');
    adapter.shellTimed(10000,'input','keyevent','123');
    if(focused.text.length)adapter.shellTimed(15000,'input','keyevent',...Array(focused.text.length).fill('67'));
    adapter.shellTimed(10000,'input','text',p.text.replace(/ /g,'%s'));
    adapter.shellTimed(10000,'input','keyevent','66');frame=null;
   }
   reply(res,200,{ok:true,...result});
  }catch(e){reply(res,400,{error:e.message});}finally{busy=false;}
 });
 return {server,token,get port(){return port;},start:async()=>{await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(port,'127.0.0.1',resolve);});port=server.address().port;return origin()+'/#'+token;},close:()=>new Promise(resolve=>server.close(resolve))};
}
module.exports={createPanel,validatePoint,validateText,uiNodes,keyEvents};
if(require.main===module)(async()=>{
 await setup.main('--install');
 const panel=createPanel(setup);const url=await panel.start();
 console.log('\nПанель проверки ТВ: '+url+'\nОставь это окно открытым на время настройки. Для закрытия нажми Ctrl+C.');
 require('node:child_process').spawn('rundll32.exe',['url.dll,FileProtocolHandler',url],{windowsHide:true,stdio:'ignore'}).on('error',()=>{});
})().catch(e=>{console.error(e.message);process.exitCode=1;});

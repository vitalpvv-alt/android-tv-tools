'use strict';
const net=require('node:net'),https=require('node:https'),crypto=require('node:crypto');
const {MODELS,XKIRO_MODELS,exchange}=require('./openrouter-setup.cjs');
function modelsFor(provider){if(provider==='openrouter')return MODELS;if(provider==='xkiro')return XKIRO_MODELS;throw Error('Неизвестный сервис диагностики.');}
const KINDS=new Set(['ok','unauthorized','missing_authorization','disabled_or_expired','forbidden','moderation','region_restricted','browser_challenge','quota','rate_limit','redirect','unexpected_response','dns','tls','timeout','connection','io','cancelled']);
function cleanProbe(p,stage){
 if(!p||p.stage!==stage||!Number.isInteger(p.http)||p.http<0||p.http>599||!['json','html','text','other'].includes(p.format)||!KINDS.has(p.kind)||typeof p.recognized!=='boolean'||!Number.isInteger(p.elapsed_ms)||p.elapsed_ms<0||p.elapsed_ms>120000)
  throw Error('Неверный отчёт диагностики ТВ.');
 return {stage,http:p.http,format:p.format,kind:p.kind,recognized:p.recognized,elapsed_ms:p.elapsed_ms};
}
function cleanReport(r,provider='openrouter'){
 const models=modelsFor(provider);
 if(!r||r.provider!==provider||typeof r.configured!=='boolean'||!(models.includes(r.model)||r.model==='')||typeof r.key_fingerprint!=='string'||!Array.isArray(r.probes))throw Error('Неверный отчёт диагностики ТВ.');
 if(r.configured?(!models.includes(r.model)||!/^[a-f0-9]{12}$/.test(r.key_fingerprint)||r.probes.length!==2):(r.model!==''||r.key_fingerprint!==''||r.probes.length!==1))throw Error('Неверный отчёт диагностики ТВ.');
 return {provider,configured:r.configured,model:r.model,key_fingerprint:r.key_fingerprint,probes:r.probes.map((p,i)=>cleanProbe(p,i===0?'catalog':'key'))};
}
async function receive(port,token,provider='openrouter'){
 modelsFor(provider);
 if(!Number.isInteger(port)||port<1||port>65535||!/^[a-f0-9]{64}$/.test(token))throw Error('Неверная сессия диагностики.');
 const socket=net.createConnection({host:'127.0.0.1',port});socket.on('error',()=>{});socket.setTimeout(45000);socket.setNoDelay(true);
 try{const r=await exchange(socket,{type:'hello',token});if(r.version!==1||!r.diagnostics)throw Error('Нужно обновить приложение на ТВ.');return cleanReport(r.diagnostics,provider);}
 finally{socket.destroy();}
}
async function diagnoseTv(adapter,provider='openrouter'){
 modelsFor(provider);
 const session=crypto.randomBytes(16).toString('hex'),token=crypto.randomBytes(32).toString('hex');let port;
 try{
  const state=adapter.readState();
  if(state.model!=='MiTV-MSSP1'||state.android!=='9'||state.sdk!=='28')throw Error();
  // No credential, URL, command or provider can be supplied to this read-only mode.
  const launch=adapter.shellTimed(15000,'am','start','-W','-n','dev.mert.tvassistant/.KeySetupActivity','--es','session_id',session,'--es','session_token',token,'--es','setup_mode',provider==='xkiro'?'diagnose_xkiro':'diagnose');
  if(/Error:|Exception|does not exist/i.test(launch))throw Error();
  const raw=adapter.adb('forward','tcp:0','localabstract:tvassistant.key.'+session);
  if(!/^\d{1,5}$/.test(raw)||Number(raw)<1||Number(raw)>65535)throw Error();port=Number(raw);
  return await receive(port,token,provider);
 }catch{throw Error('ТВ не вернул диагностику. Нужны актуальная сборка, подключение ADB и свободный помощник. Сохранённый ключ менять не нужно.');}
 finally{if(port)try{adapter.adb('forward','--remove','tcp:'+port);}catch{}}
}
function contentFormat(type){const s=String(type||'').toLowerCase();return s.includes('json')?'json':s.includes('html')?'html':s.startsWith('text/')?'text':'other';}
function publicKind(status,type,mitigated,body){
 const text=String(body||'').toLowerCase();
 if(mitigated==='challenge'||(contentFormat(type)==='html'&&/cf-chl|challenge-platform|just a moment/.test(text)))return 'browser_challenge';
 if(status===401)return 'unauthorized';if(status===403)return 'forbidden';if(status===429)return 'rate_limit';
 if(status>=300&&status<400)return 'redirect';return status===200&&contentFormat(type)==='json'?'ok':'unexpected_response';
}
function probePc(provider='openrouter'){
 modelsFor(provider);
 const url=provider==='xkiro'?'https://api.xkiro.com/v1/models':'https://openrouter.ai/api/v1/models';
 const started=Date.now();
 return new Promise(resolve=>{
  let finished=false,timer,req;
  const done=(http,format,kind)=>{if(finished)return;finished=true;clearTimeout(timer);resolve({stage:'catalog',http,format,kind,recognized:false,elapsed_ms:Date.now()-started});};
  try{
   // Anonymous fixed metadata endpoint; never sends a key or generates tokens.
   req=https.get(url,{headers:{Accept:'application/json','User-Agent':'TVAssistant/0.1'}},res=>{
    const status=res.statusCode,type=res.headers['content-type'];
    if(status===200){done(status,contentFormat(type),publicKind(status,type,'',''));res.destroy();return;}
    let body=Buffer.alloc(0);
    const finish=()=>{done(status,contentFormat(type),publicKind(status,type,res.headers['cf-mitigated'],body.toString('utf8')));res.destroy();};
    res.on('data',chunk=>{body=Buffer.concat([body,chunk.subarray(0,Math.max(0,8192-body.length))]);if(body.length>=8192)finish();});
    res.on('end',finish);res.on('error',()=>done(status,contentFormat(type),'io'));
   });
   req.on('error',e=>done(0,'other',e.code==='ENOTFOUND'||e.code==='EAI_AGAIN'?'dns':/CERT|TLS|SSL/.test(e.code||'')?'tls':e.code==='ECONNREFUSED'?'connection':'io'));
   timer=setTimeout(()=>{done(0,'other','timeout');req.destroy();},10000);
  }catch{done(0,'other','io');}
 });
}
async function diagnose(adapter,pc=probePc,provider='openrouter'){
 modelsFor(provider);
 const r=await Promise.allSettled([diagnoseTv(adapter,provider),pc(provider)]);
 return {tv:r[0].status==='fulfilled'?r[0].value:{available:false,error:'ТВ не вернул диагностику. Обнови комплект через START.cmd и повтори.'},pc:r[1].status==='fulfilled'?cleanProbe(r[1].value,'catalog'):{stage:'catalog',http:0,format:'other',kind:'io',recognized:false,elapsed_ms:0},observed_at:new Date().toISOString()};
}
module.exports={cleanReport,receive,diagnoseTv,diagnose,probePc,publicKind};

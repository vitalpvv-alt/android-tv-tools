'use strict';
const net=require('node:net'),crypto=require('node:crypto');
const MODELS=Object.freeze(['qwen/qwen3.5-9b','openai/gpt-oss-20b','openrouter/free']);
const XKIRO_MODELS=Object.freeze(['mistralai/ministral-8b','mistralai/ministral-3b','mistralai/mistral-small-2603']);
function credentialProvider(p){return p&&Object.hasOwn(p,'provider')?p.provider:'openrouter';}
function validateCredentials(p){
 const provider=credentialProvider(p),fields=p&&Object.keys(p).sort().join(',');
 const models=provider==='openrouter'?MODELS:provider==='xkiro'?XKIRO_MODELS:[];
 const pattern=provider==='xkiro'?/^sk-xt-[A-Za-z0-9_-]{12,506}$/:/^sk-or-[A-Za-z0-9_-]{26,506}$/;
 if(!p||!['key,model','key,model,provider'].includes(fields)||!models.includes(p.model)||typeof p.key!=='string'||!pattern.test(p.key))
  throw Error('Выбери поддерживаемую модель и отдельный ключ нужного сервиса без пробелов.');
}
function exchange(socket,message){
 return new Promise((resolve,reject)=>{
  let buffer=Buffer.alloc(0);
  const cleanup=()=>{socket.off('data',data);socket.off('error',error);socket.off('end',end);socket.off('timeout',timeout);};
  const fail=()=>{cleanup();reject(Error('Не получено подтверждение от ТВ. Обнови приложение и повтори передачу.'));};
  const error=()=>fail(),end=()=>fail(),timeout=()=>{socket.destroy();fail();};
  function data(chunk){
   buffer=Buffer.concat([buffer,chunk]);
   if(buffer.length>8192)return fail();
   const at=buffer.indexOf(10);if(at<0)return;
   cleanup();try{if(at!==buffer.length-1)throw Error();resolve(JSON.parse(buffer.subarray(0,at).toString('utf8')));}catch{reject(Error('Телевизор вернул неверный ответ настройки.'));}
  }
  socket.on('data',data);socket.once('error',error);socket.once('end',end);socket.once('timeout',timeout);
  socket.write(JSON.stringify(message)+'\n');
 });
}
async function transfer(port,token,p){
 validateCredentials(p);
 if(!Number.isInteger(port)||port<1||port>65535||!/^[a-f0-9]{64}$/.test(token))throw Error('Неверная сессия передачи.');
 const socket=net.createConnection({host:'127.0.0.1',port});socket.on('error',()=>{});socket.setTimeout(20000);socket.setNoDelay(true);
 try{
  const hello=await exchange(socket,{type:'hello',token});
  if(hello.version!==1||typeof hello.public_key!=='string'||hello.public_key.length>2048||Object.keys(hello).sort().join(',')!=='public_key,version')throw Error('Нужна новая версия приложения на ТВ.');
  const publicKey=crypto.createPublicKey({key:Buffer.from(hello.public_key,'base64'),format:'der',type:'spki'});
  if(publicKey.asymmetricKeyType!=='rsa'||publicKey.asymmetricKeyDetails.modulusLength!==2048)throw Error('Неверный ключ сессии ТВ.');
  const secret=crypto.randomBytes(32),iv=crypto.randomBytes(12);
  let envelope;
  try{
   const cipher=crypto.createCipheriv('aes-256-gcm',secret,iv);cipher.setAAD(Buffer.from(token,'ascii'));
   const ciphertext=Buffer.concat([cipher.update(JSON.stringify(p),'utf8'),cipher.final(),cipher.getAuthTag()]);
   envelope={wrapped:crypto.publicEncrypt({key:publicKey,padding:crypto.constants.RSA_PKCS1_OAEP_PADDING,oaepHash:'sha256'},secret).toString('base64'),iv:iv.toString('base64'),payload:ciphertext.toString('base64')};
  }finally{secret.fill(0);}
  const result=await exchange(socket,envelope);
  if(result.ok!==true||result.provider!==credentialProvider(p)||result.model!==p.model||Object.keys(result).sort().join(',')!=='model,ok,provider')throw Error('Не получено подтверждение сохранения. Останови задачу помощника и повтори передачу.');
  return {saved:true,provider:credentialProvider(p),model:p.model,verified:false};
 }finally{socket.destroy();}
}
async function provision(adapter,p){
 validateCredentials(p);
 const session=crypto.randomBytes(16).toString('hex'),token=crypto.randomBytes(32).toString('hex');let port;
 try{
  const state=adapter.readState();
  if(state.model!=='MiTV-MSSP1'||state.android!=='9'||state.sdk!=='28')throw Error();
  const launch=adapter.shellTimed(15000,'am','start','-W','-n','dev.mert.tvassistant/.KeySetupActivity','--es','session_id',session,'--es','session_token',token);
  if(/Error:|Exception|does not exist/i.test(launch))throw Error();
  const raw=adapter.adb('forward','tcp:0','localabstract:tvassistant.key.'+session);
  if(!/^\d{1,5}$/.test(raw)||Number(raw)<1||Number(raw)>65535)throw Error();port=Number(raw);
  return await transfer(port,token,p);
 }catch{throw Error('Не получено подтверждение сохранения ключа. Проверь обновление ТВ-помощника, подключение и отсутствие запущенной задачи, затем повтори.');}
 finally{if(port)try{adapter.adb('forward','--remove','tcp:'+port);}catch{}}
}
module.exports={MODELS,XKIRO_MODELS,credentialProvider,validateCredentials,transfer,provision,exchange};

'use strict';
const readline=require('node:readline/promises');

function validateTarget(value){
 const target=String(value||'').trim();
 if(!target||target.length>200||!target.match(/^[A-Za-z0-9][A-Za-z0-9_.:-]*$/))
  throw Error('Укажи адрес своего ТВ в формате IP:порт или серийный номер USB.');
 if(target.includes(':')){
  const m=target.match(/^([A-Za-z0-9][A-Za-z0-9_.-]*):(\d{1,5})$/);
  if(!m||Number(m[2])<1||Number(m[2])>65535)throw Error('Проверь адрес и порт ADB телевизора.');
  if(/^\d+(?:\.\d+){3}$/.test(m[1])&&m[1].split('.').some(n=>Number(n)>255))throw Error('Неверный IPv4-адрес телевизора.');
 }
 return target;
}
function isNetworkTarget(target){return target.includes(':');}
async function chooseTarget(options={}){
 const env=options.env||process.env;
 let target=env.TV_SERIAL;
 if(!target){
  if(options.ask)target=await options.ask();
  else{
   const rl=readline.createInterface({input:process.stdin,output:process.stdout});
   try{target=await rl.question('Адрес своего ТВ (IP:порт) или серийный номер USB: ');}finally{rl.close();}
  }
 }
 return validateTarget(target);
}
module.exports={validateTarget,isNetworkTarget,chooseTarget};

'use strict';
const token=location.hash.slice(1),status=document.getElementById('status'),image=document.getElementById('tv');
let busy=false,frame=null,blobUrl;
async function api(route,data={},binary=false){
 const r=await fetch('/api/'+route,{method:'POST',headers:{'Content-Type':'application/json','X-Tv-Token':token},body:JSON.stringify(data)});
 if(!r.ok){const e=await r.json();throw Error(e.error||'Ошибка команды');}return binary?r.blob():r.json();
}
async function screen(){
 const b=await api('screen',{},true);const r=await fetch('/api/frame',{headers:{'X-Tv-Token':token}});frame=(await r.json()).frame;
 if(blobUrl)URL.revokeObjectURL(blobUrl);blobUrl=URL.createObjectURL(b);image.src=blobUrl;image.hidden=false;
 document.getElementById('frame-info').textContent=frame?new Date(frame.at).toLocaleTimeString():'';
}
async function action(work){if(busy)return;busy=true;document.querySelectorAll('button').forEach(b=>b.disabled=true);status.textContent='Выполняется…';try{await work();status.textContent='Готово';}catch(e){status.textContent=e.message;}finally{busy=false;document.querySelectorAll('button').forEach(b=>b.disabled=false);}}
document.getElementById('keys').onclick=()=>action(async()=>{document.getElementById('nodes').textContent=JSON.stringify(await api('keys'),null,2);});
document.getElementById('check').onclick=()=>action(async()=>{document.getElementById('report').textContent=JSON.stringify(await api('check'),null,2);});
document.getElementById('weather').onclick=()=>action(async()=>{document.getElementById('weather-check').textContent=JSON.stringify(await api('weather'),null,2);});
document.getElementById('voice').onclick=()=>action(async()=>{document.getElementById('voice-check').textContent=JSON.stringify(await api('voice'),null,2);});
document.getElementById('mi-learn').onclick=()=>action(async()=>{document.getElementById('voice-check').textContent=JSON.stringify(await api('mi-learn'),null,2);});
document.getElementById('mini-preview').onclick=()=>action(async()=>{document.getElementById('voice-check').textContent=JSON.stringify(await api('mini-preview'),null,2);});
document.getElementById('mini-speech').onclick=()=>action(async()=>{document.getElementById('voice-check').textContent=JSON.stringify(await api('mini-speech'),null,2);});
document.getElementById('screen').onclick=()=>action(screen);
document.getElementById('ui').onclick=()=>action(async()=>{document.getElementById('nodes').textContent=JSON.stringify(await api('ui'),null,2);});
document.getElementById('assistant').onclick=()=>action(async()=>{await api('assistant');await screen();});
document.querySelectorAll('[data-key]').forEach(b=>b.onclick=()=>action(async()=>{await api('key',{key:b.dataset.key});await screen();}));
image.onclick=e=>action(async()=>{if(!frame)throw Error('Сначала обнови экран');const b=image.getBoundingClientRect();const x=Math.floor((e.clientX-b.left)*frame.width/b.width),y=Math.floor((e.clientY-b.top)*frame.height/b.height);await api('tap',{x,y,frame:frame.id});await screen();});
document.getElementById('run').onclick=()=>action(async()=>{await api('command',{text:document.getElementById('command').value});await screen();});
document.getElementById('or-save').onclick=()=>action(async()=>{
 const input=document.getElementById('or-key'),note=document.getElementById('or-result');const key=input.value;input.value='';input.disabled=true;note.textContent='Передаю ключ на телевизор…';
 try{
  const result=await api('openrouter',{key,model:document.getElementById('or-model').value});
  if(!result.saved)throw Error('Телевизор не подтвердил сохранение ключа.');
  note.textContent='Ключ сохранён на ТВ. Нажми «Проверить подключение OpenRouter», затем проверь свободную команду.';
 }catch(e){note.textContent='Подтверждение не получено. Проверь сообщение выше и повтори передачу.';throw e;}
 finally{input.value='';input.disabled=false;}
});
document.getElementById('xk-save').onclick=()=>action(async()=>{
 const input=document.getElementById('xk-key'),note=document.getElementById('xk-result'),key=input.value;input.value='';input.disabled=true;note.textContent='Передаю ключ xKiro на телевизор…';
 try{const r=await api('xkiro',{key,model:document.getElementById('xk-model').value});if(!r.saved)throw Error('ТВ не подтвердил сохранение.');note.textContent='Ключ xKiro сохранён. Нажми «Проверить подключение xKiro».';}
 catch(e){note.textContent='Передача не подтверждена. Проверь сообщение выше.';throw e;}
 finally{input.value='';input.disabled=false;}
});
if(!/^[a-f0-9]{64}$/.test(token))status.textContent='Открой полную ссылку из окна установщика.';
async function diagnoseService(route,prefix,label){
 const note=document.getElementById(prefix+'-result'),details=document.getElementById(prefix+'-diagnostics');
 note.textContent='Проверяю доступ с ПК и телевизора. До 45 секунд…';details.textContent='';
 const result=await api(route);details.textContent=JSON.stringify(result,null,2);
 const statusLabel=p=>(p.http?'HTTP '+p.http:p.kind)+' · '+p.format;
 if(result.tv.available===false){note.textContent=result.tv.error+' ПК: '+statusLabel(result.pc);return;}
 const auth=result.tv.probes.find(p=>p.stage==='key');
 const names={ok:'доступен',unauthorized:'авторизация отклонена',missing_authorization:'сервер не получил авторизацию',disabled_or_expired:'ключ отключён или истёк',forbidden:'запрос запрещён',moderation:'ограничение политики',region_restricted:'сервис сообщил об ограничении региона',browser_challenge:'браузерная проверка',quota:'недостаточно квоты или средств',rate_limit:'лимит запросов',redirect:'перенаправление',unexpected_response:'неожиданный ответ',dns:'ошибка DNS',tls:'ошибка TLS',timeout:'нет ответа вовремя',connection:'ошибка соединения',io:'ошибка сети',cancelled:'проверка отменена'};
 note.textContent='ПК: '+statusLabel(result.pc)+'. ТВ: '+statusLabel(result.tv.probes[0])+'. '+(auth?'Ключ на ТВ: '+statusLabel(auth)+' — '+names[auth.kind]+'.':'В ТВ не сохранён ключ '+label+'.')+' Проверка не вызывает модель.';
}
document.getElementById('or-diagnose').onclick=()=>action(()=>diagnoseService('openrouter-diagnostics','or','OpenRouter'));
document.getElementById('xk-diagnose').onclick=()=>action(()=>diagnoseService('xkiro-diagnostics','xk','xKiro'));
document.getElementById('xk-test').onclick=()=>action(async()=>{
 const result=await api('xkiro-test');document.getElementById('xk-check').textContent=JSON.stringify(result,null,2);
 document.getElementById('xk-result').textContent=result.plan_ok?'xKiro ответил с телевизора; JSON-план верен. Действия на ТВ не выполнялись.':'Проверка ИИ не пройдена: HTTP '+result.http+', '+result.kind+'.';
});

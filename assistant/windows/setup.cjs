'use strict';
const fs = require('node:fs');
const path = require('node:path');
const https = require('node:https');
const crypto = require('node:crypto');
const { spawnSync } = require('node:child_process');
const readline = require('node:readline/promises');

const ROOT = __dirname;
let SERIAL = '';
const { chooseTarget, isNetworkTarget } = require('./adb-target.cjs');
const MODEL = 'MiTV-MSSP1';
const APP = 'dev.mert.tvassistant';
const NAV = APP + '/' + APP + '.NavigationService';
const PLAY = APP + '/' + APP + '.PlaybackService';
const ADB_PORT = '15039';
const APK_HASH = 'c69f0a87a1be1d8f54058df26a2813db5101a67f6bf9de5caac64972913b961a';
const TOOLS_URL = 'https://dl.google.com/android/repository/platform-tools_r37.0.1-win.zip';
const TOOLS_SHA1 = 'e03e78b1d80b396f1c3358e31251cb31740e1110';
const TOOL_HASHES = Object.freeze({
  'adb.exe': 'b4a6b455702684652cccf7b46258b29e653538904359a58fd4931cf3ef286b3f',
  'AdbWinApi.dll': 'c1d653030b4bde65d3e07e4d0b0979e17be56df1436cdd15528630f27808050d',
  'AdbWinUsbApi.dll': '0710e894d9b40f71a670c13c694079d564c92c1279da382cfe4850983aaebe1b',
});
const REPORTS = path.join(ROOT, 'reports');
const BASELINE = path.join(REPORTS, 'assistant-baseline.json');
let adbExe;

function digest(file, algorithm = 'sha256') {
  return crypto.createHash(algorithm).update(fs.readFileSync(file)).digest('hex');
}
function verifyFile(file, expected, algorithm = 'sha256') {
  if (!fs.existsSync(file) || digest(file, algorithm) !== expected) {
    throw new Error('Файл не прошёл проверку: ' + path.basename(file));
  }
}
function componentList(raw) {
  return [...new Set(String(raw || '').trim().split(':').filter(x => x && x !== 'null'))];
}
function addComponent(raw, component) {
  return [...new Set([...componentList(raw), component])].join(':');
}
function removeOwnComponent(current, original, component) {
  if (componentList(original).includes(component)) return current;
  const next = componentList(current).filter(x => x !== component);
  if (!next.length) return original === 'null' || !original ? null : '';
  return next.join(':');
}
function sameSet(a, b) {
  return JSON.stringify(componentList(a).sort()) === JSON.stringify(componentList(b).sort());
}
function validateTarget(info) {
  if (info.model !== MODEL || info.android !== '9' || info.sdk !== '28') {
    throw new Error('Остановлено: ожидался Xiaomi MiTV-MSSP1, Android 9/API 28. Получено: ' + JSON.stringify(info));
  }
}
function quoteRemote(word) {
  return "'" + String(word).replace(/'/g, "'\\''") + "'";
}
function run(exe, args, timeout = 60000, env) {
  const r = spawnSync(exe, args, {
    encoding: 'utf8', windowsHide: true, shell: false, timeout,
    maxBuffer: 8 * 1024 * 1024, env: env || process.env,
  });
  if (r.error) throw r.error;
  if (r.status !== 0) throw new Error((r.stderr || r.stdout || 'Команда не выполнена').trim());
  return (r.stdout || '').trim();
}
function adb(...args) { return run(adbExe, ['-P', ADB_PORT, '-s', SERIAL, ...args], 120000); }
function shell(...args) { return adb('shell', args.map(quoteRemote).join(' ')); }
function shellTimed(timeout, ...args) {
  return run(adbExe, ['-P', ADB_PORT, '-s', SERIAL, 'shell', args.map(quoteRemote).join(' ')], timeout);
}
function setting(key) { return shell('settings', 'get', 'secure', key); }
function putSetting(key, value) {
  if (value === null || value === 'null') return shell('settings', 'delete', 'secure', key);
  return shell('settings', 'put', 'secure', key, value);
}
function saveJson(file, object) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  const temp = file + '.tmp';
  fs.writeFileSync(temp, JSON.stringify(object, null, 2), { mode: 0o600 });
  fs.renameSync(temp, file);
}
async function download(url, destination, depth = 0) {
  if (depth > 4) throw new Error('Слишком много перенаправлений загрузки');
  const u = new URL(url);
  if (u.protocol !== 'https:' || !['dl.google.com', 'redirector.gvt1.com'].includes(u.hostname)) {
    throw new Error('Непредусмотренный адрес загрузки инструментов Google');
  }
  await new Promise((resolve, reject) => {
    const req = https.get(u, { timeout: 45000 }, response => {
      if ([301, 302, 303, 307, 308].includes(response.statusCode)) {
        response.resume();
        download(new URL(response.headers.location, u).href, destination, depth + 1).then(resolve, reject);
        return;
      }
      if (response.statusCode !== 200) { response.resume(); reject(new Error('Загрузка Google: HTTP ' + response.statusCode)); return; }
      let bytes = 0;
      const out = fs.createWriteStream(destination);
      response.on('data', chunk => { bytes += chunk.length; if (bytes > 40 * 1024 * 1024) response.destroy(new Error('Слишком большой архив')); });
      response.on('error', e => { out.destroy(); reject(e); });
      out.on('error', reject);
      out.on('finish', () => out.close(resolve));
      response.pipe(out);
    });
    req.on('timeout', () => req.destroy(new Error('Истекло время загрузки Google')));
    req.on('error', reject);
  });
}
async function ensureAdb() {
  const tools = path.join(ROOT, 'tools');
  const folder = path.join(tools, 'platform-tools');
  const verified = () => {
    try { for (const [name, hash] of Object.entries(TOOL_HASHES)) verifyFile(path.join(folder, name), hash); return true; }
    catch { return false; }
  };
  if (!verified()) {
    console.log('Загружаю инструменты Google ADB (около 8 МБ)...');
    fs.mkdirSync(tools, { recursive: true });
    const archive = path.join(tools, 'platform-tools-windows.zip');
    if (!fs.existsSync(archive) || digest(archive, 'sha1') !== TOOLS_SHA1) await download(TOOLS_URL, archive);
    verifyFile(archive, TOOLS_SHA1, 'sha1');
    run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command',
      'Expand-Archive -LiteralPath $env:TV_ASSISTANT_TOOLS_ZIP -DestinationPath $env:TV_ASSISTANT_TOOLS_DIR -Force'],
      90000, { ...process.env, TV_ASSISTANT_TOOLS_ZIP: archive, TV_ASSISTANT_TOOLS_DIR: tools });
    if (!verified()) throw new Error('Распакованные инструменты Google не прошли проверку');
  }
  return path.join(folder, 'adb.exe');
}
async function connect() {
  SERIAL = await chooseTarget();
  adbExe = await ensureAdb();
  run(adbExe, ['-P', ADB_PORT, 'start-server']);
  console.log('Подключаюсь к телевизору ' + SERIAL + '...');
  for (let attempt = 0; attempt < 3; attempt++) {
    const connection = isNetworkTarget(SERIAL) ? run(adbExe, ['-P', ADB_PORT, 'connect', SERIAL]) : '';
    if (/cannot connect|failed to connect|no route|connection refused/i.test(connection)) {
      throw new Error('Нет сетевого подключения к ' + SERIAL + ': ' + connection + '. Проверь адрес ТВ и одну домашнюю сеть.');
    }
    try { if (adb('get-state') === 'device') return; } catch {}
    console.log('На телевизоре разреши отладку для ноутбука. При необходимости отметь «Всегда разрешать».');
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
    try { await rl.question('После подтверждения нажми Enter здесь. '); } finally { rl.close(); }
  }
  throw new Error('Телевизор не подтвердил подключение. Проверь отладку и одну домашнюю сеть.');
}
function readState() {
  const info = {
    model: shell('getprop', 'ro.product.model'), android: shell('getprop', 'ro.build.version.release'),
    sdk: shell('getprop', 'ro.build.version.sdk'), firmware: shell('getprop', 'ro.build.display.id'),
  };
  validateTarget(info);
  let installed = '', packageInfo = '';
  try { installed = shell('pm', 'path', APP); if (installed) packageInfo = shell('dumpsys', 'package', APP); } catch {}
  return {
    ...info, observed_at: new Date().toISOString(), serial: SERIAL,
    installed: Boolean(installed), version: (packageInfo.match(/versionName=([^\s]+)/) || [])[1] || null,
    microphone_granted: /android\.permission\.RECORD_AUDIO:\s*granted=true/.test(packageInfo),
    accessibility: setting('enabled_accessibility_services'), accessibility_enabled: setting('accessibility_enabled'),
    listeners: setting('enabled_notification_listeners'),
    assistant: setting('assistant'), voice_interaction_service: setting('voice_interaction_service'),
    navigation_configured: componentList(setting('enabled_accessibility_services')).includes(NAV),
    playback_configured: componentList(setting('enabled_notification_listeners')).includes(PLAY),
  };
}
function enableControls() {
  const current = setting('enabled_accessibility_services');
  putSetting('enabled_accessibility_services', addComponent(current, NAV));
  putSetting('accessibility_enabled', '1');
  if (!componentList(setting('enabled_accessibility_services')).includes(NAV)) throw new Error('Не подтверждено разрешение навигации');
  const listeners = setting('enabled_notification_listeners');
  if (!componentList(listeners).includes(PLAY)) shell('cmd', 'notification', 'allow_listener', PLAY, '0');
  if (!componentList(setting('enabled_notification_listeners')).includes(PLAY)) throw new Error('Не подтверждено управление воспроизведением');
}
function restoreControls(baseline) {
  const before = setting('enabled_accessibility_services');
  const next = removeOwnComponent(before, baseline.accessibility, NAV);
  if (next !== before) putSetting('enabled_accessibility_services', next);
  if (sameSet(setting('enabled_accessibility_services'), baseline.accessibility)) {
    putSetting('accessibility_enabled', baseline.accessibility_enabled);
  }
  if (!componentList(baseline.listeners).includes(PLAY) && componentList(setting('enabled_notification_listeners')).includes(PLAY)) {
    shell('cmd', 'notification', 'disallow_listener', PLAY, '0');
  }
  if (!baseline.microphone_granted) {
    try { shell('pm', 'revoke', APP, 'android.permission.RECORD_AUDIO'); } catch {}
  }
}
async function main(mode) {
  if (process.platform !== 'win32') throw new Error('Этот установщик предназначен для ноутбука Windows.');
  if (Number(process.versions.node.split('.')[0]) < 20) throw new Error('Нужен Node.js 20 или новее, как для прежнего помощника Xiaomi.');
  console.log('TV Assistant — установка на Xiaomi MiTV-MSSP1 / Android 9');
  if (!['--install', '--install-mini', '--check', '--restore'].includes(mode)) throw new Error('Используй MI.cmd, START.cmd, CHECK.cmd или RESTORE_ACCESS.cmd.');
  if (mode === '--install' || mode === '--install-mini') verifyFile(path.join(ROOT, 'tv-assistant.apk'), APK_HASH);
  await connect();
  const before = readState();
  console.log('Телевизор подтверждён: ' + before.model + ', Android ' + before.android + '.');
  if (mode === '--check') {
    saveJson(path.join(REPORTS, 'assistant-check.json'), before);
    console.log(JSON.stringify(before, null, 2));
    console.log('Диагностика: reports/assistant-check.json. Настройки ТВ не менялись.');
    return;
  }
  if (mode === '--restore') {
    if (!fs.existsSync(BASELINE)) throw new Error('Нет исходного состояния от этой установки: assistant-baseline.json.');
    const baseline = JSON.parse(fs.readFileSync(BASELINE, 'utf8'));
    validateTarget(baseline);
    if (baseline.serial !== SERIAL) throw new Error('Исходное состояние относится к другому телевизору.');
    restoreControls(baseline);
    saveJson(path.join(REPORTS, 'assistant-restored.json'), readState());
    console.log('Права TV Assistant возвращены к исходному состоянию. Само приложение и его данные сохранены.');
    return;
  }
  if (!fs.existsSync(BASELINE)) saveJson(BASELINE, before);
  const baseline = JSON.parse(fs.readFileSync(BASELINE, 'utf8'));
  validateTarget(baseline);
  console.log('Устанавливаю проверенный APK. Подожди...');
  console.log(adb('install', '-r', path.join(ROOT, 'tv-assistant.apk')));
  let controls = false;
  const warnings = [];
  try {
    shell('pm', 'grant', APP, 'android.permission.RECORD_AUDIO');
    enableControls();
    controls = true;
  } catch (error) {
    warnings.push('Настройка прав не завершена: ' + error.message);
    try { restoreControls(before); warnings.push('Права возвращены к состоянию перед этой попыткой; повтори настройку через приложение.'); }
    catch (restoreError) { warnings.push('Проверь права вручную: ' + restoreError.message); }
  }
  const compact = mode === '--install-mini';
  const launch = compact ? 'Мини-режим: полноэкранное приложение не запускалось.' : shell('am', 'start', '-W', '-n', APP + '/.MainActivity');
  if (/Error:|Exception|does not exist/i.test(launch)) throw new Error('Android не подтвердил запуск: ' + launch);
  const after = readState();
  const activity = shell('dumpsys', 'activity', 'activities').split(/\r?\n/).filter(x => /mResumedActivity|mFocusedActivity|topResumedActivity/.test(x)).join('\n');
  const report = {
    ...after, apk_sha256: APK_HASH, controls_configured: controls, launch,
    foreground_activity: activity, foreground_observed: activity.includes(APP), warnings,
    physical_remote_voice_verified: false, chatgpt_signin_verified: false, marusia_connection_verified: false,
  };
  saveJson(path.join(REPORTS, 'assistant-installed.json'), report);
  console.log(compact ? '\nAPK обновлён. Далее появится мини-окно назначения Mi.' : '\nAPK установлен. Проверь экран телевизора: открылся ли TV Assistant.');
  console.log('Права управления: ' + (controls ? 'записаны и проверены чтением' : 'нужно проверить на ТВ'));
  for (const warning of warnings) console.log(warning);
  console.log('Настройки сохраняются. Погода и простые команды работают без ключа ИИ; для свободных команд укажи свой ключ в панели.');
  console.log('Результат сохранён: reports/assistant-installed.json. Голос и Маруся ещё требуют проверки.');
}
module.exports = { componentList, addComponent, removeOwnComponent, sameSet, validateTarget, quoteRemote, verifyFile, main, adb, shell, shellTimed, readState };
if (require.main === module) {
  main(process.argv[2]).catch(error => {
    console.error('\nОстановлено: ' + error.message);
    if (/signature|UPDATE_INCOMPATIBLE/i.test(error.message)) console.error('Существующая установка подписана другим ключом. Автоматического удаления приложения не было.');
    process.exitCode = 1;
  }).finally(() => {
    if (adbExe) try { run(adbExe, ['-P', ADB_PORT, 'disconnect', SERIAL], 15000); } catch {}
  });
}

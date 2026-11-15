'use strict';
// Task-scoped installer. The APK itself never needs ADB, Internet, an overlay or accessibility.
const fs = require('node:fs/promises');
const path = require('node:path');
const https = require('node:https');
const crypto = require('node:crypto');
const { spawn } = require('node:child_process');

let TARGET = '';
const { chooseTarget, isNetworkTarget } = require('./adb-target.cjs');
const MODEL = 'MiTV-MSSP1';
const PKG = 'ru.vital.tv.sunset';
const PANEL_KEY = 'picture_backlight';
const BYEDPI = 'io.github.romanvht.byedpi';
const DOWNLOAD = 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip';
const ROOT = __dirname;

function run(exe, args, { timeout = 20000, env = process.env } = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(exe, args, { shell: false, windowsHide: true, env });
    let out = '', err = '', bytes = 0, finished = false;
    const finish = (error, value) => {
      if (finished) return; finished = true; clearTimeout(timer);
      if (error) reject(error); else resolve(value);
    };
    const timer = setTimeout(() => { child.kill(); finish(new Error('Телевизор не ответил вовремя. Проверь подключение и отладку.')); }, timeout);
    for (const [stream, isError] of [[child.stdout, false], [child.stderr, true]]) stream.on('data', chunk => {
      bytes += chunk.length;
      if (bytes > 2 * 1024 * 1024) { child.kill(); finish(new Error('Неожиданно большой ответ ADB.')); return; }
      if (isError) err += chunk; else out += chunk;
    });
    child.on('error', e => finish(e));
    child.on('close', code => finish(code === 0 ? null : new Error((out + '\n' + err).trim() || 'ADB: ' + code), out.trim()));
  });
}
function level(value) {
  if (!/^(?:0|[1-9][0-9]?|100)$/.test(value)) throw new Error('В этой прошивке не найдено ожидаемое управление подсветкой 0–100. Настройки не изменены.');
  return Number(value);
}
async function snapshot(adb) {
  const model = await adb(['shell', 'getprop', 'ro.product.model']);
  if (model !== MODEL) throw new Error('Ожидался Xiaomi ' + MODEL + ', подключено: ' + model + '. Установка остановлена.');
  const backlight = level(await adb(['shell', 'settings', 'get', 'global', PANEL_KEY]));
  return { target: TARGET, model, backlight,
    home: await adb(['shell', 'cmd', 'package', 'resolve-activity', '--brief', '--user', '0', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME']),
    byedpi: await adb(['shell', 'pm', 'list', 'packages', '-e', '--user', '0', BYEDPI]),
    android: await adb(['shell', 'getprop', 'ro.build.version.release']),
    takenAt: new Date().toISOString() };
}
async function json(file, value) {
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file + '.tmp', JSON.stringify(value, null, 2) + '\n', { mode: 0o600 });
  await fs.rename(file + '.tmp', file);
}
async function installWith(adb, root, say = console.log) {
  const apk = path.join(root, 'SunsetTV_1_1.apk');
  const meta = JSON.parse(await fs.readFile(path.join(root, 'bundle.json'), 'utf8'));
  if (meta.package !== PKG || meta.model !== MODEL || !/^[a-f0-9]{64}$/.test(meta.sha256)) throw new Error('Повреждены сведения о комплекте.');
  const bytes = await fs.readFile(apk);
  if (crypto.createHash('sha256').update(bytes).digest('hex') !== meta.sha256) throw new Error('APK не совпадает с проверенной сборкой. Установка остановлена.');
  say('Проверяю модель, исходную подсветку, главный экран и ByeByeDPI…');
  const before = await snapshot(adb);
  const installed = (await adb(['shell', 'pm', 'list', 'packages', '--user', '0', PKG])).split(/\r?\n/).includes('package:' + PKG);
  before.recoveryBacklight = before.backlight;
  if (installed) {
    const status = await adb(['shell', 'am', 'broadcast', '-a', PKG + '.STATUS', '-n', PKG + '/.StatusReceiver']);
    const original = status.match(/\\?"original\\?"\s*:\s*(\d{1,3})(?=\s*[,}])/);
    if (original) before.recoveryBacklight = level(original[1]);
  }
  const reportPath = path.join(root, 'reports', 'before-install.json');
  try { await fs.access(reportPath); }
  catch { await json(reportPath, before); }
  say('Устанавливаю «Сумерки ТВ» и разрешаю приложению менять системную подсветку…');
  const answer = await adb(['install', '-r', apk], { timeout: 90000 });
  if (!/\bSuccess\b/.test(answer)) throw new Error('Телевизор не подтвердил установку: ' + answer);
  await adb(['shell', 'pm', 'grant', PKG, 'android.permission.WRITE_SECURE_SETTINGS']);
  await adb(['shell', 'am', 'start', '-n', PKG + '/.MainActivity']);
  const after = await snapshot(adb);
  if (!installed && after.backlight !== before.backlight) {
    await adb(['shell', 'settings', 'put', 'global', PANEL_KEY, String(before.backlight)]);
    throw new Error('При первой установке подсветка неожиданно изменилась; исходное значение возвращено. Расписание не подтверждено.');
  }
  if (after.home !== before.home || after.byedpi !== before.byedpi) throw new Error('Изменилось состояние главного экрана или ByeByeDPI. Требуется проверка перед включением автоматики.');
  const status = await adb(['shell', 'am', 'broadcast', '-a', PKG + '.STATUS', '-n', PKG + '/.StatusReceiver']);
  await json(path.join(root, 'reports', 'install-result.json'), { before, after, sha256: meta.sha256, appStatus: status });
  say('\nГотово. На телевизоре открыты «Сумерки ТВ 1.1».\nСумерки: вечером 100 → 75 → 50 → 25 → 0%, утром обратно.\nНастройки и состояние автоматики сохраняются при обновлении.\nПри первой установке проверь ночные 0% на 10 секунд и подтверди результат.\nЛаунчер, порядок Wink и ByeByeDPI сохранены.\nПосле настройки отладку можно выключить: программа работает без компьютера.');
  return { before, after, status };
}
async function restoreWith(adb, root, say = console.log) {
  await snapshot(adb); // Refuse another model or an unknown native control.
  let answer = '';
  try {answer = await adb(['shell', 'am', 'broadcast', '-a', PKG + '.STOP', '-n', PKG + '/.StatusReceiver']);}
  catch { /* A damaged app can still be stopped by Android and the saved panel value restored. */ }
  const confirmed = /result=0\b/.test(answer) && /(?:\\?"enabled\\?"\s*:\s*false)/.test(answer);
  await adb(['shell', 'am', 'force-stop', PKG]);
  if (!confirmed) {
    const before = JSON.parse(await fs.readFile(path.join(root, 'reports', 'before-install.json'), 'utf8'));
    if (before.target !== TARGET || before.model !== MODEL) throw new Error('Нет подходящей исходной копии подсветки.');
    const value = level(String(before.recoveryBacklight ?? before.backlight));
    await adb(['shell', 'settings', 'put', 'global', PANEL_KEY, String(value)]);
    say('Автоматика принудительно остановлена. Возвращена подсветка до установки: ' + value + '%.');
  } else say('Автоматика выключена, исходная подсветка возвращена.');
  return level(await adb(['shell', 'settings', 'get', 'global', PANEL_KEY]));
}
async function download(url, file, redirects = 0) {
  const u = new URL(url);
  if (u.protocol !== 'https:' || u.hostname !== 'dl.google.com' || redirects > 3) throw new Error('Разрешён только официальный источник ADB Google.');
  await new Promise((resolve, reject) => {
    const request = https.get(u, res => {
      if ([301, 302, 303, 307, 308].includes(res.statusCode)) {
        res.resume();
        if (!res.headers.location) return reject(new Error('Пустое перенаправление Google.'));
        download(new URL(res.headers.location, u).href, file, redirects + 1).then(resolve, reject); return;
      }
      if (res.statusCode !== 200) { res.resume(); reject(new Error('Загрузка Google: HTTP ' + res.statusCode)); return; }
      let bytes = 0; const chunks = [];
      res.on('data', b => { bytes += b.length; if (bytes > 80 * 1024 * 1024) res.destroy(new Error('Пакет ADB слишком велик.')); else chunks.push(b); });
      res.on('error', reject);res.on('end', () => fs.writeFile(file, Buffer.concat(chunks), { flag: 'wx' }).then(resolve, reject));
    });
    request.setTimeout(30000, () => request.destroy(new Error('Google не ответил за 30 секунд.')));
    request.on('error', reject);
  });
}
async function locateAdb(root) {
  const folders = [path.join(root, 'tools', 'platform-tools'),
    process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk', 'platform-tools'),
    ...(process.env.PATH || '').split(path.delimiter)].filter(Boolean);
  for (const folder of folders) {
    const exe = path.join(folder.replace(/^"|"$/g, ''), 'adb.exe');
    try { if ((await fs.stat(exe)).isFile()) return exe; } catch {}
  }
  return null;
}
async function prepareAdb(root) {
  let exe = await locateAdb(root); if (exe) return exe;
  const tools = path.join(root, 'tools');await fs.mkdir(tools, { recursive: true });
  const zip = path.join(tools, 'google-adb-' + crypto.randomBytes(6).toString('hex') + '.zip');
  console.log('Скачиваю официальный ADB Google…');
  try {
    await download(DOWNLOAD, zip);
    await run('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', "$ErrorActionPreference='Stop'; Expand-Archive -LiteralPath $env:SUNSET_ARCHIVE -DestinationPath $env:SUNSET_TOOLS -Force"],
      { timeout: 45000, env: { ...process.env, SUNSET_ARCHIVE: zip, SUNSET_TOOLS: tools } });
  } finally { await fs.unlink(zip).catch(() => {}); }
  exe = await locateAdb(root);if (!exe) throw new Error('ADB не найден после загрузки.');return exe;
}
async function main() {
  if (process.platform !== 'win32') throw new Error('Этот установщик предназначен для компьютера Windows.');
  TARGET = await chooseTarget();
  const exe = await prepareAdb(ROOT);
  await run(exe, ['-P', '15037', 'start-server']);
  console.log('Подключаюсь к телевизору ' + TARGET + '…');
  if (isNetworkTarget(TARGET)) await run(exe, ['-P', '15037', 'connect', TARGET]);
  const adb = (args, options) => run(exe, ['-P', '15037', '-s', TARGET, ...args], options);
  if (process.argv.slice(2).includes('--restore')) await restoreWith(adb, ROOT);
  else await installWith(adb, ROOT);
}
if (require.main === module) main().catch(e => {console.error('\n'+e.message+'\nЕсли ТВ просит разрешение на отладку, подтверди его пультом и запусти файл снова.');process.exitCode=1;});
module.exports = { installWith, restoreWith, snapshot, level, TARGET, MODEL, PKG, PANEL_KEY, BYEDPI };

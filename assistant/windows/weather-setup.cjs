'use strict';
const fs = require('node:fs');
const path = require('node:path');
const setup = require('./setup.cjs');
const APP = 'dev.mert.tvassistant';
const marker = 'TV_WEATHER_JSON=';

function parseReport(output) {
  const line = String(output).split(/\r?\n/).find(x => x.trim().startsWith(marker));
  if (!line) return null;
  try {
    const report = JSON.parse(line.trim().slice(marker.length));
    if (report.version !== 1 || typeof report.visible !== 'boolean' || typeof report.ambient_sensor_available !== 'boolean') return null;
    return report;
  } catch { return null; }
}
function summarize(report) {
  if (!report) return ['Служба погоды пока не ответила. Открой ТВ-помощник → Настройки → Погода на главном экране и проверь специальные возможности.'];
  const lines = [report.visible ? 'Карточка видна над иконками главного экрана.' : 'Карточка пока скрыта: ' + report.visibility + '.'];
  if (report.has_usable_weather) lines.push((report.stale ? 'Сохранён старый прогноз: ' : 'Прогноз доступен: ') + report.city + ', ' + report.source + '.');
  else lines.push('Погода ещё не получена: ' + report.network + '. Проверь интернет на телевизоре.');
  if (report.ambient_probe_error) lines.push('Проверка датчика воздуха не завершена: ' + report.ambient_probe_error + '.');
  else if (!report.ambient_sensor_available) lines.push('Датчик комнатной температуры Android не обнаружил. Строка скрыта.');
  else lines.push(report.room_celsius == null ? 'Датчик воздуха есть. Ожидается достоверное измерение.' : 'В комнате по датчику ТВ: ' + report.room_celsius + ' °C.');
  return lines;
}
async function main() {
  console.log('Погода для главного экрана Xiaomi. Ключ ИИ для неё не нужен.');
  await setup.main('--install');
  setup.shell('input', 'keyevent', 'KEYCODE_HOME');
  let report = null;
  const deadline = Date.now() + 24000;
  while (Date.now() < deadline) {
    await new Promise(resolve => setTimeout(resolve, 1000));
    try { report = parseReport(setup.shellTimed(4000, 'dumpsys', 'activity', 'service', APP + '/.NavigationService', 'weather')); } catch {}
    if (report && (report.has_usable_weather || report.visibility === 'disabled' || report.visibility === 'no_clear_space')) break;
  }
  fs.mkdirSync(path.join(__dirname, 'reports'), { recursive: true });
  const file = path.join(__dirname, 'reports', 'weather-check.json');
  fs.writeFileSync(file, JSON.stringify({ observed_at: new Date().toISOString(), actual_tv_report: report, physical_layout_checked_by_user: false }, null, 2));
  console.log('\n' + summarize(report).join('\n'));
  console.log('Настройки: ТВ-помощник → Настройки → Погода на главном экране. Отчёт: reports/weather-check.json.');
}
module.exports = { parseReport, summarize, main };
if (require.main === module) main().catch(error => { console.error('Погода: ' + error.message); process.exitCode = 1; });

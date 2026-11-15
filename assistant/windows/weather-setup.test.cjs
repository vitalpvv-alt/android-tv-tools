'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const { parseReport, summarize } = require('./weather-setup.cjs');
test('only a structured actual-service report confirms temperature sensor availability', () => {
  assert.equal(parseReport('no sensor data'), null);
  assert.equal(parseReport('TV_WEATHER_JSON={"version":1,"available":false}'), null);
  assert.equal(parseReport('TV_WEATHER_JSON=broken'), null);
  const absent = { version:1, visible:true, ambient_sensor_available:false, ambient_probe_error:null, has_usable_weather:true, city:'Находка', source:'MET Norway' };
  const parsed = parseReport('SERVICE dev.mert.tvassistant/.NavigationService\n  TV_WEATHER_JSON='+JSON.stringify(absent));
  assert.deepEqual(parsed,absent);
  assert.match(summarize(parsed).join(' '),/не обнаружил/);
  assert.doesNotMatch(summarize(null).join(' '),/не обнаружил/);
  assert.match(summarize({...absent,ambient_probe_error:'probe_failed'}).join(' '),/не завершена/);
  assert.match(summarize({...absent,ambient_sensor_available:true,room_celsius:null}).join(' '),/Ожидается/);
});
test('weather installation neither reads the clipboard nor changes the launcher or other apps', () => {
  const source=fs.readFileSync(require.resolve('./weather-setup.cjs'),'utf8');
  assert.doesNotMatch(source,/Get-Clipboard|clipboard|set-home|pm.+clear|uninstall|disable-user|byedpi|sunset/i);
  assert.match(source,/setup\.main\('--install'\)/);
  assert.match(source,/KEYCODE_HOME/);
  assert.match(source,/physical_layout_checked_by_user: false/);
});

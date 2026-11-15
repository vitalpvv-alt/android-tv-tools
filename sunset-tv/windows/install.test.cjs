'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');
const crypto = require('node:crypto');
const { installWith, restoreWith, MODEL, PKG, PANEL_KEY, BYEDPI } = require('./install.cjs');
async function fixture(t, overrides = {}) {
  const root = await fs.mkdtemp(path.join(os.tmpdir(), 'sunset-installer-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const bytes = Buffer.from('test-only-apk');await fs.writeFile(path.join(root, 'SunsetTV_1_1.apk'), bytes);
  await fs.writeFile(path.join(root, 'bundle.json'), JSON.stringify({ package: PKG, model: MODEL, sha256: crypto.createHash('sha256').update(bytes).digest('hex') }));
  const state = { model: MODEL, backlight: '100', home: 'com.gathin.tvhome/.MainActivity', byedpi: 'package:' + BYEDPI, installed: false, original: null, stopFails: false, calls: [], ...overrides };
  const adb = async args => {
    state.calls.push(args);
    if (args.join(' ') === 'shell getprop ro.product.model') return state.model;
    if (args.join(' ') === 'shell getprop ro.build.version.release') return '9';
    if (args.join(' ') === 'shell settings get global ' + PANEL_KEY) return state.backlight;
    if (args.join(' ').startsWith('shell settings put global ' + PANEL_KEY)) {state.backlight = args.at(-1);return '';}
    if (args.includes('resolve-activity')) return state.home;
    if (args.includes(BYEDPI)) return state.byedpi;
    if (args.join(' ').startsWith('shell pm list packages --user 0 ' + PKG)) return state.installed ? 'package:' + PKG : '';
    if (args[0] === 'install') {state.installed = true;return 'Success';}
    if (args.includes(PKG + '.STOP')) {if(state.stopFails)throw new Error('receiver unavailable');state.backlight=String(state.original??100);return 'Broadcast completed: result=0, data="{\"enabled\":false}"';}
    if (args.includes(PKG + '.STATUS')) return 'Broadcast completed: result=0, data="'+JSON.stringify({enabled:state.original!==null,permission:true,original:state.original})+'"';
    if (args.includes('grant') || args.includes('start') || args.includes('force-stop')) return '';
    throw new Error('Unexpected ADB request: ' + args.join(' '));
  };
  return { root, state, adb };
}
test('installation grants only the brightness app and preserves panel/Home/ByeByeDPI', async t => {
  const f = await fixture(t);const out = await installWith(f.adb, f.root, () => {});
  assert.equal(out.before.backlight, 100);assert.equal(out.after.backlight, 100);
  assert.equal(out.after.home, out.before.home);assert.equal(out.after.byedpi, out.before.byedpi);
  assert.deepEqual(f.state.calls.filter(c => c.includes('grant')), [['shell', 'pm', 'grant', PKG, 'android.permission.WRITE_SECURE_SETTINGS']]);
  assert.equal(f.state.calls.some(c => /disable-user|uninstall|set-home|settings put/.test(c.join(' '))), false);
});
test('APK tampering is rejected before any TV request', async t => {
  const f = await fixture(t);await fs.appendFile(path.join(f.root, 'SunsetTV_1_1.apk'), 'tampered');
  await assert.rejects(installWith(f.adb, f.root, () => {}));assert.equal(f.state.calls.length, 0);
});
test('another TV model is rejected before install or grants', async t => {
  const f = await fixture(t, { model: 'Another TV' });
  await assert.rejects(installWith(f.adb, f.root, () => {}));assert.equal(f.state.calls.length, 1);
});
test('missing native backlight key is rejected before installation', async t => {
  const f = await fixture(t, { backlight: 'null' });
  await assert.rejects(installWith(f.adb, f.root, () => {}));assert.equal(f.state.calls.some(c => c[0] === 'install'), false);
});
test('emergency restore stops the app and restores zero without disabling other packages', async t => {
  const f = await fixture(t);await installWith(f.adb, f.root, () => {});f.state.backlight = '0';
  assert.equal(await restoreWith(f.adb, f.root, () => {}), 100);
  const stops = f.state.calls.filter(c => c.includes('force-stop'));assert.deepEqual(stops, [['shell', 'am', 'force-stop', PKG]]);
});
test('a repeated install retains the first recovery snapshot', async t => {
  const f = await fixture(t);await installWith(f.adb, f.root, () => {});f.state.backlight = '55';
  await installWith(f.adb, f.root, () => {});
  assert.equal(JSON.parse(await fs.readFile(path.join(f.root, 'reports/before-install.json'))).backlight, 100);
});
test('an update retains the earlier original even while the current panel is at zero', async t => {
  const f=await fixture(t,{installed:true,original:86,backlight:'0'});
  await installWith(f.adb,f.root,()=>{});
  const saved=JSON.parse(await fs.readFile(path.join(f.root,'reports/before-install.json')));
  assert.equal(saved.backlight,0);assert.equal(saved.recoveryBacklight,86);
  f.state.stopFails=true;
  assert.equal(await restoreWith(f.adb,f.root,()=>{}),86);
});

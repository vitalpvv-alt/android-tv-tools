'use strict';
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { componentList, addComponent, removeOwnComponent, sameSet, validateTarget, quoteRemote, verifyFile } = require('./setup.cjs');
const ours = 'dev.mert.tvassistant/dev.mert.tvassistant.NavigationService';
test('wrong TV and wrong Android stop before installation', () => {
  validateTarget({model:'MiTV-MSSP1',android:'9',sdk:'28'});
  assert.throws(() => validateTarget({model:'MiTV-AFMU0',android:'14',sdk:'34'}));
  assert.throws(() => validateTarget({model:'MiTV-MSSP1',android:'8',sdk:'26'}));
});
test('navigation grant preserves all existing services and is idempotent', () => {
  const prior = 'reader.app/.Reader:buttons.app/.Keys';
  const once = addComponent(prior, ours);
  assert.deepEqual(componentList(once), ['reader.app/.Reader','buttons.app/.Keys',ours]);
  assert.equal(addComponent(once, ours), once);
});
test('rollback preserves services added by someone else after setup', () => {
  const prior = 'reader.app/.Reader';
  const current = addComponent(prior,ours)+':another.app/.Later';
  assert.equal(removeOwnComponent(current,prior,ours),'reader.app/.Reader:another.app/.Later');
  assert.equal(sameSet(removeOwnComponent(current,prior,ours),prior),false);
});
test('rollback does not revoke navigation already granted before this setup', () => {
  const prior = 'reader.app/.Reader:'+ours;
  assert.equal(removeOwnComponent(prior,prior,ours),prior);
});
test('original absent setting is deleted instead of creating a literal null component', () => {
  assert.equal(removeOwnComponent(ours,'null',ours),null);
  assert.deepEqual(componentList('null'),[]);
});
test('remote shell quoting keeps component dollar signs and apostrophes literal', () => {
  assert.equal(quoteRemote('com.example/.Outer$Inner'),"'com.example/.Outer$Inner'");
  assert.equal(quoteRemote("a'b"),"'a'\\''b'");
});
test('modified APK is rejected by the pinned digest check', () => {
  const folder=fs.mkdtempSync(path.join(os.tmpdir(),'tv-assistant-digest-'));
  const file=path.join(folder,'tv-assistant.apk');
  try {fs.writeFileSync(file,'tampered');assert.throws(()=>verifyFile(file,'0'.repeat(64)));}
  finally {fs.rmSync(folder,{recursive:true,force:true});}
});

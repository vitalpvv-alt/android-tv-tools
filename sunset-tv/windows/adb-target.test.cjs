'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {validateTarget,isNetworkTarget,chooseTarget}=require('./adb-target.cjs');
test('public installers accept a user-selected network TV or USB serial',()=>{
 for(const value of ['tv.example:5555','android-tv.local:37111','USB-TV-0001'])assert.equal(validateTarget(value),value);
 assert.equal(isNetworkTarget('tv.example:5555'),true);assert.equal(isNetworkTarget('USB-TV-0001'),false);
});
test('invalid ports, addresses and command text are rejected',()=>{
 for(const value of ['', 'tv.example:0','tv.example:65536','256.1.1.1:5555','tv.example; echo bad','tv.example\n:5555','--all'])assert.throws(()=>validateTarget(value));
});
test('an explicit TV_SERIAL is used without prompting or connecting to a default TV',async()=>{
 assert.equal(await chooseTarget({env:{TV_SERIAL:'tv.example:5555'},ask:()=>{throw Error('unexpected prompt');}}),'tv.example:5555');
});
test('missing configuration prompts once and never chooses the authors TV',async()=>{
 let n=0;assert.equal(await chooseTarget({env:{},ask:async()=>{n++;return 'USB-TV-0001';}}),'USB-TV-0001');assert.equal(n,1);
 await assert.rejects(chooseTarget({env:{},ask:async()=>''}));
});

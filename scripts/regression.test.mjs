import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { plan, catalog, assess, inside, junitSummary, fingerprint } from './regression.mjs';

const selected = { checks: [{ id: 'unit' }], cases: [{ id: 'LAN-01' }] };
function record() { return { sourceFingerprint: 'current', checks: [{ id: 'unit', status: 'passed', evidence: [{}] }],
  cases: [{ id: 'LAN-01', status: 'passed', operator: 'tester', environment: 'API37', artifact: 'sha256:' + 'a'.repeat(64),
    completedAt: '2026-09-14T00:00:00Z', evidence: [{}] }] }; }
test('catalog IDs and profile references validate', () => {
  assert.equal(catalog().cases.length, 19);
  assert.ok(plan('login').cases.some(c => c.id === 'LAN-01'));
});
test('unknown profiles fail closed', () => assert.throws(() => plan('typo')));
test('release includes original sample and signed upgrade acceptance', () => {
  const ids = plan('release').cases.map(c => c.id);
  assert.ok(ids.includes('RC-01') && ids.includes('PANEL-02'));
});
test('plan never contains automatic simulator reset or remote writes', () => {
  assert.doesNotMatch(JSON.stringify(catalog().checks), /pm clear|git push|uninstall|emu kill/);
});
test('complete current evidence passes', () => assert.equal(assess(record(), selected, 'current', () => true).accepted, true));
test('stale source invalidates previous green record', () => assert.equal(assess(record(), selected, 'changed', () => true).accepted, false));
test('a command pass cannot substitute for runtime acceptance', () => {
  const r = record(); r.cases[0].status = 'pending';
  assert.equal(assess(r, selected, 'current', () => true).accepted, false);
});
test('blocked and skipped cases cannot pass', () => {
  for (const status of ['blocked', 'skipped', 'failed']) {
    const r = record(); r.cases[0].status = status;
    assert.equal(assess(r, selected, 'current', () => true).accepted, false);
  }
});
test('JUnit skips keep acceptance incomplete', () => {
  const r = record(); r.checks[0].junit = { skipped: 1 };
  assert.equal(assess(r, selected, 'current', () => true).accepted, false);
});
test('missing, duplicate and forged evidence fail', () => {
  assert.equal(assess(record(), selected, 'current', () => false).accepted, false);
  const r = record(); r.checks.push(r.checks[0]);
  assert.equal(assess(r, selected, 'current', () => true).accepted, false);
  r.checks = []; assert.equal(assess(r, selected, 'current', () => true).accepted, false);
});
test('manual acceptance requires environment and operator', () => {
  const r = record(); r.cases[0].operator = '';
  assert.equal(assess(r, selected, 'current', () => true).accepted, false);
});
test('evidence rejects traversal, directories and external symlinks', t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'komelia-evidence-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  const inner = path.join(dir, 'run'); fs.mkdirSync(inner);
  fs.writeFileSync(path.join(dir, 'outside'), 'private');
  fs.symlinkSync(path.join(dir, 'outside'), path.join(inner, 'link'));
  assert.throws(() => inside(inner, '../outside'));
  assert.throws(() => inside(inner, 'link'));
  assert.throws(() => inside(inner, '.'));
});
test('JUnit counts failures and skips rather than file presence', t => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'komelia-junit-'));
  t.after(() => fs.rmSync(dir, { recursive: true, force: true }));
  fs.writeFileSync(path.join(dir, 'TEST.xml'), '<testsuite tests="3" failures="1" errors="0" skipped="1"/>');
  assert.deepEqual(junitSummary([dir]), { files: 1, tests: 3, failures: 1, errors: 0, skipped: 1 });
  fs.writeFileSync(path.join(dir, 'TEST.xml'), 'broken');
  assert.throws(() => junitSummary([dir]));
});
test('fingerprint is stable on an unchanged tree', () => assert.equal(fingerprint(), fingerprint()));
test('native runner rejects physical-device and missing arguments before touching adb', () => {
  for (const args of [[], ['physical-device', 'app.apk', 'test.apk']]) {
    const result = spawnSync(process.execPath, ['scripts/android-regression.mjs', ...args], { encoding: 'utf8' });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /Usage:/);
  }
});

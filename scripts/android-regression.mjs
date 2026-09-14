import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { root, fingerprint } from './regression.mjs';

// Explicit installation workflow. Never start, clear, reset, or stop an emulator.
const [serial, app, testApk] = process.argv.slice(2);
const command = (program, args) => {
  const r = spawnSync(program, args, { encoding: 'utf8', timeout: 180000, maxBuffer: 16 * 1024 * 1024 });
  if (r.error || r.status !== 0) throw Error(r.error?.message ?? r.stderr);
  return r.stdout;
};
try {
  if (process.argv.length !== 5 || !/^emulator-\d+$/.test(serial ?? '')) throw Error('Usage: node scripts/android-regression.mjs emulator-N APP.apk TEST.apk');
  const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT;
  if (!sdk) throw Error('Set ANDROID_HOME');
  const adb = path.join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
  const shell = (...args) => command(adb, ['-s', serial, ...args]);
  const avd = shell('emu', 'avd', 'name').split(/\r?\n/)[0].trim();
  if (!avd.startsWith('Komelia_Regression_')) throw Error('Use a dedicated AVD named Komelia_Regression_*');
  const tools = fs.readdirSync(path.join(sdk, 'build-tools')).sort((a,b) => b.localeCompare(a, undefined, { numeric: true }));
  const aapt = path.join(sdk, 'build-tools', tools[0], process.platform === 'win32' ? 'aapt2.exe' : 'aapt2');
  for (const [file, expected] of [[app, 'io.github.zhengningning.komelia.debug'], [testApk, 'io.github.zhengningning.komelia.debug.test']]) {
    const metadata = command(aapt, ['dump', 'badging', file]);
    if (metadata.match(/package: name='([^']+)'/)?.[1] !== expected) throw Error('Only the isolated debug package may be installed');
  }
  const sha = file => createHash('sha256').update(fs.readFileSync(file)).digest('hex');
  const parent = path.join(root, 'build/regression'); fs.mkdirSync(parent, { recursive: true });
  const out = fs.mkdtempSync(path.join(parent, 'android-'));
  const result = { sourceFingerprint: fingerprint(), serial, avd, api: shell('shell','getprop','ro.build.version.sdk').trim(),
    apkSha256: sha(app), testApkSha256: sha(testApk), status: 'pending', startedAt: new Date().toISOString() };
  const save = () => fs.writeFileSync(path.join(out, 'native.json'), JSON.stringify(result, null, 2), { mode: 0o600 });
  save();
  try {
    shell('install','-r',app); shell('install','-r',testApk);
    const log = shell('shell','am','instrument','-w','io.github.zhengningning.komelia.debug.test/androidx.test.runner.AndroidJUnitRunner');
    fs.writeFileSync(path.join(out, 'instrumentation.log'), log, { mode: 0o600 });
    const match = log.match(/OK \((\d+) tests?\)/);
    result.tests = Number(match?.[1] ?? 0);
    result.status = result.tests > 0 && !/FAILURES|INSTRUMENTATION_FAILED|Process crashed/.test(log) ? 'passed' : 'failed';
  } catch (error) { result.status = 'blocked'; result.reason = error.message; }
  result.finishedAt = new Date().toISOString(); save();
  console.log(path.relative(root, out));
  process.exitCode = result.status === 'passed' ? 0 : 1;
} catch (error) { console.error(error.message); process.exitCode = 1; }

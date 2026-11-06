import assert from 'node:assert/strict';
import {execFileSync, spawnSync} from 'node:child_process';
import {mkdtempSync, mkdirSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {dirname, join, resolve} from 'node:path';
import {test} from 'node:test';
import {fileURLToPath} from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const javaBin = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin') : '';
const java = javaBin ? join(javaBin, process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
const jar = javaBin ? join(javaBin, process.platform === 'win32' ? 'jar.exe' : 'jar') : 'jar';
const architectures = {
  'armeabi-v7a': [1, 40], 'arm64-v8a': [2, 183], x86: [1, 3], x86_64: [2, 62],
};
const nativeLibraries = ['libkomelia_vips.so', 'libkomelia_android_bitmap.so',
  'libkomelia_onnxruntime.so', 'libonnxruntime.so'];
const sqliteLibrary = 'libsqlitejdbc.so';

function elfHeader(abi) {
  const header = Buffer.alloc(20);
  header.set([0x7f, 0x45, 0x4c, 0x46, architectures[abi][0], 1]);
  header.writeUInt16LE(3, 16);
  header.writeUInt16LE(architectures[abi][1], 18);
  return header;
}

function fixture(t, {missing = '', wrongArchitecture = '', bundle = false} = {}) {
  const directory = mkdtempSync(join(tmpdir(), 'komelia-android-abi-'));
  t.after(() => rmSync(directory, {recursive: true, force: true}));
  const nativeDir = join(directory, 'native');
  const sqliteDir = join(directory, 'sqlite');
  const contents = join(directory, 'contents');
  mkdirSync(nativeDir);
  mkdirSync(sqliteDir);
  for (const name of nativeLibraries) writeFileSync(join(nativeDir, name), elfHeader('arm64-v8a'));
  writeFileSync(join(sqliteDir, sqliteLibrary), elfHeader('arm64-v8a'));
  for (const abi of Object.keys(architectures)) {
    const output = join(contents, ...(bundle ? ['base'] : []), 'lib', abi);
    mkdirSync(output, {recursive: true});
    for (const name of [...nativeLibraries, sqliteLibrary]) {
      if (`${abi}/${name}` === missing) continue;
      writeFileSync(join(output, name), elfHeader(`${abi}/${name}` === wrongArchitecture ? 'arm64-v8a' : abi));
    }
  }
  const packagePath = join(directory, bundle ? 'fixture.aab' : 'fixture.apk');
  execFileSync(jar, ['--create', '--file', packagePath, '-M', '-C', contents, '.']);
  const result = spawnSync(java, [join(root, 'scripts/CheckAndroidNativeAbis.java'), packagePath, nativeDir, sqliteDir],
    {encoding: 'utf8'});
  if (result.error) throw result.error;
  return result;
}

test('rejects a missing library on an advertised architecture', t => {
  const result = fixture(t, {missing: 'x86_64/libkomelia_vips.so'});
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /x86_64 missing.*libkomelia_vips\.so/);
});

test('rejects a library built for a different architecture', t => {
  const result = fixture(t, {wrongArchitecture: 'x86_64/libkomelia_vips.so'});
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /x86_64 has an invalid ELF architecture: libkomelia_vips\.so/);
});

test('accepts complete APK and app bundle native libraries', t => {
  for (const bundle of [false, true]) {
    const result = fixture(t, {bundle});
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /ANDROID_NATIVE_ABIS_PASSED/);
  }
});

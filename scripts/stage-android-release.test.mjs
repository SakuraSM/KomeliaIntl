import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {chmodSync, mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';
import {spawnSync} from 'node:child_process';
import {test} from 'node:test';
import {fileURLToPath} from 'node:url';

const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const script = join(root, 'scripts/stage-android-release.mjs');
const abis = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'];

function fixture(t) {
  const directory = mkdtempSync(join(tmpdir(), 'komelia-android-release-'));
  t.after(() => rmSync(directory, {recursive: true, force: true}));
  const build = join(directory, 'build');
  mkdirSync(build);
  const elements = [null, ...abis].map(abi => {
    const outputFile = `app-${abi ?? 'universal'}.apk`;
    writeFileSync(join(build, outputFile), `signed fixture ${abi ?? 'universal'}`);
    return {versionCode: 62, versionName: '0.33.0-beta.1', outputFile,
      filters: abi ? [{filterType: 'ABI', value: abi}] : []};
  });
  const metadata = {variantName: 'release', applicationId: 'io.github.zhengningning.komelia', elements};
  const metadataFile = join(build, 'output-metadata.json');
  const out = join(directory, 'staged');
  const signer = join(directory, 'apksigner');
  writeFileSync(signer, `#!/bin/sh\nprintf 'Signer #1 certificate SHA-256 digest: ${'a'.repeat(64)}\\n'\n`);
  chmodSync(signer, 0o755);
  const aapt = join(directory, 'aapt');
  writeFileSync(aapt, `#!/bin/sh\nprintf "package: name='io.github.zhengningning.komelia' versionCode='62' versionName='0.33.0-beta.1'\\n"\ncase "$*" in\n *arm64-v8a*) echo "native-code: 'arm64-v8a'";;\n *armeabi-v7a*) echo "native-code: 'armeabi-v7a'";;\n *x86_64*) echo "native-code: 'x86_64'";;\n *x86*) echo "native-code: 'x86'";;\n *) echo "native-code: 'arm64-v8a' 'armeabi-v7a' 'x86' 'x86_64'";;\nesac\n`);
  chmodSync(aapt, 0o755);
  const run = ({base = 'https://downloads.example.test/releases/0.33.0-beta.1/', data = metadata} = {}) => {
    writeFileSync(metadataFile, JSON.stringify(data));
    const args = [script, '--metadata', metadataFile, '--out', out, '--apksigner', signer, '--aapt', aapt];
    if (base !== null) args.push('--abi-base-url', base);
    return spawnSync(process.execPath, args, {encoding: 'utf8'});
  };
  return {run, out, metadata, signer, aapt};
}

test('stages one legacy APK separately from four ABI APKs and matching checksums', t => {
  const {run, out} = fixture(t);
  const result = run();
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(readdirSync(join(out, 'release')).sort(),
    ['Komelia-0.33.0-beta.1.apk', 'android-abi-apks.json']);
  const manifest = JSON.parse(readFileSync(join(out, 'release/android-abi-apks.json'), 'utf8'));
  assert.equal(manifest.version, '0.33.0-beta.1');
  assert.equal(manifest.schemaVersion, 1);
  assert.deepEqual(Object.keys(manifest.packages).sort(), [...abis].sort());
  for (const abi of abis) {
    const apk = manifest.packages[abi];
    assert.equal(apk.name, `Komelia-0.33.0-beta.1-${abi}.apk`);
    assert.equal(apk.url, `https://downloads.example.test/releases/0.33.0-beta.1/${apk.name}`);
    assert.equal(apk.sha256, createHash('sha256').update(readFileSync(join(out, 'abi', apk.name))).digest('hex'));
  }
});

test('stages a safe legacy release without a manifest when no ABI host is configured', t => {
  const {run, out} = fixture(t);
  const result = run({base: null});
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(readdirSync(join(out, 'release')), ['Komelia-0.33.0-beta.1.apk']);
  assert.equal(readdirSync(join(out, 'abi')).length, 4);
  assert.notEqual(run({base: null}).status, 0);
  assert.deepEqual(readdirSync(join(out, 'release')), ['Komelia-0.33.0-beta.1.apk']);
});

test('rejects incomplete or ambiguous ABI outputs', t => {
  const {run, metadata} = fixture(t);
  const incomplete = {...metadata, elements: metadata.elements.slice(0, -1)};
  assert.notEqual(run({data: incomplete}).status, 0);
  const duplicate = {...metadata, elements: [...metadata.elements, metadata.elements[1]]};
  assert.notEqual(run({data: duplicate}).status, 0);
});

test('rejects unsafe URLs, incompatible metadata, and output path traversal', t => {
  const {run, metadata} = fixture(t);
  assert.notEqual(run({base: 'http://downloads.example.test/'}).status, 0);
  assert.notEqual(run({base: 'https://user:pass@downloads.example.test/'}).status, 0);
  assert.notEqual(run({data: {...metadata, variantName: 'debug'}}).status, 0);
  const elements = structuredClone(metadata.elements);
  elements[1].outputFile = '../outside.apk';
  assert.notEqual(run({data: {...metadata, elements}}).status, 0);
});

test('rejects unsigned APKs and mismatched signing certificates', t => {
  const {run, signer} = fixture(t);
  writeFileSync(signer, '#!/bin/sh\nexit 1\n');
  assert.notEqual(run().status, 0);
  writeFileSync(signer, `#!/bin/sh\ncase "$*" in *x86_64*) cert=${'b'.repeat(64)};; *) cert=${'a'.repeat(64)};; esac\nprintf 'Signer #1 certificate SHA-256 digest: %s\\n' "$cert"\n`);
  assert.notEqual(run().status, 0);
});

test('accepts the Android 37 apksigner certificate output format', t => {
  const {run, signer} = fixture(t);
  writeFileSync(signer, `#!/bin/sh\nprintf 'V3.0 Signer: certificate SHA-256 digest: ${'a'.repeat(64)}\\n'\n`);
  const result = run();
  assert.equal(result.status, 0, result.stderr);
});

test('rejects an APK whose actual package identity or ABI does not match metadata', t => {
  const {run, aapt} = fixture(t);
  writeFileSync(aapt, "#!/bin/sh\necho \"package: name='wrong.package' versionCode='62' versionName='0.33.0-beta.1'\"\n");
  assert.notEqual(run().status, 0);
  writeFileSync(aapt, "#!/bin/sh\necho \"package: name='io.github.zhengningning.komelia' versionCode='62' versionName='0.33.0-beta.1'\"\necho \"native-code: 'x86'\"\n");
  assert.notEqual(run().status, 0);
});

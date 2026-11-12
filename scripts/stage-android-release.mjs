import {createHash} from 'node:crypto';
import {spawnSync} from 'node:child_process';
import {constants, copyFileSync, createReadStream, existsSync, lstatSync, mkdirSync, readFileSync, readdirSync, writeFileSync} from 'node:fs';
import {basename, dirname, join, resolve} from 'node:path';

const abis = ['arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'];
const options = {};
for (let i = 2; i < process.argv.length; i += 2) {
  const key = process.argv[i];
  if (!['--metadata', '--out', '--abi-base-url', '--apksigner', '--aapt'].includes(key) || !process.argv[i + 1] || options[key]) {
    throw new Error('Usage: node scripts/stage-android-release.mjs --metadata <output-metadata.json> --out <new-directory> [--abi-base-url <https-directory-url>] [--apksigner <path>] [--aapt <path>]');
  }
  options[key] = process.argv[i + 1];
}
if (!options['--metadata'] || !options['--out']) throw new Error('--metadata and --out are required');

const base = options['--abi-base-url'] ? new URL(options['--abi-base-url']) : null;
if (base && (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash)) {
  throw new Error('ABI base URL must be an HTTPS directory without credentials, query, or fragment');
}
if (base && !base.pathname.endsWith('/')) base.pathname += '/';

const metadataPath = resolve(options['--metadata']);
const metadata = JSON.parse(readFileSync(metadataPath, 'utf8'));
if (metadata.variantName !== 'release' || metadata.applicationId !== 'io.github.zhengningning.komelia' ||
    !Array.isArray(metadata.elements) || metadata.elements.length !== abis.length + 1) {
  throw new Error('Expected one universal and four ABI outputs from the standalone release variant');
}
const entries = new Map();
let version;
let versionCode;
for (const element of metadata.elements) {
  if (!Array.isArray(element.filters) || element.filters.length > 1 ||
      (element.filters.length && element.filters[0].filterType !== 'ABI')) {
    throw new Error('Unexpected APK output filters');
  }
  const abi = element.filters.length ? element.filters[0].value : 'universal';
  if ((abi !== 'universal' && !abis.includes(abi)) || entries.has(abi)) {
    throw new Error(`Unexpected or duplicate APK ABI: ${abi}`);
  }
  if (!/^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-beta\.[1-9]\d*)?$/.test(element.versionName) ||
      (element.versionName.includes('-beta.') && Number(element.versionName.split('.').at(-1)) > 998) ||
      !Number.isInteger(element.versionCode) || element.versionCode < 1 ||
      (version !== undefined && version !== element.versionName) ||
      (versionCode !== undefined && versionCode !== element.versionCode)) {
    throw new Error('APK output versions do not match');
  }
  version = element.versionName;
  versionCode = element.versionCode;
  const file = element.outputFile;
  if (typeof file !== 'string' || basename(file) !== file || !file.endsWith('.apk')) {
    throw new Error('Unsafe APK output filename');
  }
  const source = join(dirname(metadataPath), file);
  if (!lstatSync(source).isFile()) throw new Error(`APK output is not a regular file: ${file}`);
  entries.set(abi, source);
}
for (const abi of ['universal', ...abis]) if (!entries.has(abi)) throw new Error(`Missing APK output: ${abi}`);

function sdkBuildTools() {
  for (const sdk of [process.env.ANDROID_HOME, process.env.ANDROID_SDK_ROOT]) {
    if (!sdk) continue;
    const tools = join(sdk, 'build-tools');
    if (!existsSync(tools)) continue;
    const versions = readdirSync(tools).sort((a, b) => b.localeCompare(a, 'en', {numeric: true}));
    for (const version of versions) {
      const directory = join(tools, version);
      if (existsSync(join(directory, 'lib', 'apksigner.jar')) &&
          existsSync(join(directory, process.platform === 'win32' ? 'aapt.exe' : 'aapt'))) return directory;
    }
  }
  return null;
}
const buildTools = sdkBuildTools();
function apksigner() {
  if (options['--apksigner']) return {command: options['--apksigner'], args: []};
  if (buildTools) {
    const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
    return {command: java, args: ['-jar', join(buildTools, 'lib', 'apksigner.jar')]};
  }
  return {command: 'apksigner', args: []};
}
const signer = apksigner();
const aapt = options['--aapt'] ?? (buildTools ? join(buildTools, process.platform === 'win32' ? 'aapt.exe' : 'aapt') : 'aapt');
let signingCertificates;
for (const [abi, source] of entries) {
  const badging = spawnSync(aapt, ['dump', 'badging', source], {encoding: 'utf8'});
  const packageInfo = (badging.stdout ?? '').match(/^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'/m);
  const nativeLine = (badging.stdout ?? '').match(/^native-code:\s*(.*)$/m);
  const actualAbis = nativeLine ? [...nativeLine[1].matchAll(/'([^']+)'/g)].map(match => match[1]).sort() : [];
  const expectedAbis = (abi === 'universal' ? [...abis] : [abi]).sort();
  if (badging.status !== 0 || !packageInfo || packageInfo[1] !== metadata.applicationId ||
      Number(packageInfo[2]) !== versionCode || packageInfo[3] !== version ||
      JSON.stringify(actualAbis) !== JSON.stringify(expectedAbis)) {
    throw new Error(`APK package identity, version, or ABI does not match metadata: ${basename(source)}`);
  }
  const result = spawnSync(signer.command, [...signer.args, 'verify', '--print-certs', source], {encoding: 'utf8'});
  const certificates = [...(result.stdout ?? '').matchAll(/^(?:Signer #\d+ |V\d+(?:\.\d+)? Signer(?: #\d+)?: )certificate SHA-256 digest:\s*([a-f0-9]{64})/gim)]
    .map(match => match[1].toLowerCase()).sort();
  if (result.status !== 0 || certificates.length === 0) {
    throw new Error(`APK signature verification failed: ${basename(source)}; set ANDROID_HOME or --apksigner if needed`);
  }
  if (signingCertificates && JSON.stringify(certificates) !== JSON.stringify(signingCertificates)) {
    throw new Error(`APK signing certificates do not match: ${basename(source)}`);
  }
  signingCertificates = certificates;
}

const out = resolve(options['--out']);
mkdirSync(out);
const releaseDir = join(out, 'release');
const abiDir = join(out, 'abi');
mkdirSync(releaseDir);
mkdirSync(abiDir);
const sums = [];
async function copyAndHash(source, relative) {
  const target = join(out, relative);
  copyFileSync(source, target, constants.COPYFILE_EXCL);
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(target)) hash.update(chunk);
  const digest = hash.digest('hex');
  sums.push(`${digest}  ${relative}`);
  return digest;
}
await copyAndHash(entries.get('universal'), `release/Komelia-${version}.apk`);
const manifest = {schemaVersion: 1, version, packages: {}};
for (const abi of abis) {
  const name = `Komelia-${version}-${abi}.apk`;
  const sha256 = await copyAndHash(entries.get(abi), `abi/${name}`);
  if (base) manifest.packages[abi] = {name, url: new URL(name, base).href, sha256};
}
if (base) {
  const manifestText = JSON.stringify(manifest, null, 2) + '\n';
  writeFileSync(join(releaseDir, 'android-abi-apks.json'), manifestText, {flag: 'wx'});
  sums.push(`${createHash('sha256').update(manifestText).digest('hex')}  release/android-abi-apks.json`);
}
writeFileSync(join(out, 'SHA256SUMS'), sums.join('\n') + '\n', {flag: 'wx'});
console.log(`Staged Android ${version} (${versionCode}) in ${out}`);
console.log(base
  ? 'Upload only release/ to the existing Release; host abi/ at the specified HTTPS base URL.'
  : 'No ABI host configured: release/ contains only the universal APK; clients will use it.');

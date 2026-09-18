import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

export const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const digest = value => createHash('sha256').update(value).digest('hex');
const git = (...args) => {
  const result = spawnSync('git', args, { cwd: root, encoding: 'utf8' });
  if (result.status !== 0) throw Error('Cannot inspect Git state');
  return result.stdout;
};
export function catalog() {
  const data = JSON.parse(fs.readFileSync(path.join(root, 'tests/regression/catalog.json')));
  const ids = new Set();
  for (const item of data.cases) {
    if (ids.has(item.id) || !item.steps?.length || !item.expected?.length) throw Error('Invalid case: ' + item.id);
    ids.add(item.id);
    for (const p of item.profiles) if (!data.profiles[p]) throw Error('Unknown case profile: ' + p);
  }
  for (const checks of Object.values(data.profiles)) {
    if (!checks.length) throw Error('Empty profile');
    for (const id of checks) if (!data.checks[id]) throw Error('Unknown check: ' + id);
  }
  return data;
}
export function plan(profile, data = catalog()) {
  if (!data.profiles[profile]) throw Error('Unknown profile: ' + profile);
  return { profile, checks: data.profiles[profile].map(id => ({ id, ...data.checks[id] })),
    cases: data.cases.filter(c => c.profiles.includes(profile)) };
}
export function fingerprint() {
  const hash = createHash('sha256');
  hash.update(git('rev-parse', 'HEAD'));
  hash.update(git('diff', 'HEAD', '--binary', '--ignore-submodules=none'));
  for (const name of git('ls-files', '--others', '--exclude-standard', '-z').split('\0').filter(Boolean).sort()) {
    const file = path.join(root, name);
    const stat = fs.lstatSync(file);
    hash.update(name + '\0');
    if (stat.isSymbolicLink()) hash.update(fs.readlinkSync(file));
    else if (stat.isFile()) hash.update(fs.readFileSync(file));
  }
  return hash.digest('hex');
}
export function inside(base, relative) {
  if (typeof relative !== 'string' || !relative || path.isAbsolute(relative)) throw Error('Expected a relative evidence path');
  const resolved = fs.realpathSync(path.resolve(base, relative));
  if (!resolved.startsWith(fs.realpathSync(base) + path.sep) || !fs.statSync(resolved).isFile()) throw Error('Evidence must be a file inside the run directory');
  return resolved;
}
export function assess(record, selected, currentFingerprint, verifyEvidence = () => false) {
  const errors = [];
  if (record.sourceFingerprint !== currentFingerprint) errors.push('Source changed: rerun against the final tree');
  for (const check of selected.checks) {
    const matches = record.checks?.filter(r => r.id === check.id) ?? [];
    if (matches.length !== 1 || matches[0].status !== 'passed' || !matches[0].evidence?.length ||
        !matches[0].evidence.every(verifyEvidence)) errors.push('Missing or unsuccessful command evidence: ' + check.id);
    if (matches[0]?.junit?.skipped > 0) errors.push('Skipped tests need separate acceptance: ' + check.id);
  }
  for (const item of selected.cases) {
    const matches = record.cases?.filter(r => r.id === item.id) ?? [];
    const result = matches[0];
    if (matches.length !== 1 || result.status !== 'passed' || !result.operator || !result.environment ||
        !/^sha256:[a-f0-9]{64}$/.test(result.artifact ?? '') || !Number.isFinite(Date.parse(result.completedAt)) ||
        !result.evidence?.length || !result.evidence.every(verifyEvidence)) {
      errors.push('Pending or incomplete runtime acceptance: ' + item.id);
    }
  }
  return { accepted: errors.length === 0, errors };
}
export function junitSummary(directories) {
  const total = { files: 0, tests: 0, failures: 0, errors: 0, skipped: 0 };
  function visit(directory) {
    if (!fs.existsSync(directory)) return;
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      const file = path.join(directory, entry.name);
      if (entry.isDirectory()) visit(file);
      else if (entry.isFile() && entry.name.endsWith('.xml')) {
        const xml = fs.readFileSync(file, 'utf8');
        const headers = [...xml.matchAll(/<testsuite\b[^>]*>/g)];
        if (!headers.length) throw Error('Invalid JUnit report');
        total.files++;
        for (const [header] of headers) for (const key of ['tests', 'failures', 'errors', 'skipped']) {
          const value = header.match(new RegExp(`\\b${key}="(\\d+)"`));
          if (!value) throw Error('Missing JUnit attribute: ' + key);
          total[key] += Number(value[1]);
        }
      }
    }
  }
  directories.forEach(visit);
  return total;
}
export function run(profile) {
  const selected = plan(profile);
  const directory = path.join(root, 'build/regression');
  fs.mkdirSync(directory, { recursive: true, mode: 0o700 });
  const output = fs.mkdtempSync(path.join(directory, profile + '-'));
  const record = { schemaVersion: 1, profile, commit: git('rev-parse', 'HEAD').trim(),
    sourceFingerprint: fingerprint(), startedAt: new Date().toISOString(), node: process.version,
    checks: [], cases: selected.cases.map(c => ({ id: c.id, status: 'pending', reason: '',
      operator: '', environment: '', artifact: '', completedAt: '', evidence: [] })) };
  const save = () => fs.writeFileSync(path.join(output, 'record.json'), JSON.stringify(record, null, 2) + '\n', { mode: 0o600 });
  save();
  let stopped = false;
  for (const check of selected.checks) {
    const item = { id: check.id, status: 'blocked', reason: '', evidence: [] };
    record.checks.push(item);
    if (stopped) { item.reason = 'Earlier command failed'; save(); continue; }
    if (check.requires?.some(file => !fs.existsSync(path.join(root, file)))) {
      item.reason = 'Dependencies missing; bootstrap explicitly before retrying'; stopped = true; save(); continue;
    }
    const log = path.join(output, check.id + '.log');
    const fd = fs.openSync(log, 'wx', 0o600);
    const argv = [...check.argv];
    // Never report historical test XML as a fresh test run.
    if (check.reports) argv.push('--rerun-tasks');
    console.log('Running ' + check.id);
    let result;
    try { result = spawnSync(argv[0], argv.slice(1), { cwd: path.join(root, check.cwd),
      stdio: ['ignore', fd, fd], timeout: 60 * 60 * 1000, env: process.env }); }
    finally { fs.closeSync(fd); }
    item.exitCode = result.status;
    item.status = result.error ? 'blocked' : result.status === 0 ? 'passed' : 'failed';
    item.reason = result.error?.code ?? (result.signal ?? '');
    item.evidence = [{ path: check.id + '.log', sha256: digest(fs.readFileSync(log)) }];
    if (check.reports && item.status === 'passed') {
      try {
        item.junit = junitSummary(check.reports.map(p => path.join(root, p)));
        if (!item.junit.tests || item.junit.failures || item.junit.errors) item.status = 'failed';
      } catch { item.status = 'failed'; item.reason = 'Missing or invalid JUnit reports'; }
    }
    stopped = item.status !== 'passed';
    save();
  }
  record.finishedAt = new Date().toISOString();
  record.acceptance = assess(record, selected, fingerprint(), evidence => verify(output, evidence));
  save();
  console.log(path.relative(root, path.join(output, 'record.json')));
  return record.acceptance.accepted ? 0 : stopped ? 1 : 2;
}
function verify(directory, evidence) {
  try { return digest(fs.readFileSync(inside(directory, evidence.path))) === evidence.sha256; }
  catch { return false; }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    const [mode = 'plan', value = 'core', ...extra] = process.argv.slice(2);
    if (extra.length) throw Error('Unexpected arguments');
    if (mode === 'plan') console.log(JSON.stringify(plan(value), null, 2));
    else if (mode === 'run') process.exitCode = run(value);
    else if (mode === 'check') {
      const file = path.resolve(value);
      const record = JSON.parse(fs.readFileSync(file));
      const result = assess(record, plan(record.profile), fingerprint(), e => verify(path.dirname(file), e));
      console.log(JSON.stringify(result, null, 2));
      process.exitCode = result.accepted ? 0 : 2;
    } else throw Error('Usage: node scripts/regression.mjs plan|run PROFILE, or check RECORD');
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}

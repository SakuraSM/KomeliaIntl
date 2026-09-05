import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';

const source = path => readFileSync(new URL(`../entry/src/main/ets/${path}`, import.meta.url), 'utf8');
function array(sourceText, name) {
  const expression = sourceText.match(new RegExp(`${name}: string\\[\\] = (\\[[\\s\\S]*?\\]);`))?.[1];
  assert.ok(expression, `${name} was not found`);
  return Array.from(runInNewContext(expression));
}
const legacy = array(source('data/local/HarmonyDatabase.ets'), 'V1_SCHEMA').join(';');
const local = array(source('data/local/LocalLibrarySchema.ets'), 'LOCAL_LIBRARY_SCHEMA').join(';');
const progress = array(source('model/DownloadProgressModels.ets'), 'DOWNLOAD_PROGRESS_SCHEMA').join(';');
const seed = `
  INSERT INTO SERVER_PROFILE VALUES ('fixture', 'https://example.invalid', 'fixture', 0, '', 1);
  INSERT INTO OFFLINE_BOOK VALUES ('server:user', 'remote', 'PAGE_CACHE', 'fixture', '/fixture', 2, 2, 100, 1);
  INSERT INTO DOWNLOAD_JOB VALUES ('server:user', 'job', 'remote', 'Paused', 1, 2, 3, '', 1);
  INSERT INTO PROGRESS_OUTBOX VALUES ('server:user', 'remote', 1, 0, '2026-01-01', 0);
`;
function sqlite(script) {
  const result = spawnSync('sqlite3', ['-batch', '-bail', '-json', ':memory:'], {
    input: `PRAGMA foreign_keys=ON; ${legacy}; ${seed} ${script}`, encoding: 'utf8'
  });
  assert.equal(result.status, 0, result.stderr || result.error?.message);
  return JSON.parse(result.stdout.trim());
}

test('v8 to v10 additive migrations preserve old accounts, downloads, tasks and progress', () => {
  const rows = sqlite(`BEGIN; ${local}; ${progress}; COMMIT;
    SELECT (SELECT COUNT(*) FROM SERVER_PROFILE) AS accounts,
      (SELECT COUNT(*) FROM OFFLINE_BOOK) AS downloads,
      (SELECT COUNT(*) FROM PROGRESS_OUTBOX) AS outbox,
      PHASE, COMPLETED_PARTS, ATTEMPTS, TITLE, COMPLETED_BYTES, TOTAL_BYTES, BYTES_PER_SECOND FROM DOWNLOAD_JOB;`);
  assert.deepEqual(rows[0], { accounts: 1, downloads: 1, outbox: 1, PHASE: 'Paused', COMPLETED_PARTS: 1,
    ATTEMPTS: 3, TITLE: '', COMPLETED_BYTES: 0, TOTAL_BYTES: 0, BYTES_PER_SECOND: 0 });
});

const localSeed = `
  INSERT INTO LOCAL_LIBRARY VALUES ('library', '{}');
  INSERT INTO LOCAL_BOOK VALUES ('local-book-1', 'library', '{}');
  INSERT INTO LOCAL_BOOK VALUES ('local-book-2', 'library', '{}');
  INSERT INTO LOCAL_PROGRESS VALUES ('local-book-1', '{"page":1}');
  INSERT INTO LOCAL_PROGRESS VALUES ('local-book-2', '{"page":2}');
`;

test('local cleanup deletes progress before book and leaves other progress and server data intact', () => {
  const repository = source('data/repository/LocalLibraryRepository.ets');
  const method = repository.match(/private async deleteBookIndex\(bookId: string,[\s\S]*?\n  }/)?.[0];
  assert.ok(method);
  const statements = [...method.matchAll(/transaction.execute\('([^']+)'/g)].map(match => match[1]);
  assert.equal(statements.length, 2);
  const deletes = statements.map(sql => sql.replace('?', "'local-book-1'")).join(';');
  const rows = sqlite(`${local}; ${localSeed} BEGIN; ${deletes}; COMMIT;
    SELECT (SELECT COUNT(*) FROM LOCAL_BOOK) AS books,
      (SELECT COUNT(*) FROM LOCAL_PROGRESS) AS progress,
      (SELECT COUNT(*) FROM OFFLINE_BOOK) AS downloads,
      (SELECT PAYLOAD FROM LOCAL_PROGRESS) AS kept;`);
  assert.deepEqual(rows[0], { books: 1, progress: 1, downloads: 1, kept: '{"page":2}' });
});

test('rolling back index cleanup preserves the previous index and progress', () => {
  const rows = sqlite(`${local}; ${localSeed} BEGIN;
    DELETE FROM LOCAL_PROGRESS WHERE BOOK_ID = 'local-book-1';
    DELETE FROM LOCAL_BOOK WHERE ID = 'local-book-1'; ROLLBACK;
    SELECT (SELECT COUNT(*) FROM LOCAL_BOOK) AS books, (SELECT COUNT(*) FROM LOCAL_PROGRESS) AS progress;`);
  assert.deepEqual(rows[0], { books: 2, progress: 2 });
});

test('guarded progress writes cannot restart paused or cancelled downloads', () => {
  const repository = source('data/repository/DefaultRepositories.ets');
  const method = repository.match(/async updateBytes\([\s\S]*?\n  }/)?.[0];
  assert.ok(method?.includes('WHERE JOB_ID = ? AND PHASE = ?'));
  const rows = sqlite(`${progress};
    UPDATE DOWNLOAD_JOB SET COMPLETED_BYTES=500, BYTES_PER_SECOND=100 WHERE JOB_ID='job' AND PHASE='Running';
    SELECT PHASE, COMPLETED_BYTES, BYTES_PER_SECOND FROM DOWNLOAD_JOB;`);
  assert.deepEqual(rows[0], { PHASE: 'Paused', COMPLETED_BYTES: 0, BYTES_PER_SECOND: 0 });
});

import test from 'node:test';
import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createFixtureServer } from './permission-server.mjs';

test('permission fixture responds 401 and redacts query, credentials and body', async () => {
  const logs = [];
  const server = createFixtureServer(line => logs.push(line));
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  try {
    const response = await fetch(`http://127.0.0.1:${server.address().port}/api/v2/users/me?secret=synthetic-query`, {
      method: 'POST', headers: { Authorization: 'Bearer synthetic-header' }, body: 'synthetic-body'
    });
    assert.equal(response.status, 401);
    assert.equal((await response.json()).message, 'Synthetic permission test');
    assert.equal(JSON.parse(logs[0]).path, '/api/v2/users/me');
    assert.doesNotMatch(logs.join(''), /synthetic-query|synthetic-header|synthetic-body/);
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});

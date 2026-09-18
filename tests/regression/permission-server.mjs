import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// No credentials, cookies, request headers or bodies are logged.
const port = Number(process.env.PORT ?? 18764);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw Error('Use an unprivileged TCP port');
export const createFixtureServer = (log = console.log) => http.createServer((request, response) => {
  log(JSON.stringify({ at: new Date().toISOString(), method: request.method,
    path: new URL(request.url, 'http://fixture.invalid').pathname }));
  response.writeHead(401, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify({ message: 'Synthetic permission test' }));
});
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const server = createFixtureServer();
  server.listen(port, '127.0.0.1', () => console.log(`QA fixture listening on loopback port ${port}`));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close());
}

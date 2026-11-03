// Serve the production component with test callbacks through its own Vite compiler.
import {mkdtemp, readFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join, resolve} from 'node:path';

export async function startReaderToolbarServer(createServer, entry = 'tests/reader-toolbar.fixture.ts') {
  const server = await createServer({
    server: { host: '127.0.0.1', port: 0 },
    optimizeDeps: { entries: [entry] },
    plugins: [{
      name: 'reader-toolbar-test-page',
      configureServer(vite) {
        vite.middlewares.use((request, response, next) => {
          if (!request.url?.startsWith('/__reader-toolbar.html')) return next();
          response.setHeader('Content-Type', 'text/html');
          response.end(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Reader toolbar fixture</title></head><body style="margin:0"><div id="app"></div><output id="result"></output>
            <script type="module" src="/${entry}"></script></body></html>`);
        });
      },
    }],
  });
  await server.listen();
  return { url: `${server.resolvedUrls.local[0]}__reader-toolbar.html`, close: () => server.close() };
}

// Keep the production CSS extraction order, not Vite's development injection order.
export async function startBuiltReaderToolbarServer(build, createServer) {
  const directory = await mkdtemp(join(tmpdir(), 'komelia-built-reader-'));
  let server;
  try {
    await build({logLevel: 'error', build: {outDir: directory, emptyOutDir: true,
      rollupOptions: {input: resolve('tests/reader-toolbar.fixture.html')}}});
    const html = await readFile(join(directory, 'tests/reader-toolbar.fixture.html'), 'utf8');
    server = await createServer({configFile: false, server: {host: '127.0.0.1', port: 0}, plugins: [{
      name: 'built-reader-toolbar-page', configureServer(vite) {
        vite.middlewares.use((request, response, next) => {
          if (!request.url?.startsWith('/__reader-toolbar.html')) return next();
          response.setHeader('Content-Type', 'text/html'); response.end(html);
        });
      },
    }]});
    await server.listen();
    return {url: `${server.resolvedUrls.local[0]}__reader-toolbar.html`, close: async () => {
      await server.close(); await rm(directory, {recursive: true, force: true});
    }};
  } catch (error) {
    await server?.close(); await rm(directory, {recursive: true, force: true}); throw error;
  }
}

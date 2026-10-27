// Serve the production component with test callbacks through its own Vite compiler.
export async function startReaderToolbarServer(createServer) {
  const server = await createServer({
    server: { host: '127.0.0.1', port: 0 },
    optimizeDeps: { entries: ['tests/reader-toolbar.fixture.ts'] },
    plugins: [{
      name: 'reader-toolbar-test-page',
      configureServer(vite) {
        vite.middlewares.use((request, response, next) => {
          if (!request.url?.startsWith('/__reader-toolbar.html')) return next();
          response.setHeader('Content-Type', 'text/html');
          response.end(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Reader toolbar fixture</title></head><body style="margin:0"><div id="app"></div><output id="result"></output>
            <script type="module" src="/tests/reader-toolbar.fixture.ts"></script></body></html>`);
        });
      },
    }],
  });
  await server.listen();
  return { url: `${server.resolvedUrls.local[0]}__reader-toolbar.html`, close: () => server.close() };
}

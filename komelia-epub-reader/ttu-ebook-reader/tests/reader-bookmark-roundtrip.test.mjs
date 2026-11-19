import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {createServer} from 'vite';
import {startReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');

for (const orientation of ['horizontal', 'vertical']) test(`continuous ${orientation} bookmarks stay stable without scrolling`, async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-bookmark-roundtrip.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 411, height: 864}, deviceScaleFactor: 2.625,
      isMobile: orientation === 'horizontal'});
    await page.goto(`${server.url}${orientation === 'vertical' ? '?vertical' : ''}`);
    await page.waitForFunction(() => typeof window.bookmarkRoundTrip === 'function');
    const results = await page.evaluate(async () => {
      const values = [];
      for (const offset of [0, 117.4, 303.2]) {
        for (let index = 5; index < 50; index += 1) values.push(await window.bookmarkRoundTrip(index, offset));
      }
      return values;
    });
    for (const result of results) {
      assert.deepEqual(result.saved, [result.expected, result.expected, result.expected], JSON.stringify(result));
      assert.equal(result.beforeBoundary, result.previous, JSON.stringify(result));
    }
  } finally {
    await browser.close();
    await server.close();
  }
});

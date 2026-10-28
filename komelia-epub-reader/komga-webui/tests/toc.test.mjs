import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {test} from 'node:test';
import {createServer} from 'vite';

const require = createRequire(import.meta.url);
const {chromium} = require('playwright');

test('EPUB contents keeps a compact transparent expand control and separate chapter navigation', async () => {
  const cacheDir = await mkdtemp(join(tmpdir(), 'komelia-toc-test-'));
  const server = await createServer({
    cacheDir,
    server: {host: '127.0.0.1', port: 0},
    optimizeDeps: {entries: ['tests/toc.fixture.ts']},
  });
  await server.listen();
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 360, height: 800}, hasTouch: true, isMobile: true});
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(`${server.resolvedUrls.local[0]}tests/toc.fixture.html`);
    const expand = page.locator('.v-list-item__append button');
    await expand.waitFor();
    const bounds = await expand.boundingBox();
    assert.equal(bounds.width, 48, 'The expansion control must not become a wide filled pill');
    assert.equal(bounds.height, 48);
    assert.equal(await expand.evaluate(element => getComputedStyle(element).backgroundColor), 'rgba(0, 0, 0, 0)');
    assert.equal(await page.getByRole('option').count(), 0, 'Chapter and expansion buttons belong in a list, not nested interactive options');
    assert.ok(await page.getByRole('listitem').count() >= 2);
    await expand.click();
    await page.getByText('First section', {exact: true}).waitFor({state: 'visible'});
    assert.equal(await page.locator('output').textContent(), '');
    await expand.focus();
    await page.keyboard.press('Space');
    await page.getByText('First section', {exact: true}).waitFor({state: 'hidden'});
    await page.getByText('A long synthetic chapter title with nested sections', {exact: true}).click();
    assert.equal(await page.locator('output').textContent(), 'chapter.xhtml');
    for (const [surface, theme, locale] of [['glass', 'DARK', 'zh-CN'], ['solid', 'LIGHT', 'en'], ['classic', 'DARK', 'en']]) {
      await page.goto(`${server.resolvedUrls.local[0]}tests/toc.fixture.html?surface=${surface}&theme=${theme}&locale=${locale}`);
      await expand.waitFor();
      assert.equal(await expand.count(), 1, 'Empty sections must not have an expansion control');
      const size = await expand.boundingBox();
      assert.equal(size.width, 48);
      assert.equal(size.height, 48);
      assert.equal(await expand.evaluate(element => getComputedStyle(element).backgroundColor), 'rgba(0, 0, 0, 0)');
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 360);
      assert.match(await expand.getAttribute('aria-label'), locale === 'zh-CN' ? /^展开/ : /^Expand /);
      await expand.click();
      await page.getByRole('button', {name: 'First section', exact: true}).click();
      assert.equal(await page.locator('output').textContent(), 'section-one.xhtml');
    }
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
    await server.close();
    await rm(cacheDir, {recursive: true, force: true});
  }
});

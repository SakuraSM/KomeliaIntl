import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {createServer} from 'vite';
import {startReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';

const require = createRequire(import.meta.url);
const {chromium} = require('playwright');

test('Komga EPUB toolbar preserves touch targets, navigation and overflow actions', async () => {
  const server = await startReaderToolbarServer(createServer);
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 360, height: 800}, hasTouch: true, isMobile: true});
    const pageErrors = [];
    page.on('pageerror', error => { pageErrors.push(error.message); console.error(error.message); });
    await page.goto(server.url);
    const settings = page.getByRole('button', {name: 'Settings', exact: true});
    await settings.waitFor();
    const toolbar = page.locator('[data-komelia-toolbar="top"]');
    const bounds = await toolbar.boundingBox();
    assert.equal(bounds.width, 360);
    assert.equal(bounds.height, 48);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 360);
    const size = await settings.boundingBox();
    assert.equal(size.width, 48);
    assert.equal(size.height, 48);
    await settings.click();
    assert.equal(await settings.getAttribute('aria-expanded'), 'true');
    assert.equal(await page.locator('output').textContent(), 'settings');
    const toc = page.getByRole('button', {name: 'Show/hide table of contents'});
    await toc.click();
    assert.equal(await toc.getAttribute('aria-expanded'), 'true');
    assert.equal(await page.locator('output').textContent(), 'toc');
    const more = page.getByRole('button', {name: 'More options'});
    await more.click();
    await page.getByText('Show/hide help', {exact: true}).click();
    assert.equal(await page.locator('output').textContent(), 'help');
    await more.focus();
    await page.keyboard.press('Enter');
    await page.getByText('Enter/exit full screen', {exact: true}).waitFor();
    await page.keyboard.press('Escape');
    await page.getByText('Enter/exit full screen', {exact: true}).waitFor({state: 'hidden'});
    assert.equal(await more.evaluate(node => node === document.activeElement), true);
    await more.click();
    await page.getByText('Enter/exit full screen', {exact: true}).click();
    assert.equal(await page.locator('output').textContent(), 'fullscreen');
    await page.goto(`${server.url}?locale=zh-CN&theme=DARK`);
    await page.getByRole('button', {name: '更多选项'}).click();
    await page.getByText('显示/隐藏帮助', {exact: true}).click();
    assert.equal(await page.locator('output').textContent(), 'help');
    assert.deepEqual(pageErrors, []);
  } finally {
    await browser.close();
    await server.close();
  }
});

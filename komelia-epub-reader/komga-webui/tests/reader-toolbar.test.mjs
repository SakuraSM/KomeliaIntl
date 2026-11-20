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
    assert.equal(bounds.width, 336);
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
    assert.equal(await page.locator('output').getAttribute('data-overflow-open'), 'true');
    const shadow = await page.getByRole('menu', {name: 'More options'}).evaluate(node => getComputedStyle(node).boxShadow);
    const shadowLengths = shadow.match(/-?[\d.]+px/g) ?? [];
    assert.ok(shadow === 'none' || (shadowLengths.length > 0 && shadowLengths.every(length => parseFloat(length) === 0)), shadow);
    await page.getByText('Show/hide help', {exact: true}).click();
    assert.equal(await page.locator('output').textContent(), 'help');
    await more.focus();
    await page.keyboard.press('Enter');
    await page.getByText('Enter/exit full screen', {exact: true}).waitFor();
    await page.keyboard.press('Escape');
    await page.getByText('Enter/exit full screen', {exact: true}).waitFor({state: 'hidden'});
    assert.equal(await page.locator('output').getAttribute('data-overflow-open'), 'false');
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

test('Mac EPUB toolbar is centered and retains the original actions', async () => {
  const server = await startReaderToolbarServer(createServer);
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport:{width:960,height:720}});
    await page.emulateMedia({reducedMotion:'reduce'});
    await page.goto(server.url);
    const toolbar = page.locator('[data-komelia-toolbar="top"]');
    await toolbar.waitFor();
    await page.evaluate(() => { document.documentElement.dataset.komeliaPlatform = 'macos'; });
    const bounds = await toolbar.boundingBox();
    assert.equal(bounds.width, 350);
    assert.ok(Math.abs(bounds.x + bounds.width / 2 - 480) <= 1);
    for (const name of ['Close', 'Show/hide table of contents', 'Settings', 'More options']) {
      const button = toolbar.getByRole('button', {name, exact:true});
      const size = await button.boundingBox();
      assert.ok(size.width >= 40 && size.height >= 40, name);
    }
    const settings = toolbar.getByRole('button', {name:'Settings', exact:true});
    await settings.focus();
    assert.notEqual(await settings.evaluate(node => getComputedStyle(node).outlineStyle), 'none');
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('output').textContent(), 'settings');
  } finally {
    await browser.close();
    await server.close();
  }
});

test('Android paper toolbar keeps its position, touch targets and book navigation', async () => {
  const server = await startReaderToolbarServer(createServer);
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport:{width:390,height:844}, hasTouch:true, isMobile:true});
    await page.goto(server.url);
    const toolbar = page.locator('[data-komelia-toolbar="top"]');
    await toolbar.waitFor();
    await page.evaluate(() => { document.documentElement.dataset.komeliaPlatform = 'android'; });
    assert.deepEqual(await toolbar.boundingBox(), {x:12,y:10,width:366,height:48});
    assert.equal(await toolbar.evaluate(node => getComputedStyle(node).borderTopLeftRadius), '12px');
    const title = toolbar.getByText('从零开始的异世界生活', {exact:true});
    assert.ok(await title.isVisible());
    assert.equal(await title.evaluate(node => node.scrollWidth === node.clientWidth), true);
    const settings = toolbar.getByRole('button', {name:'Settings', exact:true});
    assert.equal((await settings.boundingBox()).width, 48);
    await toolbar.getByRole('button', {name:'More options'}).click();
    const previous = page.getByRole('menuitem', {name:'Previous book'});
    assert.equal(await previous.isEnabled(), false);
    await page.getByRole('menuitem', {name:'Next book'}).click();
    assert.equal(await page.locator('output').textContent(), 'nextBook');
  } finally {
    await browser.close();
    await server.close();
  }
});

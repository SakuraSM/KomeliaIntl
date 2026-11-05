import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { createServer } from 'vite';
import { startReaderToolbarServer } from '../../shared/tests/reader-toolbar-server.mjs';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const chromeStyles = await readFile(new URL('../../shared/chrome-appearance.css', import.meta.url), 'utf8');

test('reader edge controls reveal on pointer or keyboard focus without covering text at rest', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-edge.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 1280, height: 800}});
    await page.goto(`${server.url}?surface=glass`);
    const previous = page.locator('button[data-komelia-chrome][aria-label="Previous page"]');
    const next = page.locator('button[data-komelia-chrome][aria-label="Next page"]');
    await previous.waitFor({state: 'attached'});
    assert.equal(await previous.evaluate(node => getComputedStyle(node).opacity), '0');
    assert.equal(await next.evaluate(node => getComputedStyle(node).opacity), '0');
    await previous.hover();
    await page.waitForFunction(() => Number(getComputedStyle(document.querySelector('button[data-komelia-chrome][aria-label="Previous page"]')).opacity) > 0.99);
    await previous.click();
    assert.equal(await page.locator('output').textContent(), 'previous');
    await page.mouse.move(640, 100);
    await page.keyboard.press('Tab');
    await page.waitForFunction(() => Number(getComputedStyle(document.querySelector('button[data-komelia-chrome][aria-label="Next page"]')).opacity) > 0.99);
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('output').textContent(), 'next');

    const touch = await browser.newPage({viewport: {width: 1280, height: 800}, hasTouch: true, isMobile: true});
    await touch.goto(`${server.url}?footer&surface=glass`);
    const touchEdge = touch.locator('button[data-komelia-chrome][aria-label="Previous page"]');
    await touchEdge.waitFor({state: 'attached'});
    assert.equal(await touchEdge.evaluate(node => getComputedStyle(node).display), 'none');
    const footerPrevious = touch.locator('#ttu-page-footer button[aria-label="Previous page"]');
    await footerPrevious.click();
    assert.equal(await touch.locator('output').textContent(), 'previous');
  } finally {
    await browser.close();
    await server.close();
  }
});

test('EPUB chrome uses the approved floating inset while keeping its touch targets', async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN });
  try {
    const page = await browser.newPage({ viewport: { width: 360, height: 800 }, hasTouch: true, isMobile: true });
    await page.setContent(`<!doctype html><html data-komelia-surface="glass" data-komelia-theme="LIGHT"><head>
      <meta name="viewport" content="width=device-width,initial-scale=1"><style>${chromeStyles}
      body { margin:0 } nav { position:fixed; height:48px; width:100% }
      </style></head><body><nav data-komelia-chrome data-komelia-toolbar="top"><button>Settings</button></nav>
      <nav data-komelia-chrome data-komelia-toolbar="bottom">Progress</nav></body></html>`);
    const geometry = await page.evaluate(() => {
      const measure = (position) => {
        const node = document.querySelector(`[data-komelia-toolbar="${position}"]`);
        const bounds = node.getBoundingClientRect();
        return { left: bounds.left, width: bounds.width, radius: getComputedStyle(node).borderTopLeftRadius };
      };
      return { top: measure('top'), bottom: measure('bottom'), viewport: innerWidth };
    });
    assert.deepEqual(geometry.top, { left: 12, width: geometry.viewport - 24, radius: '12px' });
    assert.deepEqual(geometry.bottom, { left: 12, width: geometry.viewport - 24, radius: '20px' });
  } finally {
    await browser.close();
  }
});

test('TTU keeps narrow chrome on one row and preserves overflow actions and keyboard focus', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN });
  try {
    const page = await browser.newPage({ viewport: { width: 360, height: 800 }, hasTouch: true, isMobile: true });
    const pageErrors = [];
    page.on('pageerror', error => { pageErrors.push(error.message); console.error(error.message); });
    await page.goto(server.url);
    const toolbar = page.getByRole('navigation', {name: 'Reader toolbar'});
    await toolbar.waitFor();
    const bounds = await toolbar.boundingBox();
    assert.equal(bounds.width, 336);
    assert.equal(bounds.height, 48);
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 360);
    for (const name of ['Close Book', 'Open Table of Contents', 'Reader settings', 'More options']) {
      const control = toolbar.getByRole('button', {name, exact: true});
      const size = await control.boundingBox();
      assert.equal(size.width, 48);
      assert.equal(size.height, 48);
    }
    await toolbar.getByRole('button', {name: 'Reader settings'}).click();
    assert.equal(await page.locator('output').textContent(), 'settings');
    const more = toolbar.getByRole('button', {name: 'More options'});
    await more.focus();
    await page.keyboard.press('ArrowDown');
    await page.getByRole('menu').waitFor();
    assert.equal(await page.evaluate(() => document.activeElement.textContent.trim()), 'Update Bookmark');
    await page.keyboard.press('End');
    assert.equal(await page.evaluate(() => document.activeElement.textContent.trim()), 'View images');
    await page.keyboard.press('Escape');
    assert.equal(await page.getByRole('menu').count(), 0);
    assert.equal(await more.evaluate(node => node === document.activeElement), true);
    for (const [label, result] of Object.entries({
      'Update Bookmark': 'bookmark', 'Return to Bookmark': 'return', 'Mark as read': 'complete',
      'Show custom reading point': 'showPoint', 'Set custom reading point': 'setPoint',
      'Reset custom reading point': 'resetPoint', 'Toggle Fullscreen': 'fullscreen', 'View images': 'images',
    })) {
      await more.click();
      await page.getByRole('menuitem', {name: label, exact: true}).click();
      assert.equal(await page.locator('output').textContent(), result);
      assert.equal(await page.getByRole('menu').count(), 0);
    }
    await page.goto(`${server.url}?locale=zh-CN&theme=DARK`);
    await page.getByRole('button', {name: '更多选项'}).click();
    await page.getByRole('menuitem', {name: '设置定位', exact: true}).click();
    assert.equal(await page.locator('output').textContent(), 'setPoint');
    assert.deepEqual(pageErrors, []);
  } finally {
    await browser.close();
    await server.close();
  }
});

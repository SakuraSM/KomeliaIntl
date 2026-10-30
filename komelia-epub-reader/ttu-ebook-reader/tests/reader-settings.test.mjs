import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {createServer} from 'vite';
import {startReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');


test('actual reader settings keep theme samples and view choices readable under dark glass', async () => {
  const server = await startReaderToolbarServer(createServer);
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 360, height: 800}, hasTouch: true, isMobile: true});
    await page.goto(`${server.url}?settings&theme=DARK`);
    const panel = page.locator('[data-komelia-reader-settings]');
    const sample = panel.locator('button[title="light-theme"]');
    await sample.waitFor();
    const colors = await sample.evaluate(node => ({color: getComputedStyle(node).color, background: getComputedStyle(node).backgroundColor}));
    assert.equal(colors.background, 'rgb(255, 255, 255)');
    assert.match(colors.color, /^rgba?\(0, 0, 0/);
    const continuous = panel.getByRole('button', {name: 'Continuous', exact: true});
    const paginated = panel.getByRole('button', {name: 'Paginated', exact: true});
    assert.equal(await paginated.getAttribute('aria-pressed'), 'true');
    await continuous.click();
    assert.equal(await continuous.getAttribute('aria-pressed'), 'true');
    assert.equal(await paginated.getAttribute('aria-pressed'), 'false');
    const unselected = await paginated.evaluate(node => getComputedStyle(node).backgroundColor);
    assert.notEqual(unselected, 'rgb(255, 255, 255)');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 360);
  } finally {
    await browser.close();
    await server.close();
  }
});

test('continuous chapter entry selects the requested chapter rather than the preceding bookmark boundary', async () => {
  const server = await startReaderToolbarServer(createServer);
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport:{width:360,height:800}, hasTouch:true, isMobile:true});
    await page.goto(`${server.url}?initialChapter`);
    await page.waitForFunction(() => window.scrollY > 100, undefined, {timeout:5000});
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    assert.equal(await page.locator('output').textContent(), 'chapter-two');
    const position = await page.locator('#selected').boundingBox();
    assert.ok(position.y >= 0 && position.y < 150, JSON.stringify(position));
    await page.setViewportSize({width:360, height:700});
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => requestAnimationFrame(resolve)))));
    const resized = await page.locator('#selected').boundingBox();
    assert.ok(Math.abs(resized.y - position.y) < 2, JSON.stringify({position,resized}));
    await page.setViewportSize({width:328, height:700});
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => requestAnimationFrame(resolve)))));
    const widthAdjusted = await page.locator('#selected').boundingBox();
    assert.ok(Math.abs(widthAdjusted.y - position.y) < 2, JSON.stringify({position,widthAdjusted}));
    const beforeScroll = await page.evaluate(() => window.scrollY);
    await page.mouse.move(160, 400);
    await page.mouse.wheel(0, 400);
    await page.waitForFunction(before => window.scrollY > before + 100, beforeScroll);
    await page.setViewportSize({width:360, height:700});
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(() => requestAnimationFrame(resolve)))));
    assert.ok((await page.locator('#selected').boundingBox()).y < -100, 'Reading must not snap back to the initial chapter after a user scroll');
    await page.goto(`${server.url}?initialChapter&bookmarkOnly`);
    await page.waitForFunction(() => window.scrollY > 100 && document.querySelector('output')?.textContent === 'chapter-one', undefined, {timeout:5000});
  } finally {
    await browser.close();
    await server.close();
  }
});

test('reader settings keep font controls in one row and theme samples in balanced columns', async () => {
  const server = await startReaderToolbarServer(createServer);
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?settings&theme=DARK`);
    const panel = page.locator('[data-komelia-reader-settings]');
    const font = panel.locator('[data-komelia-font-choice]').first();
    await font.waitFor();
    const labelBounds = await panel.locator('section').first().locator('h2').boundingBox();
    const fontBounds = await font.boundingBox();
    assert.ok(Math.abs(labelBounds.y + labelBounds.height / 2 - fontBounds.y - fontBounds.height / 2) <= 2,
      JSON.stringify({labelBounds, fontBounds}));
    const samples = await panel.locator('[data-komelia-theme-swatch]').evaluateAll(nodes => nodes.map(node => {
      const bounds = node.getBoundingClientRect();
      return {x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height};
    }));
    assert.equal(samples.length, 6);
    assert.equal(samples.filter(sample => Math.abs(sample.y - samples[0].y) <= 2).length, 3);
    assert.ok(Math.max(...samples.map(sample => sample.width)) - Math.min(...samples.map(sample => sample.width)) <= 2);
    assert.ok(samples.every(sample => sample.height >= 48));
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 390);
    await font.click();
    const firstOption = panel.locator('button[data-popover]').first();
    const optionBounds = await firstOption.boundingBox();
    assert.ok(optionBounds.y >= fontBounds.y + fontBounds.height - 2);
    await panel.getByRole('button', {name: 'Synthetic serif', exact: true}).click();
    assert.equal((await font.textContent()).trim(), 'Synthetic serif');
    assert.equal(await font.evaluate(node => node === document.activeElement), true);
    await font.click();
    await font.press('Escape');
    assert.equal(await font.getAttribute('aria-expanded'), 'false');
  } finally {
    await browser.close();
    await server.close();
  }
});

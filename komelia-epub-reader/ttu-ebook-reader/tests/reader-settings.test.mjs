import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {build, createServer} from 'vite';
import {startReaderToolbarServer, startBuiltReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';
import {fileURLToPath} from 'node:url';
import {saveVisualComparison} from '../../shared/tests/visual-compare.mjs';
import {assertControlBounds, paintedBounds} from '../../shared/tests/control-geometry.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');

for (const theme of ['DARK', 'LIGHT']) {
test(`classic ${theme.toLowerCase()} settings distinguish the selected reading mode`, async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?settings&surface=classic&theme=${theme}`);
    const panel = page.locator('[data-komelia-reader-settings]');
    const continuous = panel.getByRole('button', {name: 'Continuous', exact: true});
    const paginated = panel.getByRole('button', {name: 'Paginated', exact: true});
    await continuous.click();
    assert.equal(await continuous.getAttribute('aria-pressed'), 'true');
    assert.equal(await paginated.getAttribute('aria-pressed'), 'false');
    const selectedBackground = await continuous.evaluate(node => getComputedStyle(node).backgroundColor);
    const unselectedBackground = await paginated.evaluate(node => getComputedStyle(node).backgroundColor);
    assert.equal(unselectedBackground, 'rgba(0, 0, 0, 0)');
    assert.notEqual(selectedBackground, unselectedBackground);
  } finally {
    await browser.close();
    await server.close();
  }
});
}


test('reader settings keep theme samples, view choices, and font names readable', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
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

    await page.goto(`${server.url}?settings&surface=classic&theme=DARK`);
    const fontName = panel.locator('.font-row-controls .font-name');
    assert.equal(await fontName.evaluate(node => getComputedStyle(node).color),
      await panel.evaluate(node => getComputedStyle(node).color));
  } finally {
    await browser.close();
    await server.close();
  }
});

test('continuous chapter entry selects the requested chapter rather than the preceding bookmark boundary', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
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
    await page.evaluate(() => window.navigateTestChapter('chapter-two#selected'));
    await page.waitForFunction(() => {
      const bounds = document.querySelector('#selected').getBoundingClientRect();
      return bounds.top >= 0 && bounds.top < 150;
    }, undefined, {timeout:5000});
  } finally {
    await browser.close();
    await server.close();
  }
});

test('reader settings keep font controls in one row and theme samples in balanced columns', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
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

test('settings sheet matches the normalized phone design without overlapping touch targets', async () => {
  const server = await startBuiltReaderToolbarServer(build, createServer);
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}, hasTouch: true, isMobile: true});
    await page.goto(`${server.url}?settings&theme=DARK&locale=zh-CN`);
    const panel = page.locator('[data-komelia-reader-settings]');
    await panel.waitFor();
    const bounds = await panel.boundingBox();
    const expected = {x: 0, y: 274, width: 390, height: 570};
    console.log('VISUAL_MEASUREMENT ' + JSON.stringify({screen: '11-text-reader-settings', expected, measured: bounds}));
    for (const key of Object.keys(expected)) assert.ok(Math.abs(bounds[key] - expected[key]) <= 2, `${key}: ${JSON.stringify(bounds)}`);
    const geometry = [];
    for (const [index, x] of [219.5,327].entries()) {
      const hit = await panel.locator('.font-size-controls button').nth(index).boundingBox();
      assertControlBounds('11', `font-step-${index}`, {x:hit.x+4,y:hit.y+4,width:hit.width-8,height:hit.height-8},
        {x,y:400.5,width:39,height:38}, geometry);
    }
    for (let index = 0; index < 6; index++) {
      assertControlBounds('11', `theme-${index}`, await panel.locator('[data-komelia-theme-swatch]').nth(index).boundingBox(),
        {x:27+116*(index%3),y:504+88*Math.floor(index/3),width:104,height:56}, geometry);
    }
    for (let index = 0; index < 2; index++) {
      assertControlBounds('11', `mode-${index}`, await paintedBounds(panel.locator('.komelia-reading-settings > .h-full [data-komelia-toggle-option]').nth(index)),
        {x:107+130*index,y:698,width:130.8,height:38}, geometry);
    }
    const add = await panel.locator('.komelia-theme-add').boundingBox();
    const increase = await panel.locator('.font-size-controls button').last().boundingBox();
    assert.ok(add.y >= increase.y + increase.height, JSON.stringify({add, increase}));
    assert.ok(add.height >= 48 && increase.height >= 48);
    if (process.env.KOMELIA_VISUAL_OUTPUT) {
      const {mkdir, writeFile} = await import('node:fs/promises');
      const {join} = await import('node:path');
      await mkdir(process.env.KOMELIA_VISUAL_OUTPUT, {recursive: true});
      await panel.locator('[data-komelia-theme-swatch][title="ecru-theme"]').click();
      await panel.locator('.komelia-reading-settings > .h-full [data-komelia-toggle-option]').first().click();
      await panel.getByRole('spinbutton').first().fill('18');
      await panel.getByRole('spinbutton').first().press('Tab');
      await panel.locator('.komelia-settings-header h2').click();
      await page.mouse.move(0, 0);
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      await page.waitForFunction(() => !document.getAnimations().some(animation => animation.playState === 'running'));
      await page.screenshot({path: join(process.env.KOMELIA_VISUAL_OUTPUT, '11-text-reader-settings.png')});
      await writeFile(join(process.env.KOMELIA_VISUAL_OUTPUT, '11-text-reader-settings.json'), JSON.stringify({expected, measured: bounds, add, increase, geometry}, null, 2));
      await saveVisualComparison({page,
        referencePath: fileURLToPath(new URL('../../../docs/design/modern-ui-20261030/screens/11-text-reader-settings.png', import.meta.url)),
        implementationPath: join(process.env.KOMELIA_VISUAL_OUTPUT, '11-text-reader-settings.png'),
        outputPath: join(process.env.KOMELIA_VISUAL_OUTPUT, '11-text-reader-settings-comparison.png'), caption: 'TTU reading settings · 390 × 844'});
    }
  } finally {
    await browser.close();
    await server.close();
  }
});

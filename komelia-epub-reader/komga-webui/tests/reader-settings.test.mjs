import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {test} from 'node:test';
import {createServer} from 'vite';
import {fileURLToPath} from 'node:url';
import {saveVisualComparison} from '../../shared/tests/visual-compare.mjs';

const require = createRequire(import.meta.url);
const {chromium} = require('playwright');
const englishLabels = JSON.parse(await readFile(new URL('../src/locales/en.json', import.meta.url), 'utf8'));
const chineseLabels = JSON.parse(await readFile(new URL('../src/locales/zh-CN.json', import.meta.url), 'utf8'));

test('EPUB settings controls follow the application palette and retain their callbacks', async () => {
  const cacheDir = await mkdtemp(join(tmpdir(), 'komelia-settings-test-'));
  const server = await createServer({cacheDir, server: {host: '127.0.0.1', port: 0},
    optimizeDeps: {entries: ['tests/reader-settings.fixture.ts']}});
  await server.listen();
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}, hasTouch: true, isMobile: true});
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    const url = `${server.resolvedUrls.local[0]}tests/reader-settings.fixture.html`;
    for (const surface of ['glass', 'solid', 'classic']) {
      for (const theme of ['LIGHT', 'DARK', 'DARKER']) {
        await page.goto(`${url}?surface=${surface}&theme=${theme}`);
        const panel = page.locator('[data-komelia-reader-settings]');
        await panel.waitFor();
        const colors = await panel.evaluate(node => {
          const panelColor = getComputedStyle(node).backgroundColor;
          return [...node.querySelectorAll('.v-list-subheader, button:not([data-komelia-theme-swatch])')]
            .filter(element => element.getBoundingClientRect().height > 0)
            .map(element => ({name: element.getAttribute('aria-label') || element.textContent.trim(),
              color: getComputedStyle(element).color, background: getComputedStyle(element).backgroundColor, panelColor}));
        });
        for (const color of colors) {
          const background = composite(parseColor(color.background), composite(parseColor(color.panelColor), [1, 1, 1, 1]));
          const foreground = composite(parseColor(color.color), background);
          assert.ok(contrast(foreground, background) >= 4.5, `${surface}/${theme} ${JSON.stringify(color)}`);
        }
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 390);
        assert.equal(await panel.locator('[data-komelia-theme-swatch]').count(), 3);
      }
    }
    await page.goto(`${url}?theme=LIGHT`);
    await page.getByRole('button', {name: 'Increase font size', exact: true}).click();
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), {name: 'fontSize', value: 110});
    await page.getByRole('button', {name: 'Sepia', exact: true}).click();
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), {name: 'appearance', value: 'readium-sepia-on'});
    await page.getByRole('button', {name: englishLabels.enums.epubreader.column_count.one, exact: true}).click();
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), {name: 'columnCount', value: '1'});
    await page.getByRole('button', {name: 'Increase line height', exact: true}).click();
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), {name: 'lineHeight', value: 1.1});
    await page.getByRole('checkbox').click();
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), {name: 'navigationClick', value: false});
    await page.getByRole('button', {name: 'Scroll', exact: true}).click();
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), {name: 'verticalScroll', value: true});
    await page.getByRole('slider').focus();
    await page.keyboard.press('ArrowRight');
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), {name: 'pageMargins', value: 1.25});
    await page.keyboard.press('Escape');
    await page.locator('[data-komelia-reader-settings]').waitFor({state: 'hidden'});
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), {name: 'open', value: false});
    await page.goto(`${url}?theme=LIGHT&fixed-layout`);
    await page.locator('div[role="combobox"]').click();
    await page.getByRole('option', {name: 'Left to right', exact: true}).click();
    assert.deepEqual(JSON.parse(await page.locator('output').textContent()), {name: 'readingDirection', value: 'ltr'});
    await page.setViewportSize({width: 360, height: 800});
    await page.goto(`${url}?theme=LIGHT&locale=zh-CN`);
    await page.locator('[data-komelia-reader-settings]').waitFor();
    await page.evaluate(() => { document.documentElement.style.fontSize = '130%'; });
    const panelSize = await page.locator('[data-komelia-reader-settings]').evaluate(node => ({width: node.clientWidth, scrollWidth: node.scrollWidth}));
    assert.ok(panelSize.scrollWidth <= panelSize.width, JSON.stringify(panelSize));
    const labels = await page.locator('.komelia-settings-segments button').evaluateAll(buttons => buttons.map(button => {
      const label = document.createRange();
      label.selectNodeContents(button.querySelector('.v-btn__content'));
      const content = label.getBoundingClientRect(), bounds = button.getBoundingClientRect();
      return {text: button.textContent.trim(), left: content.left, right: content.right,
        buttonLeft: bounds.left, buttonRight: bounds.right};
    }));
    assert.ok(labels.every(label => label.left >= label.buttonLeft + 3 && label.right <= label.buttonRight - 3), JSON.stringify(labels));
    await page.getByRole('slider').scrollIntoViewIfNeeded();
    assert.equal(await page.getByRole('slider').isVisible(), true);
    assert.deepEqual(errors, []);
  } finally {
    await browser.close();
    await server.close();
    await rm(cacheDir, {recursive: true, force: true});
  }
});

function parseColor(value) {
  const channels = value.match(/[\d.]+/g).map(Number);
  return [channels[0] / 255, channels[1] / 255, channels[2] / 255, channels[3] ?? 1];
}
function composite(foreground, background) {
  return [...foreground.slice(0, 3).map((channel, index) => channel * foreground[3] + background[index] * (1 - foreground[3])), 1];
}
function contrast(first, second) {
  const luminance = channels => channels.slice(0, 3).map(channel => channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4)
    .reduce((sum, channel, index) => sum + channel * [.2126, .7152, .0722][index], 0);
  const a = luminance(first), b = luminance(second);
  return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
}

test('settings sheet matches the normalized 390px design within two pixels', async () => {
  const cacheDir = await mkdtemp(join(tmpdir(), 'komelia-settings-geometry-'));
  const server = await createServer({cacheDir, server: {host: '127.0.0.1', port: 0}, optimizeDeps: {entries: ['tests/reader-settings.fixture.ts']}});
  await server.listen();
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}, hasTouch: true, isMobile: true});
    await page.goto(`${server.resolvedUrls.local[0]}tests/reader-settings.fixture.html?surface=glass&theme=DARK&locale=zh-CN`);
    const panel = page.locator('[data-komelia-reader-settings]');
    await panel.waitFor();
    await page.waitForFunction(() => {
      const node = document.querySelector('[data-komelia-reader-settings]');
      return node && node.getBoundingClientRect().bottom <= innerHeight + .5 &&
        !node.closest('.v-overlay--active')?.getAnimations({subtree: true}).some(animation => animation.playState === 'running');
    });
    const bounds = await panel.boundingBox();
    const expected = {x: 0, y: 274, width: 390, height: 570};
    console.log('VISUAL_MEASUREMENT ' + JSON.stringify({screen: '17-komga-settings', expected, measured: bounds}));
    for (const key of Object.keys(expected)) assert.ok(Math.abs(bounds[key] - expected[key]) <= 2, `${key}: ${JSON.stringify(bounds)}`);
    const controls = await panel.locator('button').evaluateAll(nodes => nodes.filter(node => node.getBoundingClientRect().height > 0).map(node => ({
      name: node.getAttribute('aria-label') || node.textContent.trim(), width: node.getBoundingClientRect().width, height: node.getBoundingClientRect().height,
    })));
    assert.ok(controls.every(control => control.width >= 48 && control.height >= 48), JSON.stringify(controls));
    if (process.env.KOMELIA_VISUAL_OUTPUT) {
      const {mkdir, writeFile} = await import('node:fs/promises');
      await mkdir(process.env.KOMELIA_VISUAL_OUTPUT, {recursive: true});
      await page.getByRole('button', {name: chineseLabels.enums.epubreader.appearances.sepia, exact: true}).click();
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      await page.waitForFunction(() => !document.getAnimations().some(animation => animation.playState === 'running'));
      await page.screenshot({path: join(process.env.KOMELIA_VISUAL_OUTPUT, '17-komga-settings.png')});
      await writeFile(join(process.env.KOMELIA_VISUAL_OUTPUT, '17-komga-settings.json'), JSON.stringify({expected, measured: bounds, controls}, null, 2));
      await saveVisualComparison({page,
        referencePath: fileURLToPath(new URL('../../../docs/design/modern-ui-20261030/screens/17-komga-settings.png', import.meta.url)),
        implementationPath: join(process.env.KOMELIA_VISUAL_OUTPUT, '17-komga-settings.png'),
        outputPath: join(process.env.KOMELIA_VISUAL_OUTPUT, '17-komga-settings-comparison.png'), caption: 'Komga EPUB settings · 390 × 844'});
    }
  } finally {
    await browser.close();
    await server.close();
    await rm(cacheDir, {recursive: true, force: true});
  }
});

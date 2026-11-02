import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {createServer} from 'vite';
import {startReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';
import {assertControlBounds} from '../../shared/tests/control-geometry.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');

test('compact contents keeps chapter navigation, search, marks and Escape available', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}, hasTouch: true, isMobile: true});
    await page.goto(`${server.url}?contents&theme=DARK`);
    const drawer = page.locator('[data-komelia-reader-contents]');
    await drawer.waitFor();
    await page.waitForFunction(() => !document.querySelector('[data-komelia-reader-contents]')?.getAnimations({subtree: true}).some(animation => animation.playState === 'running'));
    const bounds = await drawer.boundingBox();
    console.log('VISUAL_MEASUREMENT ' + JSON.stringify({screen: '10-epub-contents', measured: bounds}));
    assert.ok(Math.abs(bounds.height - 570) <= 2 && Math.abs(bounds.y - 274) <= 2, JSON.stringify(bounds));
    const geometry = [];
    for (let index = 0; index < 2; index++) {
      assertControlBounds('10',`chapter-${index}`,await drawer.locator('.navigation-chapter').nth(index).boundingBox(),
        {x:14,y:359+49*index,width:362,height:48},geometry);
    }
    assertControlBounds('10','navigation-type',await drawer.getByRole('combobox').boundingBox(),{x:27,y:291,height:48},geometry);
    const current = drawer.getByRole('button', {name: /Second chapter/});
    assert.equal(await current.getAttribute('aria-current'), 'location');
    assert.equal(await drawer.getByText('143 / 143').count(), 0);
    await drawer.getByRole('combobox').selectOption('search');
    await drawer.locator('input').fill('ocean');
    assert.equal(await page.locator('output').textContent(), 'query:ocean');
    await drawer.getByRole('combobox').selectOption('marks');
    assert.equal(await drawer.getByText('No highlights yet', {exact: true}).isVisible(), true);
    await drawer.getByRole('combobox').selectOption('toc');
    await current.click();
    assert.equal(await page.locator('output').getAttribute('data-chapter'), 'chapter-two');
    assert.equal(await page.locator('output').textContent(), 'closed-contents');
    await drawer.getByRole('combobox').focus();
    await page.keyboard.press('Escape');
    assert.equal(await page.locator('output').textContent(), 'closed-contents');
    if (process.env.KOMELIA_VISUAL_OUTPUT) {
      const {mkdir} = await import('node:fs/promises');
      const {join} = await import('node:path');
      await mkdir(process.env.KOMELIA_VISUAL_OUTPUT, {recursive: true});
      await page.screenshot({path: join(process.env.KOMELIA_VISUAL_OUTPUT, '10-epub-contents.png')});
    }
  } finally {
    await browser.close();
    await server.close();
  }
});

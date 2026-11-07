import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {createServer} from 'vite';
import {startReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';
import {assertControlBounds} from '../../shared/tests/control-geometry.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');

test('contents highlights the visible nested fragment and expands its parent', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?contents&nestedContents&activeFragment&theme=DARK`);
    const drawer = page.locator('[data-komelia-reader-contents]');
    const target = drawer.getByRole('button', {name: /Nested fragment/});
    await target.waitFor({timeout: 5000});
    assert.equal(await target.getAttribute('aria-current'), 'location');
    assert.equal(await drawer.getByRole('button', {name: /Second chapter/}).getAttribute('aria-current'), null);
    assert.equal(await drawer.getByRole('button', {name: 'Collapse Parent chapter'}).getAttribute('aria-expanded'), 'true');
    await drawer.getByRole('button', {name: 'Collapse Parent chapter'}).click();
    assert.equal(await target.count(), 0);
  } finally {
    await browser.close();
    await server.close();
  }
});

for (const query of ['activeFragment&paginatedContents', 'activeFragment&verticalContents',
  'activeFragment&emptyActive', 'activeFragment&duplicateAnchor']) {
test(`contents finds the current fragment in ${query}`, async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?contents&nestedContents&${query}`);
    const drawer = page.locator('[data-komelia-reader-contents]');
    const target = drawer.getByRole('button', {name: /Nested fragment/});
    await target.waitFor({timeout: 5000});
    assert.equal(await target.getAttribute('aria-current'), 'location');
  } finally {
    await browser.close();
    await server.close();
  }
});
}

test('contents follows the latest reached fragment and leaves future fragments unselected', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?contents&nestedContents&activeFragment&fragmentAfter`);
    const drawer = page.locator('[data-komelia-reader-contents]');
    const later = drawer.getByRole('button', {name: /Deep fragment/});
    await later.waitFor({timeout: 5000});
    assert.equal(await later.getAttribute('aria-current'), 'location');
    await page.goto(`${server.url}?contents&nestedContents&activeFragment&fragmentFuture`);
    assert.equal(await drawer.getByRole('button', {name: /Second chapter/}).getAttribute('aria-current'), 'location');
    assert.equal(await drawer.getByRole('button', {name: /Nested fragment/}).count(), 0);
  } finally {
    await browser.close();
    await server.close();
  }
});

test('a hidden fragment does not become the current chapter', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?contents&nestedContents&activeFragment&fragmentHidden`);
    const drawer = page.locator('[data-komelia-reader-contents]');
    const chapter = drawer.getByRole('button', {name: /Second chapter/});
    await chapter.waitFor();
    assert.equal(await chapter.getAttribute('aria-current'), 'location');
  } finally {
    await browser.close();
    await server.close();
  }
});

test('a long unbroken chapter title stays inside the contents without hiding its expand control', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?contents&longContents&theme=DARK`);
    const drawer = page.locator('[data-komelia-reader-contents]');
    const expand = drawer.locator('button[aria-expanded]');
    await expand.waitFor();
    const list = drawer.locator('.navigation-chapters');
    const sizes = await list.evaluate(node => ({width: node.clientWidth, scrollWidth: node.scrollWidth}));
    assert.ok(sizes.scrollWidth <= sizes.width, JSON.stringify(sizes));
    const listBounds = await list.boundingBox();
    const toggleBounds = await expand.boundingBox();
    assert.ok(toggleBounds.x + toggleBounds.width <= listBounds.x + listBounds.width, JSON.stringify({listBounds, toggleBounds}));
  } finally {
    await browser.close();
    await server.close();
  }
});

test('contents falls back to readable sections when no table entry can be opened', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?contents&invalidContents`);
    const drawer = page.locator('[data-komelia-reader-contents]');
    await drawer.getByRole('button', {name: /First chapter/}).waitFor({timeout: 5000});
    await drawer.getByRole('button', {name: /First chapter/}).click();
    assert.equal(await page.locator('output').getAttribute('data-chapter'), 'chapter-one');
  } finally {
    await browser.close();
    await server.close();
  }
});

test('nested contents can be expanded and navigate to a fragment without opening the parent', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?contents&nestedContents&theme=DARK`);
    const drawer = page.locator('[data-komelia-reader-contents]');
    const expand = drawer.getByRole('button', {name: 'Expand Parent chapter'});
    await expand.waitFor({timeout: 5000});
    assert.equal(await expand.getAttribute('aria-expanded'), 'false');
    assert.equal(await drawer.getByRole('button', {name: 'Nested fragment'}).count(), 0);
    assert.equal(await drawer.locator('[aria-current="location"]').count(), 1);
    const listBounds = await drawer.locator('.navigation-chapters').boundingBox();
    const labelBounds = await drawer.getByText('Parent chapter', {exact: true}).boundingBox();
    assert.ok(labelBounds.x >= listBounds.x, JSON.stringify({listBounds, labelBounds}));
    await expand.focus();
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('output').getAttribute('data-chapter'), null);
    assert.equal(await drawer.getByRole('button', {name: 'Collapse Parent chapter'}).getAttribute('aria-expanded'), 'true');
    assert.equal(await drawer.getByRole('button', {name: 'Section group', exact: true}).count(), 0);
    await drawer.getByRole('button', {name: 'Expand Section group'}).click();
    assert.equal(await drawer.getByRole('button', {name: 'Deep fragment'}).isVisible(), true);
    const expandedListBounds = await drawer.locator('.navigation-chapters').boundingBox();
    const expandedLabelBounds = await drawer.getByText('Parent chapter', {exact: true}).boundingBox();
    assert.ok(expandedLabelBounds.x >= expandedListBounds.x, JSON.stringify({expandedListBounds, expandedLabelBounds}));
    await drawer.getByRole('button', {name: 'Nested fragment'}).click();
    assert.equal(await page.locator('output').getAttribute('data-chapter'), 'chapter-two#selected');
    assert.equal(await page.locator('output').textContent(), 'closed-contents');
  } finally {
    await browser.close();
    await server.close();
  }
});

for (const viewport of [{width: 390, height: 844}, {width: 1280, height: 800}]) {
test(`contents keeps the focus ring above its summary at ${viewport.width}px`, async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport});
    await page.goto(`${server.url}?contents&theme=DARK`);
    const drawer = page.locator('[data-komelia-reader-contents]');
    await drawer.waitFor();
    await page.waitForFunction(() => !document.querySelector('[data-komelia-reader-contents]')?.getAnimations({subtree: true}).some(a => a.playState === 'running'));
    await drawer.getByRole('combobox').focus();
    const selector = await drawer.getByRole('combobox').boundingBox();
    const summary = await drawer.locator('.navigation-summary').boundingBox();
    assert.ok(summary.y >= selector.y + selector.height + 4, JSON.stringify({selector, summary}));
    assert.equal(await drawer.locator('.navigation-chapters').evaluate(el => getComputedStyle(el).overflowX), 'hidden');
  } finally {
    await browser.close();
    await server.close();
  }
});
}

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

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

test('Mac TTU toolbar is centered and retains its reader actions', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport:{width:960,height:720}});
    await page.goto(server.url);
    const toolbar = page.getByRole('navigation', {name:'Reader toolbar'});
    await toolbar.waitFor();
    await page.evaluate(() => {
      const host = document.getElementById('app');
      host.style.width = 'calc(100% - 17px)';
      host.style.transform = 'translateZ(0)';
      document.documentElement.dataset.komeliaPlatform = 'macos';
    });
    const bounds = await toolbar.boundingBox();
    assert.equal(bounds.width, 350);
    assert.ok(Math.abs(bounds.x + bounds.width / 2 - 480) <= 1);
    for (const name of ['Close Book', 'Open Table of Contents', 'Reader settings', 'More options']) {
      const button = toolbar.getByRole('button', {name, exact:true});
      const size = await button.boundingBox();
      assert.ok(size.width >= 40 && size.height >= 40, name);
    }
    const settings = toolbar.getByRole('button', {name:'Reader settings'});
    await settings.focus();
    assert.notEqual(await settings.evaluate(node => getComputedStyle(node).outlineStyle), 'none');
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('output').textContent(), 'settings');
  } finally {
    await browser.close();
    await server.close();
  }
});

test('Android TTU uses the current paper colors and retains a single toolbar row', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-toolbar.bootstrap.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport:{width:390,height:844}, hasTouch:true, isMobile:true});
    await page.goto(server.url);
    const toolbar = page.getByRole('navigation', {name:'Reader toolbar'});
    await toolbar.waitFor();
    const initial = await toolbar.evaluate(node => getComputedStyle(node).backgroundColor);
    await page.evaluate(() => {
      document.documentElement.dataset.komeliaPlatform = 'android';
      document.body.style.setProperty('--komelia-reader-paper', '#faf4e8');
    });
    assert.deepEqual(await toolbar.boundingBox(), {x:12,y:10,width:366,height:48});
    const paperColor = await toolbar.evaluate(node => getComputedStyle(node).backgroundColor);
    assert.notEqual(paperColor, initial);
    await page.evaluate(() => {
      document.body.style.setProperty('--komelia-reader-paper', '#23272a');
      document.body.style.setProperty('--komelia-reader-ink', '#dedede');
    });
    assert.notEqual(await toolbar.evaluate(node => getComputedStyle(node).backgroundColor), paperColor);
    assert.equal(await toolbar.evaluate(node => getComputedStyle(node).color), 'rgb(222, 222, 222)');
    const settings = toolbar.getByRole('button', {name:'Reader settings'});
    assert.equal((await settings.boundingBox()).height, 48);
  } finally {
    await browser.close();
    await server.close();
  }
});

test('Android TTU keeps every auto-scroll control reachable on medium viewports', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-edge.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport:{width:640,height:844}, hasTouch:true, isMobile:true});
    await page.goto(`${server.url}?footer&android&hideCharacterCounter`);
    const footer = page.locator('[data-komelia-reader-progress]');
    await footer.waitFor();
    for (const width of [640, 700, 839]) {
      await page.setViewportSize({width, height:844});
      const bounds = await footer.boundingBox();
      for (const [name, result] of [
        ['Decrease auto-scroll speed', 'speed'],
        ['Toggle auto-scroll', 'auto-scroll'],
        ['Increase auto-scroll speed', 'speed'],
      ]) {
        const control = footer.getByRole('button', {name});
        const button = await control.boundingBox();
        assert.ok(button.x >= bounds.x && button.x + button.width <= bounds.x + bounds.width, `${name} at ${width}px`);
        await control.click();
        assert.equal(await page.locator('output').textContent(), result);
      }
    }
  } finally {
    await browser.close();
    await server.close();
  }
});

test('Android TTU paper progress follows the character counter preference', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-edge.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport:{width:390,height:844}, hasTouch:true, isMobile:true});
    const summary = page.locator('[data-komelia-reader-progress-summary]');
    await page.goto(`${server.url}?footer&android`);
    await summary.waitFor();
    assert.match((await summary.innerText()).trim(), /10 \/ 100 10\.00%/);
    await page.goto(`${server.url}?footer&android&hideCharacterCounter`);
    await summary.waitFor();
    assert.equal((await summary.innerText()).trim(), 'Synthetic chapter · 10%');
  } finally {
    await browser.close();
    await server.close();
  }
});

test('Android paper controls blur the page only in supported Glass mode', async () => {
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport:{width:390,height:844}, hasTouch:true, isMobile:true});
    await page.setContent(`<!doctype html>
      <html data-komelia-platform="android" data-komelia-surface="glass" data-komelia-theme="LIGHT">
        <head><meta name="viewport" content="width=device-width,initial-scale=1"><style>${chromeStyles}</style></head>
        <body>
          <header data-komelia-chrome data-komelia-toolbar="top">Reader toolbar</header>
          <footer data-komelia-chrome data-komelia-reader-progress>Progress</footer>
        </body>
      </html>`);
    const styles = () => page.evaluate(() => {
      const canvas = document.createElement('canvas');
      canvas.width = canvas.height = 1;
      const pixel = canvas.getContext('2d');
      return [...document.querySelectorAll('[data-komelia-chrome]')].map(control => {
        const style = getComputedStyle(control);
        pixel.clearRect(0, 0, 1, 1);
        pixel.fillStyle = style.backgroundColor;
        pixel.fillRect(0, 0, 1, 1);
        return {
          filter:style.backdropFilter,
          opacity:pixel.getImageData(0, 0, 1, 1).data[3],
          border:style.borderTopWidth,
          shadow:style.boxShadow,
        };
      });
    });
    for (const style of await styles()) {
      assert.match(style.filter, /blur\(/);
      assert.ok(style.opacity > 0 && style.opacity < 255);
      assert.equal(style.border, '0px');
      assert.equal(style.shadow, 'none');
    }
    for (const surface of ['classic', 'solid']) {
      await page.evaluate(value => { document.documentElement.dataset.komeliaSurface = value; }, surface);
      assert.deepEqual(await styles(), Array(2).fill({filter:'none',opacity:255,border:'0px',shadow:'none'}));
    }
    await page.evaluate(() => { document.documentElement.dataset.komeliaSurface = 'glass'; });
    const session = await page.context().newCDPSession(page);
    await session.send('Emulation.setEmulatedMedia', {features:[{name:'prefers-reduced-transparency',value:'reduce'}]});
    assert.equal(await page.evaluate(() => matchMedia('(prefers-reduced-transparency: reduce)').matches), true);
    assert.deepEqual(await styles(), Array(2).fill({filter:'none',opacity:255,border:'0px',shadow:'none'}));
  } finally {
    await browser.close();
  }
});

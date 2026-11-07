import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {createServer} from 'vite';
import {startReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');

test('native exit saves the current bookmark before closing the reader', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-native-exit.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(server.url);
    await page.locator('.book-content p').waitFor();
    await page.waitForFunction(() => window.exitTrace.includes('wait'), undefined, {timeout: 3000});
    await page.evaluate(() => window.requestNativeExit());
    await page.waitForFunction(() => window.exitTrace.includes('bookmark-start'));
    assert.equal(await page.evaluate(() => window.exitTrace.includes('close')), false);
    await page.evaluate(() => window.finishBookmark());
    await page.waitForFunction(() => window.exitTrace.includes('close'));
    assert.deepEqual(await page.evaluate(() => window.exitTrace.slice(0, 3)), ['wait', 'bookmark-start', 'close']);
  } finally {
    await browser.close();
    await server.close();
  }
});


test('native exit keeps manual bookmark behavior', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-native-exit.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?manual`);
    await page.locator('.book-content p').waitFor();
    await page.waitForFunction(() => window.exitTrace.includes('wait'));
    await page.evaluate(() => window.requestNativeExit());
    await page.waitForFunction(() => window.exitTrace.includes('close'));
    assert.equal(await page.evaluate(() => window.exitTrace.includes('bookmark-start')), false);
  } finally {
    await browser.close();
    await server.close();
  }
});

test('a failed bookmark keeps the reader open for retry', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-native-exit.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(server.url);
    await page.locator('.book-content p').waitFor();
    await page.waitForFunction(() => window.exitTrace.includes('wait'));
    await page.evaluate(() => window.requestNativeExit());
    await page.waitForFunction(() => window.exitTrace.includes('bookmark-start'));
    await page.evaluate(() => window.failBookmark());
    await page.waitForFunction(() => window.exitDialogCount() > 0 && window.exitTrace.filter(value => value === 'wait').length === 2);
    assert.equal(await page.evaluate(() => window.exitTrace.includes('close')), false);
    assert.equal(await page.evaluate(() => window.exitDialogMessage()), 'Could not save your reading position. Try again.');
    await page.evaluate(() => { window.dismissExitError(); window.requestNativeExit(); });
    await page.waitForFunction(() => window.exitTrace.filter(value => value === 'bookmark-start').length === 2);
    await page.evaluate(() => window.finishBookmark());
    await page.waitForFunction(() => window.exitTrace.includes('close'));
  } finally {
    await browser.close();
    await server.close();
  }
});

test('native exit waits for an earlier bookmark before saving the latest position', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-native-exit.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(server.url);
    await page.locator('.book-content p').waitFor();
    await page.waitForFunction(() => window.exitTrace.includes('wait'));
    await page.keyboard.press('b');
    await page.waitForFunction(() => window.exitTrace.includes('bookmark-start'));
    await page.evaluate(() => window.requestNativeExit());
    await page.waitForTimeout(200);
    assert.equal(await page.evaluate(() => window.exitTrace.filter(value => value === 'bookmark-start').length), 1);
    await page.evaluate(() => window.finishBookmark());
    await page.waitForFunction(() => window.exitTrace.filter(value => value === 'bookmark-start').length === 2);
    assert.equal(await page.evaluate(() => window.exitTrace.includes('close')), false);
    await page.evaluate(() => window.finishBookmark());
    await page.waitForFunction(() => window.exitTrace.includes('close'));
  } finally {
    await browser.close();
    await server.close();
  }
});


test('native save failures show a Chinese message without internal details', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-native-exit.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?locale=zh-CN`);
    await page.locator('.book-content p').waitFor();
    await page.waitForFunction(() => window.exitTrace.includes('wait'));
    await page.evaluate(() => window.requestNativeExit());
    await page.waitForFunction(() => window.exitTrace.includes('bookmark-start'));
    await page.evaluate(() => window.failBookmark());
    await page.waitForFunction(() => window.exitDialogMessage().length > 0);
    assert.equal(await page.evaluate(() => window.exitDialogMessage()), '无法保存阅读进度，请重试。');
    assert.equal(await page.evaluate(() => window.exitTrace.includes('close')), false);
  } finally {
    await browser.close();
    await server.close();
  }
});


test('after a save failure the reader can explicitly exit without saving', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-native-exit.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(server.url);
    await page.locator('.book-content p').waitFor();
    await page.waitForFunction(() => window.exitTrace.includes('wait'));
    await page.evaluate(() => window.requestNativeExit());
    await page.waitForFunction(() => window.exitTrace.includes('bookmark-start'));
    await page.evaluate(() => window.failBookmark());
    await page.waitForFunction(() => window.exitDialogMessage().length > 0);
    await page.evaluate(() => window.exitWithoutSaving());
    await page.waitForFunction(() => window.exitTrace.includes('close'), undefined, {timeout: 3000});
  } finally {
    await browser.close();
    await server.close();
  }
});

test('a close failure remains visible and can be retried', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-native-exit.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    page.setDefaultTimeout(5000);
    await page.goto(server.url);
    await page.locator('.book-content p').waitFor();
    await page.waitForFunction(() => window.exitTrace.includes('wait'));
    await page.evaluate(() => window.requestNativeExit());
    await page.waitForFunction(() => window.exitTrace.includes('bookmark-start'));
    await page.evaluate(() => window.failBookmark());
    await page.waitForFunction(() => window.exitDialogMessage().length > 0);
    await page.evaluate(() => { window.setCloseFailure(true); window.exitWithoutSaving(); });
    await page.waitForFunction(() => window.exitDialogMessage() === 'Could not close the book. Try again.');
    await page.evaluate(() => { window.setCloseFailure(false); window.retryExit(); });
    await page.waitForFunction(() => window.exitTrace.filter(value => value === 'close').length === 2);
    assert.equal(await page.evaluate(() => window.exitTrace.filter(value => value === 'bookmark-start').length), 1);
  } finally {
    await browser.close();
    await server.close();
  }
});

test('a close failure after saving does not report a save failure', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-native-exit.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    page.setDefaultTimeout(5000);
    await page.goto(server.url);
    await page.locator('.book-content p').waitFor();
    await page.waitForFunction(() => window.exitTrace.includes('wait'));
    await page.evaluate(() => { window.setCloseFailure(true); window.requestNativeExit(); });
    await page.waitForFunction(() => window.exitTrace.includes('bookmark-start'));
    await page.evaluate(() => window.finishBookmark());
    await page.waitForFunction(() => window.exitDialogMessage() === 'Could not close the book. Try again.');
    await page.evaluate(() => { window.setCloseFailure(false); window.retryExit(); });
    await page.waitForFunction(() => window.exitTrace.filter(value => value === 'close').length === 2);
    assert.equal(await page.evaluate(() => window.exitTrace.filter(value => value === 'bookmark-start').length), 1);
  } finally {
    await browser.close();
    await server.close();
  }
});

test('save failure actions are available and translated', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-exit-dialog.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 390, height: 844}});
    await page.goto(`${server.url}?locale=zh-CN`);
    await page.getByRole('button', {name: '继续阅读'}).waitFor();
    await page.getByRole('button', {name: '重试'}).click();
    assert.equal(await page.locator('output').textContent(), 'retry');
    await page.getByRole('button', {name: '不保存退出'}).click();
    assert.equal(await page.locator('output').textContent(), 'exit');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 390);
    await page.goto(server.url);
    await page.getByRole('button', {name: 'Keep reading'}).waitFor();
    await page.getByRole('button', {name: 'Retry'}).click();
    assert.equal(await page.locator('output').textContent(), 'retry');
    await page.getByRole('button', {name: 'Exit without saving'}).click();
    assert.equal(await page.locator('output').textContent(), 'exit');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth), 390);
  } finally {
    await browser.close();
    await server.close();
  }
});

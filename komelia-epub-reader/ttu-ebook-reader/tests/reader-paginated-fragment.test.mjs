import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {createServer} from 'vite';
import {startReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');

test('paginated chapter navigation opens a fragment in another file and the current file', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-paginated-fragment.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 360, height: 800}});
    await page.goto(server.url);
    await page.locator('#first').waitFor();
    await page.evaluate(() => window.navigateTestChapter('chapter-two#selected'));
    await page.locator('#selected').waitFor();
    await page.waitForFunction(() => {
      const bounds = document.querySelector('#selected')?.getBoundingClientRect();
      return bounds && bounds.right > 0 && bounds.left < innerWidth && bounds.bottom > 0 && bounds.top < innerHeight;
    }, undefined, {timeout: 5000});
    const selected = await page.locator('#selected').boundingBox();
    assert.ok(selected.x < 360 && selected.x + selected.width > 0, JSON.stringify(selected));

    await page.evaluate(() => window.navigateTestChapter('chapter-two'));
    await page.waitForFunction(() => document.querySelector('#selected').getBoundingClientRect().left > innerWidth);
    await page.evaluate(() => window.navigateTestChapter('chapter-two#selected'));
    await page.waitForFunction(() => {
      const bounds = document.querySelector('#selected').getBoundingClientRect();
      return bounds.right > 0 && bounds.left < innerWidth;
    }, undefined, {timeout: 5000});
    await page.evaluate(() => window.navigateTestChapter('chapter-two#selected%20part'));
    await page.waitForFunction(() => {
      const bounds = document.getElementById('selected part').getBoundingClientRect();
      return bounds.right > 0 && bounds.left < innerWidth;
    }, undefined, {timeout: 5000});
    await page.evaluate(() => window.navigateTestChapter('chapter-two#missing'));
    await page.waitForFunction(() => document.querySelector('#selected').getBoundingClientRect().left > innerWidth);
    await page.evaluate(() => window.navigateTestChapter('selected%20part'));
    await page.waitForFunction(() => {
      const bounds = document.getElementById('selected part').getBoundingClientRect();
      return bounds.right > 0 && bounds.left < innerWidth;
    }, undefined, {timeout: 5000});
    await page.evaluate(() => window.navigateTestChapter('chapter-two'));
    await page.waitForFunction(() => document.getElementById('selected part').getBoundingClientRect().left > innerWidth);
    await page.evaluate(() => window.navigateTestChapter('chapter-two#empty-target'));
    await page.waitForFunction(() => {
      const bounds = document.getElementById('selected part').getBoundingClientRect();
      return bounds.right > 0 && bounds.left < innerWidth;
    }, undefined, {timeout: 5000});
    await page.evaluate(() => window.navigateTestChapter('chapter-two#end-target'));
    await page.waitForFunction(() => {
      const bounds = document.getElementById('last-line').getBoundingClientRect();
      return bounds.right > 0 && bounds.left < innerWidth && bounds.bottom > 0 && bounds.top < innerHeight;
    }, undefined, {timeout: 5000});
  } finally {
    await browser.close();
    await server.close();
  }
});

test('a later navigation cancels a pending fragment jump', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-paginated-fragment.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 360, height: 800}});
    await page.goto(server.url);
    await page.locator('#first').waitFor();
    await page.evaluate(() => {
      window.navigateTestChapter('chapter-two#selected');
      window.navigateTestChapter('chapter-one');
    });
    await page.waitForTimeout(300);
    assert.equal(await page.locator('#first').isVisible(), true);
    assert.equal(await page.locator('#selected').count(), 0);
  } finally {
    await browser.close();
    await server.close();
  }
});

test('paginated vertical writing opens the selected fragment', async () => {
  const server = await startReaderToolbarServer(createServer, 'tests/reader-paginated-fragment.fixture.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage({viewport: {width: 360, height: 800}});
    await page.goto(`${server.url}?vertical`);
    await page.locator('#first').waitFor();
    await page.evaluate(() => window.navigateTestChapter('chapter-two#selected'));
    await page.locator('#selected').waitFor();
    await page.waitForFunction(() => {
      const bounds = document.querySelector('#selected').getBoundingClientRect();
      return bounds.right > 0 && bounds.left < innerWidth && bounds.bottom > 0 && bounds.top < innerHeight;
    }, undefined, {timeout: 5000});
  } finally {
    await browser.close();
    await server.close();
  }
});

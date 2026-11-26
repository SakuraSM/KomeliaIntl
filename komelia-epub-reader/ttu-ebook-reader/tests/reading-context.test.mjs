import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {after, before, test} from 'node:test';
import {createServer} from 'vite';
import {startReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');
let server, browser;
before(async () => {
  server = await startReaderToolbarServer(createServer, 'tests/reading-context.fixture.ts');
  browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
});
after(async () => { await browser?.close(); await server?.close(); });

test('find base text across ruby and locate its reading without changing the markup', async () => {
  const page = await browser.newPage();
  try {
    await page.goto(server.url);
    const actual = await page.evaluate(async () => {
      const {buildReaderSearchIndex, searchReaderIndex, renderReaderMarks} = await import('/src/lib/functions/reader-interactions.ts');
      const element = document.createElement('div');
      element.innerHTML = '<div id="test">𠮟る <ruby>東<rt>とう</rt></ruby><ruby>京<rt>きょう</rt></ruby>駅</div>';
      const section = {reference:'test', label:'test', startCharacter:0, characters:5};
      const index = buildReaderSearchIndex(element.innerHTML, [section]);
      const inspect = query => {
        const hits = searchReaderIndex(index, query);
        renderReaderMarks(element, [], hits[0], [section]);
        return [hits.length, [...element.querySelectorAll('mark')].map(mark => mark.textContent).join(''),
          element.querySelectorAll('ruby').length, element.querySelectorAll('rt').length];
      };
      const {readingOffset} = await import('/src/lib/functions/epub-search-text.ts');
      const station = searchReaderIndex(index, '駅')[0];
      return {hits: [inspect('東京駅'), inspect('とうきょう'), inspect('駅')],
        readingPosition: readingOffset(index[0], station.startCharacter)};
    });
    assert.deepEqual(actual, {hits: [[1,'東京駅',2,2], [1,'東京',2,2], [1,'駅',2,2]], readingPosition: 4});
  } finally { await page.close(); }
});

for (const mode of ['continuous','paginated']) test(mode + ' notes keep the source position and consume Back', async () => {
  const page = await browser.newPage({viewport:{width:412,height:860}});
  try {
    await page.goto(server.url + '?mode=' + mode);
    const source = page.locator('.book-content a').first();
    await source.waitFor({timeout:10000});
    const top = () => source.evaluate(el => Math.round(el.getBoundingClientRect().top));
    const before = await top();
    for (const [index,expected] of ['Same chapter note','Other chapter note'].entries()) {
      const trigger = page.locator('.book-content a').nth(index);
      await trigger.click();
      const dialog = page.getByRole('dialog');
      await dialog.getByText(expected, {exact:false}).waitFor({timeout:2000});
      if (index === 0) await page.keyboard.press('Escape');
      else await page.evaluate(() => window.requestTestBack());
      await dialog.waitFor({state:'hidden',timeout:2000});
      assert.equal(await top(), before);
      assert.equal(await trigger.evaluate(el => el === document.activeElement), true);
      assert.equal(await page.getAttribute('body','data-closed'), null);
    }
  } finally { await page.close(); }
});

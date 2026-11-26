import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {test} from 'node:test';
import {createServer} from 'vite';
import {startReaderToolbarServer} from '../../shared/tests/reader-toolbar-server.mjs';

const {chromium} = createRequire(import.meta.url)('playwright');

test('footnotes intercept only publication references and discard results from retired frames', async () => {
  const server = await startReaderToolbarServer(createServer, 'src/functions/footnote.ts');
  const browser = await chromium.launch({executablePath: process.env.CHROME_BIN});
  try {
    const page = await browser.newPage();
    await page.goto(server.url);
    const result = await page.evaluate(async () => {
      const {listenForFootnotes} = await import('/src/functions/footnote.ts');
      const frame = document.createElement('iframe');
      document.body.append(frame);
      const chapter = location.origin + '/api/v1/books/one/resource/text/open.xhtml';
      frame.contentDocument.head.innerHTML = '<base href="' + chapter + '">';
      frame.contentDocument.body.innerHTML = '<a href="#n" epub:type="noteref">1</a>' +
        '<a href="../notes/end.xhtml#n" role="doc-noteref"><sup>2</sup></a>' +
        '<a href="/api/v1/books/two/resource/end.xhtml#n" role="doc-noteref">external</a>' +
        '<a href="#ordinary">regular link</a><aside id="n">Local note</aside>';
      const output = [];
      let resolve;
      const stop = listenForFootnotes(frame.contentWindow, {
        bookId:'one', open:link=>output.push('open ' + link.textContent),
        show:text=>output.push(text), unavailable:()=>output.push('missing'),
        getContent:href=>{output.push(new URL(href).pathname); return new Promise(done=>{resolve=done})},
      });
      frame.contentDocument.addEventListener('click', event=>{output.push('normal'); event.preventDefault()});
      const links = frame.contentDocument.querySelectorAll('a');
      links[0].click();
      links[1].firstElementChild.click();
      resolve('<aside id="n" epub:type="endnote"><p>Other note</p><a role="doc-backlink">Return</a></aside>');
      await Promise.resolve();
      links[2].click();
      links[3].click();
      links[1].click();
      stop();
      resolve('<p id="n">Stale</p>');
      await Promise.resolve();
      return output;
    });
    const resource = '/api/v1/books/one/resource/notes/end.xhtml';
    assert.deepEqual(result, ['open 1','Local note','open 2',resource,'Other note','normal','normal','open 2',resource]);
  } finally { await browser.close(); await server.close(); }
});

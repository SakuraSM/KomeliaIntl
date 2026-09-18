import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import { compile } from 'sass';
import postcss from 'postcss';
import tailwindcss from 'tailwindcss';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');
const styles = compile(new URL('../src/app.scss', import.meta.url).pathname).css;
const { css } = await postcss([tailwindcss({ content: [{
  raw: '<body class="h-full w-full reader-horizontal-mode"><div id="app"><article class="book-content--writing-horizontal-rl"></article></div></body>',
  extension: 'html',
}] })]).process(styles, { from: undefined });

test('continuous reader scroll stays on the document used by bookmark calculation', async () => {
  const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN });
  try {
    const page = await browser.newPage({ viewport: { width: 412, height: 800 } });
    // Same html/body/#app sizing as ttsu.html; compile the production overflow rules.
    await page.setContent(`<!doctype html><html class="h-full w-full"><head><style>${css}
      p { margin:24px; line-height:32px }
    </style></head><body class="h-full w-full reader-horizontal-mode"><div id="app" class="h-full w-full">
      <article class="book-content--writing-horizontal-rl">
        ${Array.from({ length: 80 }, (_, index) => `<p>Reading paragraph ${index} with enough text to scroll.</p>`).join('')}
      </article></div></body></html>`);
    await page.mouse.move(200, 400);
    await page.mouse.wheel(0, 700);
    await page.waitForFunction(() => window.scrollY > 0 || document.getElementById('app').scrollTop > 0);
    const position = await page.evaluate(() => ({
      document: document.documentElement.scrollTop,
      window: window.scrollY,
      app: document.getElementById('app').scrollTop,
      horizontalOverflow: document.documentElement.scrollWidth > window.innerWidth,
    }));
    assert.ok(position.document > 0, JSON.stringify(position));
    assert.equal(position.window, position.document);
    assert.equal(position.app, 0);
    assert.equal(position.horizontalOverflow, false);
  } finally {
    await browser.close();
  }
});

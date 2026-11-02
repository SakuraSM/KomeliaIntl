import {readFile} from 'node:fs/promises';

/** Compare frozen output with its approved reference, without changing either PNG. */
export async function saveVisualComparison({page, referencePath, implementationPath, outputPath, caption, panelTop = 274}) {
  const source = (await readFile(referencePath)).toString('base64');
  const rendered = (await readFile(implementationPath)).toString('base64');
  await page.setViewportSize({width: 828, height: 660});
  await page.setContent(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
    <style>body{margin:0;padding:12px;background:#edf2f7;color:#13283e;font:14px sans-serif;box-sizing:border-box}h1{font-size:18px;margin:0 0 8px}.row{display:flex;gap:16px}.frame{width:390px;height:${844 - panelTop}px;overflow:hidden;border-radius:16px;background:#09121e}.frame img{display:block;width:390px;height:844px;transform:translateY(-${panelTop}px)}p{margin:0 0 8px}</style></head>
    <body><h1>${caption}</h1><div class="row"><div><p>Approved reference</p><div class="frame"><img src="data:image/png;base64,${source}"></div></div><div><p>Production component</p><div class="frame"><img src="data:image/png;base64,${rendered}"></div></div></div></body></html>`);
  await page.evaluate(() => Promise.all([...document.images].map(image => image.decode())));
  await page.screenshot({path: outputPath});
}

import {readFile} from 'node:fs/promises'
import {test} from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'

const source = await readFile(new URL('../src/functions/publicationContent.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.ESNext}}).outputText
const {loadPublicationContent} = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const base = 'https://reader.example/api/v1/books/test/resource/'

test('missing, rejected and hanging chapter bodies reach the reader failure handler', async () => {
  const failures = []
  const input = { href: base + 'chapter.xhtml#section', bookId: 'test', onFailure: error => failures.push(error) }
  assert.equal(await loadPublicationContent({...input, load: async () => undefined}), '')
  const networkFailure = new Error('offline')
  assert.equal(await loadPublicationContent({...input, load: async () => { throw networkFailure }}), '')
  assert.equal(await loadPublicationContent({...input, load: () => new Promise(() => {}), timeoutMs: 5}), '')
  assert.match(failures[0].message, /unavailable/)
  assert.equal(failures[1], networkFailure)
  assert.match(failures[2].message, /timed out/)
})

test('valid chapters pass through and missing optional assets do not report fatal failures', async () => {
  const onFailure = error => assert.fail(error)
  const html = '<html><body>chapter</body></html>'
  assert.equal(await loadPublicationContent({
    href: base + 'chapter.xhtml', bookId: 'test', load: async () => html, onFailure,
  }), html)
  assert.equal(await loadPublicationContent({
    href: base + 'style.css', bookId: 'test', load: async () => undefined, onFailure,
  }), undefined)
})

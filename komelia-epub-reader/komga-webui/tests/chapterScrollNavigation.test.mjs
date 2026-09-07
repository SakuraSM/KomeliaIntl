import {readFile} from 'node:fs/promises'
import {test} from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'

const source = await readFile(new URL('../src/functions/chapterScrollNavigation.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.ESNext}}).outputText
const {setupChapterScrollNavigation} = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

function reader(t, scrollTarget) {
  t.mock.timers.enable({apis: ['setTimeout']})
  const document = new EventTarget()
  let isAtEnd = false
  let navigations = 0
  const cleanup = setupChapterScrollNavigation({
    document, scrollTarget, isEnabled: () => true,
    navigateAtBoundary: () => { if (!isAtEnd) return false; navigations++; return true },
  })
  t.after(cleanup)
  function touch(type, coordinates, active = coordinates) {
    coordinates.forEach(point => { point.screenX ??= point.clientX; point.screenY ??= point.clientY })
    const event = new Event(type, {cancelable: true})
    Object.assign(event, {touches: active, changedTouches: coordinates})
    document.dispatchEvent(event)
  }
  return {
    document, cleanup, touch,
    get navigations() { return navigations },
    end: () => { isAtEnd = true },
    start: () => touch('touchstart', [{clientX: 100, clientY: 500}]),
    finish: () => touch('touchend', [{clientX: 100, clientY: 100}], []),
    pinch: () => touch('touchmove', [{clientX: 100, clientY: 500}, {clientX: 200, clientY: 300}]),
  }
}

test('slow drag longer than 650ms crosses chapter boundary', (t) => {
  const view = reader(t)
  view.end(); view.start(); t.mock.timers.tick(1100); view.finish()
  assert.equal(view.navigations, 1)
})

test('iframe motion does not cancel a vertical screen gesture', (t) => {
  const view = reader(t)
  view.start(); view.end()
  view.touch('touchend', [{clientX: 100, clientY: 500, screenX: 100, screenY: 100}], [])
  assert.equal(view.navigations, 1)
})

test('momentum reaches boundary after touchend and advances only once', (t) => {
  const view = reader(t)
  view.start(); view.finish()
  assert.equal(view.navigations, 0)
  view.end(); view.document.dispatchEvent(new Event('scroll'))
  t.mock.timers.tick(120)
  assert.equal(view.navigations, 1)
  view.document.dispatchEvent(new Event('scrollend'))
  t.mock.timers.tick(2000)
  assert.equal(view.navigations, 1)
})

test('initial short chapter and programmatic scroll never auto skip', (t) => {
  const view = reader(t)
  view.end(); view.document.dispatchEvent(new Event('scroll'))
  view.document.dispatchEvent(new Event('scrollend'))
  t.mock.timers.tick(2000)
  assert.equal(view.navigations, 0)
})

test('outer reader container momentum is observed after iframe touchend', (t) => {
  const wrapper = new EventTarget()
  const view = reader(t, wrapper)
  view.start(); view.finish()
  t.mock.timers.tick(200)
  view.end(); wrapper.dispatchEvent(new Event('scroll'))
  t.mock.timers.tick(120)
  assert.equal(view.navigations, 1)
  view.cleanup()
  view.start(); view.finish(); wrapper.dispatchEvent(new Event('scrollend'))
  assert.equal(view.navigations, 1)
})

test('pinch and cancelled gestures cannot turn chapters', (t) => {
  const view = reader(t)
  view.end(); view.start(); view.pinch(); view.finish()
  assert.equal(view.navigations, 0)
  view.start(); view.document.dispatchEvent(new Event('touchcancel')); view.finish()
  assert.equal(view.navigations, 0)
})

test('cleanup cancels pending momentum navigation', (t) => {
  const view = reader(t)
  view.start(); view.finish(); view.cleanup(); view.end()
  view.document.dispatchEvent(new Event('scrollend'))
  t.mock.timers.tick(2000)
  assert.equal(view.navigations, 0)
})

test('expired gesture cannot advance on a later layout scroll', (t) => {
  const view = reader(t)
  view.start(); view.finish(); t.mock.timers.tick(2000); view.end()
  view.document.dispatchEvent(new Event('scrollend'))
  assert.equal(view.navigations, 0)
})

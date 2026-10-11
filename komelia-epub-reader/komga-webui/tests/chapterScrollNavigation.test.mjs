import {readFile} from 'node:fs/promises'
import {test} from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'

const source = await readFile(new URL('../src/functions/chapterScrollNavigation.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.ESNext}}).outputText
const {setupChapterScrollNavigation, navigateChapterAtScrollBoundary} = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)

test('DOM chapter end advances immediately even when SDK atEnd is stale', () => {
  let next = 0
  const sdk = {atEnd: false, atStart: false, nextResource: () => next++, previousResource: () => assert.fail('wrong direction')}
  assert.equal(navigateChapterAtScrollBoundary(-100, sdk, {scrollTop: 900, clientHeight: 100, scrollHeight: 1000}), true)
  assert.equal(next, 1)
})

test('DOM chapter start goes back immediately even when SDK atStart is stale', () => {
  let previous = 0
  const sdk = {atEnd: false, atStart: false, nextResource: () => assert.fail('wrong direction'), previousResource: () => previous++}
  assert.equal(navigateChapterAtScrollBoundary(100, sdk, {scrollTop: 0, clientHeight: 100, scrollHeight: 1000}), true)
  assert.equal(previous, 1)
})

test('DOM interior cannot skip a chapter even if SDK reports a boundary', () => {
  const sdk = {atEnd: true, atStart: true, nextResource: () => assert.fail('skip'), previousResource: () => assert.fail('skip')}
  const geometry = {scrollTop: 300, clientHeight: 100, scrollHeight: 1000}
  assert.equal(navigateChapterAtScrollBoundary(-100, sdk, geometry), false)
  assert.equal(navigateChapterAtScrollBoundary(100, sdk, geometry), false)
  assert.equal(navigateChapterAtScrollBoundary(0, sdk, geometry), false)
})

test('missing DOM uses SDK boundary without reversing the gesture', () => {
  let next = 0
  const sdk = {atEnd: true, atStart: false, nextResource: () => next++, previousResource: () => assert.fail('wrong direction')}
  assert.equal(navigateChapterAtScrollBoundary(100, sdk), false)
  assert.equal(navigateChapterAtScrollBoundary(-100, sdk), true)
  assert.equal(next, 1)
})

test('momentum to a short chapter with stale SDK flags advances once, then disarms', (t) => {
  t.mock.timers.enable({apis: ['setTimeout']})
  const document = new EventTarget()
  const wrapper = new EventTarget()
  const geometry = {scrollTop: 300, clientHeight: 100, scrollHeight: 1000}
  let next = 0
  const sdk = {atEnd: false, atStart: false, nextResource: () => { next++; geometry.scrollTop = 0; geometry.scrollHeight = 100 }, previousResource: () => assert.fail('wrong direction')}
  const cleanup = setupChapterScrollNavigation({document, scrollTarget: wrapper, isEnabled: () => true,
    navigateAtBoundary: delta => navigateChapterAtScrollBoundary(delta, sdk, geometry)})
  t.after(cleanup)
  for (const [type, y, touches] of [['touchstart', 500, true], ['touchend', 100, false]]) {
    const event = new Event(type, {cancelable: true})
    const touch = {screenX: 100, screenY: y}
    Object.assign(event, {touches: touches ? [touch] : [], changedTouches: [touch]})
    document.dispatchEvent(event)
  }
  assert.equal(next, 0)
  geometry.scrollTop = 900
  wrapper.dispatchEvent(new Event('scrollend'))
  assert.equal(next, 1)
  wrapper.dispatchEvent(new Event('scrollend'))
  t.mock.timers.tick(2000)
  assert.equal(next, 1)
})

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

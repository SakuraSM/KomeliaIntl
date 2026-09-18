import {readFile} from 'node:fs/promises'
import {test} from 'node:test'
import assert from 'node:assert/strict'
import ts from 'typescript'

const source = await readFile(new URL('../src/functions/bookSiblingState.ts', import.meta.url), 'utf8')
const compiled = ts.transpileModule(source, {compilerOptions: {module: ts.ModuleKind.ESNext}}).outputText
const {BookSiblingState} = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`)
const settle = () => new Promise(resolve => setImmediate(resolve))
function deferred() {
  let resolve, reject
  const promise = new Promise((accept, fail) => { resolve = accept; reject = fail })
  return {promise, resolve, reject}
}
function reader(load) {
  let state
  const opened = [], failed = [], requests = []
  let closes = 0
  const siblings = new BookSiblingState({
    load: (id, direction) => { requests.push([id, direction]); return load(id, direction) },
    changed: next => { state = next },
    open: book => { opened.push(book.id); siblings.reset(book.id) },
    close: () => { closes++ }, failed: direction => failed.push(direction),
  })
  return {siblings, opened, failed, requests, get state() { return state }, get closes() { return closes }}
}

test('A to B failed next lookup cannot retain B or exit; previous remains usable', async () => {
  const view = reader(async (id, direction) => {
    if (id === 'A' && direction === 'next') return {id: 'B'}
    if (id === 'B' && direction === 'previous') return {id: 'A'}
    throw new Error('Network unavailable')
  })
  view.siblings.reset('A'); await settle()
  await view.siblings.navigate('next')
  assert.equal(view.state.bookId, 'B')
  assert.equal(view.state.next.status, 'loading')
  await settle()
  assert.equal(view.state.next.status, 'error')
  assert.equal(view.state.previous.book.id, 'A')
  await view.siblings.navigate('next')
  assert.deepEqual(view.opened, ['B'])
  assert.equal(view.closes, 0)
  assert.deepEqual(view.failed, ['next'])
})

test('normal null at either boundary retains the existing close behavior', async () => {
  for (const direction of ['previous', 'next']) {
    const view = reader(async () => null)
    view.siblings.reset('A'); await settle()
    await view.siblings.navigate(direction)
    assert.equal(view.state[direction].status, 'empty')
    assert.equal(view.closes, 1)
  }
})

test('slow previous lookup does not block next; loading clicks do nothing', async () => {
  const pending = deferred()
  const view = reader(async (_id, direction) => direction === 'previous' ? pending.promise : {id: 'B'})
  view.siblings.reset('A'); await settle()
  await view.siblings.navigate('previous')
  assert.equal(view.state.next.status, 'ready')
  assert.equal(view.closes, 0)
  assert.deepEqual(view.opened, [])
  view.siblings.dispose(); pending.resolve(null); await settle()
})

test('late success and failure from old books do not overwrite a new generation', async () => {
  const previous = deferred(), next = deferred()
  const view = reader(async (id, direction) => id === 'A'
    ? (direction === 'previous' ? previous.promise : next.promise) : null)
  view.siblings.reset('A'); await settle()
  view.siblings.reset('B'); await settle()
  previous.resolve({id: 'old'}); next.reject(new Error('late failure')); await settle()
  assert.equal(view.state.bookId, 'B')
  assert.equal(view.state.previous.status, 'empty')
  assert.equal(view.state.next.status, 'empty')
})

test('repeat clicks retry only the failed direction once and complete one navigation', async () => {
  const retry = deferred()
  let nextCalls = 0
  const view = reader(async (id, direction) => {
    if (id !== 'A' || direction === 'previous') return null
    if (++nextCalls === 1) throw new Error('temporary failure')
    return retry.promise
  })
  view.siblings.reset('A'); await settle()
  const navigation = view.siblings.navigate('next')
  await view.siblings.navigate('next'); await view.siblings.navigate('previous'); await settle()
  assert.equal(nextCalls, 2)
  assert.equal(view.requests.filter(([id, dir]) => id === 'A' && dir === 'previous').length, 1)
  retry.resolve({id: 'B'}); await navigation
  assert.deepEqual(view.opened, ['B'])
  assert.equal(view.closes, 0)
})

test('dispose discards pending navigation, updates and notifications', async () => {
  const retry = deferred()
  let calls = 0
  const view = reader(async () => { if (++calls <= 2) throw new Error('failure'); return retry.promise })
  view.siblings.reset('A'); await settle()
  const navigation = view.siblings.navigate('next'); await settle()
  const snapshot = view.state
  view.siblings.dispose(); retry.resolve({id: 'B'}); await navigation
  assert.equal(view.state, snapshot)
  assert.deepEqual(view.opened, [])
  assert.deepEqual(view.failed, [])
  assert.equal(view.closes, 0)
})

test('self reference is an error on initial load and retry', async () => {
  const view = reader(async () => ({id: 'A'}))
  view.siblings.reset('A'); await settle()
  assert.equal(view.state.next.status, 'error')
  await view.siblings.navigate('next')
  assert.deepEqual(view.opened, [])
  assert.equal(view.closes, 0)
  assert.deepEqual(view.failed, ['next'])
})

test('changing book while retrying cancels the old navigation intent', async () => {
  const pending = deferred()
  let calls = 0
  const view = reader(async id => {
    if (id === 'B') return null
    if (++calls <= 2) throw new Error('failed')
    return pending.promise
  })
  view.siblings.reset('A'); await settle()
  const navigation = view.siblings.navigate('next'); await settle()
  view.siblings.reset('B'); pending.resolve({id: 'C'}); await navigation; await settle()
  assert.equal(view.state.bookId, 'B')
  assert.deepEqual(view.opened, [])
})

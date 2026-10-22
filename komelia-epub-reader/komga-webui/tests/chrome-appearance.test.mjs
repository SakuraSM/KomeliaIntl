import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizeChromeAppearance, refreshChromeAppearance} from '../../shared/chrome-appearance.mjs';

test('old hosts and unknown appearance values preserve classic reader controls', () => {
  for (const value of [undefined, null, {}, {surfaceStyle:'FUTURE'}]) {
    assert.equal(normalizeChromeAppearance(value).surface, 'classic');
  }
});

test('transparency and renderer fallbacks keep the explicit app theme', () => {
  for (const theme of ['LIGHT','DARK','DARKER']) {
    assert.deepEqual(normalizeChromeAppearance({surfaceStyle:'LIQUID_GLASS', theme}), {surface:'glass',theme,reducedMotion:false});
    assert.equal(normalizeChromeAppearance({surfaceStyle:'LIQUID_GLASS', theme, reduceTransparency:true}).surface, 'solid');
    assert.equal(normalizeChromeAppearance({surfaceStyle:'LIQUID_GLASS', theme, renderingSupported:false}).surface, 'solid');
  }
});

test('appearance bridge updates only chrome attributes and handles older hosts', async () => {
  const root = {dataset:{publicationTheme:'sepia',readingLocation:'chapter2#paragraph4'}};
  const result = await refreshChromeAppearance({getChromeAppearance:async()=>({result:{surfaceStyle:'LIQUID_GLASS',theme:'LIGHT',reducedMotion:true}})},root);
  assert.equal(result.surface,'glass');
  assert.equal(root.dataset.komeliaTheme,'LIGHT');
  assert.equal(root.dataset.komeliaReducedMotion,'true');
  assert.equal(root.dataset.publicationTheme,'sepia');
  assert.equal(root.dataset.readingLocation,'chapter2#paragraph4');
  await refreshChromeAppearance({},root);
  assert.equal(root.dataset.komeliaSurface,'classic');
  await refreshChromeAppearance({getChromeAppearance:async()=>{throw new Error('old bridge');}},root);
  assert.equal(root.dataset.komeliaSurface,'classic');
});

test('an unresponsive optional bridge cannot block the reader', async () => {
  const root = {dataset:{}};
  const result = await refreshChromeAppearance({getChromeAppearance:()=>new Promise(()=>{})},root);
  assert.equal(result.surface,'classic');
});

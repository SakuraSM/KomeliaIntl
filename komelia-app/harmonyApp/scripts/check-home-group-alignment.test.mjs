import { strict as assert } from 'node:assert';
import { test } from 'node:test';
import { checkHomeGroupAlignment } from './check-home-group-alignment.mjs';

function layout(top = 542, pageBottom = 2506) {
  return { children: [
    { attributes: { id: 'main_shell', bounds: '[0,137][1320,2758]' } },
    { attributes: { id: 'home_page', bounds: `[0,137][1320,${pageBottom}]` } },
    { attributes: { id: 'home_book_card_keepReading_0', bounds: `[42,${top}][426,${top + 782}]` } }
  ] };
}
test('accepts fixed top alignment', () => assert.equal(checkHomeGroupAlignment(layout(), layout()).delta, 0));
test('rejects the observed short-content centering regression', () => {
  assert.throws(() => checkHomeGroupAlignment(layout(), layout(693)), /151px/);
});
test('allows only two pixels of rounding', () => {
  assert.equal(checkHomeGroupAlignment(layout(), layout(544)).delta, 2);
  assert.throws(() => checkHomeGroupAlignment(layout(), layout(545)), /3px/);
});
test('does not accept a changed viewport', () => {
  assert.throws(() => checkHomeGroupAlignment(layout(), layout(542, 2000)), /Viewport changed/);
});
test('fails when unauthenticated or required content is missing', () => {
  assert.throws(() => checkHomeGroupAlignment({}, layout()), /main_shell/);
  const missing = layout();
  missing.children.pop();
  assert.throws(() => checkHomeGroupAlignment(layout(), missing), /home_book_card/);
});
test('rejects duplicate matching nodes', () => {
  const duplicate = layout();
  duplicate.children.push(duplicate.children[2]);
  assert.throws(() => checkHomeGroupAlignment(layout(), duplicate), /found 2/);
});

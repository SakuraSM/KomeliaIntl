import assert from 'node:assert/strict';
/** The approved PNG canvas is normalized to 390 CSS pixels; OS areas are excluded. */
export function assertControlBounds(screen, control, measured, expected, records) {
  const deviation = Object.fromEntries(Object.keys(expected).map(key => [key, Math.abs(measured[key] - expected[key])]));
  const record = {screen, control, expected, measured, deviation};
  records.push(record);
  console.log('CONTROL_GEOMETRY ' + JSON.stringify(record));
  for (const [key, delta] of Object.entries(deviation)) {
    assert.ok(delta <= 2, `${screen}/${control}.${key}: expected ${expected[key]}, got ${measured[key]}`);
  }
}

/** Segments paint inside padding while the complete 48px button remains clickable. */
export async function paintedBounds(locator) {
  return locator.evaluate(node => {
    const rect = node.getBoundingClientRect(), css = getComputedStyle(node);
    const top = parseFloat(css.paddingTop), bottom = parseFloat(css.paddingBottom);
    const left = parseFloat(css.paddingLeft), right = parseFloat(css.paddingRight);
    return {x: rect.x + left, y: rect.y + top, width: rect.width - left - right, height: rect.height - top - bottom};
  });
}

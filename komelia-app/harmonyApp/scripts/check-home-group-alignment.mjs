import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

function boundsFor(tree, id) {
  const matches = [];
  function visit(node) {
    if (!node || typeof node !== 'object') return;
    if (node.attributes?.id === id) matches.push(node.attributes.bounds);
    for (const value of Object.values(node)) {
      if (Array.isArray(value)) value.forEach(visit);
      else if (value && typeof value === 'object') visit(value);
    }
  }
  visit(tree);
  if (matches.length !== 1) throw new Error(`Expected exactly one ${id}; found ${matches.length}`);
  const match = /^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$/.exec(matches[0]);
  if (!match) throw new Error(`Invalid bounds for ${id}`);
  const bounds = match.slice(1).map(Number);
  if (bounds[2] <= bounds[0] || bounds[3] <= bounds[1]) throw new Error(`Invisible ${id}`);
  return bounds;
}

export function checkHomeGroupAlignment(overview, singleGroup) {
  for (const id of ['main_shell', 'home_page']) {
    const before = boundsFor(overview, id);
    const after = boundsFor(singleGroup, id);
    if (before.some((value, index) => value !== after[index])) {
      throw new Error(`Viewport changed for ${id}; capture the same orientation and size`);
    }
  }
  const id = 'home_book_card_keepReading_0';
  const before = boundsFor(overview, id);
  const after = boundsFor(singleGroup, id);
  if (before[0] !== after[0] || before[2] !== after[2]) throw new Error('Card width or horizontal position changed');
  const delta = after[1] - before[1];
  if (Math.abs(delta) > 2) throw new Error(`Home group moved vertically by ${delta}px (maximum 2px)`);
  return { overviewTop: before[1], singleGroupTop: after[1], delta };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 4) throw new Error('Usage: node check-home-group-alignment.mjs overview.json continue-reading.json');
    const inputs = process.argv.slice(2).map(path => JSON.parse(readFileSync(path, 'utf8')));
    console.log('Home group alignment passed:', checkHomeGroupAlignment(...inputs));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

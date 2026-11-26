export const noteSelector = '[epub\\:type~="footnote"], [epub\\:type~="endnote"], [role~="doc-footnote"], [role~="doc-endnote"]';
export const referenceSelector = '[epub\\:type~="noteref"], [role~="doc-noteref"]';

export function footnoteText(note: Element): string {
  const copy = note.cloneNode(true) as Element;
  copy.querySelectorAll('script, style, [role~="doc-backlink"], [epub\\:type~="backlink"]').forEach(el => el.remove());
  copy.querySelectorAll('p, li, div, br').forEach(el => el.append('\n'));
  return copy.textContent?.trim() ?? '';
}

import {footnoteText, noteSelector, referenceSelector} from '../../../../shared/footnote';

// TTU combines the reading order before pagination. Resolve links while every resource
// is present and before the ordinary chapter-link handler rewrites their hrefs.
export function prepareFootnotes(book: Element): void {
  const chapters = new Map<string, Element>();
  for (const section of book.children) {
    if (!section.id) continue;
    try { chapters.set(new URL(section.id, 'https://epub.invalid/').href, section); }
    catch { /* A malformed resource name must not prevent the rest of the book from opening. */ }
  }
  const noteTexts = new WeakMap<Element, string>();
  for (const [source, section] of chapters) {
    for (const link of section.querySelectorAll('a[href]')) {
      link.removeAttribute('data-reader-footnote');
      const href = link.getAttribute('href')!;
      if (!href.includes('#')) continue;
      let target: URL;
      let id: string;
      try {
        target = new URL(href, source);
        id = decodeURIComponent(target.hash.slice(1));
      } catch { continue; }
      target.hash = '';
      if (!id) continue;
      const anchor = chapters.get(target.href)?.querySelector('#' + CSS.escape(id));
      if (!anchor) continue;
      const note = anchor.closest(noteSelector) ?? (link.matches(referenceSelector) ? anchor : null);
      if (!note) continue;
      let text = noteTexts.get(note);
      if (text === undefined) {
        text = footnoteText(note);
        noteTexts.set(note, text);
      }
      if (text) link.setAttribute('data-reader-footnote', text);
    }
  }
}

import {getChapterData, type SectionWithProgress} from '$lib/components/book-reader/book-toc/book-toc';
import type {TocEntry} from '$lib/data/books-db';

const CONTINUOUS_READING_LINE = 0.5;
const COMPLETED_SECTION_PROGRESS = 100;

interface TocLocationContext {
  entries: TocEntry[];
  sections: SectionWithProgress[];
  verticalMode: boolean;
  document: Document;
}

export function findCurrentTocPath(context: TocLocationContext): string | undefined {
  const {entries, sections, verticalMode, document: documentRef} = context;
  if (!sections.length) return undefined;
  const currentSection = sections.find(section => section.progress < COMPLETED_SECTION_PROGRESS) ?? sections[sections.length - 1];
  const [, , mainReference] = getChapterData(sections);
  const paginatedRoot = documentRef.querySelector<HTMLElement>('.book-content-container');
  const chapterRoot = documentRef.getElementById(currentSection.reference) ?? paginatedRoot;
  const anchors = new Map<string, Element>();
  chapterRoot?.querySelectorAll('[id]').forEach(element => {
    if (!anchors.has(element.id)) anchors.set(element.id, element);
  });

  let fallbackPath: string | undefined;
  let anchorPath: string | undefined;
  let bestPosition = Number.NEGATIVE_INFINITY;
  const viewport = documentRef.defaultView;

  function visit(nodes: TocEntry[], prefix: string): void {
    nodes.forEach((node, index) => {
      const path = `${prefix}${index}`;
      const reference = node.reference ?? '';
      if (!fallbackPath && reference === mainReference) fallbackPath = path;
      const separator = reference.indexOf('#');
      if (separator >= 0 && reference.slice(0, separator) === currentSection.reference && viewport) {
        const fragment = reference.slice(separator + 1);
        let anchorId = fragment;
        try { anchorId = decodeURIComponent(fragment); } catch { /* Keep a malformed fragment literal. */ }
        const anchor = anchors.get(anchorId);
        if (anchor && anchor.getClientRects().length) {
          const bounds = anchor.getBoundingClientRect();
          const position = paginatedRoot
            ? (verticalMode ? bounds.top : bounds.left)
            : (verticalMode ? -bounds.right : bounds.top);
          const limit = paginatedRoot
            ? (verticalMode ? viewport.innerHeight : viewport.innerWidth)
            : (verticalMode ? 0 : viewport.innerHeight * CONTINUOUS_READING_LINE);
          if (position < limit && position > bestPosition) {
            bestPosition = position;
            anchorPath = path;
          }
        }
      }
      visit(node.children ?? [], `${path}.`);
    });
  }

  visit(entries, '');
  return anchorPath ?? fallbackPath;
}

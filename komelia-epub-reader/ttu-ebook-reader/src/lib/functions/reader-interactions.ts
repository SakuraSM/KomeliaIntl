/**
 * @license BSD-3-Clause
 * Copyright (c) 2024, ッツ Reader Authors
 * All rights reserved.
 */

import type {SectionWithProgress} from '$lib/components/book-reader/book-toc/book-toc';
import type {BookmarkData, Section} from '$lib/data/books-db';
import {
  DEFAULT_READER_HIGHLIGHT_COLOR,
  READER_ANNOTATION_TYPE_HIGHLIGHT,
  type ReaderAnnotation,
  type ReaderAnnotationsByBook
} from '$lib/data/reader-annotation';

const SEARCH_SNIPPET_RADIUS = 36;
const MAX_SEARCH_RESULTS = 200;
const WEAK_MATCH_MIN_LENGTH = 8;

export interface ReaderSearchIndexItem {
  chapterReference: string;
  chapterIndex: number;
  chapterLabel: string;
  startCharacter: number;
  text: string;
}

export interface ReaderSearchResult {
  id: string;
  chapterReference: string;
  chapterIndex: number;
  chapterLabel: string;
  startCharacter: number;
  endCharacter: number;
  text: string;
  snippetBefore: string;
  snippetMatch: string;
  snippetAfter: string;
}

export interface SelectionToolbarState {
  text: string;
  rect: DOMRect;
}

export function buildReaderSearchIndex(
  htmlContent: string,
  sections: SectionWithProgress[] | Section[] | undefined,
  documentRef: Document = document
) {
  if (!htmlContent || !sections?.length) {
    return [];
  }

  const container = documentRef.createElement('div');
  container.innerHTML = htmlContent;

  return sections
    .map((section, chapterIndex) => {
      const sectionRoot = container.querySelector<HTMLElement>(`#${cssEscape(section.reference)}`) ??
        container.querySelector<HTMLElement>(`[id="${escapeAttributeValue(section.reference)}"]`);
      const text = sectionRoot?.textContent || '';

      if (!text) {
        return undefined;
      }

      return {
        chapterReference: section.reference,
        chapterIndex,
        chapterLabel: section.label || `章节 ${chapterIndex + 1}`,
        startCharacter: section.startCharacter || 0,
        text
      } satisfies ReaderSearchIndexItem;
    })
    .filter((item): item is ReaderSearchIndexItem => !!item);
}

export function searchReaderIndex(index: ReaderSearchIndexItem[], rawQuery: string) {
  const query = rawQuery.trim();

  if (!query) {
    return [];
  }

  const normalizedQuery = query.toLocaleLowerCase();
  const results: ReaderSearchResult[] = [];

  for (const item of index) {
    const normalizedText = item.text.toLocaleLowerCase();
    let fromIndex = 0;

    while (results.length < MAX_SEARCH_RESULTS) {
      const matchIndex = normalizedText.indexOf(normalizedQuery, fromIndex);

      if (matchIndex === -1) {
        break;
      }

      const endIndex = matchIndex + query.length;
      const snippetStart = Math.max(0, matchIndex - SEARCH_SNIPPET_RADIUS);
      const snippetEnd = Math.min(item.text.length, endIndex + SEARCH_SNIPPET_RADIUS);

      results.push({
        id: `${item.chapterReference}:${matchIndex}:${endIndex}`,
        chapterReference: item.chapterReference,
        chapterIndex: item.chapterIndex,
        chapterLabel: item.chapterLabel,
        startCharacter: item.startCharacter + matchIndex,
        endCharacter: item.startCharacter + endIndex,
        text: item.text.slice(matchIndex, endIndex),
        snippetBefore: item.text.slice(snippetStart, matchIndex),
        snippetMatch: item.text.slice(matchIndex, endIndex),
        snippetAfter: item.text.slice(endIndex, snippetEnd)
      });

      fromIndex = endIndex;
    }

    if (results.length >= MAX_SEARCH_RESULTS) {
      break;
    }
  }

  return results;
}

export function getBookAnnotations(annotationsByBook: ReaderAnnotationsByBook, bookId: string) {
  return annotationsByBook[bookId] || [];
}

export function upsertBookAnnotation(
  annotationsByBook: ReaderAnnotationsByBook,
  annotation: ReaderAnnotation
) {
  const currentAnnotations = getBookAnnotations(annotationsByBook, annotation.bookId);
  const nextAnnotations = [
    ...currentAnnotations.filter((item) => item.id !== annotation.id),
    annotation
  ].sort((a, b) => a.startCharacter - b.startCharacter);

  return {
    ...annotationsByBook,
    [annotation.bookId]: nextAnnotations
  };
}

export function removeBookAnnotation(
  annotationsByBook: ReaderAnnotationsByBook,
  bookId: string,
  annotationId: string
) {
  return {
    ...annotationsByBook,
    [bookId]: getBookAnnotations(annotationsByBook, bookId).filter((item) => item.id !== annotationId)
  };
}

export function getSelectionToolbarState(windowRef: Window, range?: Range) {
  const selection = windowRef.getSelection();
  const selectedText = selection?.toString().trim();

  if (!range || !selectedText || !isRangeInsideBookContent(range)) {
    return undefined;
  }

  const rect = getVisibleRangeRect(range);

  if (!rect) {
    return undefined;
  }

  return {
    text: selectedText,
    rect
  } satisfies SelectionToolbarState;
}

export function createAnnotationFromRange(
  windowRef: Window,
  range: Range | undefined,
  sections: SectionWithProgress[] | undefined,
  bookId: string,
  color = DEFAULT_READER_HIGHLIGHT_COLOR
) {
  const selectionText = windowRef.getSelection()?.toString().trim();

  if (!range || !sections?.length || !bookId || !selectionText || !isRangeInsideBookContent(range)) {
    return undefined;
  }

  const startSection = findSectionForNode(range.startContainer, sections);
  const endSection = findSectionForNode(range.endContainer, sections);

  if (!startSection || !endSection || startSection.section.reference !== endSection.section.reference) {
    return undefined;
  }

  const sectionRoot = findSectionRoot(range.commonAncestorContainer, startSection.section.reference);

  if (!sectionRoot) {
    return undefined;
  }

  const localStart = getTextOffset(sectionRoot, range.startContainer, range.startOffset);
  const localEnd = getTextOffset(sectionRoot, range.endContainer, range.endOffset);

  if (localStart === undefined || localEnd === undefined || localStart === localEnd) {
    return undefined;
  }

  const startOffset = Math.min(localStart, localEnd);
  const endOffset = Math.max(localStart, localEnd);
  const now = Date.now();

  return {
    id: `${bookId}:${startSection.section.reference}:${startOffset}:${endOffset}:${now}`,
    type: READER_ANNOTATION_TYPE_HIGHLIGHT,
    bookId,
    chapterReference: startSection.section.reference,
    chapterIndex: startSection.index,
    startCharacter: (startSection.section.startCharacter || 0) + startOffset,
    endCharacter: (startSection.section.startCharacter || 0) + endOffset,
    text: selectionText,
    color,
    createdAt: now,
    updatedAt: now
  } satisfies ReaderAnnotation;
}

export function createBookmarkDataFromCharacter(
  bookId: string,
  sections: SectionWithProgress[] | undefined,
  startCharacter: number
) {
  if (!bookId || !sections?.length) {
    return undefined;
  }

  const sectionIndex = findSectionIndexByCharacter(sections, startCharacter);
  const section = sections[sectionIndex] || sections[0];

  return {
    bookId,
    exploredCharCount: startCharacter,
    progress: section.progress,
    lastBookmarkModified: Date.now(),
    chapterIndex: sectionIndex,
    chapterReference: section.reference
  } satisfies BookmarkData;
}

export function renderReaderMarks(
  container: Element | null,
  annotations: ReaderAnnotation[],
  activeSearchResult: ReaderSearchResult | undefined,
  sections: SectionWithProgress[] | undefined
) {
  if (!container || !sections?.length) {
    return;
  }

  clearReaderMarks(container);

  const searchAnnotation = activeSearchResult
    ? searchResultToAnnotation(activeSearchResult)
    : undefined;
  const marks = [
    ...annotations,
    ...(searchAnnotation ? [searchAnnotation] : [])
  ].sort((a, b) => b.startCharacter - a.startCharacter);

  for (const annotation of marks) {
    try {
      renderSingleMark(container, annotation, sections, annotation.id === searchAnnotation?.id);
    } catch {
      // Annotation recovery is best-effort; invalid offsets are ignored.
    }
  }
}

export function clearReaderMarks(container: Element) {
  const marks = [...container.querySelectorAll<HTMLElement>('[data-reader-mark-id]')];

  for (const mark of marks) {
    const parent = mark.parentNode;

    if (!parent) {
      continue;
    }

    while (mark.firstChild) {
      parent.insertBefore(mark.firstChild, mark);
    }

    parent.removeChild(mark);
    parent.normalize();
  }
}

function renderSingleMark(
  container: Element,
  annotation: ReaderAnnotation,
  sections: SectionWithProgress[],
  isSearchMark: boolean
) {
  const section = sections.find((item) => item.reference === annotation.chapterReference);
  const sectionRoot = section ? container.querySelector<HTMLElement>(`#${cssEscape(section.reference)}`) ??
    container.querySelector<HTMLElement>(`[id="${escapeAttributeValue(section.reference)}"]`) : undefined;

  if (!section || !sectionRoot) {
    return;
  }

  const startOffset = annotation.startCharacter - (section.startCharacter || 0);
  const endOffset = annotation.endCharacter - (section.startCharacter || 0);

  if (startOffset < 0 || endOffset <= startOffset) {
    return;
  }

  const start = findTextPosition(sectionRoot, startOffset);
  const end = findTextPosition(sectionRoot, endOffset);

  if (!start || !end) {
    return;
  }

  const range = document.createRange();
  range.setStart(start.node, start.offset);
  range.setEnd(end.node, end.offset);

  if (!isSearchMark && !isWeakTextMatch(range.toString(), annotation.text)) {
    return;
  }

  const span = document.createElement('mark');
  span.dataset.readerMarkId = annotation.id;
  span.className = isSearchMark ? 'reader-search-match' : 'reader-highlight';
  span.style.setProperty('--reader-mark-color', annotation.color || DEFAULT_READER_HIGHLIGHT_COLOR);

  const content = range.extractContents();
  span.appendChild(content);
  range.insertNode(span);
}

function searchResultToAnnotation(result: ReaderSearchResult) {
  return {
    id: `reader-search-match:${result.id}`,
    type: READER_ANNOTATION_TYPE_HIGHLIGHT,
    bookId: '',
    chapterReference: result.chapterReference,
    chapterIndex: result.chapterIndex,
    startCharacter: result.startCharacter,
    endCharacter: result.endCharacter,
    text: result.text,
    color: '#38bdf8',
    createdAt: 0,
    updatedAt: 0
  } satisfies ReaderAnnotation;
}

function findSectionIndexByCharacter(sections: SectionWithProgress[], character: number) {
  const index = sections.findIndex((section, currentIndex) => {
    const nextSection = sections[currentIndex + 1];
    const start = section.startCharacter || 0;
    const end = nextSection?.startCharacter ?? start + (section.characters || 0);

    return character >= start && character < end;
  });

  if (index !== -1) {
    return index;
  }

  return character >= (sections[sections.length - 1].startCharacter || 0) ? sections.length - 1 : 0;
}

function isWeakTextMatch(renderedText: string, savedText: string) {
  const normalizedRendered = normalizeReaderText(renderedText);
  const normalizedSaved = normalizeReaderText(savedText);

  if (normalizedSaved.length < WEAK_MATCH_MIN_LENGTH) {
    return normalizedRendered === normalizedSaved;
  }

  return normalizedRendered.includes(normalizedSaved.slice(0, WEAK_MATCH_MIN_LENGTH));
}

function getVisibleRangeRect(range: Range) {
  const rects = [...range.getClientRects()].filter((rect) => rect.width > 0 && rect.height > 0);
  return rects[0] || range.getBoundingClientRect();
}

function isRangeInsideBookContent(range: Range) {
  const node = range.commonAncestorContainer;
  const element = node.nodeType === Node.ELEMENT_NODE ? node as Element : node.parentElement;

  return !!element?.closest('.book-content');
}

function findSectionForNode(node: Node, sections: SectionWithProgress[]) {
  for (const [index, section] of sections.entries()) {
    if (findSectionRoot(node, section.reference)) {
      return {section, index};
    }
  }

  return undefined;
}

function findSectionRoot(node: Node, reference: string) {
  let current: Node | null = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;

  while (current instanceof Element) {
    if (current.id === reference) {
      return current;
    }

    current = current.parentElement;
  }

  return document.getElementById(reference);
}

function getTextOffset(root: Element, targetNode: Node, targetOffset: number) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let offset = 0;
  let currentNode = walker.nextNode();

  while (currentNode) {
    if (currentNode === targetNode) {
      return offset + targetOffset;
    }

    offset += currentNode.textContent?.length || 0;
    currentNode = walker.nextNode();
  }

  return undefined;
}

function findTextPosition(root: Element, characterOffset: number) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let remaining = characterOffset;
  let currentNode = walker.nextNode();

  while (currentNode) {
    const length = currentNode.textContent?.length || 0;

    if (remaining <= length) {
      return {node: currentNode, offset: Math.max(0, remaining)};
    }

    remaining -= length;
    currentNode = walker.nextNode();
  }

  return undefined;
}

function normalizeReaderText(text: string) {
  return text.replace(/\s+/g, ' ').trim();
}

function cssEscape(value: string) {
  return globalThis.CSS?.escape ? globalThis.CSS.escape(value) : value.replace(/"/g, '\\"');
}

function escapeAttributeValue(value: string) {
  return value.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
}

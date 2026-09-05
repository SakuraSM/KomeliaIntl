import { KomgaBookPage } from './KomgaModels';
import { PageDisplayLayout } from './ImageReaderModels';

export interface ImageReaderSpread {
  pages: KomgaBookPage[];
  firstIndex: number;
  lastIndex: number;
  key: string;
}

interface IndexedPage {
  page: KomgaBookPage;
  index: number;
}

function isLandscape(page: KomgaBookPage): boolean {
  return (page.width || 0) > 0 && (page.height || 0) > 0 && (page.width || 0) > (page.height || 0);
}

function makeSpread(pages: IndexedPage[]): ImageReaderSpread {
  const first = pages[0].index;
  const last = pages[pages.length - 1].index;
  return {
    pages: pages.map((item: IndexedPage) => item.page),
    firstIndex: first,
    lastIndex: last,
    key: pages.map((item: IndexedPage) => `${item.page.number}`).join('-')
  };
}

function appendPortraitSegment(result: ImageReaderSpread[], segment: IndexedPage[], offset: boolean): void {
  if (segment.length === 0) return;
  let start = 0;
  if (offset) {
    result.push(makeSpread([segment[0]]));
    start = 1;
  }
  for (let index = start; index < segment.length; index += 2) {
    result.push(makeSpread(segment.slice(index, Math.min(index + 2, segment.length))));
  }
}

/**
 * Mirrors Android's spread construction: optional cover isolation, landscape pages
 * as independent spreads, and optional one-page offset at each portrait segment.
 */
export function buildImageReaderSpreads(pages: KomgaBookPage[], layout: PageDisplayLayout,
  offset: boolean): ImageReaderSpread[] {
  if (pages.length === 0) return [];
  const indexed = pages.map((page: KomgaBookPage, index: number): IndexedPage => ({ page: page, index: index }));
  if (layout === PageDisplayLayout.SINGLE_PAGE) {
    return indexed.map((item: IndexedPage): ImageReaderSpread => makeSpread([item]));
  }

  const result: ImageReaderSpread[] = [];
  let start = 0;
  if (layout === PageDisplayLayout.DOUBLE_PAGES) {
    result.push(makeSpread([indexed[0]]));
    start = 1;
  }

  let segment: IndexedPage[] = [];
  for (let index = start; index < indexed.length; index++) {
    const item = indexed[index];
    if (isLandscape(item.page)) {
      appendPortraitSegment(result, segment, offset);
      segment = [];
      result.push(makeSpread([item]));
    } else {
      segment.push(item);
    }
  }
  appendPortraitSegment(result, segment, offset);
  return result;
}

export function spreadIndexForPage(spreads: ImageReaderSpread[], pageIndex: number): number {
  if (spreads.length === 0) return 0;
  const match = spreads.findIndex((spread: ImageReaderSpread) =>
    pageIndex >= spread.firstIndex && pageIndex <= spread.lastIndex);
  return match < 0 ? Math.max(0, Math.min(spreads.length - 1, pageIndex)) : match;
}

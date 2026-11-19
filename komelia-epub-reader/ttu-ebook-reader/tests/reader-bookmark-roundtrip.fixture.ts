import {CharacterStatsCalculator} from '../src/lib/components/book-reader/book-reader-continuous/character-stats-calculator';
import {BookmarkManagerContinuous} from '../src/lib/components/book-reader/book-reader-continuous/bookmark-manager-continuous';
import type {BookmarkData} from '../src/lib/data/books-db';

declare global {
  interface Window {
    bookmarkRoundTrip: (index: number, offset: number) => Promise<{
      expected: number; previous: number; beforeBoundary: number; saved: number[]; positions: number[]; ideal: number;
    }>;
  }
}

const vertical = new URLSearchParams(location.search).has('vertical');
if (vertical) document.documentElement.dir = 'rtl';
const content = document.createElement('article');
content.style.cssText = vertical
  ? 'writing-mode: vertical-rl; height: 600px; width: max-content; margin: 32px 0; font-size: 20px; line-height: 33.3px'
  : 'width: 300px; margin: 32px auto; font-size: 20px; line-height: 33.3px';
content.innerHTML = Array.from({length: 70}, (_, index) =>
  `<p style="margin:0">Paragraph ${index}. Synthetic text for progress restoration across repeated reader sessions.</p>`
).join('');
document.body.append(content);

window.bookmarkRoundTrip = async (index, offset) => {
  window.scrollTo(0, 0);
  const calculator = new CharacterStatsCalculator(content, vertical ? 'vertical' : 'horizontal',
    vertical ? 'rtl' : 'ltr', document.documentElement, document);
  calculator.updateParagraphPos();
  const expected = calculator.accumulatedCharCount[index];
  const previous = calculator.accumulatedCharCount[index - 1];
  const ideal = calculator.getScrollPosByCharCount(expected);
  const manager = new BookmarkManagerContinuous(calculator, window, 0);
  window.scrollTo(vertical ? {left: ideal + offset + 2} : {top: ideal - offset - 2});
  await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
  const beforeBoundary = calculator.calcExploredCharCount(offset);
  const saved: number[] = [];
  const positions: number[] = [];
  let bookmark: BookmarkData = {bookId: 'synthetic', chapterIndex: 0, chapterReference: 'chapter',
    exploredCharCount: expected, progress: 0, lastBookmarkModified: 0};
  for (let cycle = 0; cycle < 3; cycle += 1) {
    manager.scrollToBookmark(bookmark, offset);
    await new Promise<void>(resolve => requestAnimationFrame(() => resolve()));
    const current = manager.formatBookmarkData('synthetic', 0, 'chapter', offset);
    saved.push(current.exploredCharCount ?? 0);
    positions.push(vertical ? window.scrollX : window.scrollY);
    bookmark = {...current, scrollX: undefined, scrollY: undefined};
  }
  return {expected, previous, beforeBoundary, saved, positions, ideal};
};

import '../../shared/chrome-appearance.css';
import '../src/app.scss';
import {createClassComponent} from 'svelte/legacy';
import PaginatedReader from '../src/lib/components/book-reader/book-reader-paginated/book-reader-paginated.svelte';
import {nextChapter$, sectionList$} from '../src/lib/components/book-reader/book-toc/book-toc';
import {FuriganaStyle} from '../src/lib/data/furigana-style';

declare global {
  interface Window { navigateTestChapter: (reference: string) => void }
}

const verticalMode = new URLSearchParams(location.search).has('vertical');
const paragraphsBeforeTarget = 55;
const paragraphsAfterTarget = 30;
const paragraphsAfterEncodedTarget = 14;
const first = '<div id="chapter-one"><h1 id="first">First chapter</h1><p>Introduction.</p></div>';
const second = '<div id="chapter-two"><h1>Second chapter</h1>' +
  '<p>Text preceding the target location.</p>'.repeat(paragraphsBeforeTarget) +
  '<h2 id="selected">Requested fragment</h2>' + '<p>Text after the target.</p>'.repeat(paragraphsAfterTarget) +
  '<a id="empty-target"></a><h2 id="selected part">Encoded fragment</h2>' +
  '<p>Text after the encoded target.</p>'.repeat(paragraphsAfterEncodedTarget) +
  '<p id="last-line">Last paragraph in the section.</p><a id="end-target"></a></div>';
sectionList$.next([
  {reference: 'chapter-one', charactersWeight: 30, startCharacter: 0, characters: 30, label: 'First chapter'},
  {reference: 'chapter-two', charactersWeight: 3000, startCharacter: 30, characters: 3000, label: 'Second chapter'},
]);
const font = {displayName: 'Test sans', familyName: 'sans-serif'};
createClassComponent({component: PaginatedReader, target: document.querySelector('#app')!, props: {
  htmlContent: first + second, width: 360, height: 800, verticalMode,
  fontColor: '#222', backgroundColor: '#fff', hintFuriganaFontColor: '#555',
  hintFuriganaShadowColor: 'transparent', fontFamilyGroupOne: font, fontFamilyGroupTwo: font,
  fontSize: 20, lineHeight: 1.6, hideSpoilerImage: false, hideFurigana: false,
  furiganaStyle: FuriganaStyle.Full, bookmarkData: Promise.resolve(undefined),
  pageManager: undefined, bookmarkManager: undefined, exploredCharCount: 0, bookCharCount: 0,
  pageColumns: 1, firstDimensionMargin: 0, autoBookmark: false, autoBookmarkTime: 3,
  customReadingPointRange: undefined, showCustomReadingPoint: false,
}});
window.navigateTestChapter = reference => nextChapter$.next(reference);

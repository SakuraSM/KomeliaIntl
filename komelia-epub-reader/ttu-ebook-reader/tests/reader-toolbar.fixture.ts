import {mount} from 'svelte';
import {createClassComponent} from 'svelte/legacy';
import BookReaderHeader from '../src/lib/components/book-reader/book-reader-header.svelte';
import ReaderSettingsPanel from '../src/lib/components/settings/reader-settings-panel.svelte';
import ReaderNavigationDrawer from '../src/lib/components/reader/ReaderNavigationDrawer.svelte';
import {nextChapter$} from '../src/lib/components/book-reader/book-toc/book-toc';
import ContinuousReader from '../src/lib/components/book-reader/book-reader-continuous/book-reader-continuous.svelte';
import {sectionList$, sectionProgress$, getChapterData} from '../src/lib/components/book-reader/book-toc/book-toc';
import {FuriganaStyle} from '../src/lib/data/furigana-style';
import {viewMode$, availableSystemFonts$} from '../src/lib/data/store';
import {ViewMode} from '../src/lib/data/view-mode';
import {setLocale} from '../src/lib/i18n';
import '../src/app.scss';
import '../../shared/chrome-appearance.css';

const parameters = new URLSearchParams(location.search);
setLocale(parameters.get('locale') ?? 'en');
document.documentElement.dataset.komeliaSurface = parameters.get('surface') ?? 'glass';
document.documentElement.dataset.komeliaTheme = parameters.get('theme') ?? 'LIGHT';
viewMode$.next(ViewMode.Paginated);
const action = (name: string): (() => void) => () => { document.querySelector('output')!.textContent = name; };
if (parameters.has('contents')) {
  nextChapter$.subscribe(reference => { document.querySelector('output')!.setAttribute('data-chapter', reference); });
  mount(ReaderNavigationDrawer, {target: document.querySelector('#app')!, props: {
    sectionData: [
      {reference: 'chapter-one', label: 'First chapter', startCharacter: 0, characters: 200, charactersWeight: 200, progress: 100},
      {reference: 'chapter-two', label: 'Second chapter', startCharacter: 200, characters: 200, charactersWeight: 200, progress: 60},
    ], verticalMode: false, exploredCharCount: 320, bookTitle: 'Synthetic publication',
    searchQuery: '', searchResults: [], annotations: [], onClose: action('closed-contents'),
    onSearchQueryInput: query => { document.querySelector('output')!.textContent = `query:${query}`; },
    onPreviousSearchResult: action('previous-result'), onNextSearchResult: action('next-result'),
    onSearchResultClick: action('search-result'), onAnnotationClick: action('annotation'), onAnnotationDelete: action('delete-annotation'),
  }});
}
mount(BookReaderHeader, {
  target: document.querySelector('#app')!,
  props: {
    title: 'A long synthetic book title for narrow reader toolbars',
    chapterTitle: 'Chapter 2: A very long chapter title that should truncate',
    hasChapterData: true, autoScrollMultiplier: 20, hasCustomReadingPoint: true,
    showFullscreenButton: true, isBookmarkScreen: true, hasBookmarkData: true,
    tocClick: action('toc'), bookmarkClick: action('bookmark'), scrollToBookmarkClick: action('return'),
    completeBook: action('complete'), fullscreenClick: action('fullscreen'),
    showCustomReadingPoint: action('showPoint'), setCustomReadingPoint: action('setPoint'),
    resetCustomReadingPoint: action('resetPoint'), readerImageGalleryClick: action('images'),
    settingsClick: action('settings'), closeBook: action('close'),
  },
});

if (parameters.has('settings')) {
  availableSystemFonts$.next(['Noto Sans CJK JP', 'Synthetic serif']);
  mount(ReaderSettingsPanel, {target: document.querySelector('#app')!, props: {onExit: action('closeSettings')}});
}

if (parameters.has('initialChapter')) {
  document.body.classList.add('reader-horizontal-mode');
  const first = '<div id="chapter-one"><h1>First chapter</h1>' + '<p>Introduction text.</p>'.repeat(40) + '</div>';
  const second = '<div id="chapter-two"><h1>Second chapter</h1>' + '<p>Chapter introduction.</p>'.repeat(8) +
    '<h2 id="selected">Selected section</h2>' + '<p>Requested chapter text.</p>'.repeat(40) + '</div>';
  const firstCount = new DOMParser().parseFromString(first, 'text/html').body.textContent!.replace(/\s/g, '').length;
  sectionList$.next([
    {reference:'chapter-one', startCharacter:0, characters:firstCount, charactersWeight:firstCount, label:'First chapter'},
    {reference:'chapter-two', startCharacter:firstCount, characters:1000, charactersWeight:1000, label:'Second chapter'},
  ]);
  sectionProgress$.subscribe(progress => {
    if (progress.size) document.querySelector('output')!.textContent = getChapterData([...progress.values()])[2];
  });
  const font = {displayName:'Test sans', familyName:'sans-serif'};
  const reader = createClassComponent({component:ContinuousReader, target:document.querySelector('#app')!, props:{
    htmlContent:first+second, width:360, height:800, verticalMode:false,
    fontColor:'#222', backgroundColor:'#fff', hintFuriganaFontColor:'#555', hintFuriganaShadowColor:'transparent',
    fontFamilyGroupOne:font, fontFamilyGroupTwo:font, fontSize:20, lineHeight:1.6,
    hideSpoilerImage:false, hideFurigana:false, furiganaStyle:FuriganaStyle.Full,
    secondDimensionMaxValue:0, firstDimensionMargin:20, autoPositionOnResize:true,
    autoBookmark:false, autoBookmarkTime:3, loadingState:false, multiplier:1,
    bookmarkData:Promise.resolve({bookId:'synthetic', exploredCharCount:firstCount, progress:.4,
      lastBookmarkModified:0, chapterIndex:1, chapterReference:'chapter-two',
      initialChapterHref:parameters.has('bookmarkOnly') ? undefined : 'chapter-two#selected'}),
    exploredCharCount:0, bookCharCount:0, autoScroller:undefined, bookmarkManager:undefined, pageManager:undefined,
    customReadingPoint:0, customReadingPointLeft:0, customReadingPointTop:0, customReadingPointScrollOffset:0,
  }});
  window.addEventListener('resize', () => reader.$set({width:innerWidth, height:innerHeight}));
}

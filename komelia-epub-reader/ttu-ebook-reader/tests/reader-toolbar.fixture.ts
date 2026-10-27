import {mount} from 'svelte';
import BookReaderHeader from '../src/lib/components/book-reader/book-reader-header.svelte';
import {viewMode$} from '../src/lib/data/store';
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

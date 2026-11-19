import '../../shared/chrome-appearance.css';
import '../src/app.scss';
import {mount} from 'svelte';
import ReaderChrome from '../src/lib/components/reader/ReaderChrome.svelte';
import {ViewMode} from '../src/lib/data/view-mode';
import {setLocale} from '../src/lib/i18n';

const parameters = new URLSearchParams(location.search);
setLocale('en');
document.documentElement.dataset.komeliaSurface = parameters.get('surface') ?? 'classic';
document.documentElement.dataset.komeliaTheme = 'LIGHT';
const action = (name: string): (() => void) => () => { document.querySelector('output')!.textContent = name; };

mount(ReaderChrome, {target: document.querySelector('#app')!, props: {
  showHeader: false, showImageGallery: false, navigationOpen: false, hasBookData: true,
  hasChapterData: true, hasPageManager: true, showFooter: parameters.has('footer'),
  bookCharCount: 100, showCharacterCounter: true, readingProgressPercent: 10,
  readingProgressLabel: '10%', activeChapterLabel: 'Synthetic chapter', viewMode: ViewMode.Continuous,
  autoScrollMultiplier: 1, autoScrollEnabled: false,
  onOpenNavigation: action('navigation'), onPreviousPage: action('previous'), onNextPage: action('next'),
  onPreviousChapter: action('previous-chapter'), onNextChapter: action('next-chapter'),
  onCopyProgress: action('copy'), onToggleAutoScroll: action('auto-scroll'),
  onAutoScrollMultiplierChange: action('speed'),
}});

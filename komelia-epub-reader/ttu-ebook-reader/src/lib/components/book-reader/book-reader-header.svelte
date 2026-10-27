<script lang="ts">
  import {faBookmark as farBookmark} from '@fortawesome/free-regular-svg-icons';
  import {
    faArrowLeft, faBookmark as fasBookmark, faCog, faCrosshairs, faExpand,
    faFlag, faImages, faList, faRotateLeft,
  } from '@fortawesome/free-solid-svg-icons';
  import {customReadingPointEnabled$, viewMode$} from '$lib/data/store';
  import {ViewMode} from '$lib/data/view-mode';
  import {isMobile$} from '$lib/functions/utils';
  import {t} from '$lib/i18n';
  import Fa from 'svelte-fa';
  import ReaderOverflowMenu, {type ReaderMenuAction} from './reader-overflow-menu.svelte';

  interface Props {
    title: string;
    chapterTitle: string;
    hasChapterData: boolean;
    autoScrollMultiplier: number;
    hasCustomReadingPoint: boolean;
    showFullscreenButton: boolean;
    isBookmarkScreen: boolean;
    hasBookmarkData: boolean;
    tocClick: () => void;
    bookmarkClick: () => void;
    scrollToBookmarkClick: () => void;
    completeBook: () => void;
    fullscreenClick: () => void;
    showCustomReadingPoint: () => void;
    setCustomReadingPoint: () => void;
    resetCustomReadingPoint: () => void;
    readerImageGalleryClick: () => void;
    settingsClick: () => void;
    closeBook: () => void;
  }

  let {
    title, chapterTitle, hasChapterData, autoScrollMultiplier, hasCustomReadingPoint,
    showFullscreenButton, isBookmarkScreen = $bindable(), hasBookmarkData,
    tocClick, bookmarkClick, scrollToBookmarkClick, completeBook, fullscreenClick,
    showCustomReadingPoint, setCustomReadingPoint, resetCustomReadingPoint,
    readerImageGalleryClick, settingsClick, closeBook,
  }: Props = $props();

  let readingPointActions = $derived<ReaderMenuAction[]>(
    $customReadingPointEnabled$ || $viewMode$ === ViewMode.Paginated ? [
      ...(hasCustomReadingPoint ? [{label: t('Show custom reading point'), icon: faCrosshairs, onSelect: showCustomReadingPoint}] : []),
      {label: t('Set custom reading point'), icon: faCrosshairs, onSelect: setCustomReadingPoint},
      ...(hasCustomReadingPoint ? [{label: t('Reset custom reading point'), icon: faCrosshairs, onSelect: resetCustomReadingPoint}] : []),
    ] : [],
  );
  let menuActions = $derived<ReaderMenuAction[]>([
    {label: isBookmarkScreen ? t('Update Bookmark') : t('Create Bookmark'), icon: isBookmarkScreen ? fasBookmark : farBookmark, onSelect: bookmarkClick, active: isBookmarkScreen},
    ...(hasBookmarkData ? [{label: t('Return to Bookmark'), icon: faRotateLeft, onSelect: scrollToBookmarkClick}] : []),
    {label: t('Mark as read'), icon: faFlag, onSelect: completeBook},
    ...readingPointActions,
    ...(showFullscreenButton ? [{label: t('Toggle Fullscreen'), icon: faExpand, onSelect: fullscreenClick}] : []),
    {label: t('View images'), icon: faImages, onSelect: readerImageGalleryClick},
  ]);
</script>

<nav data-komelia-chrome data-komelia-toolbar="top" aria-label={t('Reader toolbar')}>
  <button type="button" class="komelia-reader-action" title={t('Close Book')} aria-label={t('Close Book')} onclick={closeBook}>
    <Fa icon={faArrowLeft}/>
  </button>
  <div class="komelia-reader-title">
    <strong title={title}>{title}</strong>
    <span title={chapterTitle}>{chapterTitle}</span>
  </div>
  <button type="button" class="komelia-reader-action" title={t('Open Table of Contents')}
          aria-label={t('Open Table of Contents')} disabled={!hasChapterData} onclick={tocClick}>
    <Fa icon={faList}/>
  </button>
  <button type="button" class="komelia-reader-action" title={t('Settings')} aria-label={t('Reader settings')} onclick={settingsClick}>
    <Fa icon={faCog}/>
  </button>
  <ReaderOverflowMenu actions={menuActions}
                      extraInfo={$viewMode$ === ViewMode.Continuous && !$isMobile$ ? t('{speed}x Auto', {speed: autoScrollMultiplier}) : undefined}/>
</nav>

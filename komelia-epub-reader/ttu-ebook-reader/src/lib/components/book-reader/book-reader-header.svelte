<script lang="ts">
  import {faBookmark as farBookmark} from '@fortawesome/free-regular-svg-icons';
  import {
    faBookmark as fasBookmark, faCog,
    faCrosshairs,
    faExpand,
    faFlag,
    faList,
    faImages,
    faRotateLeft, faSignOutAlt
  } from '@fortawesome/free-solid-svg-icons';
  import Popover from '$lib/components/popover/popover.svelte';
  import {
    baseHeaderClasses,
    baseIconClasses,
    nTranslateXHeaderFa,
    translateXHeaderFa
  } from '$lib/css-classes';
  import {customReadingPointEnabled$, viewMode$} from '$lib/data/store';
  import {ViewMode} from '$lib/data/view-mode';
  import {isMobile$} from '$lib/functions/utils';
  import {t} from '$lib/i18n';
  import Fa from 'svelte-fa';

  interface Props {
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
    hasChapterData,
    autoScrollMultiplier,
    hasCustomReadingPoint,
    showFullscreenButton,
    isBookmarkScreen = $bindable(),
    hasBookmarkData,
    tocClick,
    bookmarkClick,
    scrollToBookmarkClick,
    completeBook,
    fullscreenClick,
    showCustomReadingPoint,
    setCustomReadingPoint,
    resetCustomReadingPoint,
    readerImageGalleryClick,
    settingsClick,
    closeBook,
  }: Props = $props()

  type HeaderMenuItem = {
    label: string;
    action: () => void;
  };

  let customReadingPointMenuItems = $derived<HeaderMenuItem[]>([
    ...(hasCustomReadingPoint ? [{label: t('Show custom reading point'), action: showCustomReadingPoint}] : []),
    {label: t('Set custom reading point'), action: setCustomReadingPoint},
    ...(hasCustomReadingPoint ? [{label: t('Reset custom reading point'), action: resetCustomReadingPoint}] : [])
  ]);

  let customReadingPointMenuElm: Popover | undefined = $state();
  const toolbarGroupClasses = 'flex items-center gap-1 rounded-full bg-white/10 p-1 shadow-inner';

  function dispatchCustomReadingPointAction(action: () => void) {
    action()
    if (customReadingPointMenuElm) {
      customReadingPointMenuElm.toggleOpen();
    }
  }
</script>

<nav
    class="flex items-center justify-between gap-3 bg-slate-950/92 px-3 shadow-lg shadow-black/30 backdrop-blur-md md:px-6 {baseHeaderClasses}"
    aria-label={t('Reader toolbar')}
>
  <div class="{toolbarGroupClasses} transform-gpu {nTranslateXHeaderFa}">
    {#if hasChapterData}
      <button
          type="button"
          title={t('Open Table of Contents')}
          aria-label={t('Open Table of Contents')}
          class={baseIconClasses}
          onclick={tocClick}
      >
        <Fa icon={faList}/>
      </button>
    {/if}
    <button
        type="button"
        title={t('Create Bookmark')}
        aria-label={isBookmarkScreen ? t('Update Bookmark') : t('Create Bookmark')}
        class={baseIconClasses}
        onclick={bookmarkClick}
    >
      <Fa icon={isBookmarkScreen ? fasBookmark : farBookmark}/>
    </button>
    {#if hasBookmarkData}
      <button
          type="button"
          title={t('Return to Bookmark')}
          aria-label={t('Return to Bookmark')}
          class={baseIconClasses}
          onclick={scrollToBookmarkClick}
      >
        <Fa icon={faRotateLeft}/>
      </button>
    {/if}
    {#if $viewMode$ === ViewMode.Continuous && !$isMobile$}
      <div
          class="flex h-10 items-center rounded-full px-4 text-sm font-semibold tracking-wide text-white/90 xl:h-8 xl:px-3"
          title={t('Current Autoscroll Speed')}
      >
        {t('{speed}x Auto', {speed: autoScrollMultiplier})}
      </div>
    {/if}
  </div>

  <div class="{toolbarGroupClasses} transform-gpu {translateXHeaderFa}">
    <button
        type="button"
        title={t('Complete Book')}
        aria-label={t('Mark as read')}
        class={baseIconClasses}
        onclick={completeBook}
    >
      <Fa icon={faFlag}/>
    </button>
    {#if $customReadingPointEnabled$ || $viewMode$ === ViewMode.Paginated}
      <div class="flex">
        <Popover
            placement="bottom"
            fallbackPlacements={['bottom-end', 'bottom-start']}
            yOffset={0}
            bind:this={customReadingPointMenuElm}
        >
          <button
              slot="icon"
              type="button"
              title={t('Open Custom Point Actions')}
              aria-label={t('Reading point')}
              class={baseIconClasses}
          >
            <Fa icon={faCrosshairs}/>
          </button>
          <div class="w-44 rounded bg-slate-900 p-1 shadow-xl md:w-36" slot="content">
            {#each customReadingPointMenuItems as actionItem (actionItem.label)}
              <button
                  type="button"
                  class="block w-full rounded px-4 py-2 text-left text-sm font-medium text-white/90 hover:bg-white hover:text-slate-900 focus-visible:bg-white focus-visible:text-slate-900 focus-visible:outline-none"
                  onclick={() => dispatchCustomReadingPointAction(actionItem.action)}
              >
                {actionItem.label}
              </button>
            {/each}
          </div>
        </Popover>
      </div>
    {/if}
    {#if showFullscreenButton}
      <button
          type="button"
          title={t('Toggle Fullscreen')}
          aria-label={t('Toggle Fullscreen')}
          class={baseIconClasses}
          onclick={fullscreenClick}
      >
        <Fa icon={faExpand}/>
      </button>
    {/if}
    <button
        type="button"
        title={t('Images')}
        aria-label={t('View images')}
        class={baseIconClasses}
        onclick={readerImageGalleryClick}
    >
      <Fa icon={faImages}/>
    </button>
    <button
        type="button"
        title={t('Settings')}
        aria-label={t('Reader settings')}
        class={baseIconClasses}
        onclick={settingsClick}
    >
      <Fa icon={faCog}/>
    </button>

    <button
        type="button"
        title={t('Close Book')}
        aria-label={t('Close Book')}
        class={baseIconClasses}
        onclick={closeBook}
    >
      <Fa icon={faSignOutAlt}/>
    </button>
  </div>
</nav>
